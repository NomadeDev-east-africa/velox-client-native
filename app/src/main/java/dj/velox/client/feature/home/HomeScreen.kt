package dj.velox.client.feature.home

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dj.velox.client.R
import dj.velox.client.feature.auth.SessionState
import dj.velox.client.ui.theme.Inter
import dj.velox.client.ui.theme.Poppins
import dj.velox.client.ui.theme.VeloxColors
import dj.velox.client.ui.theme.VeloxTheme

/**
 * Accueil — dashboard « Kinetic Monolith » (port de `home_screen_app.dart`).
 * Header, tagline, carte fidélité, services, stats, actions rapides, footer,
 * et bottom-nav 2 onglets (Accueil / Profil).
 */
@Composable
fun HomeScreen(
    session: SessionState,
    onOpenTaxi: () -> Unit,
    onOpenFood: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenOrders: () -> Unit,
    statsVm: HomeStatsViewModel = hiltViewModel(),
) {
    val c = VeloxTheme.colors
    val profile = session.profile
    val firstName = session.displayName.split(' ').first()

    // Stats calculées en direct depuis `orders` (commandes livrées + total dépensé),
    // comme l'app Flutter — le doc users/{uid} ne porte pas ces champs.
    val stats by statsVm.stats.collectAsStateWithLifecycle()
    val ridesCount by statsVm.ridesCount.collectAsStateWithLifecycle()
    val completedOrders = stats.completedCount

    // Fidélité : « 1 commande = 10 pts ». Solde dispo = gagnés − dépensés.
    val earned = completedOrders * 10
    val available = (earned - (profile?.redeemedPoints ?: 0)).coerceAtLeast(0)
    val badge = when {
        earned >= 500 -> "VIP"
        earned >= 100 -> "GOLD"
        else -> "MEMBER"
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.bg),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(bottom = 100.dp),
        ) {
            Header(firstName, profile?.photoUrl ?: session.firebaseUser?.photoUrl?.toString(), c)
            Tagline(c)
            LoyaltyCard(available, badge, c)
            SectionRow(stringResource(R.string.our_services), c)
            Column(Modifier.padding(horizontal = 20.dp)) {
                ServiceCard("VTC DJIB", stringResource(R.string.vtc_subtitle), R.drawable.taxi_b, onOpenTaxi, c)
                Spacer(Modifier.height(12.dp))
                ServiceCard(stringResource(R.string.restaurants_fastfood), stringResource(R.string.food_subtitle), R.drawable.fast_food, onOpenFood, c)
            }
            // Tout en live : courses terminées (taxiRides), commandes + dépenses (orders).
            Stats(ridesCount, completedOrders, stats.totalSpent, c)
            QuickActions(onOpenOrders, c)
            Footer(c)
        }

        BottomNav(
            onProfile = onOpenProfile,
            c = c,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// ── HEADER ───────────────────────────────────────────────────────────────────
@Composable
private fun Header(firstName: String, photoUrl: String?, c: VeloxColors) {
    Row(
        Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(c.surfaceHigh)
                .border(2.dp, c.primary.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (photoUrl != null) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Icon(Icons.Filled.Person, null, tint = c.primary, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, null, tint = c.onSurfaceVariant, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(3.dp))
                Text(
                    "DJIBOUTI",
                    color = c.onSurfaceVariant,
                    fontFamily = Inter,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W600,
                    letterSpacing = 1.5.sp,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "${stringResource(R.string.hello)} $firstName",
                color = c.onSurface,
                fontFamily = Poppins,
                fontSize = 20.sp,
                fontWeight = FontWeight.W700,
                letterSpacing = (-0.5).sp,
            )
        }
        Image(
            painter = painterResource(R.drawable.logo_velox_bg),
            contentDescription = "Velox",
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(72.dp),
        )
    }
}

// ── TAGLINE ──────────────────────────────────────────────────────────────────
@Composable
private fun Tagline(c: VeloxColors) {
    Text(
        "✦  ${stringResource(R.string.tagline)}",
        color = c.primary,
        fontFamily = Inter,
        fontSize = 15.sp,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.W300,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(start = 20.dp, top = 10.dp, end = 20.dp),
    )
}

// ── CARTE FIDÉLITÉ ───────────────────────────────────────────────────────────
@Composable
private fun LoyaltyCard(points: Int, badge: String, c: VeloxColors) {
    Row(
        Modifier
            .padding(start = 20.dp, top = 20.dp, end = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.surfaceLow)
            .border(1.dp, c.outlineVariant.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.loyalty_points).uppercase(),
                color = c.onSurfaceVariant,
                fontFamily = Inter,
                fontSize = 11.sp,
                fontWeight = FontWeight.W600,
                letterSpacing = 1.8.sp,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$points",
                    color = c.onSurface,
                    fontFamily = Poppins,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.points_short),
                    color = c.onSurfaceVariant,
                    fontFamily = Inter,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W500,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.one_order_points),
                color = c.primary.copy(alpha = 0.7f),
                fontFamily = Inter,
                fontSize = 11.sp,
                fontWeight = FontWeight.W500,
            )
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(c.primary)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                badge,
                color = c.onPrimary,
                fontFamily = Inter,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        }
    }
}

