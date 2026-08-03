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
import dj.velox.client.core.util.toE164

/**
 * Écran bloquant « numéro obligatoire » — affiché à la place de l'app tant que le compte
 * connecté n'a pas de numéro **vérifié** (provider `phone` de Firebase Auth). Concerne les
 * inscriptions Google et email/mot de passe, ainsi que les comptes historiques dont le numéro
 * n'existait que dans Firestore.
 *
 * Le numéro est vérifié par SMS (OTP) puis **lié** au compte (`linkWithCredential`) et écrit
 * dans `users/{uid}.phone`. Dès que la liaison est faite, `RootContent` rebascule
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
            if (link.errorCode == ERROR_NUMBER_TAKEN) PhoneTakenHelp()
            Spacer(Modifier.height(8.dp))
            SubmitButton(
                text = stringResource(R.string.auth_send_code),
                loading = link.isSending,
                enabled = number.isNotBlank(),
            ) {
                (context as? Activity)?.let { viewModel.startPhoneLink(it, toE164(number)) }
            }
            LogoutRow(onLogout)
        }
    } else {
        // ── Étape 2 : saisie / auto-lecture du code SMS ─────────
        var code by rememberSaveable { mutableStateOf("") }
        LaunchedEffect(link.autoCode) {
            link.autoCode?.let { if (it.isNotBlank()) code = it }
        }
        // Un renvoi invalide le code précédent : on vide le champ pour éviter que l'utilisateur
        // resoumette l'ancien code et retombe sur « code expiré ».
        LaunchedEffect(link.codeResent) {
            if (link.codeResent) code = ""
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
            if (link.errorCode == ERROR_NUMBER_TAKEN) PhoneTakenHelp()
            if (link.codeResent && link.error == null) InfoText(stringResource(R.string.auth_code_resent))
            Spacer(Modifier.height(8.dp))
            SubmitButton(
                text = stringResource(R.string.auth_verify),
                // `linked` reste en chargement le temps que la session se rafraîchisse
                // et que RootContent bascule sur l'app.
                loading = link.isVerifying || link.linked || link.isSending,
                enabled = code.length == 6,
            ) { viewModel.verifyPhoneLink(code.trim()) }
            // Sortie du cas « code SMS expiré » : sans ce bouton, l'écran est bloquant et la
            // seule issue est de tout ressaisir depuis le numéro.
            ResendCodeRow(secondsLeft = link.resendIn, enabled = link.canResend) {
                (context as? Activity)?.let { viewModel.resendPhoneLinkCode(it) }
            }
            // Revenir à la saisie du numéro (ex. numéro déjà utilisé par un autre compte).
            TextButton(
                onClick = { viewModel.resetPhoneLink() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.auth_back)) }
            // Écran bloquant : la déconnexion doit rester atteignable aux deux étapes,
            // sinon un utilisateur dont le numéro est déjà pris n'a plus aucune sortie.
            LogoutRow(onLogout)
        }
    }
}

/** Code Firebase renvoyé quand le numéro appartient déjà à un autre compte. */
private const val ERROR_NUMBER_TAKEN = "ERROR_CREDENTIAL_ALREADY_IN_USE"

/**
 * Sortie de secours du cas « numéro déjà rattaché à un autre compte ». Sans cette
 * explication, l'utilisateur reste coincé sur l'écran bloquant avec un message d'échec
 * qui ne lui dit pas quoi faire.
 */
@Composable
private fun PhoneTakenHelp() {
    Spacer(Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.phone_taken_help),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LogoutRow(onLogout: () -> Unit) {
    TextButton(
        onClick = onLogout,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.action_logout)) }
}
