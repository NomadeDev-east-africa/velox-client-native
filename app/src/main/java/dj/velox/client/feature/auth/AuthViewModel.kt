package dj.velox.client.feature.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dj.velox.client.core.notifications.NotificationTokenManager
import dj.velox.client.data.remote.AuthException
import dj.velox.client.data.remote.AuthRepository
import dj.velox.client.domain.model.VeloxUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
}

/** État d'un formulaire d'auth (soumission / erreur traduite). */
data class AuthFormState(
    val isSubmitting: Boolean = false,
    val error: String? = null,
)

/** État du flux OTP téléphone. */
data class PhoneAuthState(
    val isSending: Boolean = false,
    val codeSent: Boolean = false,
    val verificationId: String? = null,
    val isVerifying: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repo: AuthRepository,
    private val tokenManager: NotificationTokenManager,
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

    fun clearError() {
        _form.value = _form.value.copy(error = null)
        _phone.value = _phone.value.copy(error = null)
    }

    fun resetPhoneFlow() {
        _phone.value = PhoneAuthState()
        resendToken = null
    }

    // ─── Actions ─────────────────────────────────────────────────

    fun signIn(email: String, password: String) = submit {
        repo.signInWithEmailPassword(email, password)
    }

    fun signUp(name: String, email: String, password: String, phone: String?) = submit {
        repo.signUpWithEmailPassword(email, password, name, phone)
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
            uid?.let { runCatching { tokenManager.clearToken(it) } }
            repo.signOut()
        }
    }

    /** Envoie l'email de réinitialisation ; onDone(null) si OK, sinon message. */
    fun resetPassword(email: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { repo.resetPassword(email) }
            onDone(result.exceptionOrNull()?.let { (it as? AuthException)?.message ?: it.message })
        }
    }

    // ─── OTP téléphone ───────────────────────────────────────────

    /** Lance l'envoi du SMS. L'Activity courante est requise par Firebase. */
    fun startPhoneVerification(activity: Activity, phoneNumber: String) {
        _phone.value = PhoneAuthState(isSending = true)
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // Auto-récupération du code → connexion directe (session bascule seule)
                viewModelScope.launch {
                    runCatching { repo.signInWithPhoneCredential(credential) }
                        .onFailure { _phone.value = _phone.value.copy(error = it.message) }
                }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                _phone.value = PhoneAuthState(error = e.message ?: "Échec de l'envoi du code.")
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken,
            ) {
                resendToken = token
                _phone.value = PhoneAuthState(codeSent = true, verificationId = verificationId)
            }
        }
        repo.verifyPhoneNumber(activity, phoneNumber, callbacks, resendToken)
    }

    /** Vérifie le code OTP saisi. */
    fun verifyOtp(code: String) {
        val verificationId = _phone.value.verificationId ?: return
        viewModelScope.launch {
            _phone.value = _phone.value.copy(isVerifying = true, error = null)
            runCatching { repo.verifyOtp(verificationId, code) }
                .onFailure {
                    _phone.value = _phone.value.copy(
                        isVerifying = false,
                        error = (it as? AuthException)?.message ?: it.message ?: "Code invalide.",
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
                    error = (e as? AuthException)?.message ?: e?.message ?: "Une erreur est survenue.",
                )
            }
        }
    }
}
