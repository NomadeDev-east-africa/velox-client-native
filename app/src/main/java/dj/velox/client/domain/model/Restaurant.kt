package dj.velox.client.domain.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.serialization.Serializable

/** Plage horaire d'ouverture (miroir du format {open,close} écrit par les Cloud Functions). */
@Serializable
data class OpeningRange(val open: String = "", val close: String = "")

/** Restaurant (miroir restaurant.dart). Dates en epoch-millis. */
@Serializable
data class Restaurant(
    val id: String,
    val name: String,
    val address: String,
    val description: String,
    val email: String,
    val phone: String,
    val imageUrl: String,
    val latitude: Double,
    val longitude: Double,
    val rating: Double = 0.0,
    val totalOrders: Int = 0,
    val totalRevenue: Double = 0.0,
    val isActive: Boolean = true,
    val isOpen: Boolean = true,
    /** Fermeture manuelle du jour (panneau admin/restaurant), distincte du planning hebdo. */
    val exceptionallyClosed: Boolean = false,
    /** Planning hebdo — clé = jour en anglais minuscule ("monday" … "sunday"). */
    val openingHours: Map<String, List<OpeningRange>> = emptyMap(),
    val createdAt: Long,
    val updatedAt: Long? = null,
) {
    /**
     * Horaires du jour courant (fuseau Africa/Djibouti, même logique que
     * `computeRestaurantIsOpen` côté Cloud Functions). Null si aucune plage définie
     * pour aujourd'hui (jour de repos ou planning non configuré).
     */
    fun todayOpeningHoursLabel(): String? {
        val zone = java.time.ZoneId.of("Africa/Djibouti")
        val dayKey = java.time.ZonedDateTime.now(zone).dayOfWeek.name.lowercase()
        val ranges = openingHours[dayKey]?.takeIf { it.isNotEmpty() } ?: return null
        return ranges.joinToString(", ") { "${it.open}–${it.close}" }
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "address" to address,
        "description" to description,
        "email" to email,
        "phone" to phone,
        "imageUrl" to imageUrl,
        "latitude" to latitude,
        "longitude" to longitude,
        "rating" to rating,
        "totalOrders" to totalOrders,
        "totalRevenue" to totalRevenue,
        "isActive" to isActive,
        "isOpen" to isOpen,
        "exceptionallyClosed" to exceptionallyClosed,
        "openingHours" to openingHours.mapValues { (_, ranges) -> ranges.map { mapOf("open" to it.open, "close" to it.close) } },
        "createdAt" to Timestamp(java.util.Date(createdAt)),
        "updatedAt" to updatedAt?.let { Timestamp(java.util.Date(it)) },
    )

    companion object {
        fun fromFirestore(doc: DocumentSnapshot): Restaurant {
            val data = doc.data ?: emptyMap<String, Any?>()
            return Restaurant(
                id = doc.id,
                name = data["name"] as? String ?: "",
                address = data["address"] as? String ?: "",
                description = data["description"] as? String ?: "",
                email = data["email"] as? String ?: "",
                phone = data["phone"] as? String ?: "",
                imageUrl = data["imageUrl"] as? String ?: "",
                latitude = (data["latitude"] as? Number)?.toDouble() ?: 0.0,
                longitude = (data["longitude"] as? Number)?.toDouble() ?: 0.0,
                rating = (data["rating"] as? Number)?.toDouble() ?: 0.0,
                totalOrders = (data["totalOrders"] as? Number)?.toInt() ?: 0,
                totalRevenue = (data["totalRevenue"] as? Number)?.toDouble() ?: 0.0,
                isActive = data["isActive"] as? Boolean ?: true,
                isOpen = data["isOpen"] as? Boolean ?: true,
                exceptionallyClosed = data["exceptionallyClosed"] as? Boolean ?: false,
                openingHours = (data["openingHours"] as? Map<*, *>)?.mapNotNull { (day, value) ->
                    val dayKey = day as? String ?: return@mapNotNull null
                    val ranges = (value as? List<*>)?.mapNotNull { range ->
                        val r = range as? Map<*, *> ?: return@mapNotNull null
                        val open = r["open"] as? String ?: return@mapNotNull null
                        val close = r["close"] as? String ?: return@mapNotNull null
                        OpeningRange(open, close)
                    } ?: emptyList()
                    dayKey to ranges
                }?.toMap() ?: emptyMap(),
                createdAt = (data["createdAt"] as? Timestamp)?.toDate()?.time
                    ?: System.currentTimeMillis(),
                updatedAt = (data["updatedAt"] as? Timestamp)?.toDate()?.time,
            )
        }
    }
}
