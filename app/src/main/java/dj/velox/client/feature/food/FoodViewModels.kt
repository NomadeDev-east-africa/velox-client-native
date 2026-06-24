package dj.velox.client.feature.food

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dj.velox.client.data.remote.MenuService
import dj.velox.client.data.remote.RestaurantService
import dj.velox.client.domain.model.MenuItem
import dj.velox.client.domain.model.Restaurant
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Catégorie food (vignette de carrousel → mène au restaurant qui propose le plat). */
data class FoodCategory(val name: String, val imageUrl: String, val restaurantId: String)

/**
 * Accueil food : restaurants (stream temps réel) + « meilleurs choix » (top par note)
 * + catégories dérivées des menus (port de `_CategoryRow` / `popularRestaurantsProvider`).
 */
@HiltViewModel
class RestaurantsViewModel @Inject constructor(
    service: RestaurantService,
    menuService: MenuService,
) : ViewModel() {
    val restaurants: StateFlow<List<Restaurant>> = service.streamRestaurants()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** « Meilleurs choix » : restaurants notés, triés par note décroissante. */
    val popular: StateFlow<List<Restaurant>> = restaurants
        .map { list -> list.filter { it.rating > 0 }.sortedByDescending { it.rating }.take(8) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _categories = MutableStateFlow<List<FoodCategory>>(emptyList())
    val categories: StateFlow<List<FoodCategory>> = _categories.asStateFlow()

    init {
        viewModelScope.launch {
            val menus = menuService.getAllMenus()
            // 1 vignette par catégorie : premier plat ayant une image.
            _categories.value = menus
                .filter { !it.imageUrl.isNullOrEmpty() }
                .groupBy { it.category }
                .map { (cat, items) -> FoodCategory(cat, items.first().imageUrl!!, items.first().restaurantId) }
        }
    }
}

/** Menu d'un restaurant donné (restaurantId via la route de navigation). */
@HiltViewModel
class MenuViewModel @Inject constructor(
    menuService: MenuService,
    restaurantService: RestaurantService,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val restaurantId: String = savedStateHandle["restaurantId"] ?: ""

    val menu: StateFlow<List<MenuItem>> = menuService.streamMenus(restaurantId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _restaurant = MutableStateFlow<Restaurant?>(null)
    val restaurant: StateFlow<Restaurant?> = _restaurant.asStateFlow()

    init {
        viewModelScope.launch {
            _restaurant.value = restaurantService.getRestaurantById(restaurantId)
        }
    }
}

/** Suivi de la position du livreur (port de `track_delivery_screen`). */
@HiltViewModel
class TrackDeliveryViewModel @Inject constructor(
    private val orderService: dj.velox.client.data.remote.OrderService,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    data class State(
        val driverName: String? = null,
        val destination: dj.velox.client.domain.model.LatLng? = null,
        val driver: dj.velox.client.data.remote.DriverLocation? = null,
        val loading: Boolean = true,
        val noDriver: Boolean = false,
    )

    private val orderId: String = savedStateHandle["orderId"] ?: ""
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val order = orderService.getOrderById(orderId)
            val driverId = order?.deliveryDriverId
            _state.value = _state.value.copy(
                driverName = order?.deliveryDriverName,
                destination = order?.deliveryLocation,
                noDriver = driverId.isNullOrBlank(),
                loading = !driverId.isNullOrBlank(),
            )
            if (!driverId.isNullOrBlank()) {
                orderService.streamDriverLocation(driverId).collect { loc ->
                    _state.value = _state.value.copy(driver = loc, loading = false)
                }
            }
        }
    }
}

/** Écran « commande livrée » : charge la commande + soumet la notation resto/livreur. */
@HiltViewModel
class OrderCompletedViewModel @Inject constructor(
    private val orderService: dj.velox.client.data.remote.OrderService,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val orderId: String = savedStateHandle["orderId"] ?: ""

    private val _order = MutableStateFlow<dj.velox.client.domain.model.Order?>(null)
    val order: StateFlow<dj.velox.client.domain.model.Order?> = _order.asStateFlow()

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    init {
        viewModelScope.launch { _order.value = orderService.getOrderById(orderId) }
    }

    fun submit(restaurantRating: Int, driverRating: Int, comment: String?, onDone: () -> Unit) {
        val o = _order.value ?: return onDone()
        viewModelScope.launch {
            _submitting.value = true
            runCatching { orderService.submitOrderRating(o, restaurantRating, driverRating, comment) }
            _submitting.value = false
            onDone()
        }
    }
}

/** Charge le plat + son restaurant (args de route) pour l'écran d'options. */
@HiltViewModel
class AddToOrderViewModel @Inject constructor(
    menuService: MenuService,
    restaurantService: RestaurantService,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val restaurantId: String = savedStateHandle["restaurantId"] ?: ""
    private val menuId: String = savedStateHandle["menuId"] ?: ""

    private val _item = MutableStateFlow<MenuItem?>(null)
    val item: StateFlow<MenuItem?> = _item.asStateFlow()

    private val _restaurant = MutableStateFlow<Restaurant?>(null)
    val restaurant: StateFlow<Restaurant?> = _restaurant.asStateFlow()

    init {
        viewModelScope.launch {
            _item.value = menuService.getMenuById(menuId)
            _restaurant.value = restaurantService.getRestaurantById(restaurantId)
        }
    }
}

/**
 * Recherche food (port de `food_search_screen.dart`) : charge une fois restaurants + menus,
 * filtre en mémoire avec un debounce de 300 ms (restaurants par nom, plats par nom/description).
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class FoodSearchViewModel @Inject constructor(
    private val restaurantService: RestaurantService,
    private val menuService: MenuService,
) : ViewModel() {

    data class Results(
        val restaurants: List<Restaurant> = emptyList(),
        val dishes: List<MenuItem> = emptyList(),
    )

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _results = MutableStateFlow(Results())
    val results: StateFlow<Results> = _results.asStateFlow()

    private var allRestaurants: List<Restaurant> = emptyList()
    private var allMenus: List<MenuItem> = emptyList()
    private var byId: Map<String, Restaurant> = emptyMap()

    /** Nom du restaurant pour un plat (sous-titre de la tuile plat). */
    fun restaurantNameFor(dish: MenuItem): String? = byId[dish.restaurantId]?.name

    init {
        viewModelScope.launch {
            val r = async { restaurantService.getRestaurants() }
            val m = async { menuService.getAllMenus() }
            allRestaurants = r.await()
            allMenus = m.await()
            byId = allRestaurants.associateBy { it.id }
            _loading.value = false
            runSearch(_query.value)
        }
        viewModelScope.launch {
            _query.debounce(300).collect { runSearch(it) }
        }
    }

    fun onQueryChange(value: String) { _query.value = value }

    private fun runSearch(value: String) {
        val q = value.trim().lowercase()
        if (q.isEmpty()) {
            _results.value = Results()
            return
        }
        _results.value = Results(
            restaurants = allRestaurants.filter { it.name.lowercase().contains(q) },
            dishes = allMenus.filter {
                byId.containsKey(it.restaurantId) &&
                    (it.name.lowercase().contains(q) || it.description.lowercase().contains(q))
            },
        )
    }
}
