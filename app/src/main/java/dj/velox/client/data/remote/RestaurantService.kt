package dj.velox.client.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import dj.velox.client.domain.model.Restaurant
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Service restaurants — collection `restaurants` (miroir restaurant_service.dart). */
@Singleton
class RestaurantService @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private val collection get() = firestore.collection(COLLECTION)

    fun streamRestaurants(): Flow<List<Restaurant>> = callbackFlow {
        val reg = collection.whereEqualTo("isActive", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                trySend(snapshot.toRestaurants())
            }
        awaitClose { reg.remove() }
    }

    suspend fun getRestaurants(): List<Restaurant> = runCatching {
        collection.whereEqualTo("isActive", true).get().await()
            .toRestaurants().sortedByDescending { it.rating }
    }.getOrDefault(emptyList())

    suspend fun getRestaurantById(restaurantId: String): Restaurant? = runCatching {
        val doc = collection.document(restaurantId).get().await()
        if (doc.exists()) Restaurant.fromFirestore(doc) else null
    }.getOrNull()

    fun streamRestaurant(restaurantId: String): Flow<Restaurant?> = callbackFlow {
        val reg = collection.document(restaurantId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                trySend(if (snapshot != null && snapshot.exists()) Restaurant.fromFirestore(snapshot) else null)
            }
        awaitClose { reg.remove() }
    }

    suspend fun getOpenRestaurants(): List<Restaurant> = runCatching {
        collection.whereEqualTo("isActive", true).whereEqualTo("isOpen", true).get().await()
            .toRestaurants().sortedByDescending { it.rating }
    }.getOrDefault(emptyList())

    suspend fun searchRestaurants(query: String): List<Restaurant> = runCatching {
        collection.whereEqualTo("isActive", true).get().await()
            .toRestaurants().filter { it.name.contains(query, ignoreCase = true) }
    }.getOrDefault(emptyList())

    suspend fun getPopularRestaurants(limit: Int = 10): List<Restaurant> = runCatching {
        collection.whereEqualTo("isActive", true).get().await()
            .toRestaurants().sortedByDescending { it.totalOrders }.take(limit)
    }.getOrDefault(emptyList())

    suspend fun getTopRatedRestaurants(limit: Int = 10): List<Restaurant> = runCatching {
        collection.whereEqualTo("isActive", true).get().await()
            .toRestaurants().filter { it.rating > 0 }.sortedByDescending { it.rating }.take(limit)
    }.getOrDefault(emptyList())

    private fun com.google.firebase.firestore.QuerySnapshot?.toRestaurants(): List<Restaurant> =
        this?.documents?.mapNotNull { runCatching { Restaurant.fromFirestore(it) }.getOrNull() }
            ?: emptyList()

    companion object {
        private const val COLLECTION = "restaurants"
    }
}
