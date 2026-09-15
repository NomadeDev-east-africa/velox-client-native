package dj.velox.client.feature.auth

import android.app.Activity
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dj.velox.client.R
import dj.velox.client.core.notifications.NotificationTokenManager
import dj.velox.client.data.local.VeloxLocalStore
import dj.velox.client.data.remote.AuthException
import dj.velox.client.data.remote.AuthRepository
import dj.velox.client.domain.model.VeloxUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** État de session global (équivalent UserState côté Flutter). */
data class SessionState(
    val isLoading: Boolean = true,
    val firebaseUser: FirebaseUser? = null,
    val profile: VeloxUser? = null,
) {
    val isAuthenticated: Boolean get() = firebaseUser != null
    val displayName: String get() = profile?.displayName ?: firebaseUser?.displayName ?: "Utilisateur"

    /**
     * Numéro **vérifié par SMS** (provider `phone` de Firebase Auth) — et non le champ
     * `users/{uid}.phone`, qui n'en est que le reflet d'affichage. C'est ce numéro-là qui
     * identifie le compte lors d'une connexion par OTP.
     */
    val verifiedPhone: String? get() = firebaseUser?.phoneNumber?.takeIf { it.isNotBlank() }
}

/** État d'un formulaire d'auth (soumission / erreur traduite). */
data class AuthFormState(
    val isSubmitting: Boolean = false,
    val error: String? = null,
)

/** État du flux OTP téléphone (connexion **et** liaison de numéro). */
data class PhoneAuthState(
    val isSending: Boolean = false,
    val codeSent: Boolean = false,
    val verificationId: String? = null,
    val isVerifying: Boolean = false,
    val error: String? = null,
    /** `errorCode` Firebase brut — permet à l'UI de traiter à part le numéro déjà pris. */
    val errorCode: String? = null,
    /** Code auto-récupéré du SMS (instant verification) → remplit le champ. */
    val autoCode: String? = null,
    /** Liaison du numéro au compte réussie (flux « numéro obligatoire »). */
    val linked: Boolean = false,
    /** Secondes restantes avant de pouvoir redemander un SMS (0 = renvoi possible). */
    val resendIn: Int = 0,
    /** Un nouveau code vient d'être envoyé → confirmation à afficher. */
    val codeResent: Boolean = false,
) {
    val canResend: Boolean get() = codeSent && resendIn == 0 && !isSending && !isVerifying
}

/** Délai imposé entre deux envois de SMS — aligné sur le timeout Firebase (60 s). */
private const val RESEND_DELAY_SECONDS = 60

