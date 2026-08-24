package dj.velox.client.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import dj.velox.client.domain.model.Banner
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Service bannières — collection `banners` (carrousel promo piloté depuis l'admin). */
@Singleton
class BannerService @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    /**
     * Trie par `order` côté Firestore (champ unique → pas d'index composite requis) ;
     * filtre `active == true` côté Kotlin plutôt que dans la requête, pour la même raison.
     */
    fun streamActiveBanners(): Flow<List<Banner>> = callbackFlow {
        val reg = firestore.collection(COLLECTION)
            .orderBy("order")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val banners = snapshot?.documents
                    ?.mapNotNull { runCatching { Banner.fromFirestore(it) }.getOrNull() }
                    ?.filter { it.active }
                    ?: emptyList()
                trySend(banners)
            }
        awaitClose { reg.remove() }
    }

    companion object {
        private const val COLLECTION = "banners"
    }
}
