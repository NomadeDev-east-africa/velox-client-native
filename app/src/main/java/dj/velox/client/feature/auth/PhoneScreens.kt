package dj.velox.client.feature.auth

import android.app.Activity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dj.velox.client.R
import dj.velox.client.core.util.toE164

// ════════════════════════════════════════════════════════════════
// SAISIE DU NUMÉRO → envoi du SMS
// ════════════════════════════════════════════════════════════════

@Composable
fun PhoneLoginScreen(
    viewModel: AuthViewModel,
    onCodeSent: () -> Unit,
    onBack: () -> Unit,
) {
    val phone by viewModel.phone.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var number by rememberSaveable { mutableStateOf("") }

    // Dès que le code part, on passe à l'écran OTP
    LaunchedEffect(phone.codeSent) {
        if (phone.codeSent) onCodeSent()
    }

    AuthScaffold(title = stringResource(R.string.auth_phone_title), logo = true) {
        PhoneNumberField(value = number, onValueChange = { number = it })
        phone.error?.let { ErrorText(it) }

        Spacer(Modifier.height(8.dp))
        SubmitButton(
            text = stringResource(R.string.auth_send_code),
            loading = phone.isSending,
            enabled = number.isNotBlank(),
        ) {
            (context as? Activity)?.let { viewModel.startPhoneVerification(it, toE164(number)) }
        }

        TextButton(
            onClick = { viewModel.resetPhoneFlow(); onBack() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.auth_back)) }
    }
}

// ════════════════════════════════════════════════════════════════
// VÉRIFICATION DU CODE OTP
// ════════════════════════════════════════════════════════════════

@Composable
fun OtpScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit,
) {
    val phone by viewModel.phone.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var code by rememberSaveable { mutableStateOf("") }

    // Code auto-lu par Firebase (instant verification) → on remplit le champ.
    LaunchedEffect(phone.autoCode) {
        phone.autoCode?.let { if (it.isNotBlank()) code = it }
    }
    // Un renvoi invalide le code précédent : on vide le champ pour éviter que l'utilisateur
    // resoumette l'ancien code et retombe sur « code expiré ».
    LaunchedEffect(phone.codeResent) {
        if (phone.codeResent) code = ""
    }

    AuthScaffold(title = stringResource(R.string.auth_otp_title)) {
        Text(
            text = stringResource(R.string.auth_otp_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        AuthTextField(
            value = code,
            onValueChange = { if (it.length <= 6) code = it.filter(Char::isDigit) },
            label = stringResource(R.string.auth_otp_code),
            keyboardType = KeyboardType.Number,
        )
        phone.error?.let { ErrorText(it) }
        if (phone.codeResent && phone.error == null) InfoText(stringResource(R.string.auth_code_resent))

        Spacer(Modifier.height(8.dp))
        SubmitButton(
            text = stringResource(R.string.auth_verify),
            loading = phone.isVerifying || phone.isSending,
            enabled = code.length == 6,
        ) { viewModel.verifyOtp(code.trim()) }

        ResendCodeRow(secondsLeft = phone.resendIn, enabled = phone.canResend) {
            (context as? Activity)?.let { viewModel.resendCode(it) }
        }

        TextButton(
            onClick = { viewModel.resetPhoneFlow(); onBack() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.auth_back)) }
    }
}
