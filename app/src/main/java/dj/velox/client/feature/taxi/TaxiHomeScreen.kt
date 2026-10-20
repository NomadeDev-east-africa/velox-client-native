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
import androidx.compose.material.icons.filled.LocalTaxi
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.maplibre.android.maps.MapLibreMap
import dj.velox.client.R
import dj.velox.client.domain.model.GeoUtils
import dj.velox.client.domain.model.LatLng
import dj.velox.client.domain.model.RideChoice
import dj.velox.client.domain.model.TaxiCatalog
import dj.velox.client.feature.location.LocationDisclosureDialog
import dj.velox.client.feature.location.RouteViewModel
import dj.velox.client.feature.taxi.map.MapMarkerStyle
import dj.velox.client.feature.taxi.map.MapRecenterButton
import dj.velox.client.feature.taxi.map.MapZoomControls
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
    onPickDeparture: () -> Unit,
    pickedDestination: dj.velox.client.feature.location.PickedPlace?,
    onConsumePicked: () -> Unit,
    onConfirmRide: (pickup: LatLng, destination: LatLng, pickupAddress: String, destinationAddress: String, distance: Double, vehicleId: String) -> Unit,
    locationVm: LocationViewModel = hiltViewModel(),
    pricingVm: TaxiPricingViewModel = hiltViewModel(),
) {
    val c = VeloxTheme.colors
    val context = LocalContext.current
    val loc by locationVm.state.collectAsStateWithLifecycle()
    val vehicleChoices by pricingVm.choices.collectAsStateWithLifecycle()

    // rememberSaveable : ces états doivent SURVIVRE à l'aller-retour vers le picker carte
    // (sinon `pickingDeparture` se réinitialise à false et le point choisi part en destination).
    var destLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var destLng by rememberSaveable { mutableStateOf<Double?>(null) }
    var destAddress by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedRideId by rememberSaveable { mutableStateOf(TaxiCatalog.choices.first().id) }
    // Départ modifiable (drop-pin) : override la position GPS quand l'utilisateur le change.
    var pickupLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var pickupLng by rememberSaveable { mutableStateOf<Double?>(null) }
    var pickupAddrOverride by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingDeparture by rememberSaveable { mutableStateOf(false) }

    val destination = if (destLat != null && destLng != null) LatLng(destLat!!, destLng!!) else null
    val pickupOverride = if (pickupLat != null && pickupLng != null) LatLng(pickupLat!!, pickupLng!!) else null

    // Retour du picker carte → applique au départ ou à la destination, puis consomme.
    LaunchedEffect(pickedDestination) {
        pickedDestination?.let {
            if (pickingDeparture) {
                pickupLat = it.lat; pickupLng = it.lng
                pickupAddrOverride = it.address
                pickingDeparture = false
            } else {
                destLat = it.lat; destLng = it.lng
                destAddress = it.address
            }
            onConsumePicked()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> if (result.values.any { it }) locationVm.refresh() else locationVm.onPermissionDenied() }

    // Divulgation proéminente (Google Play — User Data policy) : la boîte de dialogue
    // système ne doit JAMAIS apparaître avant que l'utilisateur ait lu et accepté
    // l'explication. Tant que `disclosureAccepted` vaut null (DataStore pas encore lu)
    // ou false, aucune permission n'est demandée.
    var showDisclosure by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(loc.disclosureAccepted) {
        val accepted = loc.disclosureAccepted ?: return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        when {
            granted -> locationVm.refresh()
            accepted -> permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            else -> showDisclosure = true
        }
    }

    if (showDisclosure) {
        LocationDisclosureDialog(
            // L'acceptation persiste le consentement : le flux repasse dans le
            // LaunchedEffect ci-dessus, qui déclenche alors la demande système.
            onAccept = { showDisclosure = false; locationVm.onDisclosureAccepted() },
            onDecline = { showDisclosure = false; locationVm.onDisclosureDeclined() },
        )
    }

    val pickup = pickupOverride ?: loc.position
    val departureAddress = pickupAddrOverride ?: loc.address
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
    val selected = vehicleChoices.firstOrNull { it.id == selectedRideId } ?: TaxiCatalog.byId(selectedRideId)
    // Résolus hors du lambda du bouton : stringResource est @Composable.
    val currentPositionLabel = stringResource(R.string.taxi_current_position)
    val selectedDestinationLabel = stringResource(R.string.taxi_selected_destination)

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            // ── Barre supérieure ──
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = c.onSurface)
                }
                Spacer(Modifier.size(4.dp))
                Image(androidx.compose.ui.res.painterResource(R.drawable.logo_velox_home), "Velox", contentScale = ContentScale.Fit, modifier = Modifier.height(30.dp))
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
                    stringResource(R.string.taxi_where_to),
                    color = c.primary,
                    fontFamily = Poppins, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp,
                )
                Spacer(Modifier.height(20.dp))

                TripCard(
                    departure = departureAddress
                        ?: stringResource(if (loc.permissionDenied) R.string.taxi_location_disabled else R.string.taxi_locating),
                    destinationLabel = destAddress ?: stringResource(R.string.taxi_destination_prompt),
                    hasDestination = dest != null,
                    c = c,
                    onPickDeparture = { pickingDeparture = true; onPickDeparture() },
                    onPickDestination = { pickingDeparture = false; onPickDestination() },
                    onClearDestination = { destLat = null; destLng = null; destAddress = null },
                )
                Spacer(Modifier.height(16.dp))

                MapPreview(pickup, dest, activeRoute?.points, distance, durationMin, c, onRecenter = { locationVm.refresh() })
                Spacer(Modifier.height(20.dp))

                Text(stringResource(R.string.taxi_choose_vehicle), color = c.onSurface, fontFamily = Poppins, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(if (dest != null) R.string.taxi_price_estimated else R.string.taxi_price_base),
                    color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 12.sp,
                )
                Spacer(Modifier.height(12.dp))
                Row {
                    vehicleChoices.forEachIndexed { i, choice ->
                        VehicleCard(choice, distance, choice.id == selectedRideId, c, Modifier.weight(1f)) { selectedRideId = choice.id }
                        if (i < vehicleChoices.lastIndex) Spacer(Modifier.size(10.dp))
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            // ── Bouton bas (bloqué en bas, flush) ──
            Box(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 6.dp)) {
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
                                    departureAddress ?: currentPositionLabel,
                                    destAddress ?: selectedDestinationLabel,
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
                            Text(stringResource(R.string.taxi_pick_destination), color = c.onPrimary, fontFamily = Inter, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.LocalTaxi, null, tint = c.onPrimary, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.size(10.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.taxi_confirm_ride), color = c.onPrimary, fontFamily = Inter, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.taxi_price_vehicle, selected.calculatePrice(distance).roundToInt(), stringResource(selected.nameRes)), color = c.onPrimary.copy(alpha = 0.9f), fontFamily = Inter, fontSize = 12.sp)
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
private fun TripCard(departure: String, destinationLabel: String, hasDestination: Boolean, c: VeloxColors, onPickDeparture: () -> Unit, onPickDestination: () -> Unit, onClearDestination: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(c.primary))
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Place, null, tint = c.error, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(stringResource(R.string.taxi_pickup_point), color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 12.sp)
                }
                Text(departure, color = c.onSurface, fontFamily = Poppins, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.size(32.dp).clip(CircleShape).background(c.surfaceHigh).clickable(onClick = onPickDeparture), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.EditLocation, stringResource(R.string.taxi_edit_pickup), tint = c.primary, modifier = Modifier.size(16.dp))
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
                Icon(Icons.Filled.Close, stringResource(R.string.taxi_clear), tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp).clip(CircleShape).clickable(onClick = onClearDestination))
            } else {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.primary, modifier = Modifier.size(22.dp))
            }
        }
    }
}

