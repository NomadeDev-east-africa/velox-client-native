package dj.velox.client.domain.model

import kotlinx.serialization.Serializable

/**
 * Item de commande food (miroir order_item.dart).
 * Immuable : les modifications (quantité, sélection) passent par copy().
 */
@Serializable
data class OrderItem(
    val menuId: String,
    val name: String,
    val description: String = "",
    val imageUrl: String = "",
    val category: String = "",
    val basePrice: Int,
    val quantity: Int = 1,
    val extras: List<ExtraOption> = emptyList(),
    val sauces: List<SauceOption> = emptyList(),
) {
    // ─── Calculs ─────────────────────────────────────────────────
    val extrasTotal: Int get() = extras.filter { it.isSelected }.sumOf { it.price }
    val saucesTotal: Int get() = sauces.filter { it.isSelected }.sumOf { it.price }
    val unitPrice: Int get() = basePrice + extrasTotal + saucesTotal
    val totalPrice: Int get() = unitPrice * quantity
    val selectedExtras: List<ExtraOption> get() = extras.filter { it.isSelected }
    val selectedSauces: List<SauceOption> get() = sauces.filter { it.isSelected }

    fun toMap(): Map<String, Any?> = mapOf(
        "menuId" to menuId,
        "name" to name,
        "description" to description,
        "imageUrl" to imageUrl,
        "category" to category,
        "basePrice" to basePrice,
        "quantity" to quantity,
        "extras" to extras.map { it.toMap() },
        "sauces" to sauces.map { it.toMap() },
        "extrasTotal" to extrasTotal,
        "saucesTotal" to saucesTotal,
        "unitPrice" to unitPrice,
        "totalPrice" to totalPrice,
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): OrderItem = OrderItem(
            menuId = map["menuId"] as? String ?: "",
            name = map["name"] as? String ?: "",
            description = map["description"] as? String ?: "",
            imageUrl = map["imageUrl"] as? String ?: "",
            category = map["category"] as? String ?: "",
            basePrice = (map["basePrice"] as? Number)?.toInt() ?: 0,
            quantity = (map["quantity"] as? Number)?.toInt() ?: 1,
            extras = (map["extras"] as? List<*>)
                ?.filterIsInstance<Map<String, Any?>>()
                ?.map { ExtraOption.fromMap(it) } ?: emptyList(),
            sauces = (map["sauces"] as? List<*>)
                ?.filterIsInstance<Map<String, Any?>>()
                ?.map { SauceOption.fromMap(it) } ?: emptyList(),
        )
    }
}