// ── EN-TÊTE DE SECTION ───────────────────────────────────────────────────────
@Composable
private fun SectionRow(title: String, c: VeloxColors) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 28.dp, end = 20.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = c.onSurface,
            fontFamily = Poppins,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
        )
    }
}

// ── CARTE SERVICE ────────────────────────────────────────────────────────────
@Composable
private fun ServiceCard(
    title: String,
    subtitle: String,
    imageRes: Int,
    onTap: () -> Unit,
    c: VeloxColors,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface)
            .border(1.dp, c.outlineVariant.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
            .clickable(onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.surfaceHigh)
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(imageRes), null, contentScale = ContentScale.Fit)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = c.onSurface,
                fontFamily = Poppins,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
            )
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 13.sp)
        }
        Spacer(Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Star, null, tint = c.primary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(3.dp))
            Text("4.8", color = c.primary, fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ── STATISTIQUES ─────────────────────────────────────────────────────────────
@Composable
private fun Stats(rides: Int, orders: Int, spent: Double, c: VeloxColors) {
    Column(Modifier.padding(start = 20.dp, top = 28.dp, end = 20.dp)) {
        Text(
            stringResource(R.string.statistics).uppercase(),
            color = c.onSurfaceVariant,
            fontFamily = Inter,
            fontSize = 11.sp,
            fontWeight = FontWeight.W600,
            letterSpacing = 1.8.sp,
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatItem("$rides", stringResource(R.string.rides).uppercase(), c, Modifier.weight(1f))
            Divider(c)
            StatItem("$orders", stringResource(R.string.orders).uppercase(), c, Modifier.weight(1f))
            Divider(c)
            StatItem(formatNumber(spent), "${stringResource(R.string.expenses).uppercase()}\n(FDJ)", c, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatItem(value: String, label: String, c: VeloxColors, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            color = c.onSurface,
            fontFamily = Poppins,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = c.onSurfaceVariant,
            fontFamily = Inter,
            fontSize = 10.sp,
            fontWeight = FontWeight.W600,
            letterSpacing = 1.5.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Divider(c: VeloxColors) {
    Box(
        Modifier
            .width(1.dp)
            .height(40.dp)
            .background(c.outlineVariant.copy(alpha = 0.3f)),
    )
}

// ── ACTIONS RAPIDES ──────────────────────────────────────────────────────────
@Composable
private fun QuickActions(onOpenOrders: () -> Unit, c: VeloxColors) {
    val context = LocalContext.current
    val soonMsg = stringResource(R.string.coming_soon)
    val soon = { Toast.makeText(context, soonMsg, Toast.LENGTH_SHORT).show() }
    Column {
        Text(
            stringResource(R.string.quick_actions),
            color = c.onSurface,
            fontFamily = Poppins,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
            modifier = Modifier.padding(start = 20.dp, top = 28.dp, end = 20.dp, bottom = 16.dp),
        )
        Row(Modifier.padding(horizontal = 20.dp)) {
            ActionCard(Icons.Filled.History, stringResource(R.string.history), onOpenOrders, c, Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            ActionCard(Icons.Filled.CreditCard, stringResource(R.string.payments), soon, c, Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            ActionCard(Icons.Filled.AccountBalanceWallet, stringResource(R.string.wallet), soon, c, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ActionCard(icon: ImageVector, label: String, onTap: () -> Unit, c: VeloxColors, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .border(1.dp, c.outlineVariant.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .clickable(onClick = onTap)
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = c.primary, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, color = c.onSurface, fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.W600)
    }
}

// ── FOOTER ───────────────────────────────────────────────────────────────────
@Composable
private fun Footer(c: VeloxColors) {
    Box(Modifier.fillMaxWidth().padding(vertical = 36.dp), contentAlignment = Alignment.Center) {
        Text(
            "VELOX — SERVICE NATIONAL DJIBOUTIEN V1.0.0",
            color = c.onSurfaceVariant,
            fontFamily = Inter,
            fontSize = 10.sp,
            fontWeight = FontWeight.W500,
            letterSpacing = 1.2.sp,
        )
    }
}

// ── BOTTOM NAV ───────────────────────────────────────────────────────────────
@Composable
private fun BottomNav(onProfile: () -> Unit, c: VeloxColors, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(c.surfaceLow)
            .navigationBarsPadding()
            .height(72.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavItem(Icons.Filled.Home, active = true, onTap = {}, c = c, modifier = Modifier.weight(1f))
        NavItem(Icons.Filled.Person, active = false, onTap = onProfile, c = c, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun NavItem(icon: ImageVector, active: Boolean, onTap: () -> Unit, c: VeloxColors, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (active) c.primary else androidx.compose.ui.graphics.Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                null,
                tint = if (active) c.onPrimary else c.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

// ── UTIL ─────────────────────────────────────────────────────────────────────
private fun formatNumber(value: Double): String = when {
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000)
    value >= 1_000 -> "%.1fK".format(value / 1_000)
    else -> value.toInt().toString()
}
