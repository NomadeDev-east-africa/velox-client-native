package dj.velox.client.feature.food

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dj.velox.client.R
import dj.velox.client.core.Constants
import dj.velox.client.domain.model.OrderItem
import dj.velox.client.feature.auth.SessionState
import dj.velox.client.ui.theme.Inter
import dj.velox.client.ui.theme.Poppins
import dj.velox.client.ui.theme.VeloxColors
import dj.velox.client.ui.theme.VeloxTheme
import kotlinx.coroutines.launch

private data class PaymentMethod(val value: String, val label: String, val icon: ImageVector)

private val PAYMENT_METHODS = listOf(
    PaymentMethod("cash", "Espèces", Icons.Filled.Payments),
    PaymentMethod("waafi", "Waafi", Icons.Filled.AccountBalanceWallet),
    PaymentMethod("d_money", "D-Money", Icons.Filled.AccountBalanceWallet),
    PaymentMethod("cac_pay", "CAC Pay", Icons.Filled.AccountBalanceWallet),
)

/**
 * Checkout — « Kinetic Monolith » (port de `order_details_screen.dart`).
 * Adresse de livraison (saisie), sélection des articles, **4 moyens de paiement**,
 * **rachat de points fidélité** (plafonné aux frais de livraison), récap, commande.
 */
