package dj.velox.client.feature.taxi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dj.velox.client.core.analytics.VeloxAnalytics
import dj.velox.client.data.local.VeloxLocalStore
import dj.velox.client.data.remote.RideService
import dj.velox.client.domain.model.Ride
import dj.velox.client.domain.model.RideStatus
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

/** État de la course active (miroir ActiveRideState). */
data class ActiveRideState(
    val ride: Ride? = null,
    val isLoading: Boolean = false,
    val isCreating: Boolean = false,
    val isWatching: Boolean = false,
    val error: String? = null,
) {
    val hasActiveRide: Boolean get() = ride != null && ride.isActive
    val isWaitingForDriver: Boolean get() = ride?.status == RideStatus.REQUESTED
    val hasDriver: Boolean get() = ride?.driverId != null
    val isTerminated: Boolean
        get() = ride == null ||
            ride.status == RideStatus.COMPLETED ||
            ride.status == RideStatus.CANCELLED ||
            ride.status == RideStatus.NO_DRIVER_AVAILABLE
    val rideId: String? get() = ride?.rideId
}

/**
 * Cerveau de la course active — portage d'ActiveRideNotifier (Flutter).
 *
 * Cycle : cache DataStore (affichage immédiat) → fetch one-time Firestore →
 * stream temps réel avec reconnexion backoff (2→4→8→16s) → persistance à chaque
 * mise à jour → nettoyage à la fin. Restauration après kill via le rideId persisté.
 *
 * NB : la notification des chauffeurs est gérée côté serveur par la Cloud Function
 * onTaxiRideCreated — pas besoin de l'appeler depuis le client.
 */
