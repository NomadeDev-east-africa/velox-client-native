package dj.velox.client.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dj.velox.client.data.local.VeloxLocalStore
import dj.velox.client.data.remote.OrderService
import dj.velox.client.data.remote.RideService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Restauration au lancement (port de `_listenForActiveOrder` / `_listenForActiveRide`
 * de `main.dart`). Après un kill ou « Don't keep activities », l'orderId/rideId d'une
 * commande/course active reste persisté dans le DataStore. Au démarrage, on vérifie
 * que cette commande/course est toujours active côté serveur ; si oui, on expose une
 * cible de restauration → le graphe de navigation ouvre directement l'écran de suivi
 * (au lieu de rester bloqué sur l'accueil).
 *
 * Indépendant du payload des notifications : même si la notif « commande en route »
 * ne porte pas l'orderId, le suivi est restauré depuis le cache.
 */
@HiltViewModel
class ActiveSessionViewModel @Inject constructor(
    private val store: VeloxLocalStore,
    private val orderService: OrderService,
    private val rideService: RideService,
) : ViewModel() {

    /** Cible de restauration détectée au lancement. */
    sealed interface Restore {
        data class ActiveOrder(val orderId: String) : Restore
        data class ActiveRide(val rideId: String) : Restore
    }

    private val _restore = MutableStateFlow<Restore?>(null)
    val restore: StateFlow<Restore?> = _restore.asStateFlow()

    private var checked = false

    /** Vérifie une seule fois (au montage du graphe) s'il y a une commande/course active à restaurer. */
    fun checkOnce() {
        if (checked) return
        checked = true
        viewModelScope.launch {
            // 1) Commande food active ?
            store.getOrderId()?.let { orderId ->
                val order = runCatching { orderService.getOrderById(orderId) }.getOrNull()
                if (order != null && order.isActive) {
                    _restore.value = Restore.ActiveOrder(orderId)
                    return@launch
                }
            }
            // 2) Sinon, course taxi active ?
            store.getRideId()?.let { rideId ->
                val ride = runCatching { rideService.getRideById(rideId) }.getOrNull()
                if (ride != null && ride.isActive) {
                    _restore.value = Restore.ActiveRide(rideId)
                }
            }
        }
    }

    fun consume() { _restore.value = null }
}
