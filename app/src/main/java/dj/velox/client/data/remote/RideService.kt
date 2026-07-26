package dj.velox.client.data.remote

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import dj.velox.client.domain.model.Ride
import dj.velox.client.domain.model.RideStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service de gestion des courses — collection Firestore `taxiRides`.
 * Miroir de lib/services/ride_service.dart (camelCase, mêmes champs).
 */
@Singleton
class RideService @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private val collection get() = firestore.collection(COLLECTION)

    // ─────────────────────────────────────────────────────────────
    // CRÉATION
    // ─────────────────────────────────────────────────────────────

    suspend fun createRide(
        userId: String,
        userName: String,
        userPhone: String,
        userPhotoUrl: String?,
        pickupLatitude: Double,
        pickupLongitude: Double,
        pickupAddress: String,
        pickupPlaceName: String,
        destinationLatitude: Double,
        destinationLongitude: Double,
        destinationAddress: String,
        destinationPlaceName: String,
        distance: Double,
        estimatedDuration: Int,
        estimatedFare: Double,
        vehicleType: String,
        paymentMethod: String,
    ): String {
        val data = hashMapOf(
            "userId" to userId,
            "userName" to userName,
            "userPhone" to userPhone,
            "userPhotoUrl" to userPhotoUrl,
            "pickup" to mapOf(
                "latitude" to pickupLatitude,
                "longitude" to pickupLongitude,
                "address" to pickupAddress,
                "placeName" to pickupPlaceName,
            ),
            "destination" to mapOf(
                "latitude" to destinationLatitude,
                "longitude" to destinationLongitude,
                "address" to destinationAddress,
                "placeName" to destinationPlaceName,
            ),
            "distance" to distance,
            "estimatedDuration" to estimatedDuration,
            "estimatedFare" to estimatedFare,
            "finalFare" to null,
            "vehicleType" to vehicleType,
            "vehicleId" to null,
            "driverId" to null,
            "driverName" to null,
            "driverPhone" to null,
            "driverPhotoUrl" to null,
            "status" to "requested",
            "paymentMethod" to paymentMethod,
            "paymentStatus" to "pending",
            "requestedAt" to FieldValue.serverTimestamp(),
            "acceptedAt" to null,
            "arrivedAt" to null,
            "startedAt" to null,
            "completedAt" to null,
            "cancelledAt" to null,
            "updatedAt" to FieldValue.serverTimestamp(),
            "userRating" to null,
            "userReview" to null,
            "driverRating" to null,
            "driverReview" to null,
            "cancellationReason" to null,
            "cancelledBy" to null,
        )
        val docRef = collection.add(data).await()
        return docRef.id
    }

    // ─────────────────────────────────────────────────────────────
    // STREAM TEMPS RÉEL
    // ─────────────────────────────────────────────────────────────

    /**
     * Écoute une course en temps réel. callbackFlow émet à chaque snapshot ;
     * `awaitClose` retire le listener quand le collecteur s'arrête.
     * (Le timeout 45s + reconnexion backoff seront gérés par le ViewModel,
     *  comme dans ActiveRideNotifier côté Flutter.)
     */
    fun listenToRide(rideId: String): Flow<Ride> = callbackFlow {
        val registration = collection.document(rideId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    runCatching { Ride.fromFirestore(snapshot) }
                        .onSuccess { trySend(it) }
                        .onFailure { close(it) }
                }
            }
        awaitClose { registration.remove() }
    }.distinctUntilChanged()

    // ─────────────────────────────────────────────────────────────
    // LECTURE ONE-TIME
    // ─────────────────────────────────────────────────────────────

    suspend fun getRideById(rideId: String): Ride? {
        val doc = collection.document(rideId).get().await()
        if (!doc.exists()) return null
        return runCatching { Ride.fromFirestore(doc) }.getOrNull()
    }

    /** Course active d'un utilisateur (reprise après kill). */
    suspend fun getActiveRide(userId: String): Ride? {
        val snapshot = collection
            .whereEqualTo("userId", userId)
            .whereIn("status", listOf("requested", "accepted", "arriving", "arrived", "started"))
            .limit(1)
            .get()
            .await()
        return snapshot.documents.firstOrNull()
            ?.let { runCatching { Ride.fromFirestore(it) }.getOrNull() }
    }

    // ─────────────────────────────────────────────────────────────
    // MISES À JOUR
    // ─────────────────────────────────────────────────────────────

    suspend fun cancelRide(rideId: String, reason: String, cancelledBy: String) {
        collection.document(rideId).update(
            mapOf(
                "status" to RideStatus.CANCELLED.toFirestore(),
                "cancelledAt" to FieldValue.serverTimestamp(),
                "cancellationReason" to reason,
                "cancelledBy" to cancelledBy,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    suspend fun rateDriver(rideId: String, rating: Int, review: String?) {
        require(rating in 1..5) { "La note doit être entre 1 et 5" }
        collection.document(rideId).update(
            mapOf(
                "userRating" to rating,
                "userReview" to review,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    // ─────────────────────────────────────────────────────────────
    // HISTORIQUE
    // ─────────────────────────────────────────────────────────────

    /**
     * Stream live du nombre de courses **terminées** de l'utilisateur (stat « Courses » de l'accueil).
     * Requête sur `userId` seul (index simple automatique, pas d'index composite à créer) ; le filtre
     * `status == completed` est appliqué côté client. Repli sur 0 en cas d'erreur.
     */
    fun streamCompletedRidesCount(userId: String): Flow<Int> = callbackFlow {
        val reg = collection.whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(0); return@addSnapshotListener }
                val count = snapshot?.documents
                    ?.mapNotNull { runCatching { Ride.fromFirestore(it) }.getOrNull() }
                    ?.count { it.isCompleted }
                    ?: 0
                trySend(count)
            }
        awaitClose { reg.remove() }
    }

    /**
     * Historique des courses du client, le plus récent d'abord.
     *
     * Le tri est fait **en mémoire** et non par `orderBy("requestedAt")` : combiné au filtre
     * `userId`, l'orderBy exige un index composite `taxiRides(userId, requestedAt)` qui n'est
     * pas déployé — la requête échouait alors silencieusement et l'historique VTC restait
     * vide. Le filtre sur `userId` seul se contente de l'index automatique, et le volume par
     * client (quelques dizaines de courses) rend le tri local négligeable.
     */
    suspend fun getUserRideHistory(userId: String, limit: Int = 20): List<Ride> {
        val snapshot = collection
            .whereEqualTo("userId", userId)
            .get()
            .await()
        return snapshot.documents
            .mapNotNull { runCatching { Ride.fromFirestore(it) }.getOrNull() }
            .sortedByDescending { it.requestedAt }
            .take(limit)
    }

    /**
     * Position live du chauffeur taxi (`drivers/{id}`). On lit `currentLocation` puis
     * `location` (GeoPoint ou map lat/lng). null si indisponible / GPS chauffeur éteint.
     */
    fun streamDriverLocation(driverId: String): Flow<DriverLocation?> = callbackFlow {
        val reg = firestore.collection("drivers").document(driverId)
            .addSnapshotListener { snap, error ->
                if (error != null) { trySend(null); return@addSnapshotListener }
                if (snap == null || !snap.exists()) { trySend(null); return@addSnapshotListener }
                val fallbackTs = snap.getTimestamp("lastHeartbeat")?.toDate()?.time ?: System.currentTimeMillis()
                val raw = snap.get("currentLocation") ?: snap.get("location")
                val loc = when (raw) {
                    is GeoPoint -> DriverLocation(raw.latitude, raw.longitude, fallbackTs)
                    is Map<*, *> -> {
                        val lat = (raw["latitude"] as? Number)?.toDouble()
                        val lng = (raw["longitude"] as? Number)?.toDouble()
                        // `updatedAt` est écrit DANS la map currentLocation par l'app chauffeur.
                        val ts = (raw["updatedAt"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: fallbackTs
                        if (lat != null && lng != null) DriverLocation(lat, lng, ts) else null
                    }
                    else -> null
                }
                trySend(loc)
            }
        awaitClose { reg.remove() }
    }

    companion object {
        private const val COLLECTION = "taxiRides"
    }
}
