package dj.velox.client.domain.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.serialization.Serializable

/** Plat d'un restaurant (miroir menu_item.dart). Dates en epoch-millis. */
@Serializable
data class MenuItem(
    val id: String,
    val restaurantId: String,
    val name: String,
    val description: String,
    val price: Double,
    val imageUrl: String? = null,
    val category: String = "Autre",
    val isAvailable: Boolean = true,
    val preparationTime: Int = 20,
    val discountPercentage: Int = 0,
    val optionGroups: List<OptionGroup> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long? = null,
) {
    val hasDiscount: Boolean get() = discountPercentage in 1..100
    val discountedPrice: Double
        get() = if (hasDiscount) price * (1 - discountPercentage / 100.0) else price

    fun toMap(): Map<String, Any?> = mapOf(
        "restaurantId" to restaurantId,
        "name" to name,
        "description" to description,
        "price" to price,
        "imageUrl" to imageUrl,
        "category" to category,
        "isAvailable" to isAvailable,
        "preparationTime" to preparationTime,
        "discountPercentage" to discountPercentage,
        "optionGroups" to optionGroups.map { it.toMap() },
        "createdAt" to Timestamp(java.util.Date(createdAt)),
        "updatedAt" to updatedAt?.let { Timestamp(java.util.Date(it)) },
    )

    companion object {
        fun fromFirestore(doc: DocumentSnapshot): MenuItem {
            val data = doc.data ?: emptyMap<String, Any?>()
            return MenuItem(
                id = doc.id,
                restaurantId = data["restaurantId"] as? String ?: "",
                name = data["name"] as? String ?: "",
                description = data["description"] as? String ?: "",
                price = (data["price"] as? Number)?.toDouble() ?: 0.0,
                imageUrl = data["imageUrl"] as? String,
                category = data["category"] as? String ?: "Autre",
                isAvailable = data["isAvailable"] as? Boolean ?: true,
                preparationTime = (data["preparationTime"] as? Number)?.toInt() ?: 20,
                discountPercentage = (data["discountPercentage"] as? Number)?.toInt() ?: 0,
                optionGroups = OptionGroup.listFromRaw(data["optionGroups"]),
                createdAt = (data["createdAt"] as? Timestamp)?.toDate()?.time
                    ?: System.currentTimeMillis(),
                updatedAt = (data["updatedAt"] as? Timestamp)?.toDate()?.time,
            )
        }
    }
}
