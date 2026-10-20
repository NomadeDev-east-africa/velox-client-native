package dj.velox.client.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import dj.velox.client.domain.model.MenuItem
import dj.velox.client.domain.model.Promotion
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Service menus — collection `menuItems` (miroir menu_service.dart). */
@Singleton
class MenuService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val promotionService: PromotionService,
) {
    private val collection get() = firestore.collection(COLLECTION)

    /**
     * Applique les promotions actives aux plats : une promo « item » (plat précis) l'emporte
     * sur une promo « category ». Le pourcentage trouvé alimente `discountPercentage`, si bien
     * que toute l'UI existante (prix barré, badge) l'affiche automatiquement.
     */
    private fun applyPromotions(dishes: List<MenuItem>, promos: List<Promotion>): List<MenuItem> {
        if (promos.isEmpty()) return dishes
        return dishes.map { dish ->
            val forRestaurant = promos.filter { it.restaurantId == dish.restaurantId }
            val pct = forRestaurant.firstOrNull { it.matchesItem(dish.id, dish.name) }?.discountPercent
                ?: forRestaurant.firstOrNull { it.matchesCategory(dish.category) }?.discountPercent
                ?: 0
            if (pct > 0) dish.copy(discountPercentage = pct) else dish
        }
    }

    fun streamMenus(restaurantId: String): Flow<List<MenuItem>> {
        val menus = callbackFlow<List<MenuItem>> {
            val reg = collection
                .whereEqualTo("restaurantId", restaurantId)
                .whereEqualTo("isAvailable", true)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) { close(error); return@addSnapshotListener }
                    trySend(snapshot.toMenus().sortedBy { it.category })
                }
            awaitClose { reg.remove() }
        }
        // Promos chargées une fois ; `combine` n'émet qu'une fois les deux prêts (pas de flash).
        val promos = flow { emit(promotionService.getActivePromotionsForRestaurant(restaurantId)) }
        return combine(menus, promos) { m, p -> applyPromotions(m, p) }
    }

    suspend fun getAllMenus(): List<MenuItem> = runCatching {
        val menus = collection.whereEqualTo("isAvailable", true).get().await().toMenus()
        applyPromotions(menus, promotionService.getAllActivePromotions())
    }.getOrDefault(emptyList())

    suspend fun getMenusByRestaurant(restaurantId: String): List<MenuItem> = runCatching {
        val menus = collection
            .whereEqualTo("restaurantId", restaurantId)
            .whereEqualTo("isAvailable", true)
            .get().await().toMenus().sortedBy { it.category }
        applyPromotions(menus, promotionService.getActivePromotionsForRestaurant(restaurantId))
    }.getOrDefault(emptyList())

    suspend fun getMenuById(menuId: String): MenuItem? = runCatching {
        val doc = collection.document(menuId).get().await()
        if (!doc.exists()) return@runCatching null
        val dish = MenuItem.fromFirestore(doc)
        applyPromotions(listOf(dish), promotionService.getActivePromotionsForRestaurant(dish.restaurantId)).first()
    }.getOrNull()

    suspend fun getMenusByCategory(restaurantId: String, category: String): List<MenuItem> =
        runCatching {
            collection
                .whereEqualTo("restaurantId", restaurantId)
                .whereEqualTo("isAvailable", true)
                .whereEqualTo("category", category)
                .get().await().toMenus()
        }.getOrDefault(emptyList())

    suspend fun getCategories(restaurantId: String): List<String> = runCatching {
        collection
            .whereEqualTo("restaurantId", restaurantId)
            .whereEqualTo("isAvailable", true)
            .get().await()
            .documents.map { (it.getString("category") ?: "Autre") }.distinct()
    }.getOrDefault(emptyList())

    suspend fun getFeaturedMenus(restaurantId: String, limit: Long = 3): List<MenuItem> =
        runCatching {
            collection
                .whereEqualTo("restaurantId", restaurantId)
                .whereEqualTo("isAvailable", true)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit)
                .get().await().toMenus()
        }.getOrDefault(emptyList())

    suspend fun searchMenus(restaurantId: String, query: String): List<MenuItem> = runCatching {
        collection
            .whereEqualTo("restaurantId", restaurantId)
            .whereEqualTo("isAvailable", true)
            .get().await().toMenus()
            .filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true)
            }
    }.getOrDefault(emptyList())

    /** Temps de préparation moyen du restaurant (25 min par défaut). */
    suspend fun getAveragePreparationTime(restaurantId: String): Int {
        val menus = getMenusByRestaurant(restaurantId)
        if (menus.isEmpty()) return 25
        return (menus.sumOf { it.preparationTime }.toDouble() / menus.size).toInt()
    }

    private fun QuerySnapshot?.toMenus(): List<MenuItem> =
        this?.documents?.mapNotNull { runCatching { MenuItem.fromFirestore(it) }.getOrNull() }
            ?: emptyList()

    companion object {
        private const val COLLECTION = "menuItems"
    }
}
