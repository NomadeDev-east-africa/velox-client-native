package dj.velox.client.feature.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dj.velox.client.R
import dj.velox.client.domain.model.Order
import dj.velox.client.domain.model.Ride
import dj.velox.client.domain.model.RideStatus
import dj.velox.client.ui.theme.Inter
import dj.velox.client.ui.theme.Poppins
import dj.velox.client.ui.theme.VeloxColors
import dj.velox.client.ui.theme.VeloxTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
private fun fmt(ms: Long) = dateFmt.format(Date(ms))

/**
 * Historique — commandes food + courses taxi (« Kinetic Monolith »).
 * Restylé pour être cohérent avec le reste de l'app (fond sombre, cartes `c.surface`,
 * Poppins/Inter, accent vert) — auparavant en Material3 par défaut.
 */
@Composable
fun OrderHistoryScreen(
    onBack: () -> Unit,
    vm: OrdersViewModel = hiltViewModel(),
    onOpenOrder: (String) -> Unit = {},
) {
    val c = VeloxTheme.colors
    val foodOrders by vm.foodOrders.collectAsStateWithLifecycle()
    val rides by vm.rides.collectAsStateWithLifecycle()
    val isEmpty = foodOrders.isEmpty() && rides.isEmpty()

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
                Text(stringResource(R.string.my_orders), color = c.onSurface, fontFamily = Poppins, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            if (isEmpty) {
                EmptyOrders(c)
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                ) {
                    if (foodOrders.isNotEmpty()) {
                        item { SectionHeader(Icons.Filled.Fastfood, "${stringResource(R.string.tab_food)} (${foodOrders.size})", c) }
                        items(foodOrders, key = { it.id }) {
                            FoodOrderCard(it, c, onClick = if (it.isActive) ({ onOpenOrder(it.id) }) else null)
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                    if (rides.isNotEmpty()) {
                        item { Spacer(Modifier.height(8.dp)); SectionHeader(Icons.Filled.DirectionsCar, "${stringResource(R.string.rides)} (${rides.size})", c) }
                        items(rides, key = { it.rideId }) {
                            RideCard(it, c)
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, text: String, c: VeloxColors) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = c.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(8.dp))
        Text(text, color = c.onSurface, fontFamily = Poppins, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusPill(icon: ImageVector, text: String, color: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
        Spacer(Modifier.size(5.dp))
        Text(text, color = color, fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.W600)
    }
}

// ── COMMANDE FOOD ─────────────────────────────────────────────────────────────

private fun orderStatusColor(status: String, c: VeloxColors): Color = when (status) {
    Order.STATUS_COMPLETED -> c.primary
    Order.STATUS_CANCELLED -> c.error
    else -> InProgressAmber
}

private fun orderStatusIcon(status: String): ImageVector = when (status) {
    Order.STATUS_PENDING -> Icons.Filled.HourglassEmpty
    Order.STATUS_CONFIRMED, Order.STATUS_ACCEPTED -> Icons.Filled.CheckCircleOutline
    Order.STATUS_PREPARING -> Icons.Filled.Restaurant
    Order.STATUS_READY -> Icons.Filled.DoneAll
    Order.STATUS_DELIVERING -> Icons.Filled.DeliveryDining
    Order.STATUS_COMPLETED -> Icons.Filled.CheckCircle
    Order.STATUS_CANCELLED -> Icons.Filled.Cancel
    else -> Icons.Filled.HourglassEmpty
}

@Composable
private fun FoodOrderCard(order: Order, c: VeloxColors, onClick: (() -> Unit)?) {
    val sColor = orderStatusColor(order.status, c)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface)
            .border(1.dp, c.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(c.surfaceHigh), contentAlignment = Alignment.Center) {
                if (order.restaurantImageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = order.restaurantImageUrl,
                        contentDescription = order.restaurantName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                    )
                } else {
                    Icon(Icons.Filled.Fastfood, null, tint = c.primary, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(order.restaurantName, color = c.onSurface, fontFamily = Poppins, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AccessTime, null, tint = c.onSurfaceVariant, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(fmt(order.createdAt), color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.size(8.dp))
            Text("${order.total} FDJ", color = c.primary, fontFamily = Poppins, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (onClick != null) {
                Spacer(Modifier.size(6.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, tint = c.onSurfaceVariant, modifier = Modifier.size(12.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.outlineVariant.copy(alpha = 0.3f)))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            StatusPill(orderStatusIcon(order.status), Order.statusText(order.status), sColor)
            Text("${order.itemCount} ${stringResource(R.string.items)}", color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 12.sp)
        }
    }
}

// ── COURSE TAXI ───────────────────────────────────────────────────────────────

private fun rideStatusColor(status: RideStatus, c: VeloxColors): Color = when (status) {
    RideStatus.COMPLETED -> c.primary
    RideStatus.CANCELLED, RideStatus.NO_DRIVER_AVAILABLE -> c.error
    else -> InProgressAmber
}

private fun rideStatusIcon(status: RideStatus): ImageVector = when (status) {
    RideStatus.REQUESTED -> Icons.Filled.HourglassEmpty
    RideStatus.ACCEPTED, RideStatus.ARRIVING, RideStatus.ARRIVED, RideStatus.STARTED -> Icons.Filled.DirectionsCar
    RideStatus.COMPLETED -> Icons.Filled.CheckCircle
    RideStatus.CANCELLED, RideStatus.NO_DRIVER_AVAILABLE -> Icons.Filled.Cancel
}

private fun rideStatusText(status: RideStatus): String = when (status) {
    RideStatus.REQUESTED -> "Recherche chauffeur"
    RideStatus.ACCEPTED -> "Chauffeur en route"
    RideStatus.ARRIVING -> "Chauffeur proche"
    RideStatus.ARRIVED -> "Chauffeur arrivé"
    RideStatus.STARTED -> "En course"
    RideStatus.COMPLETED -> "Terminée"
    RideStatus.CANCELLED -> "Annulée"
    RideStatus.NO_DRIVER_AVAILABLE -> "Aucun chauffeur"
}

@Composable
private fun RideCard(ride: Ride, c: VeloxColors) {
    val sColor = rideStatusColor(ride.status, c)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface)
            .border(1.dp, c.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(c.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.DirectionsCar, null, tint = c.primary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(ride.destination.address.ifBlank { "Course" }, color = c.onSurface, fontFamily = Poppins, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AccessTime, null, tint = c.onSurfaceVariant, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(fmt(ride.requestedAt), color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.size(8.dp))
            Text("${(ride.finalFare ?: ride.estimatedFare).toInt()} FDJ", color = c.primary, fontFamily = Poppins, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.outlineVariant.copy(alpha = 0.3f)))
        Spacer(Modifier.height(12.dp))
        StatusPill(rideStatusIcon(ride.status), rideStatusText(ride.status), sColor)
    }
}

// ── VIDE ──────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyOrders(c: VeloxColors) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(140.dp).clip(CircleShape).background(c.primary.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, tint = c.primary, modifier = Modifier.size(70.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.no_active_orders), color = c.onSurface, fontFamily = Poppins, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.history_hint), color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp)
        }
    }
}

private val InProgressAmber = Color(0xFFFFA726)
