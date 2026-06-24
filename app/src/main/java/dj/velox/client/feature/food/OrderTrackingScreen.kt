package dj.velox.client.feature.food

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dj.velox.client.R
import dj.velox.client.domain.model.Order
import dj.velox.client.ui.theme.Inter
import dj.velox.client.ui.theme.Poppins
import dj.velox.client.ui.theme.VeloxColors
import dj.velox.client.ui.theme.VeloxTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CANCEL_WINDOW_SECONDS = 120

/**
 * Suivi temps réel d'une commande food — « Kinetic Monolith » (port d'OrderTrackingScreen).
 * Stepper PROCESS STATUS, manifeste, fenêtre d'annulation (2 min), confirmation de livraison
 * (→ écran de notation). Le suivi (stream + restauration) vit dans le ViewModel.
 */
@Composable
fun OrderTrackingScreen(
    orderId: String,
    onExit: () -> Unit,
    onConfirmDelivery: () -> Unit,
    onTrackDriver: () -> Unit,
    vm: OrderTrackingViewModel = hiltViewModel(),
) {
    val c = VeloxTheme.colors
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(orderId) { if (state.orderId != orderId) vm.attachOrder(orderId) }

    val order = state.order
    LaunchedEffect(order?.status) {
        if (order?.status == Order.STATUS_CANCELLED) { delay(1_500); onExit() }
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        when {
            order == null && state.error != null -> ErrorView(state.error!!, c, onExit)
            order == null -> LoadingView(c)
            else -> OrderContent(
                order = order, c = c,
                onClose = onExit,
                onCancel = { scope.launch { runCatching { vm.cancelOrder() } } },
                onConfirmDelivery = onConfirmDelivery,
                onTrackDriver = onTrackDriver,
            )
        }
    }
}

@Composable
private fun OrderContent(
    order: Order,
    c: VeloxColors,
    onClose: () -> Unit,
    onCancel: () -> Unit,
    onConfirmDelivery: () -> Unit,
    onTrackDriver: () -> Unit,
) {
    var showCancelDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        // ── Barre supérieure ──
        Row(
            Modifier.fillMaxWidth().background(c.surfaceLow).statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Menu, null, tint = c.primary, modifier = Modifier.size(26.dp))
            Spacer(Modifier.weight(1f))
            Image(painterResource(R.drawable.logo_velox_bg), "Velox", contentScale = ContentScale.Fit, modifier = Modifier.height(34.dp))
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(c.surfaceHigh).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, stringResource(R.string.close), tint = c.onSurface, modifier = Modifier.size(20.dp))
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            StatusCard(order, c)
            Spacer(Modifier.height(14.dp))
            ProviderCard(order, c)
            if (order.status == Order.STATUS_DELIVERING && !order.deliveryDriverId.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                TrackDriverButton(c, onTrackDriver)
            }
            Spacer(Modifier.height(20.dp))
            Manifest(order, c)
        }

        BottomActions(order, c, onRequestCancel = { showCancelDialog = true }, onConfirmDelivery = onConfirmDelivery)
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            containerColor = c.surface,
            title = { Text(stringResource(R.string.cancel_order_q), color = c.onSurface, fontFamily = Poppins, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.action_irreversible), color = c.onSurfaceVariant, fontFamily = Inter) },
            confirmButton = { TextButton(onClick = { showCancelDialog = false; onCancel() }) { Text(stringResource(R.string.yes_cancel), color = c.error, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { showCancelDialog = false }) { Text(stringResource(R.string.no), color = c.onSurfaceVariant) } },
        )
    }
}

// ── PROCESS STATUS ──────────────────────────────────────────────────────────

private data class Step(val label: String, val sub: String)

private fun currentStepIndex(status: String): Int = when (status) {
    Order.STATUS_PENDING, Order.STATUS_CONFIRMED, Order.STATUS_ACCEPTED -> 0
    Order.STATUS_PREPARING -> 1
    Order.STATUS_READY -> 2
    Order.STATUS_DELIVERING -> 3
    Order.STATUS_COMPLETED -> 4
    else -> 0
}

