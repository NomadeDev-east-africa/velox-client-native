package dj.velox.client.feature.taxi

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditLocation
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dj.velox.client.R
import dj.velox.client.domain.model.GeoUtils
import dj.velox.client.domain.model.LatLng
import dj.velox.client.domain.model.RideChoice
import dj.velox.client.domain.model.TaxiCatalog
import dj.velox.client.feature.location.RouteViewModel
import dj.velox.client.feature.taxi.map.MapMarkerStyle
import dj.velox.client.feature.taxi.map.VeloxMap
import dj.velox.client.ui.theme.Inter
import dj.velox.client.ui.theme.Poppins
import dj.velox.client.ui.theme.VeloxColors
import dj.velox.client.ui.theme.VeloxTheme
import kotlin.math.roundToInt

@Composable
fun TaxiHomeScreen(
    onBack: () -> Unit,
    onPickDestination: () -> Unit,
    pickedDestination: dj.velox.client.feature.location.PickedPlace?,
    onConsumePicked: () -> Unit,
    onConfirmRide: (pickup: LatLng, destination: LatLng, pickupAddress: String, destinationAddress: String, distance: Double, vehicleId: String) -> Unit,
    locationVm: LocationViewModel = hiltViewModel(),
) {
    val c = VeloxTheme.colors
    val context = LocalContext.current
    val loc by locationVm.state.collectAsStateWithLifecycle()

    var destination by remember { mutableStateOf<LatLng?>(null) }
    var destAddress by remember { mutableStateOf<String?>(null) }
    var selectedRideId by remember { mutableStateOf(TaxiCatalog.choices.first().id) }

    // Adresse choisie via le picker carte → applique puis consomme.
    LaunchedEffect(pickedDestination) {
        pickedDestination?.let {
            destination = LatLng(it.lat, it.lng)
            destAddress = it.address
            onConsumePicked()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> if (result.values.any { it }) locationVm.refresh() else locationVm.onPermissionDenied() }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) locationVm.refresh()
        else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    val pickup = loc.position
    val dest = destination

    // Itinéraire routier réel (ORS) → distance/durée/prix calculés sur la route, pas à vol d'oiseau.
    val routeVm: RouteViewModel = hiltViewModel()
    val route by routeVm.route.collectAsStateWithLifecycle()
    LaunchedEffect(pickup, dest) {
        if (pickup != null && dest != null) routeVm.load(pickup, dest)
    }
    val activeRoute = route?.takeIf { dest != null }
    val distance = activeRoute?.distanceKm
        ?: if (pickup != null && dest != null) GeoUtils.distanceKm(pickup, dest) else 0.0
    val durationMin = activeRoute?.durationMin ?: GeoUtils.estimatedDurationMin(distance.coerceAtLeast(0.1))
    val selected = TaxiCatalog.byId(selectedRideId)

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            // ── Barre supérieure ──
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = c.onSurface)
                }
                Spacer(Modifier.size(4.dp))
                Image(androidx.compose.ui.res.painterResource(R.drawable.logo_velox_bg), "Velox", contentScale = ContentScale.Fit, modifier = Modifier.height(30.dp))
                Spacer(Modifier.size(6.dp))
                Text("Velox", color = c.primary, fontFamily = Poppins, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(40.dp).clip(CircleShape).background(c.surfaceHigh), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.NotificationsNone, null, tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Où allons-nous aujourd'hui ? 🌍",
                    color = c.primary,
                    fontFamily = Poppins, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp,
                )
                Spacer(Modifier.height(20.dp))

                TripCard(
                    departure = loc.address ?: if (loc.permissionDenied) "Localisation désactivée" else "Localisation en cours…",
                    destinationLabel = destAddress ?: "Destination — Où allez-vous ?",
                    hasDestination = dest != null,
                    c = c,
                    onPickDestination = onPickDestination,
                    onClearDestination = { destination = null; destAddress = null },
                )
                Spacer(Modifier.height(16.dp))

                MapPreview(pickup, dest, activeRoute?.points, distance, durationMin, c, onRecenter = { locationVm.refresh() })
                Spacer(Modifier.height(20.dp))

                Text("Choisissez votre véhicule", color = c.onSurface, fontFamily = Poppins, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (dest != null) "Prix estimé pour votre course" else "Prix de base — choisissez une destination pour l'estimation",
                    color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 12.sp,
                )
                Spacer(Modifier.height(12.dp))
                Row {
                    TaxiCatalog.choices.forEachIndexed { i, choice ->
                        VehicleCard(choice, distance, choice.id == selectedRideId, c, Modifier.weight(1f)) { selectedRideId = choice.id }
                        if (i < TaxiCatalog.choices.lastIndex) Spacer(Modifier.size(10.dp))
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            // ── Bouton bas (dégradé) ──
            Box(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
                val canConfirm = pickup != null
                Box(
                    Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(50))
                        .background(if (canConfirm) c.primary else c.surfaceTop)
                        .clickable(enabled = canConfirm) {
                            if (dest == null) {
                                onPickDestination()
                            } else {
                                onConfirmRide(
                                    pickup!!, dest,
                                    loc.address ?: "Position actuelle",
                                    destAddress ?: "Destination sélectionnée",
                                    distance, selectedRideId,
                                )
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (dest == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Search, null, tint = c.onPrimary, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.size(10.dp))
                            Text("Choisir une destination", color = c.onPrimary, fontFamily = Inter, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Confirmer la course", color = c.onPrimary, fontFamily = Inter, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                Text("${selected.calculatePrice(distance).roundToInt()} FDJ · ${selected.name}", color = c.onPrimary.copy(alpha = 0.9f), fontFamily = Inter, fontSize = 12.sp)
                            }
                            Spacer(Modifier.size(10.dp))
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.onPrimary, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

// ── CARTE TRAJET ─────────────────────────────────────────────────────────────
@Composable
private fun TripCard(departure: String, destinationLabel: String, hasDestination: Boolean, c: VeloxColors, onPickDestination: () -> Unit, onClearDestination: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(c.primary))
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Place, null, tint = c.error, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Point de départ", color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 12.sp)
                }
                Text(departure, color = c.onSurface, fontFamily = Poppins, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.size(32.dp).clip(CircleShape).background(c.surfaceHigh), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.EditLocation, null, tint = c.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
        Box(Modifier.padding(start = 6.dp, top = 4.dp, bottom = 4.dp).width(2.dp).height(18.dp).background(c.outlineVariant))
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = !hasDestination, onClick = onPickDestination),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(c.primary))
            Spacer(Modifier.size(12.dp))
            Text(destinationLabel, color = if (hasDestination) c.onSurface else c.onSurfaceVariant, fontFamily = Inter, fontSize = 15.sp, fontWeight = if (hasDestination) FontWeight.W600 else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (hasDestination) {
                Icon(Icons.Filled.Close, "Effacer", tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp).clip(CircleShape).clickable(onClick = onClearDestination))
            } else {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.primary, modifier = Modifier.size(22.dp))
            }
        }
    }
}

