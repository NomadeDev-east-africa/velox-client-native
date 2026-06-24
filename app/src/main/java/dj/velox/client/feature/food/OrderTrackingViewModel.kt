package dj.velox.client.feature.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dj.velox.client.data.local.VeloxLocalStore
import dj.velox.client.data.remote.OrderService
import dj.velox.client.domain.model.Order
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** État de la commande active (miroir ActiveOrderState). */
data class ActiveOrderState(
    val order: Order? = null,
    val isLoading: Boolean = false,
    val isWatching: Boolean = false,
    val attached: Boolean = false,
    val error: String? = null,
) {
    val orderId: String? get() = order?.id
    val hasActiveOrder: Boolean get() = order != null && order.isActive
    val isTerminated: Boolean get() = order == null || order.isCompleted
}

/**
 * Cerveau de la commande active — portage d'ActiveOrderNotifier (Flutter).
 *
 * Cycle identique au RideViewModel : cache DataStore (affichage immédiat) →
 * fetch one-time Firestore → stream temps réel avec reconnexion backoff (2→16s) →
 * persistance à chaque mise à jour → nettoyage à la fin. Restauration après kill
 * via l'orderId persisté (box `active_order`).
 *
 * NB : la notification du restaurant est gérée côté serveur (Cloud Function
 * onFoodOrderCreated) — pas d'appel client.
 */
