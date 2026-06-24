package dj.velox.client.feature.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Récupère un idToken Google via Credential Manager (remplace l'ancien
 * GoogleSignIn déprécié). Le webClientId provient de R.string.default_web_client_id
 * (généré par le plugin google-services depuis google-services.json).
 *
 * Lève une exception si l'utilisateur annule ou si aucun compte n'est disponible
 * (à attraper côté UI pour rester silencieux sur une annulation).
 */
object GoogleSignInHelper {

    suspend fun getIdToken(context: Context, webClientId: String): String {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false) // propose aussi les comptes non encore liés
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val response = CredentialManager.create(context).getCredential(context, request)
        val credential = GoogleIdTokenCredential.createFrom(response.credential.data)
        return credential.idToken
    }
}