@Composable
fun CartScreen(
    cartViewModel: CartViewModel,
    session: SessionState,
    onOrderPlaced: (String) -> Unit,
    onBack: () -> Unit,
    onPickAddress: () -> Unit,
    pickedAddress: dj.velox.client.feature.location.PickedPlace?,
    onConsumePicked: () -> Unit,
) {
    val c = VeloxTheme.colors
    val cart by cartViewModel.state.collectAsStateWithLifecycle()
    val availablePoints by cartViewModel.availablePoints.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var selectedPayment by rememberSaveable { mutableStateOf("cash") }
    var pointsApplied by rememberSaveable { mutableIntStateOf(0) }
    var addressName by rememberSaveable { mutableStateOf<String?>(null) }
    var pickedLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var pickedLng by rememberSaveable { mutableStateOf<Double?>(null) }

    // Adresse choisie via le picker carte → applique puis consomme.
    androidx.compose.runtime.LaunchedEffect(pickedAddress) {
        pickedAddress?.let {
            addressName = it.address
            pickedLat = it.lat
            pickedLng = it.lng
            onConsumePicked()
        }
    }

    // Fidélité : solde live (commandes completed × 10 − rachetés) ; repli sur le stat profil
    // tant que le chargement n'est pas terminé.
    androidx.compose.runtime.LaunchedEffect(session.firebaseUser?.uid) {
        val uid = session.firebaseUser?.uid ?: return@LaunchedEffect
        cartViewModel.loadAvailablePoints(uid, session.profile?.redeemedPoints ?: 0)
    }
    val available = availablePoints ?: run {
        val earned = (session.profile?.totalFoodOrders ?: 0) * 10
        (earned - (session.profile?.redeemedPoints ?: 0)).coerceAtLeast(0)
    }
    val maxUsable = minOf(available, Constants.DELIVERY_FEE / Constants.POINT_VALUE)
    val pointsClamped = pointsApplied.coerceIn(0, maxUsable)
    val discount = pointsClamped * Constants.POINT_VALUE
    val total = (cart.total - discount).coerceAtLeast(0)
    val hasAddress = !addressName.isNullOrBlank()

    if (cart.isEmpty) {
        EmptyCart(c, onBack)
        return
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        // ── Header ────────────────────────────────────────────────
        Column(Modifier.statusBarsPadding().padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, stringResource(R.string.back), tint = c.primary, modifier = Modifier.size(18.dp).clickable(onClick = onBack))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.checkout).uppercase(), color = c.primary, fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.W700, letterSpacing = 0.3.sp)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.Search, null, tint = c.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(cart.restaurant?.name ?: "Votre commande", color = c.onSurface, fontFamily = Poppins, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(8.dp))
            DeliverySection(addressName, c, onChange = onPickAddress)
            Spacer(Modifier.height(24.dp))

            SectionLabel(stringResource(R.string.your_selection), c)
            Spacer(Modifier.height(12.dp))
            cart.items.forEachIndexed { index, item ->
                ItemCard(
                    item = item, c = c,
                    onMinus = { cartViewModel.decrementAt(index) },
                    onPlus = { cartViewModel.incrementAt(index) },
                    onRemove = { cartViewModel.removeAt(index) },
                )
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(14.dp))

            SectionLabel(stringResource(R.string.payment_method), c)
            Spacer(Modifier.height(12.dp))
            PAYMENT_METHODS.forEach { m ->
                PaymentRow(m, selectedPayment == m.value, c) { selectedPayment = m.value }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(14.dp))

            PointsCard(available, maxUsable, pointsClamped, c, onApply = { pointsApplied = maxUsable }, onRemove = { pointsApplied = 0 })
            Spacer(Modifier.height(24.dp))

            SummaryCard(cart.subtotal, cart.deliveryFee, discount, pointsClamped, total, c)

            cart.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = c.error, fontFamily = Inter, fontSize = 13.sp)
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── Bouton commander ──────────────────────────────────────
        Box(Modifier.background(c.bg).navigationBarsPadding().padding(20.dp)) {
            Row(
                Modifier
                    .fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp))
                    .background(if (hasAddress) c.primary else c.surfaceTop)
                    .clickable(enabled = hasAddress && !cart.isCreatingOrder) {
                        val uid = session.firebaseUser?.uid ?: return@clickable
                        scope.launch {
                            val lat = pickedLat; val lng = pickedLng
                            val orderId = cartViewModel.createOrder(
                                userId = uid,
                                customerName = session.displayName,
                                customerPhone = session.profile?.phone ?: session.firebaseUser?.phoneNumber ?: "Non renseigné",
                                paymentMethod = selectedPayment,
                                deliveryAddress = addressName!!,
                                deliveryLocation = if (lat != null && lng != null) dj.velox.client.domain.model.LatLng(lat, lng) else null,
                                pointsUsed = pointsClamped,
                            )
                            if (orderId != null) onOrderPlaced(orderId)
                        }
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (cart.isCreatingOrder) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = c.onPrimary, strokeWidth = 2.dp)
                } else {
                    Text(
                        if (hasAddress) stringResource(R.string.place_order) else stringResource(R.string.choose_an_address),
                        color = if (hasAddress) c.onPrimary else c.onSurfaceVariant,
                        fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    )
                    if (hasAddress) {
                        Spacer(Modifier.size(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = c.onPrimary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ── SECTIONS ─────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String, c: VeloxColors) {
    Text(text, color = c.onSurface, fontFamily = Poppins, fontSize = 16.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun DeliverySection(addressName: String?, c: VeloxColors, onChange: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(stringResource(R.string.delivery_address), c)
        Text(stringResource(R.string.change).uppercase(), color = c.primary, fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onChange).padding(4.dp))
    }
    Spacer(Modifier.height(12.dp))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface).clickable(onClick = onChange),
    ) {
        Box(Modifier.fillMaxWidth().height(90.dp).background(c.surfaceHigh), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Map, null, tint = c.onSurfaceVariant, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.tap_to_pick_map), color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 11.sp)
            }
        }
        Column(Modifier.padding(14.dp)) {
            Text(addressName ?: stringResource(R.string.no_address_selected), color = c.onSurface, fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AccessTime, null, tint = c.onSurfaceVariant, modifier = Modifier.size(13.dp))
                Spacer(Modifier.size(4.dp))
                Text(stringResource(R.string.est_delivery), color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ItemCard(item: OrderItem, c: VeloxColors, onMinus: () -> Unit, onPlus: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surfaceHigh).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(c.surface), contentAlignment = Alignment.Center) {
            if (item.imageUrl.isNotEmpty()) {
                AsyncImage(model = item.imageUrl, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Filled.Fastfood, null, tint = c.primary, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, color = c.onSurface, fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                QtyMini(Icons.Filled.Remove, c, onMinus)
                Text("${stringResource(R.string.qty)}: ${item.quantity}", color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp))
                QtyMini(Icons.Filled.Add, c, onPlus)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${item.totalPrice} FDJ", color = c.primary, fontFamily = Poppins, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.remove), color = c.error, fontFamily = Inter, fontSize = 11.sp, modifier = Modifier.clickable(onClick = onRemove))
        }
    }
}