@HiltViewModel
class OrderTrackingViewModel @Inject constructor(
    private val orderService: OrderService,
    private val store: VeloxLocalStore,
    private val json: Json,
) : ViewModel() {

    private val _state = MutableStateFlow(ActiveOrderState())
    val state: StateFlow<ActiveOrderState> = _state.asStateFlow()

    private var streamJob: Job? = null
    private var reconnectJob: Job? = null
    private var isPaused = false
    private var attachCalled = false

    init { restore() }

    // ════════════════════════════════════════════════════════════
    // INIT / RESTAURATION (cache → one-time Firestore → stream)
    // ════════════════════════════════════════════════════════════

    private fun restore() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            // 1) Cache local pour affichage immédiat
            store.getOrderJson()?.let { cached ->
                runCatching { json.decodeFromString<Order>(cached) }
                    .onSuccess { order ->
                        if (!order.isActive) {
                            store.clearOrder()
                            _state.value = ActiveOrderState(isLoading = false)
                            return@launch
                        }
                        _state.value = _state.value.copy(order = order, isLoading = false)
                    }
                    .onFailure { store.clearOrder() }
            }

            // 2) orderId connu ?
            val orderId = store.getOrderId()
            if (orderId == null) {
                _state.value = _state.value.copy(isLoading = false)
                return@launch
            }
            // attachOrder() appelé entre-temps → ne pas écraser le nouvel état
            if (attachCalled) {
                _state.value = _state.value.copy(isLoading = false)
                return@launch
            }

            // 3) Fetch one-time pour vérifier l'état serveur
            runCatching { orderService.getOrderById(orderId) }
                .onSuccess { order ->
                    when {
                        order == null || !order.isActive -> { clearAndReset(); return@launch }
                        else -> {
                            _state.value = _state.value.copy(order = order, isLoading = false, error = null)
                            persist(order)
                        }
                    }
                }
                .onFailure { _state.value = _state.value.copy(isLoading = false, error = it.message) }

            // 4) Stream temps réel
            if (!attachCalled) startStream(orderId)
        }
    }

    // ════════════════════════════════════════════════════════════
    // ATTACHER UNE COMMANDE (création immédiate ou deep-link)
    // ════════════════════════════════════════════════════════════

    /** Rattache le suivi à [orderId] : annule le suivi courant, persiste, fetch + stream. */
    fun attachOrder(orderId: String) {
        // Idempotence : déjà actif/en cours sur ce même orderId → ignorer
        val current = _state.value
        if (current.orderId == orderId && (current.isLoading || current.isWatching)) return

        attachCalled = true
        streamJob?.cancel(); streamJob = null
        reconnectJob?.cancel(); reconnectJob = null

        viewModelScope.launch {
            store.clearOrder()
            _state.value = _state.value.copy(isLoading = true, attached = true, error = null)

            runCatching { orderService.getOrderById(orderId) }
                .onSuccess { order ->
                    if (order != null) {
                        _state.value = _state.value.copy(order = order, isLoading = false, error = null)
                        persist(order)
                    } else {
                        _state.value = _state.value.copy(isLoading = false)
                    }
                }
                .onFailure { _state.value = _state.value.copy(isLoading = false, error = it.message) }

            startStream(orderId)
        }
    }

    // ════════════════════════════════════════════════════════════
    // STREAM + RECONNEXION BACKOFF (2→4→8→16s)
    // ════════════════════════════════════════════════════════════

    private fun startStream(orderId: String, attempt: Int = 1) {
        streamJob?.cancel()
        reconnectJob?.cancel()
        _state.value = _state.value.copy(isWatching = true)

        streamJob = viewModelScope.launch {
            orderService.listenToOrder(orderId)
                .catch { error ->
                    _state.value = _state.value.copy(isWatching = false, error = error.message)
                    scheduleReconnect(orderId, attempt)
                }
                .collect { order ->
                    _state.value = _state.value.copy(order = order, isWatching = true, error = null)
                    if (order.isActive) {
                        persist(order)
                    } else {
                        // Terminée (livrée/annulée) : on arrête de suivre et on efface le cache
                        // (pas de restauration au prochain lancement) ; l'order reste affiché
                        // pour montrer l'état final à l'écran.
                        streamJob?.cancel(); streamJob = null
                        reconnectJob?.cancel(); reconnectJob = null
                        store.clearOrder()
                        _state.value = _state.value.copy(isWatching = false)
                    }
                }
        }
    }

    private fun scheduleReconnect(orderId: String, attempt: Int) {
        reconnectJob?.cancel()
        val delayMs = (2_000L * attempt).coerceIn(2_000L, 16_000L)
        reconnectJob = viewModelScope.launch {
            delay(delayMs)
            if (!isPaused && !_state.value.isTerminated) startStream(orderId, attempt + 1)
        }
    }

    // ════════════════════════════════════════════════════════════
    // CYCLE DE VIE (appelé par l'écran selon onStart/onStop)
    // ════════════════════════════════════════════════════════════

    fun pauseStream() {
        if (isPaused) return
        isPaused = true
        reconnectJob?.cancel()
    }

    fun resumeStream() {
        if (!isPaused) return
        isPaused = false
        viewModelScope.launch {
            val orderId = store.getOrderId() ?: return@launch
            if (_state.value.isTerminated) return@launch
            if (streamJob?.isActive != true) startStream(orderId)
            else runCatching { orderService.getOrderById(orderId) }.getOrNull()?.let { order ->
                _state.value = _state.value.copy(order = order, error = null)
                if (order.isActive) persist(order)
            }
        }
    }

    // ════════════════════════════════════════════════════════════
    // ANNULER / RESET
    // ════════════════════════════════════════════════════════════

    /** Annule la commande. Le stream recevra 'cancelled' → arrêt + nettoyage auto. */
    suspend fun cancelOrder() {
        val id = store.getOrderId() ?: _state.value.orderId ?: return
        orderService.cancelOrder(id)
    }

    fun clearOrder() {
        viewModelScope.launch { clearAndReset() }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }

    // ════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════

    private suspend fun persist(order: Order) {
        runCatching { store.saveOrder(order.id, json.encodeToString(order)) }
    }

    private suspend fun clearAndReset() {
        streamJob?.cancel(); streamJob = null
        reconnectJob?.cancel(); reconnectJob = null
        attachCalled = false
        store.clearOrder()
        _state.value = ActiveOrderState()
    }

    override fun onCleared() {
        streamJob?.cancel()
        reconnectJob?.cancel()
        super.onCleared()
    }
}