package dj.velox.client.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import dj.velox.client.domain.model.Promotion
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Service promotions — collection `promotions` (miroir promotion_service.dart). */
@Singleton
class PromotionService @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    suspend fun getActivePromotionsForRestaurant(restaurantId: String): List<Promotion> =
        runCatching {
            firestore.collection("promotions")
                .whereEqualTo("restaurantId", restaurantId)
                .whereEqualTo("isActive", true)
                .get().await()
                .documents.mapNotNull { runCatching { Promotion.fromFirestore(it) }.getOrNull() }
                .filter { it.isCurrentlyActive }
        }.getOrDefault(emptyList())
}
