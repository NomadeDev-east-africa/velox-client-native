package dj.velox.client.data.remote

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import dj.velox.client.domain.model.FavoriteDriver
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.Query
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chauffeurs favoris — sous-collection `users/{uid}/favorite_drivers`.
 * Miroir de favorite_drivers_service.dart.
 */
@Singleton
class FavoriteDriversService @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private fun favorites(userId: String) =
        firestore.collection("users").document(userId).collection("favorite_drivers")

    suspend fun addToFavorites(
        userId: String,
        driverId: String,
        driverName: String,
        driverPhotoUrl: String? = null,
        driverPhone: String? = null,
        driverRating: Double? = null,
        vehicleType: String? = null,
        rideId: String,
    ) {
        val docRef = favorites(userId).document(driverId)
        val existing = docRef.get().await()
        if (existing.exists()) {
            val currentCount = (existing.get("ridesCount") as? Number)?.toInt() ?: 0
            docRef.update(
                mapOf(
                    "ridesCount" to currentCount + 1,
                    "lastRideId" to rideId,
                    "driverName" to driverName,
                    "driverPhotoUrl" to driverPhotoUrl,
                    "driverRating" to driverRating,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } else {
            val favorite = FavoriteDriver(
                driverId = driverId,
                driverName = driverName,
                driverPhotoUrl = driverPhotoUrl,
                driverPhone = driverPhone,
                driverRating = driverRating,
                vehicleType = vehicleType,
                addedAt = System.currentTimeMillis(),
                ridesCount = 1,
                lastRideId = rideId,
            )
            docRef.set(favorite.toFirestore()).await()
        }
    }

    suspend fun removeFromFavorites(userId: String, driverId: String) {
        favorites(userId).document(driverId).delete().await()
    }

    suspend fun isFavorite(userId: String, driverId: String): Boolean = runCatching {
        favorites(userId).document(driverId).get().await().exists()
    }.getOrDefault(false)

    /** Stream des favoris trié par addedAt décroissant. */
    fun getFavoriteDrivers(userId: String): Flow<List<FavoriteDriver>> = callbackFlow {
        val reg = favorites(userId)
            .orderBy("addedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                trySend(
                    snapshot?.documents
                        ?.mapNotNull { runCatching { FavoriteDriver.fromFirestore(it) }.getOrNull() }
                        ?: emptyList()
                )
            }
        awaitClose { reg.remove() }
    }

    suspend fun getFavoriteDriversList(userId: String): List<FavoriteDriver> = runCatching {
        favorites(userId).orderBy("addedAt", Query.Direction.DESCENDING).get().await()
            .documents.mapNotNull { runCatching { FavoriteDriver.fromFirestore(it) }.getOrNull() }
    }.getOrDefault(emptyList())

    suspend fun getFavoriteDriver(userId: String, driverId: String): FavoriteDriver? = runCatching {
        val doc = favorites(userId).document(driverId).get().await()
        if (doc.exists()) FavoriteDriver.fromFirestore(doc) else null
    }.getOrNull()

    suspend fun updateFavoriteDriver(
        userId: String,
        driverId: String,
        driverName: String? = null,
        driverPhotoUrl: String? = null,
        driverRating: Double? = null,
    ) {
        val updates = buildMap<String, Any?> {
            driverName?.let { put("driverName", it) }
            driverPhotoUrl?.let { put("driverPhotoUrl", it) }
            driverRating?.let { put("driverRating", it) }
            if (isNotEmpty()) put("updatedAt", FieldValue.serverTimestamp())
        }
        if (updates.isNotEmpty()) {
            runCatching { favorites(userId).document(driverId).update(updates).await() }
        }
    }
}
