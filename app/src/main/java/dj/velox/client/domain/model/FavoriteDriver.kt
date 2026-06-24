package dj.velox.client.domain.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

/** Chauffeur favori (miroir favorite_driver.dart). Sous-collection users/{uid}/favorite_drivers. */
data class FavoriteDriver(
    val driverId: String,
    val driverName: String,
    val driverPhotoUrl: String? = null,
    val driverPhone: String? = null,
    val driverRating: Double? = null,
    val vehicleType: String? = null,
    val addedAt: Long,
    val ridesCount: Int = 1,
    val lastRideId: String,
) {
    fun toFirestore(): Map<String, Any?> = mapOf(
        "driverName" to driverName,
        "driverPhotoUrl" to driverPhotoUrl,
        "driverPhone" to driverPhone,
        "driverRating" to driverRating,
        "vehicleType" to vehicleType,
        "addedAt" to Timestamp(java.util.Date(addedAt)),
        "ridesCount" to ridesCount,
        "lastRideId" to lastRideId,
    )

    companion object {
        fun fromFirestore(doc: DocumentSnapshot): FavoriteDriver {
            val data = doc.data ?: emptyMap<String, Any?>()
            return FavoriteDriver(
                driverId = doc.id,
                driverName = data["driverName"] as? String ?: "",
                driverPhotoUrl = data["driverPhotoUrl"] as? String,
                driverPhone = data["driverPhone"] as? String,
                driverRating = (data["driverRating"] as? Number)?.toDouble(),
                vehicleType = data["vehicleType"] as? String,
                addedAt = (data["addedAt"] as? Timestamp)?.toDate()?.time
                    ?: System.currentTimeMillis(),
                ridesCount = (data["ridesCount"] as? Number)?.toInt() ?: 1,
                lastRideId = data["lastRideId"] as? String ?: "",
            )
        }
    }
}
