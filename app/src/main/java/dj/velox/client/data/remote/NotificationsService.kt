package dj.velox.client.data.remote

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dj.velox.client.domain.model.AppNotification
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Historique de notifications persistant — sous-collection Firestore
 * `users/{uid}/notifications`. Alimentée côté serveur à chaque push envoyé (rétention
 * 90 jours, purge par Cloud Function planifiée). Miroir de l'écran équivalent iOS
 * (Flutter, backend partagé) : mêmes règles de sécurité, lecture seule sauf `read`/`readAt`.
 */
@Singleton
class NotificationsService @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private fun collection(uid: String) =
        firestore.collection("users").document(uid).collection(COLLECTION)

    /** Stream live, plus récentes en premier (index `createdAt` DESC déjà déployé). */
    fun streamNotifications(uid: String): Flow<List<AppNotification>> = callbackFlow {
        val registration = collection(uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snapshot?.documents
                    ?.mapNotNull { runCatching { AppNotification.fromFirestore(it) }.getOrNull() }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Marque une notification comme lue. N'envoie que `read`/`readAt` : la règle Firestore
     * rejette tout update qui touche un autre champ — inutile de prévoir un fallback.
     */
    suspend fun markAsRead(uid: String, notificationId: String) {
        runCatching {
            collection(uid).document(notificationId)
                .update(mapOf("read" to true, "readAt" to FieldValue.serverTimestamp()))
                .await()
        }
    }

    companion object {
        private const val COLLECTION = "notifications"
    }
}
