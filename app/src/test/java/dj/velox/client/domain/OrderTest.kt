package dj.velox.client.domain

import dj.velox.client.domain.model.Order
import dj.velox.client.domain.model.OrderItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderTest {

    private fun order(
        items: List<OrderItem>,
        status: String = Order.STATUS_PENDING,
        deliveryFee: Int = 500,
        discount: Int = 0,
    ) = Order(
        id = "o1", userId = "u1", restaurantId = "r1", restaurantName = "Resto",
        restaurantImageUrl = "", customerName = "Client", customerPhone = "+253...",
        items = items, deliveryFee = deliveryFee, status = status, paymentMethod = "cash",
        deliveryAddress = "Djibouti", createdAt = 0L, updatedAt = 0L, discount = discount,
    )

    private fun item(price: Int, qty: Int) =
        OrderItem(menuId = "m", name = "x", basePrice = price, quantity = qty)

    @Test
    fun `subtotal sums item totals and total adds delivery minus discount`() {
        val o = order(items = listOf(item(1000, 2), item(500, 1)), deliveryFee = 500, discount = 300)
        assertEquals(2500, o.subtotal)        // 2000 + 500
        assertEquals(3, o.itemCount)          // 2 + 1
        assertEquals(2700, o.total)           // 2500 + 500 - 300
    }

    @Test
    fun `pending order can be cancelled and is active`() {
        val o = order(items = listOf(item(1000, 1)), status = Order.STATUS_PENDING)
        assertTrue(o.canBeCancelled)
        assertTrue(o.isActive)
        assertFalse(o.isCompleted)
    }

    @Test
    fun `completed order is not cancellable nor active`() {
        val o = order(items = listOf(item(1000, 1)), status = Order.STATUS_COMPLETED)
        assertFalse(o.canBeCancelled)
        assertFalse(o.isActive)
        assertTrue(o.isCompleted)
    }

    @Test
    fun `delivering order is active but not in the cancellable window`() {
        val o = order(items = listOf(item(1000, 1)), status = Order.STATUS_DELIVERING)
        assertTrue(o.isActive)
        assertFalse(o.canBeCancelled)
    }
}
