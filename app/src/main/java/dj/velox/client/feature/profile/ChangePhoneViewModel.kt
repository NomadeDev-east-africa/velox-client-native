package dj.velox.client.feature.profile

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dj.velox.client.data.remote.AuthException
import dj.velox.client.data.remote.AuthRepository
import dj.velox.client.feature.auth.PhoneAuthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Changement du numéro de téléphone depuis le profil — **toujours** via OTP.
 *
 * Le numéro n'est jamais saisi librement : il est vérifié par SMS puis rattaché au compte
 * Firebase Auth (`AuthRepository.linkPhoneCredential`), qui écrit ensuite `users/{uid}.phone`.
 * C'est ce qui garantit qu'une connexion ultérieure par OTP sur ce numéro retombe bien sur
 * ce compte, et non sur un nouveau compte vide.
 */
@HiltViewModel
class ChangePhoneViewModel @Inject constructor(
    private val repo: AuthRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(PhoneAuthState())
    val ui: StateFlow<PhoneAuthState> = _ui.asStateFlow()

    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var pendingNumber: String = ""

    fun reset() {
        _ui.value = PhoneAuthState()
        resendToken = null
        pendingNumber = ""
    }

    /** Étape 1 : envoi du SMS. L'instant verification enchaîne directement sur l'étape 2. */
    fun sendCode(activity: Activity, phoneNumber: String) {
        pendingNumber = phoneNumber.trim()
        _ui.value = PhoneAuthState(isSending = true)
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                _ui.value = _ui.value.copy(
                    codeSent = true,
                    autoCode = credential.smsCode,
                    isVerifying = true,
                    error = null,
                )
                attach { repo.linkPhoneCredential(credential, pendingNumber) }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                _ui.value = PhoneAuthState(error = e.message ?: "Échec de l'envoi du code.")
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken,
            ) {
                resendToken = token
                _ui.value = PhoneAuthState(codeSent = true, verificationId = verificationId)
            }
        }
        repo.verifyPhoneNumber(activity, pendingNumber, callbacks, resendToken)
    }

    /** Étape 2 : code saisi manuellement → rattachement du numéro au compte. */
    fun verify(code: String) {
        val verificationId = _ui.value.verificationId ?: return
        _ui.value = _ui.value.copy(isVerifying = true, error = null)
        attach { repo.linkPhoneWithOtp(verificationId, code.trim(), pendingNumber) }
    }

    private inline fun attach(crossinline action: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { _ui.value = _ui.value.copy(isVerifying = false, linked = true) }
                .onFailure {
                    _ui.value = _ui.value.copy(
                        isVerifying = false,
                        error = (it as? AuthException)?.message
                            ?: it.message
                            ?: "Échec de la modification du numéro.",
                        errorCode = (it as? AuthException)?.code,
                    )
                }
        }
    }
}
