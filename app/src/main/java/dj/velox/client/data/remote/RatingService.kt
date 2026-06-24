package dj.velox.client.data.remote

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Stats de notation d'un chauffeur. */
data class DriverRatingStats(
    val averageRating: Double,
    val totalRatings: Int,
    val ratingDistribution: Map<Int, Int>,
)

/** Avis récent d'un chauffeur. */
data class DriverReview(
    val userName: String,
    val rating: Int?,
    val review: String?,
    val createdAt: Long?,
)

/**
 * Notation des chauffeurs (miroir rating_service.dart).
 * La Cloud Function onTaxiRideRated recalcule drivers.rating côté serveur.
 */
@Singleton
class RatingService @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    suspend fun rateDriver(
        driverId: String,
        rideId: String,
        userId: String,
        rating: Int,
        review: String?,
    ) {
        require(rating in 1..5) { "Note invalide : doit être entre 1 et 5" }
        updateRideRating(rideId, rating, review)
        createRatingEntry(driverId, userId, rideId, rating, review)
    }

    private suspend fun updateRideRating(rideId: String, rating: Int, review: String?) {
        firestore.collection(RIDES).document(rideId).update(
            mapOf(
                "userRating" to rating,
                "userReview" to review,
                "ratedAt" to FieldValue.serverTimestamp(),
                "rated" to true,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    private suspend fun createRatingEntry(
        driverId: String,
        userId: String,
        rideId: String,
        rating: Int,
        review: String?,
    ) {
        val userName = runCatching {
            val userDoc = firestore.collection("users").document(userId).get().await()
            userDoc.getString("name") ?: userDoc.getString("displayName")
        }.getOrNull()

        runCatching {
            firestore.collection("drivers").document(driverId).collection("ratings").add(
                mapOf(
                    "userId" to userId,
                    "userName" to userName,
                    "rideId" to rideId,
                    "rating" to rating,
                    "review" to review,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } // non bloquant
    }

    suspend fun isRideRated(rideId: String): Boolean = runCatching {
        val doc = firestore.collection(RIDES).document(rideId).get().await()
        doc.getBoolean("rated") == true || doc.get("userRating") != null
    }.getOrDefault(false)

    suspend fun getDriverRatingStats(driverId: String): DriverRatingStats = runCatching {
        val driverDoc = firestore.collection("drivers").document(driverId).get().await()
        val ratings = firestore.collection("drivers").document(driverId)
            .collection("ratings").get().await()

        val distribution = (1..5).associateWith { 0 }.toMutableMap()
        for (doc in ratings.documents) {
            val r = (doc.get("rating") as? Number)?.toInt()
            if (r != null && r in 1..5) distribution[r] = (distribution[r] ?: 0) + 1
        }

        DriverRatingStats(
            averageRating = (driverDoc.get("rating") as? Number)?.toDouble() ?: 0.0,
            totalRatings = (driverDoc.get("totalRatings") as? Number)?.toInt() ?: 0,
            ratingDistribution = distribution,
        )
    }.getOrDefault(DriverRatingStats(0.0, 0, emptyMap()))

    suspend fun getDriverRecentReviews(driverId: String, limit: Long = 10): List<DriverReview> =
        runCatching {
            firestore.collection("drivers").document(driverId).collection("ratings")
                .whereNotEqualTo("review", null)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit)
                .get().await()
                .documents.map { doc ->
                    DriverReview(
                        userName = doc.getString("userName") ?: "Utilisateur",
                        rating = (doc.get("rating") as? Number)?.toInt(),
                        review = doc.getString("review"),
                        createdAt = doc.getTimestamp("createdAt")?.toDate()?.time,
                    )
                }
        }.getOrDefault(emptyList())

    companion object {
        private const val RIDES = "taxiRides"
    }
}
