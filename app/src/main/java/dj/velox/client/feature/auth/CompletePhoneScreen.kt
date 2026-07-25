package dj.velox.client.feature.auth

import android.app.Activity
import androidx.activity.compose.BackHandler
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dj.velox.client.R

/**
 * Écran bloquant « numéro obligatoire » — affiché à la place de l'app quand le compte
 * connecté (typiquement via Google, qui n'apporte pas de numéro) n'a pas de téléphone.
 *
 * Le numéro est vérifié par SMS (OTP) puis **lié** au compte (`linkWithCredential`) et écrit
 * dans `users/{uid}.phone`. Dès que le numéro est présent, `RootContent` rebascule
 * automatiquement sur l'app (le stream du profil met à jour la session). Le code SMS est
 * auto-rempli quand l'instant verification Firebase le fournit.
 */
@Composable
fun CompletePhoneScreen(
    viewModel: AuthViewModel,
    onLogout: () -> Unit,
) {
    val link by viewModel.phoneLink.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Écran bloquant : le retour système ne doit ni sortir vers l'app ni la quitter.
    // Seules issues : compléter le numéro ou se déconnecter.
    BackHandler(enabled = true) { /* consommé volontairement */ }

    // Repart d'un état propre en quittant (déconnexion ou bascule vers l'app).
    DisposableEffect(Unit) { onDispose { viewModel.resetPhoneLink() } }

    if (!link.codeSent) {
        // ── Étape 1 : saisie du numéro ──────────────────────────
        var number by rememberSaveable { mutableStateOf("") }
        AuthScaffold(
            title = stringResource(R.string.complete_phone_title),
            subtitle = stringResource(R.string.complete_phone_message),
        ) {
            AuthTextField(
                value = number,
                onValueChange = { number = it },
                label = stringResource(R.string.auth_phone_hint),
                keyboardType = KeyboardType.Phone,
            )
            link.error?.let { ErrorText(it) }
            Spacer(Modifier.height(8.dp))
            SubmitButton(
                text = stringResource(R.string.auth_send_code),
                loading = link.isSending,
                enabled = number.isNotBlank(),
            ) {
                (context as? Activity)?.let { viewModel.startPhoneLink(it, number.trim()) }
            }
            LogoutRow(onLogout)
        }
    } else {
        // ── Étape 2 : saisie / auto-lecture du code SMS ─────────
        var code by rememberSaveable { mutableStateOf("") }
        LaunchedEffect(link.autoCode) {
            link.autoCode?.let { if (it.isNotBlank()) code = it }
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
            link.error?.let { ErrorText(it) }
            Spacer(Modifier.height(8.dp))
            SubmitButton(
                text = stringResource(R.string.auth_verify),
                // `linked` reste en chargement le temps que la session se rafraîchisse
                // et que RootContent bascule sur l'app.
                loading = link.isVerifying || link.linked,
                enabled = code.length == 6,
            ) { viewModel.verifyPhoneLink(code.trim()) }
            // Revenir à la saisie du numéro (ex. numéro déjà utilisé par un autre compte).
            TextButton(
                onClick = { viewModel.resetPhoneLink() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.auth_back)) }
        }
    }
}

@Composable
private fun LogoutRow(onLogout: () -> Unit) {
    TextButton(
        onClick = onLogout,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.action_logout)) }
}
