package dj.velox.client.domain.model

import kotlinx.serialization.Serializable

// ════════════════════════════════════════════════════════════════
// Options de plat (miroir extra_option / sauce_option / option_group)
// ════════════════════════════════════════════════════════════════

@Serializable
data class ExtraOption(
    val name: String,
    val price: Int,
    val isSelected: Boolean = false,
) {
    fun toMap(): Map<String, Any?> = mapOf("name" to name, "price" to price, "isSelected" to isSelected)

    companion object {
        fun fromMap(map: Map<String, Any?>): ExtraOption = ExtraOption(
            name = map["name"] as? String ?: "",
            price = (map["price"] as? Number)?.toInt() ?: 0,
            isSelected = map["isSelected"] as? Boolean ?: false,
        )
    }
}

@Serializable
data class SauceOption(
    val name: String,
    val price: Int,
    val isSelected: Boolean = false,
) {
    fun toMap(): Map<String, Any?> = mapOf("name" to name, "price" to price, "isSelected" to isSelected)

    companion object {
        fun fromMap(map: Map<String, Any?>): SauceOption = SauceOption(
            name = map["name"] as? String ?: "",
            price = (map["price"] as? Number)?.toInt() ?: 0,
            isSelected = map["isSelected"] as? Boolean ?: false,
        )
    }
}

// ─── Options data-driven (Taille, Formule, Suppléments…) ─────────

enum class OptionType { SINGLE, MULTIPLE }

@Serializable
data class OptionChoice(val name: String, val price: Int = 0) {
    fun toMap(): Map<String, Any?> = mapOf("name" to name, "price" to price)

    companion object {
        fun fromMap(map: Map<String, Any?>): OptionChoice = OptionChoice(
            name = (map["name"] ?: "").toString(),
            price = (map["price"] as? Number)?.toInt() ?: 0,
        )
    }
}

@Serializable
data class OptionGroup(
    val name: String,
    val type: OptionType = OptionType.MULTIPLE,
    val required: Boolean = false,
    val choices: List<OptionChoice> = emptyList(),
) {
    val isSingle: Boolean get() = type == OptionType.SINGLE

    fun toMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "type" to if (isSingle) "single" else "multiple",
        "required" to required,
        "choices" to choices.map { it.toMap() },
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): OptionGroup = OptionGroup(
            name = (map["name"] ?: "").toString(),
            type = if ((map["type"] ?: "multiple").toString().lowercase() == "single")
                OptionType.SINGLE else OptionType.MULTIPLE,
            required = map["required"] == true,
            choices = (map["choices"] as? List<*>)
                ?.filterIsInstance<Map<String, Any?>>()
                ?.map { OptionChoice.fromMap(it) }
                ?: emptyList(),
        )

        /** Parser tolérant : null / liste vide / liste de Maps → liste de groupes. */
        fun listFromRaw(raw: Any?): List<OptionGroup> =
            (raw as? List<*>)
                ?.filterIsInstance<Map<String, Any?>>()
                ?.map { fromMap(it) }
                ?: emptyList()
    }
}

/** Résultat du mapping des choix sélectionnés vers le format panier (extras/sauces). */
data class OptionCartMapping(val extras: List<ExtraOption>, val sauces: List<SauceOption>)

/**
 * Outils purs pour le rendu data-driven des options (miroir de `option_selection.dart`).
 * `selections[i]` = index des choix sélectionnés pour `groups[i]` (0/1 si single, 0..N si multiple).
 */
object OptionSelection {
    private fun normalize(input: String): String =
        java.text.Normalizer.normalize(input.lowercase(), java.text.Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")

    /** Un groupe dont le nom contient « sauce » est reversé dans `sauces` plutôt qu'`extras`. */
    fun isSauceGroup(name: String): Boolean = normalize(name).contains("sauce")

    /** Somme des suppléments (FDJ) des choix sélectionnés (index hors borne ignorés). */
    fun surcharge(groups: List<OptionGroup>, selections: List<Set<Int>>): Int {
        var sum = 0
        for (gi in groups.indices) {
            if (gi >= selections.size) break
            val choices = groups[gi].choices
            for (ci in selections[gi]) if (ci in choices.indices) sum += choices[ci].price
        }
        return sum
    }

    /** Convertit les choix sélectionnés en extras/sauces (groupes « sauce » → sauces). */
    fun toCart(groups: List<OptionGroup>, selections: List<Set<Int>>): OptionCartMapping {
        val extras = mutableListOf<ExtraOption>()
        val sauces = mutableListOf<SauceOption>()
        for (gi in groups.indices) {
            if (gi >= selections.size) break
            val group = groups[gi]
            val sauce = isSauceGroup(group.name)
            for (ci in selections[gi]) {
                if (ci !in group.choices.indices) continue
                val choice = group.choices[ci]
                if (sauce) sauces.add(SauceOption(choice.name, choice.price, true))
                else extras.add(ExtraOption(choice.name, choice.price, true))
            }
        }
        return OptionCartMapping(extras, sauces)
    }
}
