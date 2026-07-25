package dj.velox.client.data.remote

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Restaurants favoris — champ tableau `users/{uid}.favoriteRestaurants` (schéma **partagé**
 * avec l'app Flutter iOS, pour que les favoris se synchronisent entre plateformes).
 *
 * ⚠️ L'écriture exige que `favoriteRestaurants` figure dans la liste blanche `onlyFields`
 * des règles Firestore du document utilisateur. Tant que cette règle n'est pas déployée
 * (backend partagé, repo `nomade_client`), le toggle est refusé (`permission-denied`) et
 * l'UI fait un rollback (le cœur se remplit puis se vide) — comportement identique à iOS
 * avant le fix règles.
 */
@Singleton
class FavoriteRestaurantsService @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private fun userDoc(userId: String) = firestore.collection("users").document(userId)

    /** Stream de l'ensemble des IDs de restaurants favoris de l'utilisateur. */
    fun streamFavorites(userId: String): Flow<Set<String>> = callbackFlow {
        val reg = userDoc(userId).addSnapshotListener { snap, error ->
            if (error != null) { trySend(emptySet()); return@addSnapshotListener }
            @Suppress("UNCHECKED_CAST")
            val ids = (snap?.get("favoriteRestaurants") as? List<String>)?.toSet() ?: emptySet()
            trySend(ids)
        }
        awaitClose { reg.remove() }
    }

    /** Ajoute ou retire un restaurant des favoris (arrayUnion / arrayRemove). */
    suspend fun setFavorite(userId: String, restaurantId: String, favorite: Boolean) {
        val op = if (favorite) FieldValue.arrayUnion(restaurantId) else FieldValue.arrayRemove(restaurantId)
        userDoc(userId).update("favoriteRestaurants", op).await()
    }
}
