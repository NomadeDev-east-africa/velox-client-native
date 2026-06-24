package dj.velox.client.feature.food

import dj.velox.client.domain.model.Order
import dj.velox.client.domain.model.OrderItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vérifie les dérivations d'état de [ActiveOrderState] (logique pure de l'OrderTrackingViewModel). */
class OrderStateTest {

    private fun order(status: String) = Order(
        id = "o1", userId = "u1", restaurantId = "r1", restaurantName = "R", restaurantImageUrl = "",
        customerName = "C", customerPhone = "P",
        items = listOf(OrderItem(menuId = "m", name = "x", basePrice = 100, quantity = 1)),
        deliveryFee = 500, status = status, paymentMethod = "cash", deliveryAddress = "A",
        createdAt = 0L, updatedAt = 0L,
    )

    @Test
    fun `pending order is active and not terminated`() {
        val s = ActiveOrderState(order = order(Order.STATUS_PENDING))
        assertTrue(s.hasActiveOrder)
        assertFalse(s.isTerminated)
        assertEquals("o1", s.orderId)
    }

    @Test
    fun `completed and cancelled orders are terminated`() {
        for (st in listOf(Order.STATUS_COMPLETED, Order.STATUS_CANCELLED)) {
            val s = ActiveOrderState(order = order(st))
            assertFalse("$st should not be active", s.hasActiveOrder)
            assertTrue("$st should be terminated", s.isTerminated)
        }
    }

    @Test
    fun `null order is terminated and inactive`() {
        val s = ActiveOrderState(order = null)
        assertFalse(s.hasActiveOrder)
        assertTrue(s.isTerminated)
        assertNull(s.orderId)
    }
}
