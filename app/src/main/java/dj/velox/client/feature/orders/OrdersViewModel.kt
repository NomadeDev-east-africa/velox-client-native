package dj.velox.client.feature.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dj.velox.client.data.remote.AuthRepository
import dj.velox.client.data.remote.OrderService
import dj.velox.client.data.remote.RideService
import dj.velox.client.domain.model.Order
import dj.velox.client.domain.model.Ride
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Historique des commandes food (live) + courses taxi (one-shot). */
@HiltViewModel
class OrdersViewModel @Inject constructor(
    orderService: OrderService,
    private val rideService: RideService,
    authRepo: AuthRepository,
) : ViewModel() {

    private val uid: String? = authRepo.currentUser?.uid

    val foodOrders: StateFlow<List<Order>> =
        (if (uid != null) orderService.streamUserOrders(uid) else flowOf(emptyList()))
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _rides = MutableStateFlow<List<Ride>>(emptyList())
    val rides: StateFlow<List<Ride>> = _rides.asStateFlow()

    init {
        if (uid != null) viewModelScope.launch {
            _rides.value = runCatching { rideService.getUserRideHistory(uid) }.getOrDefault(emptyList())
        }
    }
}
