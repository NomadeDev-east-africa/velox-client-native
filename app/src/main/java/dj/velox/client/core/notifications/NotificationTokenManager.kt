package dj.velox.client.core.notifications

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestion du token FCM (équivalent de la partie token de NotificationService.dart) :
 * écrit `users/{uid}.fcmToken` pour que les Cloud Functions puissent cibler l'appareil.
 */
@Singleton
class NotificationTokenManager @Inject constructor(
    private val messaging: FirebaseMessaging,
    private val firestore: FirebaseFirestore,
) {
    /** Récupère le token courant et l'enregistre pour [uid]. */
    suspend fun registerCurrentToken(uid: String) {
        val token = messaging.token.await()
        saveToken(uid, token)
    }

    suspend fun saveToken(uid: String, token: String) {
        firestore.collection("users").document(uid).set(
            mapOf("fcmToken" to token, "updatedAt" to FieldValue.serverTimestamp()),
            SetOptions.merge(),
        ).await()
    }

    /** Supprime le token à la déconnexion (l'ancien user ne reçoit plus de push). */
    suspend fun clearToken(uid: String) {
        firestore.collection("users").document(uid).update(
            mapOf("fcmToken" to FieldValue.delete(), "updatedAt" to FieldValue.serverTimestamp()),
        ).await()
    }
}
