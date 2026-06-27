package dj.velox.client.feature.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dj.velox.client.core.Constants
import dj.velox.client.core.analytics.VeloxAnalytics
import dj.velox.client.data.local.VeloxLocalStore
import dj.velox.client.data.remote.OrderService
import dj.velox.client.domain.model.LatLng
import dj.velox.client.domain.model.Order
import dj.velox.client.domain.model.OrderItem
import dj.velox.client.domain.model.Restaurant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** Snapshot du panier persisté (DataStore). */
@Serializable
private data class CartData(
    val restaurant: Restaurant? = null,
    val items: List<OrderItem> = emptyList(),
)

/** État du panier (miroir CartState). */
data class CartUiState(
    val items: List<OrderItem> = emptyList(),
    val restaurant: Restaurant? = null,
    val isCreatingOrder: Boolean = false,
    val error: String? = null,
) {
    val deliveryFee: Int get() = Constants.DELIVERY_FEE
    val isEmpty: Boolean get() = items.isEmpty()
    val itemCount: Int get() = items.sumOf { it.quantity }
    val subtotal: Int get() = items.sumOf { it.totalPrice }
    val total: Int get() = subtotal + deliveryFee

    fun isDifferentRestaurant(restaurantId: String): Boolean =
        restaurant != null && restaurant.id != restaurantId
}

/**
 * Paramètres de livraison/paiement saisis au checkout, retenus en attendant la confirmation
 * « Poursuivre ». L'identité client (uid, nom, téléphone) vient de la session à la création.
 */
data class PendingCheckout(
    val paymentMethod: String,
    val deliveryAddress: String,
    val deliveryLat: Double?,
    val deliveryLng: Double?,
    val pointsUsed: Int,
)

/**
 * Panier food — portage de CartNotifier. Persistance via VeloxLocalStore (box cart),
 * réduction fidélité plafonnée aux frais de livraison.
 */
