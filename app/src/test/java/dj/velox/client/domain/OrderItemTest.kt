package dj.velox.client.domain

import dj.velox.client.domain.model.ExtraOption
import dj.velox.client.domain.model.OrderItem
import dj.velox.client.domain.model.SauceOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun `toMap and fromMap round-trip keeps choiceId and group`() {
        val original = item(
            extras = listOf(
                ExtraOption("Tenders", 0, true, choiceId = "ch_tenders", group = "Viande"),
                ExtraOption("Bacon", 200, true, group = "Suppléments"),
            ),
            sauces = listOf(SauceOption("Algérienne", 0, true, choiceId = "ch_p0d81mzq", group = "Sauces")),
        )
        val map = original.toMap()
        @Suppress("UNCHECKED_CAST")
        val extras = map["extras"] as List<Map<String, Any?>>
        assertEquals("ch_tenders", extras[0]["choiceId"])
        assertEquals("Viande", extras[0]["group"])
        assertFalse(extras[1].containsKey("choiceId"))

        val restored = OrderItem.fromMap(map)
        assertEquals(original, restored)
        assertEquals(1200, restored.unitPrice)
    }

    @Test
    fun `legacy order item without the new fields reads back unchanged`() {
        val legacy = mapOf(
            "menuId" to "m1", "name" to "Burger", "basePrice" to 1000L, "quantity" to 2L,
            "extras" to listOf(mapOf("name" to "Cheese", "price" to 200L, "isSelected" to true)),
            "sauces" to listOf(mapOf("name" to "Ketchup", "price" to 50L, "isSelected" to true)),
        )
        val restored = OrderItem.fromMap(legacy)
        assertNull(restored.extras.single().choiceId)
        assertNull(restored.extras.single().group)
        assertNull(restored.sauces.single().group)
        assertEquals(2500, restored.totalPrice)

        // Réécrite, elle ne gagne aucun champ : format identique à aujourd'hui.
        @Suppress("UNCHECKED_CAST")
        val extra = (restored.toMap()["extras"] as List<Map<String, Any?>>).single()
        assertEquals(setOf("name", "price", "isSelected"), extra.keys)
    }
}
