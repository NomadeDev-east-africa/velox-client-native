package dj.velox.client.feature.food

import android.widget.Toast
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dj.velox.client.R
import dj.velox.client.domain.model.MenuItem
import dj.velox.client.domain.model.Restaurant
import dj.velox.client.ui.theme.Inter
import dj.velox.client.ui.theme.Poppins
import dj.velox.client.ui.theme.VeloxColors
import dj.velox.client.ui.theme.VeloxTheme
import kotlin.math.roundToInt

/**
 * Détail restaurant — « Kinetic Monolith » (port de `details_screen.dart`).
 * En-tête resto (note, livraison, temps), « Articles en vedette » (carrousel),
 * onglets catégories, liste des plats (→ écran d'options), panier flottant.
 */
@Composable
fun RestaurantDetailScreen(
    cartViewModel: CartViewModel,
    onOpenItem: (restaurantId: String, menuId: String) -> Unit,
    onOpenCart: () -> Unit,
    onBack: () -> Unit,
    menuVm: MenuViewModel = hiltViewModel(),
) {
    val c = VeloxTheme.colors
    val context = LocalContext.current
    val menu by menuVm.menu.collectAsStateWithLifecycle()
    val restaurant by menuVm.restaurant.collectAsStateWithLifecycle()
    val cart by cartViewModel.state.collectAsStateWithLifecycle()

    val restaurantId = menuVm.restaurantId
    val allLabel = stringResource(R.string.all_category)
    val featured = remember(menu) { menu.filter { !it.imageUrl.isNullOrEmpty() }.take(10) }
    // "" = catégorie « Tous » (clé stable, indépendante de la langue affichée).
    val categories = remember(menu) { listOf("") + menu.map { it.category }.distinct() }
    var selectedCategory by rememberSaveable { mutableStateOf("") }
    val filtered = remember(menu, selectedCategory) {
        if (selectedCategory.isEmpty()) menu else menu.filter { it.category == selectedCategory }
    }
    val avgTime = remember(menu) { if (menu.isEmpty()) 25 else menu.map { it.preparationTime }.average().roundToInt() }
    val soonMsg = stringResource(R.string.coming_soon)
    val soon = { Toast.makeText(context, soonMsg, Toast.LENGTH_SHORT).show() }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            // Barre supérieure
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), c, onBack)
                Spacer(Modifier.weight(1f))
                TopIcon(Icons.Filled.Share, stringResource(R.string.share_label), c, soon)
                TopIcon(Icons.Filled.Search, stringResource(R.string.search), c, soon)
            }

            LazyColumn(Modifier.fillMaxSize()) {
                item { restaurant?.let { RestaurantHeader(it, avgTime, c, soon) } }

                if (featured.isNotEmpty()) {
                    item { Spacer(Modifier.height(20.dp)) }
                    item {
                        Text(
                            stringResource(R.string.featured_items),
                            color = c.onSurface, fontFamily = Poppins, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp)) {
                            items(featured, key = { "f_${it.id}" }) { m ->
                                FeaturedCard(m, c) { onOpenItem(restaurantId, m.id) }
                                Spacer(Modifier.width(14.dp))
                            }
                        }
                    }
                }

                // Onglets catégories
                item { Spacer(Modifier.height(24.dp)) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp)) {
                        items(categories, key = { it }) { cat ->
                            CategoryTab(if (cat.isEmpty()) allLabel else cat, cat == selectedCategory, c) { selectedCategory = cat }
                            Spacer(Modifier.width(20.dp))
                        }
                    }
                }
                item { Box(Modifier.fillMaxWidth().height(1.dp).background(c.outlineVariant.copy(alpha = 0.2f))) }

                // Plats filtrés
                items(filtered, key = { it.id }) { m ->
                    MenuRow(m, c) { onOpenItem(restaurantId, m.id) }
                    Box(Modifier.fillMaxWidth().padding(start = 108.dp).height(1.dp).background(c.outlineVariant.copy(alpha = 0.1f)))
                }
                item { Spacer(Modifier.height(110.dp)) }
            }
        }

        if (cart.itemCount > 0) {
            CartBar(
                itemCount = cart.itemCount,
                total = cart.total,
                onClick = onOpenCart,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

// ── COMPOSANTS ───────────────────────────────────────────────────────────────

@Composable
private fun TopIcon(icon: ImageVector, cd: String, c: VeloxColors, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, cd, tint = c.onSurface)
    }
}

@Composable
private fun RestaurantHeader(r: Restaurant, avgTime: Int, c: VeloxColors, onTakeAway: () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(r.name, color = c.onSurface, fontFamily = Poppins, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, letterSpacing = (-0.5).sp)
        Spacer(Modifier.height(8.dp))
        Text(r.address, color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("%.1f".format(r.rating), color = c.onSurface, fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(5.dp))
            Icon(Icons.Filled.Star, null, tint = c.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(8.dp))
            Text("${r.totalOrders}+ ${stringResource(R.string.reviews)}", color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 13.sp)
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            InfoBit(Icons.Filled.AttachMoney, stringResource(R.string.free), stringResource(R.string.delivery), c)
            Spacer(Modifier.size(20.dp))
            InfoBit(Icons.Filled.AccessTime, "$avgTime", stringResource(R.string.minutes), c)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, c.outlineVariant, RoundedCornerShape(10.dp)).clickable(onClick = onTakeAway).padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(stringResource(R.string.takeaway), color = c.primary, fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.W600)
            }
        }
    }
}

@Composable
private fun InfoBit(icon: ImageVector, text: String, sub: String, c: VeloxColors) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = c.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Column {
            Text(text, color = c.onSurface, fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.W600)
            Text(sub, color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 11.sp)
        }
    }
}

@Composable
private fun FeaturedCard(m: MenuItem, c: VeloxColors, onClick: () -> Unit) {
    Column(Modifier.width(170.dp).clickable(onClick = onClick)) {
        AsyncImage(
            model = m.imageUrl,
            contentDescription = m.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)).background(c.surfaceHigh),
        )
        Spacer(Modifier.height(10.dp))
        Text(m.name, color = c.onSurface, fontFamily = Poppins, fontSize = 16.sp, fontWeight = FontWeight.W600, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${m.discountedPrice.roundToInt()} FDJ", color = c.primary, fontFamily = Poppins, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(6.dp))
            Text("· ${m.category}", color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CategoryTab(label: String, selected: Boolean, c: VeloxColors, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Text(
            label,
            color = if (selected) c.onSurface else c.onSurfaceVariant.copy(alpha = 0.6f),
            fontFamily = Poppins,
            fontSize = 20.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.W600,
            letterSpacing = (-0.3).sp,
        )
        Spacer(Modifier.height(6.dp))
        Box(Modifier.height(3.dp).width(if (selected) 28.dp else 0.dp).clip(RoundedCornerShape(2.dp)).background(c.primary))
    }
}

@Composable
private fun MenuRow(m: MenuItem, c: VeloxColors, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = m.imageUrl,
            contentDescription = m.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(c.surfaceHigh),
        )
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(m.name, color = c.onSurface, fontFamily = Poppins, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.category, color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 80.dp))
                Spacer(Modifier.size(6.dp))
                Text("· ${m.preparationTime} min", color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.size(12.dp))
        Text("${m.discountedPrice.roundToInt()} FDJ", color = c.primary, fontFamily = Poppins, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
