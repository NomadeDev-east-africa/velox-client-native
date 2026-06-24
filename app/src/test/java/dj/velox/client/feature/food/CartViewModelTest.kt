package dj.velox.client.feature.food

import dj.velox.client.core.Constants
import dj.velox.client.data.local.VeloxLocalStore
import dj.velox.client.data.remote.OrderService
import dj.velox.client.domain.model.Order
import dj.velox.client.domain.model.OrderItem
import dj.velox.client.domain.model.Restaurant
import dj.velox.client.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val orderService: OrderService = mockk(relaxed = true)
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

        val vm = CartViewModel(orderService, store, json)
        vm.setRestaurant(restaurant())
        vm.addItem(burger())

        // 100 points demandés → plafonnés à DELIVERY_FEE / POINT_VALUE = 500 / 15 = 33.
        val id = vm.createOrder(
            userId = "u1", customerName = "C", customerPhone = "P",
            paymentMethod = "cash", deliveryAddress = "Djibouti", pointsUsed = 100,
        )

        assertEquals("order123", id)
        val maxPoints = Constants.DELIVERY_FEE / Constants.POINT_VALUE
        assertEquals(maxPoints, captured.captured.pointsUsed)
        assertEquals(maxPoints * Constants.POINT_VALUE, captured.captured.discount)
    }

    @Test
    fun `createOrder returns null when the cart is empty`() = runTest {
        val vm = CartViewModel(orderService, store, json)
        val id = vm.createOrder(
            userId = "u1", customerName = "C", customerPhone = "P",
            paymentMethod = "cash", deliveryAddress = "Djibouti",
        )
        assertNull(id)
    }
}
