package dj.velox.client.domain

import dj.velox.client.domain.model.OptionChoice
import dj.velox.client.domain.model.OptionGroup
import dj.velox.client.domain.model.OptionSelection
import dj.velox.client.domain.model.OptionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Réglages pilotés par l'admin sur `optionGroups` : `available` (rupture d'un choix) et
 * `maxChoices` (plafond d'un groupe multiple). Lecture additive, rétro-compatible.
 */
class OptionGroupTest {

    @Test
    fun `unavailable choice is filtered out and choice without the field is kept`() {
        val group = OptionGroup.fromMap(
            mapOf(
                "name" to "Topping",
                "type" to "multiple",
                "choices" to listOf(
                    mapOf("name" to "Emmental", "price" to 0), // pas de champ available → gardé
                    mapOf("name" to "Champignons", "price" to 0, "available" to false), // rupture → masqué
                ),
            ),
        )
        assertEquals(1, group.choices.size)
        assertEquals("Emmental", group.choices.first().name)
        // Un choix conservé est disponible par défaut.
        assertEquals(true, group.choices.first().available)
    }

    @Test
    fun `maxChoices is read for a multiple group and ignored for a single group`() {
        val multiple = OptionGroup.fromMap(
            mapOf("name" to "Topping", "type" to "multiple", "maxChoices" to 3, "choices" to emptyList<Any>()),
        )
        assertEquals(OptionType.MULTIPLE, multiple.type)
        assertEquals(3, multiple.maxChoices)

        val single = OptionGroup.fromMap(
            mapOf("name" to "Taille", "type" to "single", "maxChoices" to 3, "choices" to emptyList<Any>()),
        )
        assertEquals(OptionType.SINGLE, single.type)
        // Un single ignore maxChoices : la limite reste 1 (0 = non plafonné côté data).
        assertEquals(0, single.maxChoices)
    }

    @Test
    fun `absent maxChoices defaults to unlimited (0)`() {
        val group = OptionGroup.fromMap(
            mapOf("name" to "Topping", "type" to "multiple", "choices" to emptyList<Any>()),
        )
        assertEquals(0, group.maxChoices)
    }

    // ─── Identifiants de choix + groupe (intégration caisse Loyverse) ─────

    @Test
    fun `choice id is read, blank id is ignored, and id is written only when present`() {
        val group = OptionGroup.fromMap(
            mapOf(
                "name" to "Suppléments",
                "choices" to listOf(
                    mapOf("id" to "ch_k3f9x2ab", "name" to "Cheddar", "price" to 100),
                    mapOf("id" to "  ", "name" to "Oignons", "price" to 0),
                    mapOf("name" to "Bacon", "price" to 200),
                ),
            ),
        )
        assertEquals("ch_k3f9x2ab", group.choices[0].id)
        assertNull(group.choices[1].id)
        assertNull(group.choices[2].id)
        assertEquals("ch_k3f9x2ab", group.choices[0].toMap()["id"])
        assertFalse(group.choices[2].toMap().containsKey("id"))
    }

    @Test
    fun `toCart tags every option with its group name and choice id`() {
        val groups = listOf(
            OptionGroup("Viande", OptionType.SINGLE, required = true, choices = listOf(
                OptionChoice("Poulet", 0, id = "ch_poulet"),
                OptionChoice("Tenders", 0, id = "ch_tenders"),
            )),
            OptionGroup("Suppléments", OptionType.MULTIPLE, choices = listOf(
                OptionChoice("Cheddar", 100, id = "ch_k3f9x2ab"),
                OptionChoice("Bacon", 200), // choix pas encore migré : pas d'id
            )),
            OptionGroup("Sauces", OptionType.MULTIPLE, choices = listOf(
                OptionChoice("Algérienne", 0, id = "ch_p0d81mzq"),
            )),
        )
        val selections = listOf(setOf(1), setOf(1, 0), setOf(0))
        val mapping = OptionSelection.toCart(groups, selections)

        // Ordre : groupes du menu, puis choix du menu (pas l'ordre de clic).
        assertEquals(listOf("Tenders", "Cheddar", "Bacon"), mapping.extras.map { it.name })
        assertEquals(listOf("Viande", "Suppléments", "Suppléments"), mapping.extras.map { it.group })
        assertEquals(listOf("ch_tenders", "ch_k3f9x2ab", null), mapping.extras.map { it.choiceId })

        val bacon = mapping.extras[2].toMap()
        assertFalse(bacon.containsKey("choiceId"))
        assertEquals("Suppléments", bacon["group"])

        val sauce = mapping.sauces.single()
        assertEquals("Sauces", sauce.group)
        assertEquals("ch_p0d81mzq", sauce.choiceId)

        // Le supplément total ne change pas.
        assertEquals(300, OptionSelection.surcharge(groups, selections))
    }
}
