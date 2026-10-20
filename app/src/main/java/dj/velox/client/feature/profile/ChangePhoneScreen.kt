package dj.velox.client.feature.profile

import android.app.Activity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dj.velox.client.R
import dj.velox.client.core.util.toE164
import dj.velox.client.feature.auth.AuthScaffold
import dj.velox.client.feature.auth.AuthTextField
import dj.velox.client.feature.auth.ErrorText
import dj.velox.client.feature.auth.PhoneNumberField
import dj.velox.client.feature.auth.SubmitButton

/**
 * Ajout / changement du numéro de téléphone depuis le profil, avec vérification SMS.
 *
 * Contrairement aux autres champs du profil, le numéro ne peut pas être saisi librement :
 * il doit être rattaché au compte Firebase Auth, sinon une connexion par OTP sur ce numéro
 * ouvrirait un nouveau compte vide (cf. [ChangePhoneViewModel]).
 */
@Composable
fun ChangePhoneScreen(
    currentPhone: String?,
    onBack: () -> Unit,
    vm: ChangePhoneViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    DisposableEffect(Unit) { onDispose { vm.reset() } }

    // Numéro rattaché → retour au profil (la session se rafraîchit toute seule).
    LaunchedEffect(ui.linked) { if (ui.linked) onBack() }

    if (!ui.codeSent) {
        // ── Étape 1 : nouveau numéro ────────────────────────────
        var number by rememberSaveable { mutableStateOf("") }
        AuthScaffold(
            title = stringResource(R.string.change_phone_title),
            subtitle = stringResource(R.string.change_phone_message),
            logo = true,
        ) {
            if (!currentPhone.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.change_phone_current, currentPhone),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
            }
            PhoneNumberField(value = number, onValueChange = { number = it })
            ui.error?.let { ErrorText(it) }
            if (ui.errorCode == "ERROR_CREDENTIAL_ALREADY_IN_USE") {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.phone_taken_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(8.dp))
            SubmitButton(
                text = stringResource(R.string.auth_send_code),
                loading = ui.isSending,
                enabled = number.isNotBlank(),
            ) {
                (context as? Activity)?.let { vm.sendCode(it, toE164(number)) }
            }
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.cancel))
            }
        }
    } else {
        // ── Étape 2 : code SMS ──────────────────────────────────
        var code by rememberSaveable { mutableStateOf("") }
        LaunchedEffect(ui.autoCode) {
            ui.autoCode?.let { if (it.isNotBlank()) code = it }
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
            ui.error?.let { ErrorText(it) }
            if (ui.errorCode == "ERROR_CREDENTIAL_ALREADY_IN_USE") {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.phone_taken_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(8.dp))
            SubmitButton(
                text = stringResource(R.string.auth_verify),
                loading = ui.isVerifying || ui.linked,
                enabled = code.length == 6,
            ) { vm.verify(code.trim()) }
            // Retour à la saisie du numéro (ex. numéro déjà utilisé par un autre compte).
            TextButton(onClick = vm::reset, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.auth_back))
            }
        }
    }
}
