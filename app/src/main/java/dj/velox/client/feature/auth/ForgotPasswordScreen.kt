package dj.velox.client.feature.auth

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dj.velox.client.R
import dj.velox.client.core.util.toE164
import dj.velox.client.ui.theme.Inter
import dj.velox.client.ui.theme.VeloxTheme

/**
 * Récupération de compte — deux chemins, parce qu'un compte Velox n'a pas forcément de mot de
 * passe à réinitialiser :
 *
 *  - **Par email** : lien Firebase classique. Ne concerne que les comptes créés avec
 *    email + mot de passe.
 *  - **Par SMS** : le chemin universel. Tout compte a un numéro **vérifié et lié** dans Firebase
 *    Auth (imposé par [CompletePhoneScreen]), donc un OTP sur ce numéro reconnecte au compte
 *    d'origine quelle que soit la méthode d'inscription — y compris Google, qui n'a aucun mot de
 *    passe, et les comptes créés par téléphone, qui n'ont parfois aucun email.
 *
 * Après une récupération par SMS, l'utilisateur est déjà connecté : `RootContent` bascule seul
 * sur l'app. On lui propose alors de définir un mot de passe, ce que l'OTP tout juste validé
 * autorise sans redemander l'ancien.
 */
@Composable
fun ForgotPasswordScreen(viewModel: AuthViewModel, onBack: () -> Unit) {
    var bySms by rememberSaveable { mutableStateOf(false) }

    // Le flux OTP est partagé avec l'écran de connexion par téléphone : on repart propre en
    // entrant et en sortant, sinon un code en attente d'un précédent essai s'affiche ici.
    DisposableEffect(Unit) { onDispose { viewModel.resetPhoneFlow() } }

    AuthScaffold(
        title = stringResource(R.string.auth_recover_title),
        subtitle = stringResource(R.string.auth_recover_subtitle),
        logo = true,
    ) {
        MethodSwitch(
            bySms = bySms,
            onSelect = { sms ->
                if (sms != bySms) {
                    viewModel.resetPhoneFlow()
                    viewModel.clearError()
                    bySms = sms
                }
            },
        )
        Spacer(Modifier.height(20.dp))

        if (bySms) SmsRecovery(viewModel) else EmailRecovery(viewModel)

        TextButton(
            onClick = { viewModel.resetPhoneFlow(); onBack() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.auth_back)) }
    }
}

// ════════════════════════════════════════════════════════════════
// RÉCUPÉRATION PAR EMAIL
// ════════════════════════════════════════════════════════════════

@Composable
private fun EmailRecovery(viewModel: AuthViewModel) {
    val c = VeloxTheme.colors
    var email by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    val sentMsg = stringResource(R.string.auth_reset_sent)

    Text(
        text = stringResource(R.string.auth_recover_email_hint),
        color = c.onSurfaceVariant,
        fontFamily = Inter,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )
    Spacer(Modifier.height(14.dp))
    AuthTextField(
        value = email,
        onValueChange = { email = it },
        label = stringResource(R.string.auth_email),
        keyboardType = KeyboardType.Email,
    )

    message?.let { if (isError) ErrorText(it) else InfoText(it) }

    Spacer(Modifier.height(12.dp))
    SubmitButton(
        text = stringResource(R.string.auth_send_reset),
        loading = sending,
        enabled = email.isNotBlank(),
    ) {
        sending = true
        message = null
        viewModel.resetPassword(email.trim()) { error ->
            sending = false
            isError = error != null
            message = error ?: sentMsg
        }
    }

    // Un compte Google n'a pas de mot de passe : le lien de réinitialisation ne lui sert à rien.
    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.auth_recover_google_note),
        color = c.onSurfaceVariant.copy(alpha = 0.8f),
        fontFamily = Inter,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    )
}

// ════════════════════════════════════════════════════════════════
// RÉCUPÉRATION PAR SMS (OTP sur le numéro lié au compte)
// ════════════════════════════════════════════════════════════════

@Composable
private fun SmsRecovery(viewModel: AuthViewModel) {
    val c = VeloxTheme.colors
    val phone by viewModel.phone.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Text(
        text = stringResource(R.string.auth_recover_sms_hint),
        color = c.onSurfaceVariant,
        fontFamily = Inter,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )
    Spacer(Modifier.height(14.dp))

    if (!phone.codeSent) {
        var number by rememberSaveable { mutableStateOf("") }
        PhoneNumberField(value = number, onValueChange = { number = it })
        phone.error?.let { ErrorText(it) }
        Spacer(Modifier.height(12.dp))
        SubmitButton(
            text = stringResource(R.string.auth_send_code),
            loading = phone.isSending,
            enabled = number.isNotBlank(),
        ) {
            (context as? Activity)?.let { viewModel.startPhoneVerification(it, toE164(number)) }
        }
    } else {
        var code by rememberSaveable { mutableStateOf("") }
        LaunchedEffect(phone.autoCode) {
            phone.autoCode?.let { if (it.isNotBlank()) code = it }
        }
        LaunchedEffect(phone.codeResent) {
            if (phone.codeResent) code = ""
        }
        AuthTextField(
            value = code,
            onValueChange = { if (it.length <= 6) code = it.filter(Char::isDigit) },
            label = stringResource(R.string.auth_otp_code),
            keyboardType = KeyboardType.Number,
        )
        phone.error?.let { ErrorText(it) }
        if (phone.codeResent && phone.error == null) InfoText(stringResource(R.string.auth_code_resent))
        Spacer(Modifier.height(12.dp))
        SubmitButton(
            text = stringResource(R.string.auth_verify),
            loading = phone.isVerifying || phone.isSending,
            enabled = code.length == 6,
        ) { viewModel.verifyOtp(code.trim()) }

        ResendCodeRow(secondsLeft = phone.resendIn, enabled = phone.canResend) {
            (context as? Activity)?.let { viewModel.resendCode(it) }
        }
    }
}

// ════════════════════════════════════════════════════════════════
// SÉLECTEUR DE MÉTHODE
// ════════════════════════════════════════════════════════════════

@Composable
private fun MethodSwitch(bySms: Boolean, onSelect: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MethodChip(
            label = stringResource(R.string.auth_recover_by_email),
            selected = !bySms,
            onClick = { onSelect(false) },
            modifier = Modifier.weight(1f),
        )
        MethodChip(
            label = stringResource(R.string.auth_recover_by_sms),
            selected = bySms,
            onClick = { onSelect(true) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MethodChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VeloxTheme.colors
    val green = Color(0xFF22A82E)
    Row(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) green.copy(alpha = 0.14f) else Color.Transparent)
            .border(1.dp, if (selected) green else c.outlineVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (selected) green else c.onSurfaceVariant,
            fontFamily = Inter,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.W500,
        )
    }
}