@Composable
private fun QtyMini(icon: ImageVector, c: VeloxColors, onClick: () -> Unit) {
    Box(Modifier.size(22.dp).clip(CircleShape).background(c.surface).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = c.primary, modifier = Modifier.size(13.dp))
    }
}

@Composable
private fun PaymentRow(m: PaymentMethod, selected: Boolean, c: VeloxColors, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface)
            .then(if (selected) Modifier.border(1.5.dp, c.primary.copy(alpha = 0.5f), RoundedCornerShape(14.dp)) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(m.icon, null, tint = c.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(12.dp))
        // Seul « Espèces » est traduit ; Waafi/D-Money/CAC Pay sont des noms de marque.
        val label = if (m.value == "cash") stringResource(R.string.cash_label) else m.label
        Text(label, color = c.onSurface, fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.W500, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (selected) c.primary else c.onSurfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(c.primary))
        }
    }
}

@Composable
private fun PointsCard(available: Int, maxUsable: Int, applied: Int, c: VeloxColors, onApply: () -> Unit, onRemove: () -> Unit) {
    val hasPoints = available > 0
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Star, null, tint = c.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.loyalty_points), color = c.onSurface, fontFamily = Poppins, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("$available ${stringResource(R.string.points_short)}", color = c.primary, fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (hasPoints) "1 point = ${Constants.POINT_VALUE} FDJ · ${stringResource(R.string.usable_on_delivery)}"
            else stringResource(R.string.earn_points_hint),
            color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 12.sp,
        )
        if (!hasPoints) return
        Spacer(Modifier.height(14.dp))
        if (applied > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$applied ${stringResource(R.string.points_applied)} · −${applied * Constants.POINT_VALUE} FDJ", color = c.primary, fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.W600, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.remove), color = c.error, fontFamily = Inter, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onRemove))
            }
        } else {
            val canApply = maxUsable > 0
            Box(
                Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (canApply) c.primary else c.surfaceTop)
                    .clickable(enabled = canApply, onClick = onApply),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (canApply) "${stringResource(R.string.use)} $maxUsable ${stringResource(R.string.points_short)} (−${maxUsable * Constants.POINT_VALUE} FDJ)" else stringResource(R.string.amount_too_low),
                    color = if (canApply) c.onPrimary else c.onSurfaceVariant,
                    fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(subtotal: Int, deliveryFee: Int, discount: Int, points: Int, total: Int, c: VeloxColors) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface).padding(16.dp)) {
        SummaryRow(stringResource(R.string.subtotal), "$subtotal FDJ", c)
        Spacer(Modifier.height(10.dp))
        SummaryRow(stringResource(R.string.delivery_fee), "$deliveryFee FDJ", c)
        if (discount > 0) {
            Spacer(Modifier.height(10.dp))
            SummaryRow("${stringResource(R.string.delivery_discount)} ($points ${stringResource(R.string.points_short)})", "−$discount FDJ", c)
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.outlineVariant.copy(alpha = 0.3f)))
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.total), color = c.onSurface, fontFamily = Poppins, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("$total FDJ", color = c.primary, fontFamily = Poppins, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, c: VeloxColors) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 14.sp)
        Text(value, color = c.onSurface, fontFamily = Inter, fontSize = 14.sp)
    }
}

@Composable
private fun EmptyCart(c: VeloxColors, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(c.bg).padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(140.dp).clip(CircleShape).background(c.surface), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.ShoppingCart, null, tint = c.primary.copy(alpha = 0.6f), modifier = Modifier.size(70.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.cart_empty), color = c.onSurface, fontFamily = Poppins, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.add_dishes_continue), color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 14.sp)
            Spacer(Modifier.height(32.dp))
            Box(
                Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(28.dp)).background(c.primary).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.back_to_restaurants), color = c.onPrimary, fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.W600)
            }
        }
    }
}