// ── APERÇU CARTE ─────────────────────────────────────────────────────────────
@Composable
private fun MapPreview(pickup: LatLng?, destination: LatLng?, routePoints: List<LatLng>?, distance: Double, durationMin: Int, c: VeloxColors, onRecenter: () -> Unit) {
    // Piloté directement par les boutons zoom +/- (pas de state Compose : la caméra n'a pas
    // besoin de recomposition, juste d'un appel one-shot sur la carte). Ne déclenche donc
    // aucun recalcul du tracé/marqueurs — c'est ce recalcul-là (pas le zoom) qui était source
    // de lenteur sur cette carte côté iOS.
    var mlMap by remember { mutableStateOf<MapLibreMap?>(null) }
    // Hauteur réduite : laisse la place au sélecteur « Choisissez votre véhicule » (2 cartes)
    // au-dessus du bouton, comme la maquette (avant : 380dp masquait les cartes).
    Box(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(20.dp)).background(c.surfaceHigh)) {
        VeloxMap(
            center = pickup, routeStart = pickup, routeEnd = destination, routePolyline = routePoints,
            markerStyle = MapMarkerStyle.RIDE, onMapReady = { mlMap = it }, modifier = Modifier.fillMaxSize(),
        )
        if (destination != null) {
            Row(
                Modifier.align(Alignment.BottomStart).padding(12.dp).clip(RoundedCornerShape(50)).background(c.bg.copy(alpha = 0.85f)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.taxi_km, "%.1f".format(distance)), color = c.onSurface, fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.taxi_duration_inline, durationMin), color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 13.sp)
            }
        }
        MapZoomControls(mlMap, c, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp))
        MapRecenterButton(c, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp), onClick = onRecenter)
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
            contentDescription = stringResource(choice.nameRes),
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(62.dp).fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text(stringResource(choice.nameRes), color = if (selected) c.onPrimary else c.onSurface, fontFamily = Poppins, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (choice.type == dj.velox.client.domain.model.RideType.COMFORT) {
            Spacer(Modifier.height(2.dp))
            Text(stringResource(R.string.taxi_ac), color = if (selected) c.onPrimary else c.primary, fontFamily = Inter, fontSize = 11.sp, fontWeight = FontWeight.W600)
        }
        choice.estimatedArrivalTime?.let { eta ->
            Spacer(Modifier.height(2.dp))
            Text(stringResource(R.string.taxi_eta, eta), color = if (selected) c.onPrimary.copy(alpha = 0.8f) else c.onSurfaceVariant, fontFamily = Inter, fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.ride_price_fdj, choice.calculatePrice(distance).roundToInt()), color = if (selected) c.onPrimary else c.primary, fontFamily = Poppins, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/** Image dédiée par type (alignée sur l'app Flutter : Standard → taxi_b, Confort → taxi_a). */
private fun vehicleImage(type: dj.velox.client.domain.model.RideType): Int = when (type) {
    dj.velox.client.domain.model.RideType.COMFORT -> R.drawable.taxi_a
    else -> R.drawable.taxi_b
}
