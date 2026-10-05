package dj.velox.client.feature.food

import dj.velox.client.core.Constants
import dj.velox.client.data.local.VeloxLocalStore
import dj.velox.client.data.remote.AuthRepository
import dj.velox.client.data.remote.OrderService
import dj.velox.client.data.remote.RestaurantService
import dj.velox.client.domain.model.ExtraOption
import dj.velox.client.domain.model.OptionChoice
import dj.velox.client.domain.model.OptionGroup
import dj.velox.client.domain.model.OptionSelection
import dj.velox.client.domain.model.OptionType
import dj.velox.client.domain.model.Order
import dj.velox.client.domain.model.OrderItem
import dj.velox.client.domain.model.Restaurant
import dj.velox.client.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CartViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val store: VeloxLocalStore = mockk(relaxed = true)
    private val orderService: OrderService = mockk(relaxed = true) {
        coEvery { getActiveOrder(any()) } returns null
    }
    private val restaurantService: RestaurantService = mockk(relaxed = true) {
        coEvery { getRestaurantById(any()) } returns restaurant()
    }
    private val authRepo: AuthRepository = mockk(relaxed = true) {
        every { authState } returns emptyFlow()
    }
    private val json = Json { ignoreUnknownKeys = true }

    private fun restaurant() = Restaurant(
        id = "r1", name = "Resto", address = "A", description = "D", email = "e", phone = "p",
        imageUrl = "", latitude = 11.5, longitude = 43.1, createdAt = 0L,
    )

    private fun burger() = OrderItem(menuId = "m1", name = "Burger", basePrice = 1000, quantity = 1)

    @Test
    fun `createOrder clamps loyalty points to the delivery-fee cap`() = runTest {
        val captured = slot<Order>()
        coEvery { orderService.createOrder(capture(captured)) } returns "order123"

        val vm = CartViewModel(orderService, restaurantService, store, json, authRepo)
        vm.setRestaurant(restaurant())
        vm.addItem(burger())

        // Bien plus de points que le plafond (DELIVERY_FEE / POINT_VALUE) → doivent être plafonnés.
        val id = vm.createOrder(
            userId = "u1", customerName = "C", customerPhone = "P",
            paymentMethod = "cash", deliveryAddress = "Djibouti", pointsUsed = 100_000,
        )

        assertEquals("order123", id)
        val maxPoints = Constants.DELIVERY_FEE / Constants.POINT_VALUE
        assertEquals(maxPoints, captured.captured.pointsUsed)
        assertEquals(maxPoints * Constants.POINT_VALUE, captured.captured.discount)
    }

    @Test
    fun `createOrder returns null when the cart is empty`() = runTest {
        val vm = CartViewModel(orderService, restaurantService, store, json, authRepo)
        val id = vm.createOrder(
            userId = "u1", customerName = "C", customerPhone = "P",
            paymentMethod = "cash", deliveryAddress = "Djibouti",
        )
        assertNull(id)
    }

    @Test
    fun `createOrder writes group and choiceId for every option`() = runTest {
        val captured = slot<Order>()
        coEvery { orderService.createOrder(capture(captured)) } returns "order123"

        val groups = listOf(
            OptionGroup("Viande", OptionType.SINGLE, required = true, choices = listOf(
                OptionChoice("Tenders", 0, id = "ch_tenders"),
            )),
            OptionGroup("Suppléments", OptionType.MULTIPLE, choices = listOf(
                OptionChoice("Cheddar", 100, id = "ch_k3f9x2ab"),
            )),
        )
        val mapping = OptionSelection.toCart(groups, listOf(setOf(0), setOf(0)))
        val tacos = OrderItem(
            menuId = "abc123", name = "Tacos L", basePrice = 1500,
            extras = mapping.extras, sauces = mapping.sauces,
        )

        val vm = CartViewModel(orderService, restaurantService, store, json, authRepo)
        vm.setRestaurant(restaurant())
        vm.addItem(tacos)
        assertEquals(1600, vm.state.value.subtotal)

        vm.createOrder(
            userId = "u1", customerName = "C", customerPhone = "P",
            paymentMethod = "cash", deliveryAddress = "Djibouti",
        )

        @Suppress("UNCHECKED_CAST")
        val items = captured.captured.toMap()["items"] as List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val extras = items.single()["extras"] as List<Map<String, Any?>>
        assertEquals(
            listOf(
                mapOf("name" to "Tenders", "price" to 0, "isSelected" to true, "choiceId" to "ch_tenders", "group" to "Viande"),
                mapOf("name" to "Cheddar", "price" to 100, "isSelected" to true, "choiceId" to "ch_k3f9x2ab", "group" to "Suppléments"),
            ),
            extras,
        )
    }

    @Test
    fun `persisted cart keeps choiceId and group`() {
        val item = OrderItem(
            menuId = "m1", name = "Tacos", basePrice = 1500,
            extras = listOf(ExtraOption("Tenders", 0, true, choiceId = "ch_tenders", group = "Viande")),
        )
        val encoded = json.encodeToString(OrderItem.serializer(), item)
        assertEquals(item, json.decodeFromString(OrderItem.serializer(), encoded))
    }
}
