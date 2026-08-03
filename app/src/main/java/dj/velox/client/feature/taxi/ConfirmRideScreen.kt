package dj.velox.client.feature.taxi

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dj.velox.client.R
import dj.velox.client.domain.model.GeoUtils
import dj.velox.client.domain.model.LatLng
import dj.velox.client.domain.model.RideChoice
import dj.velox.client.domain.model.RideType
import dj.velox.client.domain.model.TaxiCatalog
import dj.velox.client.feature.auth.AuthViewModel
import dj.velox.client.feature.location.RouteViewModel
import dj.velox.client.feature.taxi.map.VeloxMap
import dj.velox.client.ui.theme.Inter
import dj.velox.client.ui.theme.Poppins
import dj.velox.client.ui.theme.VeloxColors
import dj.velox.client.ui.theme.VeloxTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class PayMethod(val value: String, @param:StringRes val labelRes: Int?, val brand: String?, val icon: ImageVector)
/** Mêmes moyens de paiement que le panier food (`CartScreen.PAYMENT_METHODS`). */
private val PAY_METHODS = listOf(
    PayMethod("cash", R.string.pay_cash, null, Icons.Filled.Payments),
    PayMethod("waafi", null, "Waafi", Icons.Filled.AccountBalanceWallet),
    PayMethod("d_money", null, "D-Money", Icons.Filled.AccountBalanceWallet),
    PayMethod("cac_pay", null, "CAC Pay", Icons.Filled.AccountBalanceWallet),
    PayMethod("bci_pay", null, "BCI Pay", Icons.Filled.AccountBalance),
    PayMethod("exim_bank", null, "Exim Bank", Icons.Filled.AccountBalance),
    PayMethod("dahab_plus", null, "Dahab+", Icons.Filled.AccountBalanceWallet),
)

/** Modèle de véhicule affiché — déduit du type, jamais du libellé traduit. */
private fun carModel(rc: RideChoice): String = when (rc.type) {
    RideType.COMFORT -> "Toyota Prius"
    RideType.VAN -> "Toyota Hiace"
    else -> "Toyota Corolla"
}

