package dj.velox.client.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dj.velox.client.data.remote.AuthRepository
import dj.velox.client.data.remote.OrderService
import dj.velox.client.domain.model.Order
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Commande food à afficher dans le widget flottant de suivi (hissé au niveau du graphe
 * authentifié, comme [FavoritesViewModel][dj.velox.client.feature.food.FavoritesViewModel]).
 * **Réactif à l'uid** : bascule sur la dernière commande du compte courant, se vide à la
 * déconnexion.
 *
 * Visibilité :
 * - masqué si aucune commande, ou `status == cancelled`.
 * - visible tant que la commande est active (pending → delivering).
 * - visible encore [HIDE_DELAY_MS] après `deliveredAt` une fois `completed`, puis se masque seul.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActiveOrderViewModel @Inject constructor(
    private val repo: AuthRepository,
    private val orderService: OrderService,
) : ViewModel() {

    private val _visibleOrder = MutableStateFlow<Order?>(null)
    val visibleOrder: StateFlow<Order?> = _visibleOrder.asStateFlow()

    private var hideJob: Job? = null

    init {
        repo.authState
            .flatMapLatest { user ->
                if (user == null) flowOf(null) else orderService.streamLatestOrder(user.uid)
            }
            .onEach(::applyOrder)
            .launchIn(viewModelScope)
    }

    private fun applyOrder(order: Order?) {
        hideJob?.cancel()
        when {
            order == null || order.status == Order.STATUS_CANCELLED -> _visibleOrder.value = null

            order.status == Order.STATUS_COMPLETED -> {
                val deliveredAt = order.deliveredAt ?: order.updatedAt
                val remaining = HIDE_DELAY_MS - (System.currentTimeMillis() - deliveredAt)
                if (remaining <= 0) {
                    _visibleOrder.value = null
                } else {
                    _visibleOrder.value = order
                    hideJob = viewModelScope.launch {
                        delay(remaining)
                        _visibleOrder.value = null
                    }
                }
            }

            else -> _visibleOrder.value = order
        }
    }

    private companion object {
        /** Durée d'affichage du widget après livraison, avant disparition automatique. */
        const val HIDE_DELAY_MS = 8_000L
    }
}
