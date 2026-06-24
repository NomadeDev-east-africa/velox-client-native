package dj.velox.client.domain

import dj.velox.client.domain.model.ExtraOption
import dj.velox.client.domain.model.OrderItem
import dj.velox.client.domain.model.SauceOption
import org.junit.Assert.assertEquals
import org.junit.Test

class OrderItemTest {

    private fun item(quantity: Int = 1, extras: List<ExtraOption> = emptyList(), sauces: List<SauceOption> = emptyList()) =
        OrderItem(menuId = "m1", name = "Burger", basePrice = 1000, quantity = quantity, extras = extras, sauces = sauces)

    @Test
    fun `unit price without options equals base price`() {
        assertEquals(1000, item().unitPrice)
    }

    @Test
    fun `only selected extras and sauces count`() {
        val it = item(
            extras = listOf(ExtraOption("Cheese", 200, isSelected = true), ExtraOption("Bacon", 300, isSelected = false)),
            sauces = listOf(SauceOption("Ketchup", 50, isSelected = true), SauceOption("Mayo", 40, isSelected = false)),
        )
        assertEquals(200, it.extrasTotal)
        assertEquals(50, it.saucesTotal)
        assertEquals(1250, it.unitPrice) // 1000 + 200 + 50
    }

    @Test
    fun `total price multiplies unit by quantity`() {
        val it = item(
            quantity = 3,
            extras = listOf(ExtraOption("Cheese", 200, isSelected = true)),
        )
        assertEquals(3600, it.totalPrice) // (1000 + 200) * 3
    }

    @Test
    fun `selected lists filter correctly`() {
        val it = item(extras = listOf(ExtraOption("A", 10, true), ExtraOption("B", 20, false)))
        assertEquals(1, it.selectedExtras.size)
        assertEquals("A", it.selectedExtras.first().name)
    }
}
