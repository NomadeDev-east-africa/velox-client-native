package dj.velox.client.domain.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.GeoPoint
import kotlinx.serialization.Serializable

/**
 * Commande food (miroir order.dart).
 * Dates en epoch-millis (Long). deliveryLocation en LatLng sérialisable
 * (converti ⇄ Firestore GeoPoint au mapping).
 */
@Serializable
data class Order(
    val id: String,
    val userId: String,
    val restaurantId: String,
    val restaurantName: String,
    val restaurantImageUrl: String,
    val customerName: String,
    val customerPhone: String,
    val items: List<OrderItem>,
    val deliveryFee: Int,
    val status: String,
    val paymentMethod: String,
    val deliveryAddress: String,
    val deliveryLocation: LatLng? = null,
    val addressDetails: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deliveryDriverId: String? = null,
    val deliveryDriverName: String? = null,
    val acceptedAt: Long? = null,
    val readyAt: Long? = null,
    val pickedUpAt: Long? = null,
    val deliveredAt: Long? = null,
    val cancelledAt: Long? = null,
    val cancellationReason: String? = null,
    val estimatedPreparationTime: Int? = null,
    // Fidélité
    val pointsUsed: Int = 0,
    val discount: Int = 0,
) {
    // ─── Getters calculés ────────────────────────────────────────
    val itemCount: Int get() = items.sumOf { it.quantity }
    val subtotal: Int get() = items.sumOf { it.totalPrice }
    val total: Int get() = subtotal + deliveryFee - discount

    val canBeCancelled: Boolean
        get() = status in setOf(
            STATUS_PENDING, STATUS_CONFIRMED, STATUS_ACCEPTED, STATUS_PREPARING, STATUS_READY,
        )
    val isCompleted: Boolean get() = status == STATUS_COMPLETED || status == STATUS_CANCELLED
    val isActive: Boolean get() = status != STATUS_COMPLETED && status != STATUS_CANCELLED

    fun toMap(): Map<String, Any?> = buildMap {
        put("userId", userId)
        put("restaurantId", restaurantId)
        put("restaurantName", restaurantName)
        put("restaurantImageUrl", restaurantImageUrl)
        put("customerName", customerName)
        put("customerPhone", customerPhone)
        put("items", items.map { it.toMap() })
        put("itemCount", itemCount)
        put("subtotal", subtotal)
        put("deliveryFee", deliveryFee)
        put("pointsUsed", pointsUsed)
        put("discount", discount)
        put("total", total)
        put("status", status)
        put("paymentMethod", paymentMethod)
        put("deliveryAddress", deliveryAddress)
        put("createdAt", Timestamp(java.util.Date(createdAt)))
        put("updatedAt", Timestamp(java.util.Date(updatedAt)))
        deliveryLocation?.let { put("deliveryLocation", GeoPoint(it.latitude, it.longitude)) }
        if (!addressDetails.isNullOrEmpty()) put("addressDetails", addressDetails)
        deliveryDriverId?.let { put("deliveryDriverId", it) }
        deliveryDriverName?.let { put("deliveryDriverName", it) }
        estimatedPreparationTime?.let { put("estimatedPreparationTime", it) }
    }

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_CONFIRMED = "confirmed"
        const val STATUS_ACCEPTED = "accepted"
        const val STATUS_PREPARING = "preparing"
        const val STATUS_READY = "ready"
        const val STATUS_DELIVERING = "delivering"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_CANCELLED = "cancelled"

        /** Tolère Timestamp, Long (ms), String ISO 8601 ou java.util.Date. */
        private fun parseTs(value: Any?): Long? = when (value) {
            null -> null
            is Timestamp -> value.toDate().time
            is java.util.Date -> value.time
            is Number -> value.toLong()
            is String -> runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrNull()
            else -> null
        }

        fun fromFirestore(doc: DocumentSnapshot): Order {
            val data = doc.data ?: emptyMap<String, Any?>()
            val now = System.currentTimeMillis()
            val geo = data["deliveryLocation"] as? GeoPoint

            return Order(
                id = doc.id,
                userId = data["userId"] as? String ?: "",
                restaurantId = data["restaurantId"] as? String ?: "",
                restaurantName = data["restaurantName"] as? String ?: "",
                restaurantImageUrl = data["restaurantImageUrl"] as? String ?: "",
                customerName = data["customerName"] as? String ?: "",
                customerPhone = data["customerPhone"] as? String ?: "",
                items = (data["items"] as? List<*>)
                    ?.filterIsInstance<Map<String, Any?>>()
                    ?.map { OrderItem.fromMap(it) } ?: emptyList(),
                deliveryFee = (data["deliveryFee"] as? Number)?.toInt() ?: 500,
                pointsUsed = (data["pointsUsed"] as? Number)?.toInt() ?: 0,
                discount = (data["discount"] as? Number)?.toInt() ?: 0,
                status = data["status"] as? String ?: STATUS_PENDING,
                paymentMethod = data["paymentMethod"] as? String ?: "cash",
                deliveryAddress = data["deliveryAddress"] as? String ?: "",
                deliveryLocation = geo?.let { LatLng(it.latitude, it.longitude) },
                addressDetails = data["addressDetails"] as? String,
                createdAt = parseTs(data["createdAt"]) ?: now,
                updatedAt = parseTs(data["updatedAt"]) ?: now,
                deliveryDriverId = data["deliveryDriverId"] as? String,
                deliveryDriverName = data["deliveryDriverName"] as? String,
                acceptedAt = parseTs(data["acceptedAt"]),
                readyAt = parseTs(data["readyAt"]),
                pickedUpAt = parseTs(data["pickedUpAt"]),
                deliveredAt = parseTs(data["deliveredAt"]),
                cancelledAt = parseTs(data["cancelledAt"]),
                cancellationReason = data["cancellationReason"] as? String,
                estimatedPreparationTime = (data["estimatedPreparationTime"] as? Number)?.toInt(),
            )
        }

        fun statusText(status: String): String = when (status) {
            STATUS_PENDING -> "En attente"
            STATUS_CONFIRMED, STATUS_ACCEPTED -> "Confirmée"
            STATUS_PREPARING -> "En préparation"
            STATUS_READY -> "Prête"
            STATUS_DELIVERING -> "En livraison"
            STATUS_COMPLETED -> "Livrée"
            STATUS_CANCELLED -> "Annulée"
            else -> "Inconnu"
        }
    }
}