/**
 * Confirmer la course (port de `ride_confirmation_screen.dart`).
 * Récap trajet + carte (route) + pilote assigné + coût (sélecteur paiement) ; crée la course.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmRideScreen(
    pickup: LatLng,
    destination: LatLng,
    pickupAddress: String,
    destinationAddress: String,
    distance: Double,
    vehicleId: String,
    onConfirmed: () -> Unit,
    onBack: () -> Unit,
    rideVm: RideViewModel = hiltViewModel(),
    authVm: AuthViewModel = hiltViewModel(),
) {
    val c = VeloxTheme.colors
    val scope = rememberCoroutineScope()
    val session by authVm.session.collectAsStateWithLifecycle()
    val ride by rideVm.state.collectAsStateWithLifecycle()

    val choice = TaxiCatalog.byId(vehicleId)
    val price = choice.calculatePrice(distance).roundToInt()

    // Tracé routier réel (ORS) pour la carte + durée estimée sur la route.
    val routeVm: RouteViewModel = hiltViewModel()
    val route by routeVm.route.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { routeVm.load(pickup, destination) }
    val duration = route?.durationMin ?: GeoUtils.estimatedDurationMin(distance)

    var payment by rememberSaveable { mutableStateOf("cash") }
    var showSheet by remember { mutableStateOf(false) }
    val mid = LatLng((pickup.latitude + destination.latitude) / 2, (pickup.longitude + destination.longitude) / 2)

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            // Header
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = c.primary, modifier = Modifier.size(24.dp).clickable(onClick = onBack))
                Spacer(Modifier.size(16.dp))
                Text(stringResource(R.string.taxi_confirm_title), color = c.primary, fontFamily = Poppins, fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.Settings, null, tint = c.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                // Map
                Box(Modifier.fillMaxWidth().height(280.dp)) {
                    VeloxMap(center = mid, routeStart = pickup, routeEnd = destination, routePolyline = route?.points, markerStyle = dj.velox.client.feature.taxi.map.MapMarkerStyle.RIDE, zoom = 12.0, modifier = Modifier.fillMaxSize())
                    Row(
                        Modifier.align(Alignment.TopEnd).padding(16.dp).background(c.surfaceTop.copy(alpha = 0.9f)).padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.SatelliteAlt, null, tint = c.primary, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("SYSTEM_LINK: ACTIVE", color = c.onSurface, fontFamily = Inter, fontSize = 9.sp, fontWeight = FontWeight.W700, letterSpacing = 1.5.sp)
                    }
                    Column(Modifier.align(Alignment.BottomStart).padding(16.dp).background(c.surfaceTop.copy(alpha = 0.9f)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text("CURRENT_GPS", color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 10.sp, fontWeight = FontWeight.W700, letterSpacing = 2.sp)
                        Spacer(Modifier.size(2.dp))
                        Text(pickupAddress.uppercase(), color = c.onSurface, fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.W700, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(16.dp))

                // Détails
                Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(c.surfaceLow).padding(24.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Label(stringResource(R.string.taxi_ride_details), c)
                        Text("v1.0_ID", color = c.primary, fontFamily = Inter, fontSize = 10.sp, letterSpacing = 1.sp)
                    }
                    Spacer(Modifier.height(20.dp))
                    Column(Modifier.padding(start = 16.dp)) {
                        LocationRow(Icons.Filled.RadioButtonChecked, stringResource(R.string.taxi_origin), pickupAddress, c)
                        Spacer(Modifier.height(16.dp))
                        LocationRow(Icons.Filled.LocationOn, stringResource(R.string.taxi_destination_upper), destinationAddress, c)
                    }
                    Spacer(Modifier.height(20.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.outlineVariant.copy(alpha = 0.2f)))
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth()) {
                        MetricRow(Icons.Filled.AccessTime, stringResource(R.string.taxi_estimation), stringResource(R.string.taxi_min_upper, duration), c, Modifier.weight(1f))
                        MetricRow(Icons.Filled.Route, stringResource(R.string.taxi_distance_upper), stringResource(R.string.taxi_km_upper, "%.1f".format(distance)), c, Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(12.dp))

                // Pilote assigné
                Row(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(c.surface).padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(60.dp).background(c.surfaceHigh), contentAlignment = Alignment.Center) {
                        val vehicleImg = if (choice.type == dj.velox.client.domain.model.RideType.COMFORT) R.drawable.taxi_a else R.drawable.taxi_b
                        Image(painterResource(vehicleImg), null, contentScale = ContentScale.Fit, modifier = Modifier.size(48.dp))
                    }
                    Spacer(Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Label(stringResource(R.string.taxi_assigned_driver), c)
                        Spacer(Modifier.size(4.dp))
                        Text(carModel(choice).uppercase(), color = c.onSurface, fontFamily = Poppins, fontSize = 18.sp, fontWeight = FontWeight.W800, letterSpacing = (-0.5).sp)
                    }
                    Icon(Icons.Filled.DirectionsCar, null, tint = c.primary, modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.height(12.dp))

                // Coût
                Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(c.surface)) {
                    Box(Modifier.width(4.dp).height(150.dp).background(c.primary))
                    Column(Modifier.padding(24.dp)) {
                        Label(stringResource(R.string.taxi_cost), c)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$price", color = c.primary, fontFamily = Poppins, fontSize = 52.sp, fontWeight = FontWeight.Black, letterSpacing = (-2).sp)
                            Spacer(Modifier.size(8.dp))
                            Text("FDJ", color = c.primary.copy(alpha = 0.6f), fontFamily = Poppins, fontSize = 24.sp, fontWeight = FontWeight.W700, modifier = Modifier.padding(bottom = 6.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable { showSheet = true }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.primary))
                            Spacer(Modifier.size(8.dp))
                            Text(payLabel(payment).uppercase(), color = c.primary, fontFamily = Inter, fontSize = 10.sp, fontWeight = FontWeight.W700, letterSpacing = 1.5.sp)
                            Spacer(Modifier.size(6.dp))
                            Icon(Icons.Filled.KeyboardArrowDown, null, tint = c.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Spacer(Modifier.height(120.dp))
            }
        }

        // Bouton Confirmer
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(c.bg).navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Box(
                Modifier.fillMaxWidth().height(64.dp).background(c.primary)
                    .clickable(enabled = !ride.isCreating) {
                        val uid = session.firebaseUser?.uid ?: return@clickable
                        scope.launch {
                            runCatching {
                                rideVm.createRide(
                                    userId = uid, userName = session.displayName,
                                    // Numéro vérifié (E.164) en priorité : c'est celui que le chauffeur appellera.
                                    userPhone = session.verifiedPhone ?: session.profile?.phone ?: "",
                                    userPhotoUrl = session.profile?.photoUrl,
                                    pickupLatitude = pickup.latitude, pickupLongitude = pickup.longitude,
                                    pickupAddress = pickupAddress, pickupPlaceName = "Départ",
                                    destinationLatitude = destination.latitude, destinationLongitude = destination.longitude,
                                    destinationAddress = destinationAddress, destinationPlaceName = "Arrivée",
                                    distance = distance, estimatedDuration = duration,
                                    estimatedFare = choice.calculatePrice(distance), vehicleType = choice.vehicleType,
                                    paymentMethod = payment,
                                )
                            }.onSuccess { onConfirmed() }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (ride.isCreating) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = c.onPrimary, strokeWidth = 2.dp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.taxi_confirm_upper), color = c.onPrimary, fontFamily = Poppins, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                        Spacer(Modifier.size(12.dp))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.onPrimary, modifier = Modifier.size(26.dp))
                    }
                }
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }, containerColor = c.surfaceLow) {
            Column(Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.taxi_payment_method_upper), color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.W700, letterSpacing = 2.sp)
                Spacer(Modifier.height(12.dp))
                PAY_METHODS.forEach { m ->
                    val sel = payment == m.value
                    Row(
                        Modifier.fillMaxWidth().background(if (sel) c.primary.copy(alpha = 0.08f) else Color.Transparent).clickable { payment = m.value; showSheet = false }.padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(m.icon, null, tint = if (sel) c.primary else c.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(16.dp))
                        Text(payLabel(m.value), color = if (sel) c.primary else c.onSurface, fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.W500, modifier = Modifier.weight(1f))
                        if (sel) Icon(Icons.Filled.Check, null, tint = c.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/** Libellé d'un moyen de paiement : ressource traduite, ou nom de marque tel quel. */
@Composable
private fun payLabel(method: String): String {
    val m = PAY_METHODS.firstOrNull { it.value == method } ?: return method
    return m.labelRes?.let { stringResource(it) } ?: m.brand ?: method
}

@Composable
private fun Label(text: String, c: VeloxColors) {
    Text(text, color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 10.sp, fontWeight = FontWeight.W700, letterSpacing = 2.sp)
}

@Composable
private fun LocationRow(icon: ImageVector, label: String, value: String, c: VeloxColors) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = c.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Label(label, c)
            Spacer(Modifier.size(2.dp))
            Text(value, color = c.onSurface, fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.W600, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MetricRow(icon: ImageVector, label: String, value: String, c: VeloxColors, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(10.dp))
        Column {
            Label(label, c)
            Text(value, color = c.primary, fontFamily = Poppins, fontSize = 14.sp, fontWeight = FontWeight.W800, letterSpacing = (-0.5).sp)
        }
    }
}
