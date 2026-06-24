package dj.velox.client.domain.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.serialization.Serializable

/** Promotion plat/catégorie (miroir promotion.dart). Dates en epoch-millis. */
@Serializable
data class Promotion(
    val id: String,
    val type: String,            // "item" | "category"
    val targetId: String,
    val targetName: String,
    val restaurantId: String,
    val discountPercent: Int,
    val label: String,
    val isActive: Boolean,
    val startDate: Long,
    val endDate: Long? = null,
) {
    val isCurrentlyActive: Boolean
        get() {
            if (!isActive) return false
            val now = System.currentTimeMillis()
            if (now < startDate) return false
            if (endDate != null && now > endDate) return false
            return true
        }

    fun matchesItem(itemId: String, itemName: String): Boolean {
        if (type != "item" || !isCurrentlyActive) return false
        val slug = itemName.lowercase().trim().replace(' ', '_')
        return targetId == itemId || targetId == slug
    }

    fun matchesCategory(categoryName: String): Boolean {
        if (type != "category" || !isCurrentlyActive) return false
        val normalized = categoryName.lowercase().trim()
        return targetId == normalized || targetId == normalized.replace(' ', '_')
    }

    companion object {
        private fun parseDate(value: Any?): Long = when (value) {
            is Timestamp -> value.toDate().time
            is Number -> value.toLong()
            is String -> runCatching { java.time.Instant.parse(value).toEpochMilli() }
                .getOrDefault(System.currentTimeMillis())
            else -> System.currentTimeMillis()
        }

        fun fromFirestore(doc: DocumentSnapshot): Promotion {
            val data = doc.data ?: emptyMap<String, Any?>()
            return Promotion(
                id = doc.id,
                type = data["type"] as? String ?: "item",
                targetId = data["targetId"] as? String ?: "",
                targetName = data["targetName"] as? String ?: "",
                restaurantId = data["restaurantId"] as? String ?: "",
                discountPercent = (data["discountPercent"] as? Number)?.toInt() ?: 0,
                label = data["label"] as? String ?: "",
                isActive = data["isActive"] as? Boolean ?: false,
                startDate = parseDate(data["startDate"]),
                endDate = data["endDate"]?.let { parseDate(it) },
            )
        }
    }
}
