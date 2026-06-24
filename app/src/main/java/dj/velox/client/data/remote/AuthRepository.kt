package dj.velox.client.data.remote

import android.app.Activity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dj.velox.client.domain.model.VeloxUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Erreur d'authentification déjà traduite (message prêt pour l'UI). */
class AuthException(message: String) : Exception(message)

/**
 * Authentification Firebase — miroir de auth_service.dart + partie auth de
 * user_notifier.dart. Écrit le document `users/{uid}` en camelCase (schéma
 * partagé avec l'app Flutter iOS).
 *
 * Spécificités natives Android :
 *  - Google : on reçoit l'idToken depuis la couche UI (Credential Manager,
 *    module 3) et on construit ici le credential Firebase.
 *  - Téléphone : verifyPhoneNumber() a besoin de l'Activity courante.
 *
 * NB : l'init des notifications FCM (NotificationService) viendra au module 6.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) {
    val currentUser: FirebaseUser? get() = auth.currentUser

    /** Flux des changements d'état d'authentification (équiv. authStateChanges). */
    val authState: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    // ════════════════════════════════════════════════════════════
    // EMAIL + MOT DE PASSE
    // ════════════════════════════════════════════════════════════

    suspend fun signInWithEmailPassword(email: String, password: String): FirebaseUser {
        try {
            val user = auth.signInWithEmailAndPassword(email.trim(), password).await().user
                ?: throw AuthException("Échec de la connexion.")
            users().document(user.uid).set(
                mapOf(
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            ).await()
            return user
        } catch (e: FirebaseAuthException) {
            throw AuthException(mapError(e))
        }
    }

    suspend fun signUpWithEmailPassword(
        email: String,
        password: String,
        name: String,
        phone: String? = null,
    ): FirebaseUser {
        try {
            val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user
                ?: throw AuthException("Échec de la création du compte.")

            user.updateProfile(
                UserProfileChangeRequest.Builder().setDisplayName(name).build()
            ).await()

            users().document(user.uid).set(newUserDocument(name, email.trim(), phone)).await()
            return user
        } catch (e: FirebaseAuthException) {
            throw AuthException(mapError(e))
        }
    }

    // ════════════════════════════════════════════════════════════
    // GOOGLE (idToken fourni par la couche UI — Credential Manager)
    // ════════════════════════════════════════════════════════════

    suspend fun signInWithGoogleIdToken(idToken: String): FirebaseUser {
        try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val user = auth.signInWithCredential(credential).await().user
                ?: throw AuthException("Échec de la connexion Google.")
            createOrUpdateUserDocument(user)
            return user
        } catch (e: FirebaseAuthException) {
            throw AuthException(mapError(e))
        }
    }

    // ════════════════════════════════════════════════════════════
    // TÉLÉPHONE (OTP)
    // ════════════════════════════════════════════════════════════

    /** Lance la vérification du numéro. Les callbacks pilotent l'UI (module 3). */
    fun verifyPhoneNumber(
        activity: Activity,
        phoneNumber: String,
        callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks,
        resendToken: PhoneAuthProvider.ForceResendingToken? = null,
    ) {
        val builder = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
        resendToken?.let { builder.setForceResendingToken(it) }
        PhoneAuthProvider.verifyPhoneNumber(builder.build())
    }

    /** Vérifie le code OTP saisi (verificationId reçu via codeSent). */
    suspend fun verifyOtp(verificationId: String, smsCode: String): FirebaseUser {
        try {
            val credential = PhoneAuthProvider.getCredential(verificationId, smsCode)
            return signInWithPhoneCredential(credential)
        } catch (e: FirebaseAuthException) {
            throw AuthException(mapError(e))
        }
    }

    /** Connexion directe (cas verificationCompleted automatique). */
    suspend fun signInWithPhoneCredential(credential: PhoneAuthCredential): FirebaseUser {
        val user = auth.signInWithCredential(credential).await().user
            ?: throw AuthException("Échec de la vérification du téléphone.")
        createOrUpdateUserDocument(user)
        return user
    }

    // ════════════════════════════════════════════════════════════
    // DIVERS
    // ════════════════════════════════════════════════════════════

    suspend fun resetPassword(email: String) {
        try {
            auth.sendPasswordResetEmail(email.trim()).await()
        } catch (e: FirebaseAuthException) {
            throw AuthException(mapError(e))
        }
    }

    fun signOut() = auth.signOut()

    /** Charge le profil Firestore de l'utilisateur courant (null si absent). */
    suspend fun loadUserProfile(uid: String): VeloxUser? {
        val doc = users().document(uid).get().await()
        return if (doc.exists()) VeloxUser.fromFirestore(doc) else null
    }

    /**
     * Stream live du profil Firestore (`users/{uid}`). Toute écriture sur le document
     * (édition de profil, upload photo, stats…) est repoussée immédiatement → la session
     * globale se rafraîchit sans recharger l'auth. Émet `null` si le doc est absent/erreur.
     */
    fun streamUserProfile(uid: String): Flow<VeloxUser?> = callbackFlow {
        val registration = users().document(uid).addSnapshotListener { snap, error ->
            if (error != null) { trySend(null); return@addSnapshotListener }
            trySend(if (snap != null && snap.exists()) VeloxUser.fromFirestore(snap) else null)
        }
        awaitClose { registration.remove() }
    }

    // ─── Helpers privés ──────────────────────────────────────────

    private suspend fun createOrUpdateUserDocument(user: FirebaseUser) {
        val ref = users().document(user.uid)
        val snapshot = ref.get().await()
        if (!snapshot.exists()) {
            ref.set(
                newUserDocument(
                    name = user.displayName ?: "User",
                    email = user.email,
                    phone = user.phoneNumber,
                    photoUrl = user.photoUrl?.toString(),
                    isVerified = user.isEmailVerified,
                )
            ).await()
        } else {
            ref.update(
                mapOf(
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "isVerified" to user.isEmailVerified,
                )
            ).await()
        }
    }

    /** Document utilisateur initial (camelCase) — identique à AuthService.dart. */
    private fun newUserDocument(
        name: String,
        email: String?,
        phone: String?,
        photoUrl: String? = null,
        isVerified: Boolean = false,
    ): Map<String, Any?> = mapOf(
        "name" to name,
        "email" to email,
        "phone" to phone,
        "photoUrl" to photoUrl,
        "preferences" to mapOf(
            "language" to "fr",
            "currency" to "FDJ",
            "notificationsEnabled" to true,
            "darkMode" to false,
        ),
        "paymentMethods" to emptyList<Any>(),
        "stats" to mapOf(
            "totalTaxiRides" to 0,
            "totalFoodOrders" to 0,
            "totalSpentFdj" to 0.0,
            "memberSince" to FieldValue.serverTimestamp(),
        ),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
        "lastActiveAt" to FieldValue.serverTimestamp(),
        "isActive" to true,
        "isVerified" to isVerified,
    )

    private fun users() = firestore.collection("users")

    /** Traduction des codes d'erreur Firebase (miroir _handleAuthException). */
    private fun mapError(e: FirebaseAuthException): String = when (e.errorCode) {
        "ERROR_USER_NOT_FOUND" -> "Aucun compte trouvé avec cet email."
        "ERROR_WRONG_PASSWORD" -> "Mot de passe incorrect."
        "ERROR_INVALID_EMAIL" -> "Email invalide."
        "ERROR_USER_DISABLED" -> "Ce compte a été désactivé."
        "ERROR_EMAIL_ALREADY_IN_USE" -> "Cet email est déjà utilisé."
        "ERROR_WEAK_PASSWORD" -> "Le mot de passe doit contenir au moins 6 caractères."
        "ERROR_INVALID_VERIFICATION_CODE" -> "Code de vérification invalide."
        "ERROR_INVALID_VERIFICATION_ID" -> "Session expirée. Renvoyez le code."
        "ERROR_TOO_MANY_REQUESTS" -> "Trop de tentatives. Réessayez plus tard."
        "ERROR_OPERATION_NOT_ALLOWED" -> "Cette méthode de connexion n'est pas activée."
        "ERROR_NETWORK_REQUEST_FAILED" -> "Erreur réseau. Vérifiez votre connexion."
        "ERROR_REQUIRES_RECENT_LOGIN" -> "Session expirée. Reconnectez-vous."
        "ERROR_CREDENTIAL_ALREADY_IN_USE" -> "Ce compte est déjà associé à un autre utilisateur."
        "ERROR_INVALID_CREDENTIAL" -> "Identifiants invalides. Vérifiez vos informations."
        else -> e.message ?: e.errorCode
    }
}
