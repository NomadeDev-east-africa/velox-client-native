package dj.velox.client.feature.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dj.velox.client.R

@Composable
fun ForgotPasswordScreen(viewModel: AuthViewModel, onBack: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    val sentMsg = stringResource(R.string.auth_reset_sent)

    AuthScaffold(title = stringResource(R.string.auth_forgot_title)) {
        Text(
            text = stringResource(R.string.auth_forgot_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        AuthTextField(
            value = email,
            onValueChange = { email = it },
            label = stringResource(R.string.auth_email),
            keyboardType = KeyboardType.Email,
        )

        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = it,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(8.dp))
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

        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.auth_back))
        }
    }
}
