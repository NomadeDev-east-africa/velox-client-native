package dj.velox.client.domain

import dj.velox.client.domain.model.OptionGroup
import dj.velox.client.domain.model.OptionType
import org.junit.Assert.assertEquals
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
}
