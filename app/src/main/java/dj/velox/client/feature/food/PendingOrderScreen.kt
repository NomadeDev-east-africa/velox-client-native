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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dj.velox.client.R
import dj.velox.client.core.Constants
import dj.velox.client.domain.model.LatLng
import dj.velox.client.domain.model.Order
import dj.velox.client.feature.auth.SessionState
import dj.velox.client.ui.theme.Inter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Durée du compte à rebours avant envoi automatique de la commande au restaurant. */
private const val CONFIRM_WINDOW_SECONDS = 60

/**
 * Pré-confirmation d'une commande food (Option B) : **aucune écriture en base** tant que le
 * client n'a pas confirmé. On affiche un aperçu (réutilise le visuel du suivi) avec un compte
 * à rebours. À « Poursuivre » — ou à l'expiration du compte à rebours — la commande est créée
 * en base et envoyée au restaurant (déclenche alors la Cloud Function). « Annuler » revient au
 * panier sans rien créer.
 */
@Composable
fun PendingOrderScreen(
    cartViewModel: CartViewModel,
    session: SessionState,
    onOrderCreated: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val c = dj.velox.client.ui.theme.VeloxTheme.colors
    val cart by cartViewModel.state.collectAsStateWithLifecycle()
    val pending by cartViewModel.pendingCheckout.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val restaurant = cart.restaurant
    val checkout = pending
    val uid = session.firebaseUser?.uid

    // Garde-fou : sans params de checkout / panier vide / non connecté → on revient (rien à confirmer).
    LaunchedEffect(checkout, restaurant, cart.isEmpty, uid) {
        if (checkout == null || restaurant == null || cart.isEmpty || uid == null) onCancel()
    }
    if (checkout == null || restaurant == null || cart.isEmpty || uid == null) return

    // Commande « aperçu » construite localement (identique à ce que créera createOrder).
    val now = remember { System.currentTimeMillis() }
    val previewOrder = remember(cart.items, checkout) {
        Order(
            id = "",
            userId = uid,
            restaurantId = restaurant.id,
            restaurantName = restaurant.name,
            restaurantImageUrl = restaurant.imageUrl,
            customerName = session.displayName,
            customerPhone = session.profile?.phone ?: session.firebaseUser?.phoneNumber ?: "Non renseigné",
            items = cart.items,
            deliveryFee = Constants.DELIVERY_FEE,
            status = Order.STATUS_PENDING,
            paymentMethod = checkout.paymentMethod,
            deliveryAddress = checkout.deliveryAddress,
            deliveryLocation = if (checkout.deliveryLat != null && checkout.deliveryLng != null) {
                LatLng(checkout.deliveryLat, checkout.deliveryLng)
            } else {
                null
            },
            createdAt = now,
            updatedAt = now,
            pointsUsed = checkout.pointsUsed,
            discount = checkout.pointsUsed * Constants.POINT_VALUE,
        )
    }

    var creating by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(CONFIRM_WINDOW_SECONDS) }

    // Crée la commande en base et notifie le restaurant (idempotent : un seul envoi).
    val confirm: () -> Unit = confirm@{
        if (creating) return@confirm
        creating = true
        scope.launch {
            val orderId = cartViewModel.createOrder(
                userId = uid,
                customerName = previewOrder.customerName,
                customerPhone = previewOrder.customerPhone,
                paymentMethod = checkout.paymentMethod,
                deliveryAddress = checkout.deliveryAddress,
                deliveryLocation = previewOrder.deliveryLocation,
                pointsUsed = checkout.pointsUsed,
            )
            if (orderId != null) onOrderCreated(orderId) else creating = false
        }
    }

    // Compte à rebours → à 0, envoi automatique (choix client).
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
        confirm()
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        // ── Barre supérieure : logo seul (cohérent avec le suivi) ──
        Row(
            Modifier.fillMaxWidth().background(c.surfaceLow).statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(painterResource(R.drawable.logo_velox_bg), "Velox", contentScale = ContentScale.Fit, modifier = Modifier.height(34.dp))
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            StatusCard(previewOrder, c)
            Spacer(Modifier.height(14.dp))
            ProviderCard(previewOrder, c)
            Spacer(Modifier.height(20.dp))
            Manifest(previewOrder, c)
        }

        // ── Actions du bas : compte à rebours + Poursuivre + Annuler ──
        Column(Modifier.fillMaxWidth().background(c.surfaceLow).navigationBarsPadding().padding(16.dp)) {
            if (!creating) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Timer, null, tint = c.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.order_send_countdown), color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 13.sp)
                    Text(formatCountdown(secondsLeft), color = c.primary, fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Poursuivre → envoi immédiat
            Box(
                Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(8.dp)).background(c.primary)
                    .clickable(enabled = !creating, onClick = confirm),
                contentAlignment = Alignment.Center,
            ) {
                if (creating) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = c.onPrimary, strokeWidth = 2.dp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.proceed_order).uppercase(), color = c.onPrimary, fontFamily = Inter, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        Spacer(Modifier.size(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = c.onPrimary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Annuler → disparaît dès la confirmation (création en cours)
            if (!creating) {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(8.dp)).border(1.5.dp, c.error, RoundedCornerShape(8.dp)).clickable(onClick = onCancel),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.cancel_order).uppercase(), color = c.error, fontFamily = Inter, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                }
            }
        }
    }
}

private fun formatCountdown(seconds: Int): String =
    "${(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"
