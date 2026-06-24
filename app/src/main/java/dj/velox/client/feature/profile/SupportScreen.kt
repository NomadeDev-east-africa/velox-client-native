package dj.velox.client.feature.profile

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dj.velox.client.R
import dj.velox.client.ui.theme.Inter
import dj.velox.client.ui.theme.Poppins
import dj.velox.client.ui.theme.VeloxColors
import dj.velox.client.ui.theme.VeloxTheme

/**
 * Support & Aide (port de `support_screen.dart`). Cartes de contact cliquables :
 * tel: → composeur, mailto: → client mail.
 */
@Composable
fun SupportScreen(onBack: () -> Unit) {
    val c = VeloxTheme.colors
    val context = LocalContext.current

    fun launch(uri: String) {
        val action = if (uri.startsWith("tel:")) Intent.ACTION_DIAL else Intent.ACTION_SENDTO
        runCatching { context.startActivity(Intent(action, Uri.parse(uri))) }
            .onFailure { Toast.makeText(context, context.getString(R.string.cannot_open_app), Toast.LENGTH_SHORT).show() }
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(c.surfaceHigh).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = c.onSurface, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.size(14.dp))
            Text(stringResource(R.string.support_help), color = c.onSurface, fontFamily = Poppins, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp)) {
            Text(stringResource(R.string.contact_us_intro), color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 14.sp)
            Spacer(Modifier.height(24.dp))

            SectionTitle(stringResource(R.string.technical_support), c)
            Spacer(Modifier.height(8.dp))
            ContactCard(Icons.Filled.BugReport, stringResource(R.string.report_bug), "77 59 18 23", c) { launch("tel:77591823") }
            Spacer(Modifier.height(12.dp))
            ContactCard(Icons.Filled.Email, stringResource(R.string.email_support), "devchirdon@gmail.com", c) { launch("mailto:devchirdon@gmail.com") }

            Spacer(Modifier.height(24.dp))
            SectionTitle(stringResource(R.string.platform_service), c)
            Spacer(Modifier.height(8.dp))
            ContactCard(Icons.Filled.Phone, stringResource(R.string.platform_manager), "77 45 38 17", c) { launch("tel:77453817") }
            Spacer(Modifier.height(12.dp))
            ContactCard(Icons.Filled.Email, stringResource(R.string.email_manager), "Ouzeurb@gmail.com", c) { launch("mailto:Ouzeurb@gmail.com") }
        }
    }
}

@Composable
private fun SectionTitle(title: String, c: VeloxColors) {
    Text(title, color = c.onSurface, fontFamily = Poppins, fontSize = 16.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun ContactCard(icon: ImageVector, label: String, value: String, c: VeloxColors, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surfaceLow).border(1.dp, c.outlineVariant, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(c.primary.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.primary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = c.onSurface, fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.W600)
            Spacer(Modifier.height(2.dp))
            Text(value, color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 13.sp)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}
