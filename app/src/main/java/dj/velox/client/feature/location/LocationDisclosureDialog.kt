package dj.velox.client.feature.location

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dj.velox.client.R
import dj.velox.client.ui.theme.Inter
import dj.velox.client.ui.theme.Poppins
import dj.velox.client.ui.theme.VeloxTheme

/**
 * Divulgation proéminente de l'accès à la position — exigence « Prominent Disclosure
 * and Consent » de la User Data policy Google Play.
 *
 * Contraintes de la policy, toutes portées par ce composant :
 *  - s'affiche DANS l'app, avant la boîte de dialogue système de permission ;
 *  - nomme la donnée collectée (position GPS), son usage et avec qui elle est partagée ;
 *  - précise que la collecte cesse quand l'app n'est pas utilisée (le client Velox ne
 *    déclare pas `ACCESS_BACKGROUND_LOCATION` et n'a aucun service de localisation) ;
 *  - exige une action affirmative : ni le bouton retour ni un appui à l'extérieur ne
 *    valent consentement, d'où [DialogProperties] tout à false ;
 *  - le refus reste possible et ne bloque pas l'app.
 *
 * Ne pas transformer en `AlertDialog` standard : `onDismissRequest` y est déclenché par
 * le retour arrière, ce qui laisserait passer un consentement implicite.
 */
@Composable
fun LocationDisclosureDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val c = VeloxTheme.colors

    Dialog(
        onDismissRequest = { /* consentement explicite requis : voir KDoc */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.surfaceHigh, RoundedCornerShape(24.dp))
                .padding(24.dp),
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .background(c.primary.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.MyLocation,
                    contentDescription = null,
                    tint = c.primary,
                    modifier = Modifier.size(26.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                stringResource(R.string.location_disclosure_title),
                color = c.onSurface,
                fontFamily = Poppins,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
            )

            Spacer(Modifier.height(10.dp))

            Text(
                stringResource(R.string.location_disclosure_body),
                color = c.onSurfaceVariant,
                fontFamily = Inter,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )

            Spacer(Modifier.height(14.dp))

            DisclosureBullet(stringResource(R.string.location_disclosure_point_usage))
            DisclosureBullet(stringResource(R.string.location_disclosure_point_sharing))
            DisclosureBullet(stringResource(R.string.location_disclosure_point_background))

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = c.primary,
                    contentColor = c.onPrimary,
                ),
            ) {
                Text(
                    stringResource(R.string.location_disclosure_accept),
                    fontFamily = Poppins,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
            }

            TextButton(
                onClick = onDecline,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.location_disclosure_decline),
                    color = c.onSurfaceVariant,
                    fontFamily = Inter,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun DisclosureBullet(text: String) {
    val c = VeloxTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            Modifier
                .padding(top = 7.dp)
                .size(5.dp)
                .background(c.primary, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            color = c.onSurfaceVariant,
            fontFamily = Inter,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
    }
}
