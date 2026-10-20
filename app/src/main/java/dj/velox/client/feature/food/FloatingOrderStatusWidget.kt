package dj.velox.client.feature.food

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Restaurant
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dj.velox.client.R
import dj.velox.client.domain.model.Order
import dj.velox.client.ui.theme.Inter
import dj.velox.client.ui.theme.VeloxColors
import dj.velox.client.ui.theme.VeloxTheme

/**
 * Pastille flottante de suivi de commande — visible sur Accueil / Fiche resto / Panier / Profil
 * tant qu'une commande est active (ou jusqu'à 1 min après sa livraison), jamais sur l'écran de
 * suivi lui-même. Reprend le positionnement/les animations demandées, recolorées dans le thème
 * Velox (fond sombre, accent vert néon unique, icônes Material) plutôt que le style blanc/
 * glassmorphism d'origine, pour rester cohérent avec le reste de l'app (cf. CartBar.kt).
 *
 * À placer dans un Box parent, aligné `Alignment.BottomEnd`, appelé **inconditionnellement**
 * (ne pas faire `if (order != null) FloatingOrderStatusWidget(...)`) : c'est ce composable qui
 * gère lui-même l'apparition/disparition animée à partir de `order == null`. L'appelant est en
 * revanche responsable de décaler `modifier` vers le haut si une CartBar ou la bottom-nav
 * occupent déjà le bas de l'écran (elles ne doivent jamais se chevaucher).
 */
@Composable
fun FloatingOrderStatusWidget(
    order: Order?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VeloxTheme.colors

    // On retient la dernière commande connue : quand `order` repasse à null, l'animation de
    // sortie a besoin de continuer à afficher son contenu pendant qu'elle glisse hors écran.
    var lastOrder by remember { mutableStateOf(order) }
    LaunchedEffect(order) { if (order != null) lastOrder = order }
    val shown = lastOrder

    AnimatedVisibility(
        visible = order != null,
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(tween(400)),
        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(tween(250)),
        modifier = modifier,
    ) {
        if (shown == null) return@AnimatedVisibility
        // Micro-pulse toutes les 5s (discret, attire l'œil sans agresser).
        val infiniteTransition = rememberInfiniteTransition(label = "order_widget_pulse")
        val pulse by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(400, delayMillis = 4600),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse",
        )
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        val pressScale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "press")
        val scale = pulse * pressScale

        val (icon, labelRes) = statusIconAndLabel(shown.status)
        val label = stringResource(labelRes)
        val phase = statusPhase(shown.status)

        Row(
            Modifier
                .navigationBarsPadding()
                .wrapContentWidth()
                .scale(scale)
                .clip(RoundedCornerShape(28.dp))
                .background(c.surfaceHigh)
                .border(1.dp, c.primary.copy(alpha = 0.4f), RoundedCornerShape(28.dp))
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .semantics { contentDescription = label }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = c.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, color = c.onSurface, fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            StepDots(phase, c)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = c.onSurfaceVariant, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun StepDots(phase: Int, c: VeloxColors) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            if (i > 0) Spacer(Modifier.width(4.dp))
            Dot(active = i <= phase, c = c)
        }
    }
}

@Composable
private fun Dot(active: Boolean, c: VeloxColors) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(if (active) c.primary else c.outlineVariant.copy(alpha = 0.5f)),
    )
}

/** Regroupe les 5 statuts détaillés en 3 phases pour l'indicateur à points du widget. */
private fun statusPhase(status: String): Int = when (status) {
    Order.STATUS_READY -> 1
    Order.STATUS_DELIVERING, Order.STATUS_COMPLETED -> 2
    else -> 0
}

private fun statusIconAndLabel(status: String): Pair<ImageVector, Int> = when (status) {
    Order.STATUS_PREPARING -> Icons.Filled.Restaurant to R.string.status_preparing
    Order.STATUS_READY -> Icons.Filled.Fastfood to R.string.status_ready
    Order.STATUS_DELIVERING -> Icons.Filled.DeliveryDining to R.string.status_delivering
    Order.STATUS_COMPLETED -> Icons.Filled.CheckCircle to R.string.status_delivered
    else -> Icons.Filled.Restaurant to R.string.status_confirmed
}