@HiltViewModel
class CartViewModel @Inject constructor(
    private val orderService: OrderService,
    private val store: VeloxLocalStore,
    private val json: Json,
) : ViewModel() {

    private val _state = MutableStateFlow(CartUiState())
    val state: StateFlow<CartUiState> = _state.asStateFlow()

    /** Solde de points fidélité disponible (gagnés via commandes « completed » − rachetés). */
    private val _availablePoints = MutableStateFlow<Int?>(null)
    val availablePoints: StateFlow<Int?> = _availablePoints.asStateFlow()

    /**
     * Paramètres de checkout retenus à l'étape « Commander », en attente de confirmation
     * (« Poursuivre »). Tant qu'ils sont là, AUCUNE commande n'est créée en base : c'est l'écran
     * de pré-confirmation (PendingOrderScreen) qui appellera [createOrder] au bon moment.
     */
    private val _pendingCheckout = MutableStateFlow<PendingCheckout?>(null)
    val pendingCheckout: StateFlow<PendingCheckout?> = _pendingCheckout.asStateFlow()

    fun prepareCheckout(checkout: PendingCheckout) { _pendingCheckout.value = checkout }
    fun clearPendingCheckout() { _pendingCheckout.value = null }

    init { restore() }

    /** Recalcule le solde dispo comme Flutter : (commandes completed × 10) − redeemedPoints. */
    fun loadAvailablePoints(userId: String, redeemed: Int) {
        viewModelScope.launch {
            val earned = orderService.getCompletedOrdersCount(userId) * 10
            _availablePoints.value = (earned - redeemed).coerceAtLeast(0)
        }
    }

    private fun restore() {
        viewModelScope.launch {
            val cached = store.getCartJson() ?: return@launch
            runCatching { json.decodeFromString<CartData>(cached) }
                .onSuccess { data ->
                    if (data.items.isEmpty()) store.clearCart()
                    else _state.value = CartUiState(items = data.items, restaurant = data.restaurant)
                }
                .onFailure { store.clearCart() }
        }
    }

    private fun persist() {
        viewModelScope.launch {
            val s = _state.value
            if (s.isEmpty) store.clearCart()
            else runCatching {
                store.saveCart(json.encodeToString(CartData(s.restaurant, s.items)))
            }
        }
    }

    fun setRestaurant(restaurant: Restaurant) {
        _state.value = _state.value.copy(restaurant = restaurant)
        persist()
    }

    fun addItem(item: OrderItem) {
        _state.value = _state.value.copy(items = _state.value.items + item)
        _state.value.restaurant?.let { VeloxAnalytics.addToCart(it.id, item.name, item.totalPrice, item.quantity) }
        persist()
    }

    fun incrementAt(index: Int) {
        val items = _state.value.items.toMutableList()
        if (index !in items.indices) return
        items[index] = items[index].copy(quantity = items[index].quantity + 1)
        _state.value = _state.value.copy(items = items)
        persist()
    }

    fun decrementAt(index: Int) {
        val items = _state.value.items.toMutableList()
        if (index !in items.indices) return
        if (items[index].quantity > 1) {
            items[index] = items[index].copy(quantity = items[index].quantity - 1)
            _state.value = _state.value.copy(items = items)
        } else {
            items.removeAt(index)
            _state.value = _state.value.copy(
                items = items,
                restaurant = if (items.isEmpty()) null else _state.value.restaurant,
            )
        }
        persist()
    }

    fun removeAt(index: Int) {
        val items = _state.value.items.toMutableList()
        if (index !in items.indices) return
        items.removeAt(index)
        _state.value = _state.value.copy(
            items = items,
            restaurant = if (items.isEmpty()) null else _state.value.restaurant,
        )
        persist()
    }

    fun clearCart() {
        _state.value = CartUiState()
        viewModelScope.launch { store.clearCart() }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }

    /** Crée la commande Firestore. Retourne l'orderId ou null. */
    suspend fun createOrder(
        userId: String,
        customerName: String,
        customerPhone: String,
        paymentMethod: String,
        deliveryAddress: String,
        deliveryLocation: LatLng? = null,
        addressDetails: String? = null,
        pointsUsed: Int = 0,
    ): String? {
        val s = _state.value
        val restaurant = s.restaurant ?: return null
        if (s.isEmpty) return null

        _state.value = s.copy(isCreatingOrder = true, error = null)

        // Réduction fidélité : plafonnée aux frais de livraison (le subtotal reste intact)
        val maxByDelivery = Constants.DELIVERY_FEE / Constants.POINT_VALUE
        val safePoints = pointsUsed.coerceIn(0, maxByDelivery)
        val discount = safePoints * Constants.POINT_VALUE

        val now = System.currentTimeMillis()
        val order = Order(
            id = "",
            userId = userId,
            restaurantId = restaurant.id,
            restaurantName = restaurant.name,
            restaurantImageUrl = restaurant.imageUrl,
            customerName = customerName,
            customerPhone = customerPhone,
            items = s.items,
            deliveryFee = Constants.DELIVERY_FEE,
            status = Order.STATUS_PENDING,
            paymentMethod = paymentMethod,
            deliveryAddress = deliveryAddress,
            deliveryLocation = deliveryLocation,
            addressDetails = addressDetails,
            createdAt = now,
            updatedAt = now,
            pointsUsed = safePoints,
            discount = discount,
        )

        val orderId = runCatching { orderService.createOrder(order) }.getOrNull()
        if (orderId == null) {
            _state.value = _state.value.copy(isCreatingOrder = false, error = "Échec création commande")
            return null
        }

        // Event métier : commande effectivement envoyée au restaurant (capturé avant le vidage panier).
        VeloxAnalytics.orderPlaced(orderId, restaurant.id, order.total, paymentMethod)

        store.clearOrder()
        // Débit des points fidélité utilisés (incrément redeemedPoints), comme Flutter.
        if (safePoints > 0) runCatching { orderService.redeemPoints(userId, safePoints) }
        clearCart()
        clearPendingCheckout()
        _state.value = _state.value.copy(isCreatingOrder = false)
        return orderId
    }
}
