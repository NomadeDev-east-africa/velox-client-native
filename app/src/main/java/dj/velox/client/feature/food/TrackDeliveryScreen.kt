package dj.velox.client.feature.food

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dj.velox.client.R
import dj.velox.client.domain.model.LatLng
import dj.velox.client.feature.location.RouteViewModel
import dj.velox.client.feature.taxi.map.MapMarkerStyle
import dj.velox.client.feature.taxi.map.VeloxMap
import dj.velox.client.ui.theme.Inter
import dj.velox.client.ui.theme.Poppins
import dj.velox.client.ui.theme.VeloxTheme

/**
 * Position du livreur en temps réel (port de `track_delivery_screen`).
 * Carte MapLibre : marqueur livreur (vert) + destination (bleu) + ligne entre les deux.
 */
@Composable
fun TrackDeliveryScreen(
    onBack: () -> Unit,
    vm: TrackDeliveryViewModel = hiltViewModel(),
) {
    val c = VeloxTheme.colors
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val driver = state.driver
    val driverLatLng = driver?.let { LatLng(it.latitude, it.longitude) }

    // Itinéraire routier réel livreur → client (rechargé quand le livreur se déplace).
    val routeVm: RouteViewModel = hiltViewModel()
    val route by routeVm.route.collectAsStateWithLifecycle()
    LaunchedEffect(driverLatLng, state.destination) {
        val from = driverLatLng
        val to = state.destination
        if (from != null && to != null) routeVm.load(from, to)
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = c.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.size(10.dp))
            Text(stringResource(R.string.driver_position), color = c.primary, fontFamily = Poppins, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        }

        when {
            state.loading && driver == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp)
                    Spacer(Modifier.size(16.dp))
                    Text(stringResource(R.string.locating_driver), color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 14.sp)
                }
            }
            driver == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.LocationOff, null, tint = c.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(64.dp))
                    Spacer(Modifier.size(16.dp))
                    Text(stringResource(R.string.driver_position_unavailable), color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 14.sp)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.driver_gps_off), color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
            else -> Box(Modifier.weight(1f).fillMaxWidth()) {
                VeloxMap(
                    center = driverLatLng,
                    routeStart = driverLatLng,
                    routeEnd = state.destination,
                    routePolyline = route?.points,
                    markerStyle = MapMarkerStyle.DELIVERY,
                    zoom = 15.0,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (driver != null) {
            Row(
                Modifier.fillMaxWidth().background(c.surfaceLow).navigationBarsPadding().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(c.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.DeliveryDining, null, tint = c.primary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.driverName ?: stringResource(R.string.driver), color = c.onSurface, fontFamily = Poppins, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.size(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(c.primary))
                        Spacer(Modifier.size(5.dp))
                        Text(stringResource(R.string.on_the_way), color = c.primary, fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.W500)
                    }
                }
                Text(formatUpdated(context, driver.updatedAt), color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 11.sp)
            }
        }
    }
}

private val timeFmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.FRANCE)
private fun formatUpdated(context: android.content.Context, millis: Long): String {
    val diff = (System.currentTimeMillis() - millis) / 1000
    return when {
        diff < 10 -> context.getString(R.string.just_now)
        diff < 60 -> context.getString(R.string.ago_seconds, diff.toInt())
        diff < 3600 -> context.getString(R.string.ago_minutes, (diff / 60).toInt())
        else -> timeFmt.format(java.util.Date(millis))
    }
}
