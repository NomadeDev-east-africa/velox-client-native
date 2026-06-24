package dj.velox.client.feature.taxi

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dj.velox.client.domain.model.LatLng
import dj.velox.client.domain.model.Ride
import dj.velox.client.domain.model.RideStatus
import dj.velox.client.feature.location.RouteViewModel
import dj.velox.client.feature.taxi.map.VeloxMap
import dj.velox.client.ui.theme.Inter
import dj.velox.client.ui.theme.Poppins
import dj.velox.client.ui.theme.VeloxColors
import dj.velox.client.ui.theme.VeloxTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun TrackingScreen(
    onFinished: () -> Unit,
    onCompleted: (rideId: String) -> Unit,
    rideVm: RideViewModel = hiltViewModel(),
) {
    val c = VeloxTheme.colors
    val context = LocalContext.current
    val state by rideVm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Course terminée → écran de notation (avant que le VM ne nettoie la course).
    LaunchedEffect(state.ride?.status) {
        if (state.ride?.status == RideStatus.COMPLETED) onCompleted(state.ride!!.rideId)
    }
    // Annulée / aucun chauffeur (course nettoyée) → retour accueil.
    LaunchedEffect(state.isLoading, state.ride) {
        if (!state.isLoading && state.ride == null) onFinished()
    }

    val ride = state.ride
    val pickup = ride?.let { LatLng(it.pickup.latitude, it.pickup.longitude) }
    val destination = ride?.let { LatLng(it.destination.latitude, it.destination.longitude) }

    // Tracé routier réel (ORS) pickup → destination.
    val routeVm: RouteViewModel = hiltViewModel()
    val route by routeVm.route.collectAsStateWithLifecycle()
    LaunchedEffect(pickup, destination) {
        val p = pickup
        val d = destination
        if (p != null && d != null) routeVm.load(p, d)
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        VeloxMap(center = pickup, routeStart = pickup, routeEnd = destination, routePolyline = route?.points, modifier = Modifier.fillMaxSize())

        if (ride != null) {
            // ── Bandeau statut (haut) ──
            Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp)) {
                StatusBanner(ride, c)
                Spacer(Modifier.height(10.dp))
                StatusStrip(ride, c)
            }

            // ── Contrôles carte (droite) ──
            Column(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp, bottom = 220.dp)) {
                MapControl(Icons.Filled.MyLocation, c)
                Spacer(Modifier.height(10.dp))
                MapControl(Icons.Filled.Layers, c)
            }

            // ── Carte chauffeur (bas) ──
            DriverCard(
                ride = ride, c = c,
                onCall = { ride.driverPhone?.let { dial(context, it) } },
                onCancel = { scope.launch { runCatching { rideVm.cancelRide("Annulée par l'utilisateur") } } },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp)
            }
        }
    }
}

private fun dial(context: android.content.Context, phone: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }
}

// ── Données de statut ────────────────────────────────────────────────────────

private data class StatusInfo(val code: String, val title: String, val chip: String)

private fun statusInfo(status: RideStatus): StatusInfo = when (status) {
    RideStatus.REQUESTED -> StatusInfo("RECHERCHE", "Recherche d'un chauffeur…", "EN_ATTENTE")
    RideStatus.ACCEPTED -> StatusInfo("COMMANDANT_ASSIGNÉ", "Chauffeur en route vers vous", "EN_ROUTE")
    RideStatus.ARRIVING -> StatusInfo("COMMANDANT_ASSIGNÉ", "Chauffeur en approche", "EN_ROUTE")
    RideStatus.ARRIVED -> StatusInfo("COMMANDANT_ARRIVÉ", "Votre chauffeur est arrivé", "ARRIVÉ")
    RideStatus.STARTED -> StatusInfo("EN_TRANSIT", "En route vers la destination", "EN_TRANSIT")
    RideStatus.COMPLETED -> StatusInfo("COURSE_TERMINÉE", "Vous êtes arrivé 🎉", "TERMINÉ")
    RideStatus.CANCELLED -> StatusInfo("ANNULÉE", "Course annulée", "ANNULÉ")
    RideStatus.NO_DRIVER_AVAILABLE -> StatusInfo("AUCUN_PILOTE", "Aucun chauffeur disponible", "INDISPONIBLE")
}