@Composable
private fun StatusCard(order: Order, c: VeloxColors) {
    val steps = listOf(
        Step(stringResource(R.string.status_confirmed), stringResource(R.string.status_sub_confirmed)),
        Step(stringResource(R.string.status_preparing), stringResource(R.string.status_sub_preparing)),
        Step(stringResource(R.string.status_ready), stringResource(R.string.status_sub_ready)),
        Step(stringResource(R.string.status_delivering), stringResource(R.string.status_sub_delivering)),
        Step(stringResource(R.string.status_delivered), stringResource(R.string.status_sub_delivered)),
    )
    val current = currentStepIndex(order.status)

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface).border(1.dp, c.outlineVariant.copy(alpha = 0.2f), RoundedCornerShape(12.dp)).padding(20.dp)) {
        Text("PROCESS STATUS", color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 11.sp, fontWeight = FontWeight.W700, letterSpacing = 2.sp)
        Spacer(Modifier.height(16.dp))
        steps.forEachIndexed { i, step ->
            val done = i < current
            val active = i == current
            val isLast = i == steps.lastIndex
            Row {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(24.dp).clip(RoundedCornerShape(4.dp))
                            .background(if (active) c.primary else Color.Transparent)
                            .border(1.5.dp, if (done || active) c.primary else c.outlineVariant, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (done) Icon(Icons.Filled.Check, null, tint = c.primary, modifier = Modifier.size(15.dp))
                    }
                    if (!isLast) {
                        Box(Modifier.padding(vertical = 2.dp).width(2.dp).height(34.dp).background(if (done) c.primary else c.outlineVariant.copy(alpha = 0.5f)))
                    }
                }
                Spacer(Modifier.size(14.dp))
                Column(Modifier.padding(bottom = if (isLast) 0.dp else 6.dp)) {
                    Text(
                        step.label.uppercase(),
                        color = if (done || active) c.primary else c.onSurfaceVariant.copy(alpha = 0.6f),
                        fontFamily = Poppins, fontSize = 16.sp, fontWeight = if (done || active) FontWeight.Bold else FontWeight.W600, letterSpacing = (-0.2).sp,
                    )
                    if (i <= current) {
                        Spacer(Modifier.height(2.dp))
                        Text(step.sub, color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderCard(order: Order, c: VeloxColors) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface).border(1.dp, c.outlineVariant.copy(alpha = 0.2f), RoundedCornerShape(12.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(c.surfaceHigh), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Restaurant, null, tint = c.onSurfaceVariant, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.size(14.dp))
        Column {
            Text("PROVIDER", color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 10.sp, fontWeight = FontWeight.W700, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(2.dp))
            Text(order.restaurantName.uppercase(), color = c.onSurface, fontFamily = Poppins, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
        }
    }
}

@Composable
private fun TrackDriverButton(c: VeloxColors, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.primary.copy(alpha = 0.12f))
            .border(1.dp, c.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.DeliveryDining, null, tint = c.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(12.dp))
        Text(stringResource(R.string.track_driver_btn), color = c.primary, fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.Place, null, tint = c.primary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun Manifest(order: Order, c: VeloxColors) {
    Text("MANIFEST CONTENT", color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 11.sp, fontWeight = FontWeight.W700, letterSpacing = 2.sp)
    Spacer(Modifier.height(12.dp))
    order.items.forEach { item ->
        ManifestRow("${item.quantity}x  ${item.name.uppercase()}", "${item.totalPrice} FDJ", c)
    }
    ManifestRow(stringResource(R.string.delivery).uppercase(), "${order.deliveryFee} FDJ", c, muted = true)
    if (order.discount > 0) ManifestRow(stringResource(R.string.loyalty_discount).uppercase(), "−${order.discount} FDJ", c, muted = true)
    Spacer(Modifier.height(12.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.outlineVariant.copy(alpha = 0.3f)))
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("TOTAL PAYLOAD", color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.W700, letterSpacing = 1.sp)
        Text("${order.total} FDJ", color = c.primary, fontFamily = Poppins, fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp)
    }
    Spacer(Modifier.height(14.dp))
    Text(
        "AUTHORIZED COMMAND ONLY · REF #${order.id.take(8).uppercase()}",
        color = c.onSurfaceVariant.copy(alpha = 0.6f), fontFamily = Inter, fontSize = 11.sp, letterSpacing = 0.5.sp,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ManifestRow(label: String, value: String, c: VeloxColors, muted: Boolean = false) {
    val color = if (muted) c.onSurfaceVariant else c.onSurface
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = color, fontFamily = Inter, fontSize = 14.sp, fontWeight = if (muted) FontWeight.Normal else FontWeight.W600)
        Text(value, color = color, fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.W600)
    }
}

// ── ACTIONS DU BAS ──────────────────────────────────────────────────────────

@Composable
private fun BottomActions(order: Order, c: VeloxColors, onRequestCancel: () -> Unit, onConfirmDelivery: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(c.surfaceLow).navigationBarsPadding().padding(16.dp)) {
        if (order.status == Order.STATUS_COMPLETED) {
            Box(
                Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(8.dp)).background(c.primary).clickable(onClick = onConfirmDelivery),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = c.onPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.confirm_delivery).uppercase(), color = c.onPrimary, fontFamily = Inter, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                }
            }
        } else if (order.canBeCancelled) {
            var secondsLeft by remember(order.id) { mutableIntStateOf(CANCEL_WINDOW_SECONDS) }
            LaunchedEffect(order.id) { while (secondsLeft > 0) { delay(1_000); secondsLeft-- } }
            if (secondsLeft > 0) {
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Timer, null, tint = c.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.cancel_possible_for), color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 13.sp)
                    Text(formatCountdown(secondsLeft), color = c.error, fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(8.dp)).border(1.5.dp, c.error, RoundedCornerShape(8.dp)).clickable(onClick = onRequestCancel),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.cancel_order).uppercase(), color = c.error, fontFamily = Inter, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                }
            }
        }
    }
}

// ── États transverses ───────────────────────────────────────────────────────

@Composable
private fun LoadingView(c: VeloxColors) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.loading_order), color = c.onSurfaceVariant, fontFamily = Inter)
        }
    }
}

@Composable
private fun ErrorView(error: String, c: VeloxColors, onExit: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.error_prefix, error), color = c.error, fontFamily = Inter, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Box(Modifier.clip(RoundedCornerShape(28.dp)).background(c.primary).clickable(onClick = onExit).padding(horizontal = 24.dp, vertical = 12.dp)) {
                Text(stringResource(R.string.back_to_home), color = c.onPrimary, fontFamily = Inter, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatCountdown(seconds: Int): String =
    "${(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"
