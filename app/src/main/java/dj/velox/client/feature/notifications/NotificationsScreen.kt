package dj.velox.client.feature.notifications

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dj.velox.client.R
import dj.velox.client.domain.model.AppNotification
import dj.velox.client.domain.model.NotificationActionType
import dj.velox.client.domain.model.NotificationType
import dj.velox.client.ui.theme.Inter
import dj.velox.client.ui.theme.Poppins
import dj.velox.client.ui.theme.VeloxColors
import dj.velox.client.ui.theme.VeloxTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)

/** Horodatage relatif (« il y a X min/h/j »), au-delà d'une semaine on repasse en date absolue. */
private fun relativeTime(ms: Long): String {
    val diff = (System.currentTimeMillis() - ms).coerceAtLeast(0)
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        diff < minute -> "À l'instant"
        diff < hour -> "Il y a ${diff / minute} min"
        diff < day -> "Il y a ${diff / hour} h"
        diff < 7 * day -> "Il y a ${diff / day} j"
        else -> dateFmt.format(Date(ms))
    }
}

/**
 * Écran « Mes notifications » — historique persistant (`users/{uid}/notifications`),
 * indépendant du mécanisme `DeepLink`/`NotificationRouter` (tap sur push système).
 * Pattern Compose identique à `OrderHistoryScreen` (fond sombre, cartes `c.surface`).
 */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    vm: NotificationsViewModel = hiltViewModel(),
    onOpenOrder: (String) -> Unit = {},
    onOpenRide: () -> Unit = {},
    onOpenRestaurant: (String) -> Unit = {},
) {
    val c = VeloxTheme.colors
    val context = LocalContext.current
    val notifications by vm.notifications.collectAsStateWithLifecycle()

    fun handleClick(n: AppNotification) {
        if (!n.read) vm.markAsRead(n.id)
        val value = n.actionValue
        when (n.actionType) {
            NotificationActionType.ORDER -> value?.let(onOpenOrder)
            NotificationActionType.RIDE -> onOpenRide()
            NotificationActionType.RESTAURANT -> value?.let(onOpenRestaurant)
            NotificationActionType.URL -> value?.let { url ->
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }
            NotificationActionType.WHATSAPP -> value?.let { phone ->
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone"))) }
            }
            NotificationActionType.NONE -> Unit
        }
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(c.surfaceHigh).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = c.onSurface, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.size(14.dp))
                Text(stringResource(R.string.my_notifications), color = c.onSurface, fontFamily = Poppins, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            if (notifications.isEmpty()) {
                EmptyNotifications(c)
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                ) {
                    items(notifications, key = { it.id }) {
                        NotificationCard(it, c, onClick = { handleClick(it) })
                        Spacer(Modifier.size(12.dp))
                    }
                }
            }
        }
    }
}

private fun notificationIcon(type: NotificationType): ImageVector = when (type) {
    NotificationType.ORDER -> Icons.Filled.Fastfood
    NotificationType.RIDE -> Icons.Filled.DirectionsCar
    NotificationType.PROMO -> Icons.Filled.LocalOffer
}

@Composable
private fun NotificationCard(n: AppNotification, c: VeloxColors, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (n.read) c.surface else c.surfaceHigh)
            .border(1.dp, c.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(c.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(notificationIcon(n.type), null, tint = c.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!n.read) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(c.primary))
                    Spacer(Modifier.size(6.dp))
                }
                Text(
                    n.title,
                    color = c.onSurface,
                    fontFamily = Poppins,
                    fontSize = 15.sp,
                    fontWeight = if (n.read) FontWeight.W600 else FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(n.body, color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text(relativeTime(n.createdAt), color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 11.sp)
        }
    }
}

@Composable
private fun EmptyNotifications(c: VeloxColors) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(140.dp).clip(CircleShape).background(c.primary.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.NotificationsNone, null, tint = c.primary, modifier = Modifier.size(70.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.no_notifications), color = c.onSurface, fontFamily = Poppins, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.notifications_hint), color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp)
        }
    }
}