private fun bannerText(ride: Ride): Pair<String, String> = when (ride.status) {
    RideStatus.ACCEPTED, RideStatus.ARRIVING ->
        "✅ Un chauffeur a accepté" to "${ride.driverName ?: "Le chauffeur"} arrive — ${ride.driverPhone ?: ""}"
    RideStatus.ARRIVED -> "📍 Chauffeur arrivé" to "Votre chauffeur vous attend au point de départ"
    RideStatus.STARTED -> "🚗 Course commencée" to "Direction ${ride.destination.address}"
    RideStatus.REQUESTED -> "🔎 Recherche en cours" to "On cherche un chauffeur pour vous"
    else -> statusInfo(ride.status).title to ""
}

// ── Composants ───────────────────────────────────────────────────────────────

@Composable
private fun StatusBanner(ride: Ride, c: VeloxColors) {
    val (title, subtitle) = bannerText(ride)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surfaceHigh.copy(alpha = 0.95f)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).border(2.dp, c.onSurfaceVariant, CircleShape))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = c.onSurface, fontFamily = Poppins, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.size(6.dp))
                Text("· maintenant", color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 12.sp)
                Spacer(Modifier.size(4.dp))
                Icon(Icons.Filled.NotificationsNone, null, tint = c.onSurfaceVariant, modifier = Modifier.size(14.dp))
            }
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.size(8.dp))
        Icon(Icons.Filled.ExpandMore, null, tint = c.onSurfaceVariant, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun StatusStrip(ride: Ride, c: VeloxColors) {
    val info = statusInfo(ride.status)
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(12.dp)).background(c.surfaceLow.copy(alpha = 0.95f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(c.primary))
        Column(Modifier.weight(1f).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(c.primary))
                Spacer(Modifier.size(8.dp))
                Text(info.code, color = c.primary, fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.W700, letterSpacing = 1.5.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(info.title, color = c.onSurface, fontFamily = Poppins, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Icon(Icons.Filled.NotificationsNone, null, tint = c.onSurfaceVariant, modifier = Modifier.padding(end = 16.dp).size(20.dp))
    }
}

@Composable
private fun MapControl(icon: androidx.compose.ui.graphics.vector.ImageVector, c: VeloxColors) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(c.surfaceHigh), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = c.onSurface, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DriverCard(ride: Ride, c: VeloxColors, onCall: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val cancellable = ride.status in setOf(RideStatus.REQUESTED, RideStatus.ACCEPTED, RideStatus.ARRIVING, RideStatus.ARRIVED)
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(c.surface).navigationBarsPadding().padding(20.dp),
    ) {
        if (ride.driverName == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), color = c.primary, strokeWidth = 2.dp)
                Spacer(Modifier.size(12.dp))
                Text("Recherche d'un chauffeur…", color = c.onSurface, fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.W600)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(c.surfaceHigh), contentAlignment = Alignment.Center) {
                    if (!ride.driverPhotoUrl.isNullOrEmpty()) {
                        AsyncImage(model = ride.driverPhotoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(Icons.Filled.Person, null, tint = c.onSurfaceVariant, modifier = Modifier.size(32.dp))
                    }
                }
                Spacer(Modifier.size(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Commandant de bord", color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 12.sp)
                    Text(ride.driverName.uppercase(), color = c.onSurface, fontFamily = Poppins, fontSize = 18.sp, fontWeight = FontWeight.W800, letterSpacing = (-0.5).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Tarif estimé", color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 12.sp)
                    Text("${(ride.finalFare ?: ride.estimatedFare).roundToInt()} FDJ", color = c.primary, fontFamily = Poppins, fontSize = 18.sp, fontWeight = FontWeight.W800)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(c.surfaceHigh).padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Phone, null, tint = c.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(ride.driverPhone ?: "—", color = c.onSurface, fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.W600)
                }
                Spacer(Modifier.size(12.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(c.primary).clickable(enabled = ride.driverPhone != null, onClick = onCall).padding(horizontal = 28.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("APPELER", color = c.onPrimary, fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip("SYSTEM_STABLE", c.primary, c.primary.copy(alpha = 0.12f))
            Spacer(Modifier.size(8.dp))
            Chip(statusInfo(ride.status).chip, c.onSurfaceVariant, c.surfaceHigh)
            Spacer(Modifier.weight(1f))
            if (cancellable) {
                Text("ANNULER", color = c.error, fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onCancel).padding(6.dp))
            }
        }
    }
}

@Composable
private fun Chip(text: String, textColor: Color, bg: Color) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(text, color = textColor, fontFamily = Inter, fontSize = 11.sp, fontWeight = FontWeight.W700, letterSpacing = 1.sp)
    }
}
