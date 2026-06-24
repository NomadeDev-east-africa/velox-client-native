package dj.velox.client.data.remote

import android.net.Uri
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storageMetadata
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mise à jour du profil utilisateur (`users/{uid}`) + upload de la photo
 * (Storage `profile_photos/{uid}.jpg`). Miroir des méthodes `updateProfile` /
 * `uploadProfilePhoto` d'`UserNotifier`.
 */
@Singleton
class UserService @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
) {
    suspend fun updateProfile(
        name: String? = null,
        phone: String? = null,
        photoUrl: String? = null,
        birthDateMillis: Long? = null,
    ) {
        val user = auth.currentUser ?: throw IllegalStateException("Utilisateur non connecté")
        val updates = buildMap<String, Any?> {
            if (name != null) put("name", name)
            if (phone != null) put("phone", phone)
            if (photoUrl != null) put("photoUrl", photoUrl)
            if (birthDateMillis != null) put("birthDate", Timestamp(Date(birthDateMillis)))
        }
        if (updates.isEmpty()) return

        firestore.collection("users").document(user.uid)
            .update(updates + mapOf("updatedAt" to FieldValue.serverTimestamp())).await()

        // Synchronise aussi le profil Firebase Auth (displayName / photo).
        if (name != null || photoUrl != null) {
            val req = UserProfileChangeRequest.Builder().apply {
                if (name != null) displayName = name
                if (photoUrl != null) photoUri = Uri.parse(photoUrl)
            }.build()
            user.updateProfile(req).await()
            user.reload().await()
        }
    }

    /** Upload des octets JPEG → URL de téléchargement, puis maj `photoUrl`. */
    suspend fun uploadProfilePhoto(bytes: ByteArray): String {
        val user = auth.currentUser ?: throw IllegalStateException("Utilisateur non connecté")
        val ref = storage.reference.child("profile_photos/${user.uid}.jpg")
        ref.putBytes(bytes, storageMetadata { contentType = "image/jpeg" }).await()
        val url = ref.downloadUrl.await().toString()
        updateProfile(photoUrl = url)
        return url
    }
}
