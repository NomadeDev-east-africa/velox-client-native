package dj.velox.client.domain.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.serialization.Serializable

// ════════════════════════════════════════════════════════════════
// ENUMS — statuts course / paiement (miroir de ride.dart)
// ════════════════════════════════════════════════════════════════

enum class RideStatus {
    REQUESTED,           // Demandée — recherche driver en cours
    ACCEPTED,            // Acceptée par driver (en route)
    ARRIVING,            // Driver en approche du pickup
    ARRIVED,             // Driver arrivé au pickup
    STARTED,             // Course commencée
    COMPLETED,           // Course terminée
    CANCELLED,           // Course annulée
    NO_DRIVER_AVAILABLE; // Aucun driver trouvé (Cloud Functions)

    /** Valeur écrite dans Firestore (camelCase comme côté Flutter). */
    fun toFirestore(): String = when (this) {
        REQUESTED -> "requested"
        ACCEPTED -> "accepted"
        ARRIVING -> "arriving"
        ARRIVED -> "arrived"
        STARTED -> "started"
        COMPLETED -> "completed"
        CANCELLED -> "cancelled"
        NO_DRIVER_AVAILABLE -> "noDriverAvailable"
    }

    companion object {
        /** Parse tolérant — gère les variantes Cloud Functions (pending/waiting/new…). */
        fun parse(status: String?): RideStatus = when (status) {
            "requested", "pending", "waiting", "new", "created" -> REQUESTED
            "accepted" -> ACCEPTED
            "arriving" -> ARRIVING
            "arrived" -> ARRIVED
            "started" -> STARTED
            "completed" -> COMPLETED
            "cancelled" -> CANCELLED
            "no_driver_available", "noDriverAvailable" -> NO_DRIVER_AVAILABLE
            else -> throw IllegalArgumentException("Statut de course inconnu: \"$status\"")
        }
    }
}

enum class PaymentStatus {
    PENDING, COMPLETED, FAILED;

    fun toFirestore(): String = name.lowercase()

    companion object {
        fun parse(status: String?): PaymentStatus = when (status) {
            "completed" -> COMPLETED
            "failed" -> FAILED
            else -> PENDING
        }
    }
}

// ════════════════════════════════════════════════════════════════
// POSITION (pickup / destination)
// ════════════════════════════════════════════════════════════════

@Serializable
data class RideLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val placeName: String? = null,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "latitude" to latitude,
        "longitude" to longitude,
        "address" to address,
        "placeName" to placeName,
    )

    companion object {
        fun fromMap(map: Map<String, Any?>?): RideLocation {
            val m = map ?: emptyMap()
            return RideLocation(
                latitude = (m["latitude"] as? Number)?.toDouble() ?: 0.0,
                longitude = (m["longitude"] as? Number)?.toDouble() ?: 0.0,
                address = m["address"] as? String ?: "",
                placeName = m["placeName"] as? String,
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════
// COURSE
// Dates en epoch-millis (Long) → JSON DataStore trivial + conversion
// Timestamp ⇄ Long pour Firestore.
// @Serializable → persistance locale (DataStore) sans code custom.
// ════════════════════════════════════════════════════════════════

@Serializable
data class Ride(
    val rideId: String,
    // Participants
    val userId: String,
    val userName: String = "Utilisateur",
    val userPhone: String? = null,
    val userPhotoUrl: String? = null,
    val driverId: String? = null,
    val driverName: String? = null,
    val driverPhone: String? = null,
    val driverPhotoUrl: String? = null,
    val vehicleId: String? = null,
    // Locations
    val pickup: RideLocation,
    val destination: RideLocation,
    // Trip
    val distance: Double = 0.0,
    val estimatedDuration: Int = 0,
    val estimatedFare: Double = 0.0,
    val finalFare: Double? = null,
    val vehicleType: String = "standard",
    // Status
    val status: RideStatus,
    // Timing (epoch millis)
    val requestedAt: Long,
    val acceptedAt: Long? = null,
    val arrivedAt: Long? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val cancelledAt: Long? = null,
    // Payment
    val paymentMethod: String = "cash",
    val paymentStatus: PaymentStatus = PaymentStatus.PENDING,
    // Rating
    val userRating: Int? = null,
    val userReview: String? = null,
    val driverRating: Int? = null,
    val driverReview: String? = null,
    // Cancellation
    val cancellationReason: String? = null,
    val cancelledBy: String? = null,
) {
    // ─── Getters métier ──────────────────────────────────────────
    val isActive: Boolean
        get() = status != RideStatus.COMPLETED &&
            status != RideStatus.CANCELLED &&
            status != RideStatus.NO_DRIVER_AVAILABLE

    val isWaitingForDriver: Boolean get() = status == RideStatus.REQUESTED
    val hasDriver: Boolean get() = driverId != null
    val isCompleted: Boolean get() = status == RideStatus.COMPLETED
    val isCancelled: Boolean get() = status == RideStatus.CANCELLED

    companion object {
        /** Désérialisation depuis un document Firestore (Timestamp → millis). */
        fun fromFirestore(doc: DocumentSnapshot): Ride {
            val data = doc.data ?: throw IllegalStateException("Document Firestore vide: ${doc.id}")

            fun ts(key: String): Long? = (data[key] as? Timestamp)?.toDate()?.time
            fun map(key: String): Map<String, Any?>? =
                @Suppress("UNCHECKED_CAST") (data[key] as? Map<String, Any?>)

            return Ride(
                rideId = doc.id,
                userId = data["userId"] as? String
                    ?: throw IllegalStateException("userId manquant: ${doc.id}"),
                userName = data["userName"] as? String ?: "Utilisateur",
                userPhone = data["userPhone"] as? String,
                userPhotoUrl = data["userPhotoUrl"] as? String,
                driverId = data["driverId"] as? String,
                driverName = data["driverName"] as? String,
                driverPhone = data["driverPhone"] as? String,
                driverPhotoUrl = data["driverPhotoUrl"] as? String,
                vehicleId = data["vehicleId"] as? String,
                pickup = RideLocation.fromMap(map("pickup")),
                destination = RideLocation.fromMap(map("destination")),
                distance = (data["distance"] as? Number)?.toDouble() ?: 0.0,
                estimatedDuration = (data["estimatedDuration"] as? Number)?.toInt() ?: 0,
                estimatedFare = (data["estimatedFare"] as? Number)?.toDouble() ?: 0.0,
                finalFare = (data["finalFare"] as? Number)?.toDouble(),
                vehicleType = data["vehicleType"] as? String ?: "standard",
                status = RideStatus.parse(data["status"] as? String),
                requestedAt = ts("requestedAt") ?: System.currentTimeMillis(),
                acceptedAt = ts("acceptedAt"),
                arrivedAt = ts("arrivedAt"),
                startedAt = ts("startedAt"),
                completedAt = ts("completedAt"),
                cancelledAt = ts("cancelledAt"),
                paymentMethod = data["paymentMethod"] as? String ?: "cash",
                paymentStatus = PaymentStatus.parse(data["paymentStatus"] as? String),
                userRating = (data["userRating"] as? Number)?.toInt(),
                userReview = data["userReview"] as? String,
                driverRating = (data["driverRating"] as? Number)?.toInt(),
                driverReview = data["driverReview"] as? String,
                cancellationReason = data["cancellationReason"] as? String,
                cancelledBy = data["cancelledBy"] as? String,
            )
        }
    }
}
