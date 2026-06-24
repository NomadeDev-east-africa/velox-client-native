package dj.velox.client.domain.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.serialization.Serializable

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
    val createdAt: Long,
    val updatedAt: Long? = null,
) {
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
                createdAt = (data["createdAt"] as? Timestamp)?.toDate()?.time
                    ?: System.currentTimeMillis(),
                updatedAt = (data["updatedAt"] as? Timestamp)?.toDate()?.time,
            )
        }
    }
}
