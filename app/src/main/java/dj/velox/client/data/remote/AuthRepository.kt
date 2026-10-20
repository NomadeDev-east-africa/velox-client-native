package dj.velox.client.data.remote

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import dagger.hilt.android.qualifiers.ApplicationContext
import dj.velox.client.R
import dj.velox.client.domain.model.VeloxUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Erreur d'authentification déjà traduite (message prêt pour l'UI). [code] conserve le
 * `errorCode` Firebase d'origine pour les cas où l'UI doit réagir spécifiquement — p.ex.
 * `ERROR_CREDENTIAL_ALREADY_IN_USE`, qui demande d'orienter l'utilisateur plutôt que
 * d'afficher un simple message d'échec.
 */
class AuthException(message: String, val code: String? = null) : Exception(message)

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
    private val functions: FirebaseFunctions,
    @param:ApplicationContext private val context: Context,
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
                ?: throw AuthException(string(R.string.err_signin_failed))
            users().document(user.uid).set(
                mapOf(
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            ).await()
            return user
        } catch (e: FirebaseAuthException) {
            throw AuthException(mapError(e), e.errorCode)
        }
    }

    /**
     * Crée un compte email/mot de passe. **Aucun numéro n'est écrit ici** : le téléphone
     * n'est jamais accepté sans vérification SMS (cf. [linkPhoneCredential]). Le nouveau
     * compte est aussitôt envoyé sur `CompletePhoneScreen` par le gating de `RootContent`.
     */
    suspend fun signUpWithEmailPassword(
        email: String,
        password: String,
        name: String,
    ): FirebaseUser {
        try {
            val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user
                ?: throw AuthException(string(R.string.err_signup_failed))

            user.updateProfile(
                UserProfileChangeRequest.Builder().setDisplayName(name).build()
            ).await()

            users().document(user.uid).set(newUserDocument(name, email.trim(), phone = null)).await()
            // Adresse saisie à la main : on prouve qu'elle appartient bien à l'utilisateur.
            // Non bloquant — le compte est créé même si l'envoi échoue (renvoi depuis le profil).
            runCatching { sendEmailVerification() }
            return user
        } catch (e: FirebaseAuthException) {
            throw AuthException(mapError(e), e.errorCode)
        }
    }

    // ════════════════════════════════════════════════════════════
    // GOOGLE (idToken fourni par la couche UI — Credential Manager)
    // ════════════════════════════════════════════════════════════

    suspend fun signInWithGoogleIdToken(idToken: String): FirebaseUser {
        try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val user = auth.signInWithCredential(credential).await().user
                ?: throw AuthException(string(R.string.err_google_failed))
            createOrUpdateUserDocument(user)
            return user
        } catch (e: FirebaseAuthException) {
            throw AuthException(mapError(e), e.errorCode)
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
        applyAuthLanguage()
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
            throw AuthException(mapError(e), e.errorCode)
        }
    }

    /** Connexion directe (cas verificationCompleted automatique). */
    suspend fun signInWithPhoneCredential(credential: PhoneAuthCredential): FirebaseUser {
        val user = try {
            auth.signInWithCredential(credential).await().user
        } catch (e: FirebaseAuthException) {
            throw AuthException(mapError(e), e.errorCode)
        } ?: throw AuthException(string(R.string.err_phone_failed))
        createOrUpdateUserDocument(user)
        return user
    }

    /**
     * Vrai si le compte connecté n'a que le téléphone comme provider (ni Google, ni
     * email/mot de passe). Ces comptes paient un SMS à chaque nouvelle session (réinstall,
     * nouvel appareil, cache vidé) puisque rien d'autre ne permet de les reconnecter — d'où
     * le prompt de liaison Google proposé juste après un OTP réussi.
     */
    val isPhoneOnlyAccount: Boolean
        get() = auth.currentUser?.providerData
            ?.filter { it.providerId != "firebase" }
            ?.takeIf { it.isNotEmpty() }
            ?.all { it.providerId == PhoneAuthProvider.PROVIDER_ID } == true

    /**
     * Lie un compte Google au compte téléphone courant. Contrairement à [linkPhoneCredential],
     * ceci ne touche jamais `users/{uid}.phone` : le seul but est d'ajouter un second moyen de
     * connexion **gratuit et sans SMS** à un compte qui n'avait que le téléphone.
     */
    suspend fun linkGoogleCredential(idToken: String): FirebaseUser {
        val user = auth.currentUser ?: throw AuthException(string(R.string.err_requires_recent_login))
        try {
            user.linkWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        } catch (e: FirebaseAuthException) {
            throw AuthException(mapError(e), e.errorCode)
        }
        runCatching { user.reload().await() }
        users().document(user.uid).set(
            mapOf(
                "photoUrl" to user.photoUrl?.toString(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
        // `email` est un champ protégé (règles Firestore) : recopié par le serveur.
        syncEmailToProfile()
        return user
    }

    // ════════════════════════════════════════════════════════════
    // LIEN D'UN NUMÉRO AU COMPTE COURANT (login Google → pas de numéro)
    // ════════════════════════════════════════════════════════════

    /**
     * Rattache un credential téléphone **vérifié** au compte connecté, puis écrit le numéro
     * dans `users/{uid}.phone`.
     *
     * C'est le **seul** chemin autorisé à renseigner un numéro : `users/{uid}.phone` doit
     * toujours refléter le provider `phone` de Firebase Auth. Sinon le compte affiche un
     * numéro qu'Auth ne connaît pas, et une connexion par OTP sur ce numéro crée un
     * **nouveau compte vide** au lieu de retrouver celui de l'utilisateur.
     *
     * On utilise `linkWithCredential` (et NON `signInWithCredential`) pour rester sur le
     * compte courant ; si un numéro est déjà lié (changement de numéro depuis le profil),
     * c'est `updatePhoneNumber` qui s'applique — `link` échouerait alors sur
     * `ERROR_PROVIDER_ALREADY_LINKED`.
     */
    suspend fun linkPhoneCredential(
        credential: PhoneAuthCredential,
        phoneNumber: String,
    ): FirebaseUser {
        val user = auth.currentUser ?: throw AuthException(string(R.string.err_requires_recent_login))
        try {
            if (user.phoneNumber.isNullOrBlank()) {
                user.linkWithCredential(credential).await()
            } else {
                user.updatePhoneNumber(credential).await()
            }
        } catch (e: FirebaseAuthException) {
            throw AuthException(mapError(e), e.errorCode)
        }
        // Rafraîchit `user.phoneNumber` : c'est lui qui pilote le gating « numéro obligatoire ».
        runCatching { user.reload().await() }
        users().document(user.uid).set(
            mapOf(
                "phone" to phoneNumber,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
        return user
    }

    /** Variante à partir d'un code OTP saisi manuellement (verificationId + code). */
    suspend fun linkPhoneWithOtp(
        verificationId: String,
        smsCode: String,
        phoneNumber: String,
    ): FirebaseUser {
        val credential = PhoneAuthProvider.getCredential(verificationId, smsCode)
        return linkPhoneCredential(credential, phoneNumber)
    }

    // ════════════════════════════════════════════════════════════
    // DIVERS
    // ════════════════════════════════════════════════════════════

    suspend fun resetPassword(email: String) {
        try {
            auth.sendPasswordResetEmail(email.trim()).await()
        } catch (e: FirebaseAuthException) {
            throw AuthException(mapError(e), e.errorCode)
        }
    }

    /**
     * Définit un mot de passe sur le compte **déjà connecté** — depuis le profil, notamment
     * après une récupération par SMS, où l'utilisateur vient de prouver son identité par OTP.
     *
     * Deux cas selon les providers du compte : si `password` est déjà rattaché on met à jour le
     * mot de passe existant ; sinon (compte créé par téléphone ou par Google) on **rattache** un
     * provider email/mot de passe, ce qui exige une adresse — celle du compte si elle existe,
     * sinon celle saisie par l'utilisateur.
     *
     * [currentPassword] n'est fourni que lorsque Firebase a exigé une reconnexion récente
     * (`ERROR_REQUIRES_RECENT_LOGIN`, session ancienne) : on se ré-authentifie avant. Juste après
     * un OTP, la session est récente et l'ancien mot de passe (oublié) n'est pas demandé.
     *
     * @return l'adresse à laquelle un lien de vérification vient d'être envoyé, sinon null.
     */
    suspend fun setPassword(
        newPassword: String,
        emailIfMissing: String? = null,
        currentPassword: String? = null,
    ): String? {
        val user = auth.currentUser ?: throw AuthException(string(R.string.err_requires_recent_login))
        try {
            if (hasPasswordProvider) {
                if (!currentPassword.isNullOrEmpty()) reauthenticateWithPassword(currentPassword)
                user.updatePassword(newPassword).await()
                return null
            }
            val email = user.email?.takeIf { it.isNotBlank() }
                ?: emailIfMissing?.trim()?.takeIf { it.isNotBlank() }
                ?: throw AuthException(string(R.string.err_email_not_found))
            user.linkWithCredential(EmailAuthProvider.getCredential(email, newPassword)).await()
        } catch (e: FirebaseAuthException) {
            throw AuthException(mapError(e), e.errorCode)
        }
        runCatching { user.reload().await() }
        // `email` est protégé côté règles Firestore : le serveur le recopie depuis Auth.
        syncEmailToProfile()
        // Adresse saisie à la main (compte téléphone) : non vérifiée → lien Firebase.
        // Une adresse Google est déjà vérifiée, rien à envoyer.
        if (user.isEmailVerified) return null
        return runCatching { sendEmailVerification() }.getOrNull()?.let { user.email }
    }

    /** Le compte possède-t-il un provider email/mot de passe (donc un mot de passe à réinitialiser) ? */
    val hasPasswordProvider: Boolean
        get() = auth.currentUser?.providerData?.any { it.providerId == "password" } == true

    // ════════════════════════════════════════════════════════════
    // VÉRIFICATION D'EMAIL (lien Firebase)
    // ════════════════════════════════════════════════════════════

    /** Le compte a une adresse email qui n'a pas encore été vérifiée. */
    val needsEmailVerification: Boolean
        get() = auth.currentUser?.let { !it.email.isNullOrBlank() && !it.isEmailVerified } == true

    /** Envoie (ou renvoie) le lien de vérification Firebase à l'adresse du compte. */
    suspend fun sendEmailVerification() {
        val user = auth.currentUser ?: throw AuthException(string(R.string.err_requires_recent_login))
        if (user.email.isNullOrBlank()) throw AuthException(string(R.string.err_email_not_found))
        applyAuthLanguage()
        try {
            user.sendEmailVerification().await()
        } catch (e: FirebaseException) {
            throw AuthException(mapError(e), (e as? FirebaseAuthException)?.errorCode)
        }
    }

    /**
     * Relit l'état Auth (l'utilisateur a pu cliquer sur le lien entre-temps) et le recopie dans
     * `users/{uid}` via le serveur. Renvoie vrai si l'adresse est désormais vérifiée.
     */
    suspend fun refreshEmailVerification(): Boolean {
        val user = auth.currentUser ?: return false
        runCatching { user.reload().await() }
        syncEmailToProfile()
        return auth.currentUser?.isEmailVerified == true
    }

    /**
     * Cloud Function `syncEmailVerification` : recopie `email` + `isVerified` de Firebase Auth
     * vers `users/{uid}` (champs protégés, non modifiables par le client). Non bloquant.
     */
    private suspend fun syncEmailToProfile() {
        runCatching { functions.getHttpsCallable("syncEmailVerification").call().await() }
    }

    fun signOut() = auth.signOut()

    // ════════════════════════════════════════════════════════════
    // SUPPRESSION DE COMPTE (exige une reconnexion récente)
    // ════════════════════════════════════════════════════════════

    /** Fournisseur principal du compte : "google.com" | "password" | "phone" | null. */
    val primaryProvider: String?
        get() = auth.currentUser?.providerData
            ?.firstOrNull { it.providerId != "firebase" }?.providerId

    /** Ré-authentifie avec un idToken Google frais. */
    suspend fun reauthenticateWithGoogle(idToken: String) {
        val user = auth.currentUser ?: throw AuthException(string(R.string.err_requires_recent_login))
        try {
            user.reauthenticate(GoogleAuthProvider.getCredential(idToken, null)).await()
        } catch (e: FirebaseAuthException) {
            throw AuthException(mapError(e), e.errorCode)
        }
    }

    /** Ré-authentifie avec l'email du compte + mot de passe saisi. */
    suspend fun reauthenticateWithPassword(password: String) {
        val user = auth.currentUser ?: throw AuthException(string(R.string.err_requires_recent_login))
        val email = user.email ?: throw AuthException(string(R.string.err_email_not_found))
        try {
            user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
        } catch (e: FirebaseAuthException) {
            throw AuthException(mapError(e), e.errorCode)
        }
    }

    /**
     * Supprime le compte : d'abord le document profil (tant que l'utilisateur est authentifié,
     * requis par les règles), puis le compte Firebase Auth. Doit être précédé d'une
     * ré-authentification récente, sinon Firebase lève `ERROR_REQUIRES_RECENT_LOGIN`.
     */
    suspend fun deleteAccount() {
        val user = auth.currentUser ?: throw AuthException(string(R.string.err_requires_recent_login))
        runCatching { users().document(user.uid).delete().await() }
        try {
            user.delete().await()
        } catch (e: FirebaseAuthException) {
            throw AuthException(mapError(e), e.errorCode)
        }
    }

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
            // NB : `isVerified` est un champ protégé côté règles Firestore
            // (piloté serveur) — ne pas l'écrire depuis le client, sinon
            // l'update est refusé (permission-denied) et casse la connexion.
            ref.update(
                mapOf(
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
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

    // ─── Localisation des messages ───────────────────────────────

    /**
     * Résout une chaîne dans la langue **choisie dans l'app** (LanguageSelectionScreen), et non
     * dans celle du système. Les per-app locales d'AppCompat ne se propagent pas au contexte
     * applicatif sur toutes les versions d'Android : on reconstruit donc la configuration à la
     * volée, sinon les messages d'erreur repartiraient en français pour un utilisateur en
     * arabe ou en somali.
     */
    internal fun string(@StringRes id: Int, vararg args: Any): String = localizedResources()
        .let { if (args.isEmpty()) it.getString(id) else it.getString(id, *args) }

    /**
     * Langue des SMS et emails Firebase (+ en-tête X-Firebase-Locale) : celle choisie dans
     * l'app, sinon celle de l'appareil. `useAppLanguage()` seul lirait la langue système, pas
     * la langue applicative (cf. [localizedResources]). Afar : pas de modèle Firebase → français.
     */
    private fun applyAuthLanguage() {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val appLanguage = if (appLocales.isEmpty) null else appLocales[0]?.language
        if (appLanguage == null) auth.useAppLanguage()
        else auth.setLanguageCode(if (appLanguage == "aa") "fr" else appLanguage)
    }

    private fun localizedResources(): Resources {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return context.resources
        val config = Configuration(context.resources.configuration)
        config.setLocale(locales[0])
        return context.createConfigurationContext(config).resources
    }

    /**
     * Traduction des codes d'erreur Firebase (miroir _handleAuthException).
     *
     * Le `else` ne doit **jamais** renvoyer `e.message` : ce message est en anglais brut et
     * l'utilisateur se retrouve avec « The sms code has expired… » au milieu d'une interface
     * en français.
     */
    fun mapError(e: FirebaseException): String {
        val code = (e as? FirebaseAuthException)?.errorCode
        return when {
            code != null -> string(errorRes(code))
            // verifyPhoneNumber remonte ce type hors FirebaseAuthException (quota SMS projet).
            e is FirebaseTooManyRequestsException -> string(R.string.err_quota_exceeded)
            else -> string(R.string.err_generic)
        }
    }

    @StringRes
    private fun errorRes(code: String): Int = when (code) {
        "ERROR_USER_NOT_FOUND" -> R.string.err_user_not_found
        "ERROR_WRONG_PASSWORD" -> R.string.err_wrong_password
        "ERROR_INVALID_EMAIL" -> R.string.err_invalid_email
        "ERROR_USER_DISABLED" -> R.string.err_user_disabled
        "ERROR_EMAIL_ALREADY_IN_USE" -> R.string.err_email_in_use
        "ERROR_WEAK_PASSWORD" -> R.string.err_weak_password
        "ERROR_INVALID_VERIFICATION_CODE" -> R.string.err_invalid_code
        "ERROR_INVALID_VERIFICATION_ID" -> R.string.err_invalid_verification_id
        // Code SMS périmé — le cas le plus fréquent en conditions réseau dégradées.
        "ERROR_SESSION_EXPIRED" -> R.string.err_session_expired
        "ERROR_MISSING_VERIFICATION_CODE" -> R.string.err_missing_code
        "ERROR_INVALID_PHONE_NUMBER" -> R.string.err_invalid_phone
        "ERROR_MISSING_PHONE_NUMBER" -> R.string.err_missing_phone
        "ERROR_QUOTA_EXCEEDED" -> R.string.err_quota_exceeded
        "ERROR_TOO_MANY_REQUESTS" -> R.string.err_too_many_requests
        "ERROR_OPERATION_NOT_ALLOWED" -> R.string.err_operation_not_allowed
        "ERROR_NETWORK_REQUEST_FAILED" -> R.string.err_network
        "ERROR_REQUIRES_RECENT_LOGIN" -> R.string.err_requires_recent_login
        "ERROR_CREDENTIAL_ALREADY_IN_USE" -> R.string.err_credential_in_use
        "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> R.string.err_credential_in_use
        "ERROR_PROVIDER_ALREADY_LINKED" -> R.string.err_provider_already_linked
        "ERROR_INVALID_CREDENTIAL" -> R.string.err_invalid_credential
        "ERROR_APP_NOT_AUTHORIZED" -> R.string.err_app_not_authorized
        else -> R.string.err_generic
    }
}
