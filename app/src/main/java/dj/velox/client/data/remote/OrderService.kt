package dj.velox.client.data.remote

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.functions.FirebaseFunctions
import dj.velox.client.domain.model.Order
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service des commandes food — collection Firestore `orders`.
 * Miroir (côté client) de lib/services/order_service.dart.
 *
 * NB : pas de orderBy('createdAt') côté Firestore — le champ est stocké
 * tantôt en String ISO, tantôt en Timestamp selon l'app émettrice. On trie
 * donc côté client après lecture (comme dans l'app Flutter).
 */
@Singleton
class OrderService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
) {
    private val collection get() = firestore.collection(COLLECTION)

    suspend fun createOrder(order: Order): String? = runCatching {
        val orderId = collection.add(order.toMap()).await().id
        Log.d(TAG, "✅ Commande créée: id=$orderId resto=${order.restaurantId} (${order.restaurantName})")
        notifyRestaurant(order, orderId)
        orderId
    }.onFailure {
        Log.e(TAG, "❌ Échec création commande Firestore", it)
    }.getOrNull()

    /**
     * Notifie le restaurant de la nouvelle commande via la Cloud Function callable
     * `sendRestaurantNotification` (miroir de `FoodNotificationService.notifyRestaurantNewOrder`
     * côté Flutter). Le trigger serveur `onOrderCreated` envoie aussi une notif, mais il peut
     * s'interrompre avant (validation des prix) — ce push client est le chemin fiable.
     *
     * Fire-and-forget : un échec d'envoi ne doit jamais faire échouer la création de commande.
     */
    private suspend fun notifyRestaurant(order: Order, orderId: String) {
        if (order.restaurantId.isBlank()) {
            Log.w(TAG, "🔔 notifyRestaurant: restaurantId vide → abandon")
            return
        }
        Log.d(
            TAG,
            "🔔 notifyRestaurant → appel callable 'sendRestaurantNotification' " +
                "resto=${order.restaurantId} order=$orderId client=${order.customerName} total=${order.total}",
        )
        runCatching {
            val result = functions.getHttpsCallable("sendRestaurantNotification").call(
                mapOf(
                    "restaurantId" to order.restaurantId,
                    "restaurantName" to order.restaurantName,
                    "orderId" to orderId,
                    "customerName" to order.customerName,
                    "total" to order.total,
                ),
            ).await()
            // Réponse attendue : { success: true, messageId } ou { success: false, message }
            Log.d(TAG, "🔔 notifyRestaurant ✅ réponse callable = ${result.getData()}")
        }.onFailure { e ->
            Log.e(TAG, "🔔 notifyRestaurant ❌ échec de l'appel callable", e)
        }
    }

    suspend fun getOrderById(orderId: String): Order? = runCatching {
        val doc = collection.document(orderId).get().await()
        if (doc.exists()) Order.fromFirestore(doc) else null
    }.getOrNull()

    /** 20 commandes les plus récentes d'un utilisateur (tri client). */
    suspend fun getUserOrders(userId: String): List<Order> = runCatching {
        collection.whereEqualTo("userId", userId).get().await()
            .documents.mapNotNull { runCatching { Order.fromFirestore(it) }.getOrNull() }
            .sortedByDescending { it.createdAt }
            .take(20)
    }.getOrDefault(emptyList())

    /** Stream live des commandes utilisateur (20 récentes, tri client). */
    fun streamUserOrders(userId: String): Flow<List<Order>> = callbackFlow {
        val registration = collection.whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val orders = snapshot?.documents
                    ?.mapNotNull { runCatching { Order.fromFirestore(it) }.getOrNull() }
                    ?.sortedByDescending { it.createdAt }
                    ?.take(20)
                    ?: emptyList()
                trySend(orders)
            }
        awaitClose { registration.remove() }
    }

    /** Stream d'une commande précise (suivi temps réel). */
    fun listenToOrder(orderId: String): Flow<Order> = callbackFlow {
        val registration = collection.document(orderId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                if (snapshot != null && snapshot.exists()) {
                    runCatching { Order.fromFirestore(snapshot) }
                        .onSuccess { trySend(it) }
                        .onFailure { close(it) }
                }
            }
        awaitClose { registration.remove() }
    }

    /** Nombre de commandes « completed » de l'utilisateur (base des points gagnés : ×10). */
    suspend fun getCompletedOrdersCount(userId: String): Int = runCatching {
        collection.whereEqualTo("userId", userId).whereEqualTo("status", "completed")
            .get().await().size()
    }.getOrDefault(0)

    /**
     * Stream live des stats commandes (port de `orderStatsProvider` Flutter) : compte des
     * commandes « completed » + somme des `total`. Sert à l'accueil (commandes, dépenses, points).
     * Même requête que Flutter (userId + status == completed) → l'index composite existe déjà
     * côté backend partagé.
     */
    fun streamUserStats(userId: String): Flow<OrderStats> = callbackFlow {
        val reg = collection.whereEqualTo("userId", userId).whereEqualTo("status", "completed")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(OrderStats()); return@addSnapshotListener }
                val docs = snapshot?.documents ?: emptyList()
                val total = docs.sumOf { (it.get("total") as? Number)?.toDouble() ?: 0.0 }
                trySend(OrderStats(completedCount = docs.size, totalSpent = total))
            }
        awaitClose { reg.remove() }
    }

    /** Débite les points fidélité utilisés (incrémente `users/{uid}.redeemedPoints`). */
    suspend fun redeemPoints(userId: String, points: Int) {
        if (points <= 0) return
        runCatching {
            firestore.collection("users").document(userId)
                .update("redeemedPoints", FieldValue.increment(points.toLong())).await()
        }
    }

    /**
     * Notation d'une commande livrée (port de `order_completed_screen`) :
     * met à jour la commande (déclenche la CF `onOrderRated`) + ajoute l'avis
     * dans `restaurants/{id}/avis`.
     */
    suspend fun submitOrderRating(order: Order, restaurantRating: Int, driverRating: Int, comment: String?) {
        val now = FieldValue.serverTimestamp()
        val update = buildMap<String, Any?> {
            put("restaurantRating", restaurantRating)
            put("driverRating", driverRating)
            if (!comment.isNullOrBlank()) put("restaurantComment", comment)
            put("ratedAt", now)
            put("updatedAt", now)
        }
        collection.document(order.id).update(update).await()
        runCatching {
            firestore.collection("restaurants").document(order.restaurantId).collection("avis").add(
                mapOf(
                    "orderId" to order.id,
                    "userId" to order.userId,
                    "clientNom" to order.customerName,
                    "note" to restaurantRating,
                    "commentaire" to (comment ?: ""),
                    "createdAt" to now,
                ),
            ).await()
        }
    }

    suspend fun cancelOrder(orderId: String): Boolean = runCatching {
        collection.document(orderId).update(
            mapOf(
                "status" to Order.STATUS_CANCELLED,
                "cancelledBy" to "customer",
                "cancelledAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        true
    }.getOrDefault(false)

    /** Position live d'un livreur (`livreurs/{id}.currentLocation`). null si indisponible. */
    fun streamDriverLocation(driverId: String): Flow<DriverLocation?> = callbackFlow {
        val reg = firestore.collection("livreurs").document(driverId)
            .addSnapshotListener { snap, error ->
                if (error != null) { trySend(null); return@addSnapshotListener }
                if (snap == null || !snap.exists()) { trySend(null); return@addSnapshotListener }
                val updatedAt = snap.getTimestamp("updatedAt")?.toDate()?.time ?: System.currentTimeMillis()
                val loc = when (val raw = snap.get("currentLocation")) {
                    is GeoPoint -> DriverLocation(raw.latitude, raw.longitude, updatedAt)
                    is Map<*, *> -> {
                        val lat = (raw["latitude"] as? Number)?.toDouble()
                        val lng = (raw["longitude"] as? Number)?.toDouble()
                        if (lat != null && lng != null) DriverLocation(lat, lng, updatedAt) else null
                    }
                    else -> null
                }
                trySend(loc)
            }
        awaitClose { reg.remove() }
    }

    companion object {
        private const val COLLECTION = "orders"
        private const val TAG = "VeloxOrder"
    }
}

/** Position d'un livreur à un instant (epoch-millis). */
data class DriverLocation(val latitude: Double, val longitude: Double, val updatedAt: Long)

/**
 * Stats commandes du client (miroir de `OrderStats` Flutter).
 * `completedCount` = commandes livrées ; points gagnés = completedCount × 10.
 */
data class OrderStats(val completedCount: Int = 0, val totalSpent: Double = 0.0)
