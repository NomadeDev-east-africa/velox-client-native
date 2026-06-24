package dj.velox.client.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dj.velox.client.data.remote.AuthRepository
import dj.velox.client.data.remote.OrderService
import dj.velox.client.data.remote.OrderStats
import dj.velox.client.data.remote.RideService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Stats de l'accueil (port de `orderStatsProvider` Flutter) : compte des commandes livrées
 * + total dépensé, calculés **en direct** depuis la collection `orders` (pas depuis le doc
 * `users/{uid}`, qui ne porte pas ces champs). Les points de fidélité s'en déduisent
 * (commandes × 10) ; le solde disponible se calcule dans l'écran avec `redeemedPoints`.
 */
@HiltViewModel
class HomeStatsViewModel @Inject constructor(
    orderService: OrderService,
    rideService: RideService,
    authRepo: AuthRepository,
) : ViewModel() {

    private val uid: String? = authRepo.currentUser?.uid

    val stats: StateFlow<OrderStats> =
        (if (uid != null) orderService.streamUserStats(uid) else flowOf(OrderStats()))
            .catch { emit(OrderStats()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrderStats())

    /** Nombre de courses terminées (stat « Courses » de l'accueil), live. */
    val ridesCount: StateFlow<Int> =
        (if (uid != null) rideService.streamCompletedRidesCount(uid) else flowOf(0))
            .catch { emit(0) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
