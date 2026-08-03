package dj.velox.client.feature.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dj.velox.client.R
import dj.velox.client.ui.theme.Inter
import dj.velox.client.ui.theme.VeloxTheme

/**
 * Définit (ou remplace) le mot de passe du compte **connecté**.
 *
 * Point d'arrivée de la récupération par SMS : l'OTP tout juste validé vaut reconnexion récente,
 * donc Firebase accepte `updatePassword` sans réclamer l'ancien mot de passe — celui que
 * l'utilisateur a précisément oublié. Sert aussi aux comptes créés par téléphone ou par Google,
 * qui n'ont aucun mot de passe : on leur rattache alors un provider email + mot de passe, ce qui
 * exige une adresse (demandée ici si le compte n'en a pas).
 */
@Composable
fun SetPasswordScreen(viewModel: AuthViewModel, onDone: () -> Unit) {
    val c = VeloxTheme.colors
    val session by viewModel.session.collectAsStateWithLifecycle()
    val accountEmail = session.firebaseUser?.email?.takeIf { it.isNotBlank() }

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    val mismatchMsg = stringResource(R.string.auth_passwords_differ)
    val updatedMsg = stringResource(R.string.auth_password_updated)

    AuthScaffold(
        title = stringResource(R.string.auth_new_password_title),
        subtitle = stringResource(R.string.auth_new_password_subtitle),
    ) {
        // Rattacher un provider mot de passe impose une adresse : on la demande uniquement
        // quand le compte n'en a pas (inscription par téléphone).
        if (accountEmail == null) {
            AuthTextField(
                value = email,
                onValueChange = { email = it },
                label = stringResource(R.string.auth_email),
                keyboardType = KeyboardType.Email,
            )
            Spacer(Modifier.height(14.dp))
        } else {
            Text(
                text = accountEmail,
                color = c.onSurfaceVariant,
                fontFamily = Inter,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(14.dp))
        }

        AuthTextField(
            value = password,
            onValueChange = { password = it },
            label = stringResource(R.string.auth_new_password_hint),
            isPassword = true,
        )
        Spacer(Modifier.height(14.dp))
        AuthTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = stringResource(R.string.auth_new_password_confirm),
            isPassword = true,
        )

        message?.let { if (isError) ErrorText(it) else InfoText(it) }

        Spacer(Modifier.height(16.dp))
        SubmitButton(
            text = stringResource(R.string.auth_save_password),
            loading = saving,
            enabled = password.length >= 6 && confirm.isNotBlank() &&
                (accountEmail != null || email.isNotBlank()),
        ) {
            if (password != confirm) {
                isError = true
                message = mismatchMsg
            } else {
                saving = true
                message = null
                viewModel.setNewPassword(password, emailIfMissing = email) { error ->
                    saving = false
                    isError = error != null
                    message = error ?: updatedMsg
                    if (error == null) onDone()
                }
            }
        }

        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.auth_skip))
        }
    }
}