// ── APERÇU CARTE ─────────────────────────────────────────────────────────────
@Composable
private fun MapPreview(pickup: LatLng?, destination: LatLng?, routePoints: List<LatLng>?, distance: Double, durationMin: Int, c: VeloxColors, onRecenter: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(380.dp).clip(RoundedCornerShape(20.dp)).background(c.surfaceHigh)) {
        VeloxMap(center = pickup, routeStart = pickup, routeEnd = destination, routePolyline = routePoints, markerStyle = MapMarkerStyle.RIDE, modifier = Modifier.fillMaxSize())
        if (destination != null) {
            Row(
                Modifier.align(Alignment.BottomStart).padding(12.dp).clip(RoundedCornerShape(50)).background(c.bg.copy(alpha = 0.85f)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("%.1f km".format(distance), color = c.onSurface, fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("  ·  $durationMin min", color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 13.sp)
            }
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(12.dp).size(44.dp).clip(CircleShape).background(c.bg).clickable(onClick = onRecenter),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.MyLocation, "Recentrer", tint = c.primary, modifier = Modifier.size(22.dp))
        }
    }
}

// ── CARTE VÉHICULE ───────────────────────────────────────────────────────────
@Composable
private fun VehicleCard(choice: RideChoice, distance: Double, selected: Boolean, c: VeloxColors, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) c.primary else c.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.primary else c.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            androidx.compose.ui.res.painterResource(vehicleImage(choice.type)),
            contentDescription = choice.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(62.dp).fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text(choice.name, color = if (selected) c.onPrimary else c.onSurface, fontFamily = Poppins, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        choice.estimatedArrivalTime?.let { eta ->
            Spacer(Modifier.height(2.dp))
            Text("~ $eta", color = if (selected) c.onPrimary.copy(alpha = 0.8f) else c.onSurfaceVariant, fontFamily = Inter, fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text("${choice.calculatePrice(distance).roundToInt()} FDJ", color = if (selected) c.onPrimary else c.primary, fontFamily = Poppins, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/** Image dédiée par type (alignée sur l'app Flutter : Standard → taxi_b, Confort → taxi_a). */
private fun vehicleImage(type: dj.velox.client.domain.model.RideType): Int = when (type) {
    dj.velox.client.domain.model.RideType.COMFORT -> R.drawable.taxi_a
    else -> R.drawable.taxi_b
}