@HiltViewModel
class RideViewModel @Inject constructor(
    private val rideService: RideService,
    private val store: VeloxLocalStore,
    private val json: Json,
) : ViewModel() {

    private val _state = MutableStateFlow(ActiveRideState())
    val state: StateFlow<ActiveRideState> = _state.asStateFlow()

    private var streamJob: Job? = null
    private var reconnectJob: Job? = null
    private var isPaused = false

    init { restore() }

    // ════════════════════════════════════════════════════════════
    // INIT / RESTAURATION
    // ════════════════════════════════════════════════════════════

    private fun restore() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            // 1) Cache local pour affichage immédiat
            store.getRideJson()?.let { cached ->
                runCatching { json.decodeFromString<Ride>(cached) }
                    .onSuccess { ride ->
                        if (!ride.isActive) {
                            store.clearRide()
                            _state.value = ActiveRideState(isLoading = false)
                            return@launch
                        }
                        _state.value = _state.value.copy(ride = ride, isLoading = false)
                    }
                    .onFailure { store.clearRide() }
            }

            // 2) rideId connu ?
            val rideId = store.getRideId()
            if (rideId == null) {
                _state.value = _state.value.copy(isLoading = false)
                return@launch
            }

            // 3) Fetch one-time pour vérifier l'état serveur
            runCatching { rideService.getRideById(rideId) }
                .onSuccess { ride ->
                    when {
                        ride == null || !ride.isActive -> { clearAndReset(); return@launch }
                        else -> {
                            _state.value = _state.value.copy(ride = ride, isLoading = false, error = null)
                            persist(ride)
                        }
                    }
                }
                .onFailure { _state.value = _state.value.copy(isLoading = false, error = it.message) }

            // 4) Stream temps réel
            startStream(rideId)
        }
    }

    // ════════════════════════════════════════════════════════════
    // STREAM + RECONNEXION BACKOFF
    // ════════════════════════════════════════════════════════════

    private fun startStream(rideId: String, attempt: Int = 1) {
        streamJob?.cancel()
        reconnectJob?.cancel()
        _state.value = _state.value.copy(isWatching = true)

        streamJob = viewModelScope.launch {
            rideService.listenToRide(rideId)
                .catch { error ->
                    _state.value = _state.value.copy(isWatching = false, error = error.message)
                    scheduleReconnect(rideId, attempt)
                }
                .collect { ride ->
                    _state.value = _state.value.copy(ride = ride, isWatching = true, error = null)
                    persist(ride)
                    if (!ride.isActive) {
                        val terminatedId = ride.rideId
                        delay(4_000)
                        if (_state.value.rideId == terminatedId) clearAndReset()
                    }
                }
        }
    }

    /** Reconnexion progressive : 2s → 4s → 8s → 16s. */
    private fun scheduleReconnect(rideId: String, attempt: Int) {
        reconnectJob?.cancel()
        val delayMs = (2_000L * attempt).coerceIn(2_000L, 16_000L)
        reconnectJob = viewModelScope.launch {
            delay(delayMs)
            if (!isPaused && !_state.value.isTerminated) {
                startStream(rideId, attempt + 1)
            }
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
            val rideId = store.getRideId()
            if (rideId != null && !_state.value.isTerminated) {
                if (streamJob?.isActive != true) startStream(rideId)
                else runCatching { rideService.getRideById(rideId) }.getOrNull()?.let { ride ->
                    _state.value = _state.value.copy(ride = ride, error = null)
                    persist(ride)
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════
    // CRÉER UNE COURSE (avec retry exponentiel)
    // ════════════════════════════════════════════════════════════

    suspend fun createRide(
        userId: String,
        userName: String,
        userPhone: String,
        userPhotoUrl: String?,
        pickupLatitude: Double,
        pickupLongitude: Double,
        pickupAddress: String,
        pickupPlaceName: String,
        destinationLatitude: Double,
        destinationLongitude: Double,
        destinationAddress: String,
        destinationPlaceName: String,
        distance: Double,
        estimatedDuration: Int,
        estimatedFare: Double,
        vehicleType: String,
        paymentMethod: String,
    ): String {
        _state.value = _state.value.copy(isCreating = true, isLoading = true, error = null)
        return try {
            val rideId = retry(maxRetries = 3) {
                rideService.createRide(
                    userId, userName, userPhone, userPhotoUrl,
                    pickupLatitude, pickupLongitude, pickupAddress, pickupPlaceName,
                    destinationLatitude, destinationLongitude, destinationAddress, destinationPlaceName,
                    distance, estimatedDuration, estimatedFare, vehicleType, paymentMethod,
                )
            }
            store.saveRide(rideId, json.encodeToString(placeholderRide(rideId)))
            // saveRide écrit un placeholder ; le stream remplacera par le vrai snapshot
            startStream(rideId)
            VeloxAnalytics.rideConfirmed(vehicleType, estimatedFare, distance, paymentMethod)
            _state.value = _state.value.copy(isCreating = false, isLoading = false)
            rideId
        } catch (e: Exception) {
            _state.value = _state.value.copy(isCreating = false, isLoading = false, error = e.message)
            throw e
        }
    }

    // ════════════════════════════════════════════════════════════
    // ANNULER / RESET
    // ════════════════════════════════════════════════════════════

    suspend fun cancelRide(reason: String) {
        val id = store.getRideId() ?: _state.value.rideId ?: return
        retry(maxRetries = 3) { rideService.cancelRide(id, reason, "user") }
        // Le stream recevra 'cancelled' → clearAndReset() automatique
    }

    fun clearRide() {
        viewModelScope.launch { clearAndReset() }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    // ════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════

    private suspend fun persist(ride: Ride) {
        runCatching { store.saveRide(ride.rideId, json.encodeToString(ride)) }
    }

    private suspend fun clearAndReset() {
        streamJob?.cancel(); streamJob = null
        reconnectJob?.cancel(); reconnectJob = null
        store.clearRide()
        _state.value = ActiveRideState()
    }

    /** Course minimale pour persister immédiatement le rideId avant le 1er snapshot. */
    private fun placeholderRide(rideId: String): Ride = Ride(
        rideId = rideId,
        userId = _state.value.ride?.userId ?: "",
        pickup = dj.velox.client.domain.model.RideLocation(0.0, 0.0, ""),
        destination = dj.velox.client.domain.model.RideLocation(0.0, 0.0, ""),
        status = RideStatus.REQUESTED,
        requestedAt = System.currentTimeMillis(),
    )

    private suspend fun <T> retry(
        maxRetries: Int,
        initialDelayMs: Long = 1_000,
        block: suspend () -> T,
    ): T {
        var currentDelay = initialDelayMs
        repeat(maxRetries - 1) {
            try {
                return block()
            } catch (e: Exception) {
                delay(currentDelay)
                currentDelay *= 2
            }
        }
        return block() // dernière tentative (laisse remonter l'exception)
    }

    override fun onCleared() {
        streamJob?.cancel()
        reconnectJob?.cancel()
        super.onCleared()
    }
}