/** État du prompt « lier Google » proposé après un OTP réussi sur un compte phone-only. */
data class GoogleLinkState(
    val isLinking: Boolean = false,
    val error: String? = null,
    val linked: Boolean = false,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repo: AuthRepository,
    private val tokenManager: NotificationTokenManager,
    private val store: VeloxLocalStore,
) : ViewModel() {

    /**
     * Session dérivée du flux d'auth Firebase. À chaque (dé)connexion, on bascule
     * (`flatMapLatest`) sur le **stream live** du profil Firestore : toute édition de
     * `users/{uid}` (nom, photo, stats) se propage immédiatement à tous les écrans qui
     * observent la session — plus besoin de recharger l'auth. `stateIn` la garde chaude.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val session: StateFlow<SessionState> = repo.authState
        .flatMapLatest { fbUser ->
            if (fbUser == null) {
                flowOf(SessionState(isLoading = false))
            } else {
                repo.streamUserProfile(fbUser.uid).map { profile ->
                    SessionState(isLoading = false, firebaseUser = fbUser, profile = profile)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SessionState(isLoading = true),
        )

    private val _form = MutableStateFlow(AuthFormState())
    val form: StateFlow<AuthFormState> = _form.asStateFlow()

    private val _phone = MutableStateFlow(PhoneAuthState())
    val phone: StateFlow<PhoneAuthState> = _phone.asStateFlow()

    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    /** Numéro du flux de connexion courant — conservé pour pouvoir renvoyer le SMS. */
    private var pendingNumber: String = ""
    private var countdownJob: Job? = null
    private var linkCountdownJob: Job? = null

    // ─── Liaison d'un numéro au compte courant (login Google → pas de numéro) ───
    private val _phoneLink = MutableStateFlow(PhoneAuthState())
    val phoneLink: StateFlow<PhoneAuthState> = _phoneLink.asStateFlow()
    private var linkResendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var pendingLinkNumber: String = ""

    // ─── Prompt « lier Google » (après OTP réussi sur un compte phone-only) ───
    private val _googleLinkPromptVisible = MutableStateFlow(false)
    val googleLinkPromptVisible: StateFlow<Boolean> = _googleLinkPromptVisible.asStateFlow()

    private val _googleLink = MutableStateFlow(GoogleLinkState())
    val googleLink: StateFlow<GoogleLinkState> = _googleLink.asStateFlow()

    /** À appeler juste après un sign-in réussi par téléphone : n'affiche le prompt que si le
     *  compte n'a encore aucun autre moyen de connexion (sinon inutile, il en a déjà un). */
    private fun maybeOfferGoogleLink() {
        if (repo.isPhoneOnlyAccount) _googleLinkPromptVisible.value = true
    }

    fun linkGoogleAccount(idToken: String) {
        viewModelScope.launch {
            _googleLink.value = GoogleLinkState(isLinking = true)
            runCatching { repo.linkGoogleCredential(idToken) }
                .onSuccess {
                    _googleLink.value = GoogleLinkState(linked = true)
                    _googleLinkPromptVisible.value = false
                }
                .onFailure {
                    _googleLink.value = GoogleLinkState(
                        error = errorMessage(it, R.string.err_link_google_failed),
                    )
                }
        }
    }

    fun dismissGoogleLinkPrompt() {
        _googleLinkPromptVisible.value = false
        _googleLink.value = GoogleLinkState()
    }

    fun clearError() {
        _form.value = _form.value.copy(error = null)
        _phone.value = _phone.value.copy(error = null, errorCode = null)
        _phoneLink.value = _phoneLink.value.copy(error = null, errorCode = null)
    }

    fun resetPhoneFlow() {
        countdownJob?.cancel()
        _phone.value = PhoneAuthState()
        resendToken = null
        pendingNumber = ""
    }

    fun resetPhoneLink() {
        linkCountdownJob?.cancel()
        _phoneLink.value = PhoneAuthState()
        linkResendToken = null
        pendingLinkNumber = ""
    }

    /**
     * Décompte avant réactivation du bouton « Renvoyer le code ». Sans ce garde-fou,
     * l'utilisateur peut enchaîner les demandes et Firebase le bloque sur
     * `ERROR_TOO_MANY_REQUESTS` — c'est-à-dire échanger une attente de 60 s contre un
     * blocage d'une heure.
     */
    private fun startCountdown(state: MutableStateFlow<PhoneAuthState>): Job = viewModelScope.launch {
        for (remaining in RESEND_DELAY_SECONDS downTo 1) {
            state.value = state.value.copy(resendIn = remaining)
            delay(1_000)
        }
        state.value = state.value.copy(resendIn = 0)
    }

    // ─── Flux « numéro obligatoire » ─────────────────────────────

    /**
     * Étape 1 : envoie le SMS pour lier [phoneNumber] au compte connecté. En cas
     * d'auto-récupération du code (instant verification), on lie directement et on
     * expose `autoCode` pour remplir le champ à l'écran.
     */
    fun startPhoneLink(activity: Activity, phoneNumber: String, isResend: Boolean = false) {
        pendingLinkNumber = phoneNumber.trim()
        linkCountdownJob?.cancel()
        // Un renvoi conserve l'étape « saisie du code » : repartir d'un PhoneAuthState() vierge
        // renverrait l'utilisateur à l'écran du numéro et lui ferait tout ressaisir.
        _phoneLink.value = if (isResend) {
            _phoneLink.value.copy(isSending = true, error = null, errorCode = null, codeResent = false)
        } else {
            PhoneAuthState(isSending = true)
        }
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                _phoneLink.value = _phoneLink.value.copy(
                    isSending = false,
                    codeSent = true,
                    autoCode = credential.smsCode,
                    isVerifying = true,
                    error = null,
                )
                linkWith { repo.linkPhoneCredential(credential, pendingLinkNumber) }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                linkCountdownJob?.cancel()
                // On garde `codeSent` : si l'échec survient sur un renvoi, l'utilisateur doit
                // rester sur l'écran du code avec son bouton « Renvoyer », pas être éjecté.
                _phoneLink.value = _phoneLink.value.copy(
                    isSending = false,
                    isVerifying = false,
                    error = repo.mapError(e),
                    errorCode = (e as? FirebaseAuthException)?.errorCode,
                    resendIn = 0,
                )
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken,
            ) {
                linkResendToken = token
                _phoneLink.value = _phoneLink.value.copy(
                    isSending = false,
                    codeSent = true,
                    verificationId = verificationId,
                    error = null,
                    errorCode = null,
                    codeResent = isResend,
                )
                linkCountdownJob = startCountdown(_phoneLink)
            }
        }
        repo.verifyPhoneNumber(activity, pendingLinkNumber, callbacks, linkResendToken.takeIf { isResend })
    }

    /** Renvoie un SMS pour le même numéro (code expiré / jamais reçu). */
    fun resendPhoneLinkCode(activity: Activity) {
        if (!_phoneLink.value.canResend || pendingLinkNumber.isBlank()) return
        startPhoneLink(activity, pendingLinkNumber, isResend = true)
    }

    /** Étape 2 : vérifie le code saisi manuellement puis lie le numéro au compte. */
    fun verifyPhoneLink(code: String) {
        val verificationId = _phoneLink.value.verificationId ?: return
        _phoneLink.value = _phoneLink.value.copy(isVerifying = true, error = null, codeResent = false)
        linkWith { repo.linkPhoneWithOtp(verificationId, code.trim(), pendingLinkNumber) }
    }

    private inline fun linkWith(crossinline action: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { _phoneLink.value = _phoneLink.value.copy(isVerifying = false, linked = true) }
                .onFailure {
                    _phoneLink.value = _phoneLink.value.copy(
                        isVerifying = false,
                        error = errorMessage(it, R.string.err_link_phone_failed),
                        errorCode = (it as? AuthException)?.code,
                    )
                }
        }
    }

    /**
     * Message affichable pour une exception. Les `AuthException` sortent déjà traduites du
     * repository ; pour tout le reste on retombe sur une chaîne localisée — jamais sur
     * `throwable.message`, qui est le texte anglais brut du SDK Firebase.
     */
    private fun errorMessage(e: Throwable?, @StringRes fallback: Int): String = when (e) {
        is AuthException -> e.message ?: repo.string(fallback)
        is FirebaseException -> repo.mapError(e)
        else -> repo.string(fallback)
    }

    // ─── Actions ─────────────────────────────────────────────────

    fun signIn(email: String, password: String) = submit {
        repo.signInWithEmailPassword(email, password)
    }

    fun signUp(name: String, email: String, password: String) = submit {
        repo.signUpWithEmailPassword(email, password, name)
    }

    fun signInWithGoogleIdToken(idToken: String) = submit {
        repo.signInWithGoogleIdToken(idToken)
    }

    /** Enregistre le token FCM pour l'utilisateur connecté (appelé à l'authentification). */
    fun registerPushToken() {
        val uid = repo.currentUser?.uid ?: return
        viewModelScope.launch { runCatching { tokenManager.registerCurrentToken(uid) } }
    }

    fun signOut() {
        val uid = repo.currentUser?.uid
        viewModelScope.launch {
            // 1) Retirer le token FCM tant que l'auth est encore valide (écrit sous users/{uid}).
            uid?.let { runCatching { tokenManager.clearToken(it) } }
            // 2) Vider les données de session persistées (panier, commande/course active,
            //    notif en attente) AVANT de couper l'auth. Sinon elles survivent dans
            //    DataStore et le prochain compte connecté sur ce téléphone les recharge
            //    (fuite inter-comptes qui survit même au redémarrage de l'app). Les prefs
            //    UI — thème, langue — sont conservées par clearAllSession().
            runCatching { store.clearAllSession() }
            // 3) Couper l'auth : le RootNavGraph bascule alors sur les écrans d'auth et
            //    détruit MainNavGraph (donc l'état mémoire du panier).
            repo.signOut()
        }
    }

    // ─── Suppression de compte (reconnexion récente exigée) ─────────

    /** Fournisseur principal du compte : "google.com" | "password" | "phone" | null. */
    val accountProvider: String? get() = repo.primaryProvider

    /** Reconnexion Google (idToken frais) puis suppression. onResult(null)=OK sinon message. */
    fun deleteAccountWithGoogle(idToken: String, onResult: (String?) -> Unit) = deleteFlow(onResult) {
        repo.reauthenticateWithGoogle(idToken)
        repo.deleteAccount()
    }

    /** Reconnexion email+mot de passe puis suppression. */
    fun deleteAccountWithPassword(password: String, onResult: (String?) -> Unit) = deleteFlow(onResult) {
        repo.reauthenticateWithPassword(password)
        repo.deleteAccount()
    }

    /** Suppression directe (téléphone : suppose une connexion récente). */
    fun deleteAccountDirect(onResult: (String?) -> Unit) = deleteFlow(onResult) {
        repo.deleteAccount()
    }

    private inline fun deleteFlow(crossinline onResult: (String?) -> Unit, crossinline action: suspend () -> Unit) {
        viewModelScope.launch {
            val err = runCatching { action() }.exceptionOrNull()
            onResult(err?.let { errorMessage(it, R.string.err_generic) })
        }
    }

    /** Envoie l'email de réinitialisation ; onDone(null) si OK, sinon message. */
    fun resetPassword(email: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { repo.resetPassword(email) }
            onDone(result.exceptionOrNull()?.let { errorMessage(it, R.string.err_generic) })
        }
    }

    /** Le compte connecté a-t-il un mot de passe (donc quelque chose à réinitialiser) ? */
    val hasPassword: Boolean get() = repo.hasPasswordProvider

    /**
     * Définit un mot de passe sur le compte courant, après récupération par SMS. L'OTP qui
     * vient d'être validé fait office de reconnexion récente, donc `updatePassword` passe sans
     * redemander l'ancien mot de passe — que l'utilisateur a justement oublié.
     */
    fun setNewPassword(password: String, emailIfMissing: String? = null, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { repo.setPassword(password, emailIfMissing) }
            onDone(result.exceptionOrNull()?.let { errorMessage(it, R.string.err_generic) })
        }
    }

    // ─── OTP téléphone ───────────────────────────────────────────

    /** Lance l'envoi du SMS. L'Activity courante est requise par Firebase. */
    fun startPhoneVerification(activity: Activity, phoneNumber: String, isResend: Boolean = false) {
        pendingNumber = phoneNumber.trim()
        countdownJob?.cancel()
        _phone.value = if (isResend) {
            _phone.value.copy(isSending = true, error = null, errorCode = null, codeResent = false)
        } else {
            PhoneAuthState(isSending = true)
        }
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // Auto-récupération du code → connexion directe (session bascule seule)
                viewModelScope.launch {
                    _phone.value = _phone.value.copy(isSending = false, isVerifying = true)
                    runCatching { repo.signInWithPhoneCredential(credential) }
                        .onSuccess { maybeOfferGoogleLink() }
                        .onFailure {
                            _phone.value = _phone.value.copy(
                                isVerifying = false,
                                error = errorMessage(it, R.string.err_phone_failed),
                            )
                        }
                }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                countdownJob?.cancel()
                _phone.value = _phone.value.copy(
                    isSending = false,
                    isVerifying = false,
                    error = repo.mapError(e),
                    errorCode = (e as? FirebaseAuthException)?.errorCode,
                    resendIn = 0,
                )
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken,
            ) {
                resendToken = token
                _phone.value = _phone.value.copy(
                    isSending = false,
                    codeSent = true,
                    verificationId = verificationId,
                    error = null,
                    errorCode = null,
                    codeResent = isResend,
                )
                countdownJob = startCountdown(_phone)
            }
        }
        repo.verifyPhoneNumber(activity, pendingNumber, callbacks, resendToken.takeIf { isResend })
    }

    /** Renvoie un SMS pour le même numéro (code expiré / jamais reçu). */
    fun resendCode(activity: Activity) {
        if (!_phone.value.canResend || pendingNumber.isBlank()) return
        startPhoneVerification(activity, pendingNumber, isResend = true)
    }

    /** Vérifie le code OTP saisi. */
    fun verifyOtp(code: String) {
        val verificationId = _phone.value.verificationId ?: return
        viewModelScope.launch {
            _phone.value = _phone.value.copy(isVerifying = true, error = null, codeResent = false)
            runCatching { repo.verifyOtp(verificationId, code) }
                .onSuccess { maybeOfferGoogleLink() }
                .onFailure {
                    _phone.value = _phone.value.copy(
                        isVerifying = false,
                        error = errorMessage(it, R.string.err_invalid_code),
                    )
                }
            // Succès : la session bascule via le flux authState ; pas d'autre action.
        }
    }

    /** Exécute une action d'auth en gérant submitting/erreur. La navigation se
     *  fait automatiquement via le flux `session` au changement d'état d'auth. */
    private fun submit(action: suspend () -> Unit) {
        viewModelScope.launch {
            _form.value = AuthFormState(isSubmitting = true, error = null)
            val result = runCatching { action() }
            _form.value = if (result.isSuccess) {
                AuthFormState(isSubmitting = false)
            } else {
                val e = result.exceptionOrNull()
                AuthFormState(
                    isSubmitting = false,
                    error = errorMessage(e, R.string.err_generic),
                )
            }
        }
    }
}
