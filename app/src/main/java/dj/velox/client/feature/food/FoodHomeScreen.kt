package dj.velox.client.feature.food

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dj.velox.client.R
import dj.velox.client.domain.model.Restaurant
import dj.velox.client.ui.theme.Inter
import dj.velox.client.ui.theme.Poppins
import dj.velox.client.ui.theme.VeloxColors
import dj.velox.client.ui.theme.VeloxTheme
import kotlinx.coroutines.delay

/**
 * Accueil Food — « Kinetic Monolith » (port de `home_screen_food.dart`).
 * Header « LIVRER À / Ville de Djibouti », carrousels auto (catégories + meilleurs choix),
 * bannière promo, liste complète des restaurants, bouton panier flottant.
 */
@Composable
fun FoodHomeScreen(
    cartViewModel: CartViewModel,
    onOpenRestaurant: (String) -> Unit,
    onOpenCart: () -> Unit,
    onOpenSearch: () -> Unit,
    onBack: () -> Unit,
    restaurantsVm: RestaurantsViewModel = hiltViewModel(),
) {
    val c = VeloxTheme.colors
    val restaurants by restaurantsVm.restaurants.collectAsStateWithLifecycle()
    val popular by restaurantsVm.popular.collectAsStateWithLifecycle()
    val categories by restaurantsVm.categories.collectAsStateWithLifecycle()
    val cart by cartViewModel.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            // ── Header ────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().background(c.surfaceLow).statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeaderIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), c, onBack)
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.food_deliver_to).uppercase(), color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 10.sp, fontWeight = FontWeight.W700, letterSpacing = 2.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(stringResource(R.string.food_city), color = c.primary, fontFamily = Poppins, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
                }
                HeaderIcon(Icons.Filled.Search, stringResource(R.string.search), c, onOpenSearch)
            }

            // ── Contenu défilant ──────────────────────────────────
            LazyColumn(Modifier.fillMaxSize()) {
                if (categories.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.categories).uppercase(), c) }
                    item { Spacer(Modifier.height(16.dp)) }
                    item {
                        AutoScrollCarousel(items = categories, height = 128.dp, pageFraction = 0.42f, c = c) { cat ->
                            CategoryTile(cat, c) { onOpenRestaurant(cat.restaurantId) }
                        }
                    }
                    item { Spacer(Modifier.height(28.dp)) }
                }

                item { PromoBanner(c) }
                item { Spacer(Modifier.height(28.dp)) }

                if (popular.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.best_picks).uppercase(), c) }
                    item { Spacer(Modifier.height(16.dp)) }
                    item {
                        AutoScrollCarousel(items = popular, height = 212.dp, pageFraction = 0.5f, c = c) { r ->
                            PopularCard(r, c) { onOpenRestaurant(r.id) }
                        }
                    }
                    item { Spacer(Modifier.height(28.dp)) }
                }

                item { SectionHeader(stringResource(R.string.all_restaurants).uppercase(), c) }
                item { Spacer(Modifier.height(16.dp)) }
                items(restaurants, key = { it.id }) { restaurant ->
                    RestaurantCard(restaurant, c) { onOpenRestaurant(restaurant.id) }
                    Spacer(Modifier.height(16.dp))
                }
                item { Spacer(Modifier.height(110.dp)) }
            }
        }

        // ── Panier flottant (bouton unifié) ───────────────────────
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

// ── HEADER ICON ──────────────────────────────────────────────────────────────
@Composable
private fun HeaderIcon(icon: ImageVector, cd: String, c: VeloxColors, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, cd, tint = c.primary, modifier = Modifier.size(24.dp))
    }
}

// ── SECTION HEADER ───────────────────────────────────────────────────────────
@Composable
private fun SectionHeader(title: String, c: VeloxColors) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = c.onSurface, fontFamily = Poppins, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
    }
}

// ── CARROUSEL AUTO-DÉFILANT ──────────────────────────────────────────────────
// Défile seul toutes les 4 s ; toute interaction met en pause ~5 s. Dots en bas.
@Composable
private fun <T> AutoScrollCarousel(
    items: List<T>,
    height: Dp,
    pageFraction: Float,
    c: VeloxColors,
    itemContent: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { items.size })
    var pauseUntil by remember { mutableLongStateOf(0L) }

    LaunchedEffect(pagerState.isScrollInProgress) {
        if (pagerState.isScrollInProgress) pauseUntil = System.currentTimeMillis() + 5_000
    }
    LaunchedEffect(items.size) {
        if (items.size <= 1) return@LaunchedEffect
        while (true) {
            delay(4_000)
            if (System.currentTimeMillis() < pauseUntil || pagerState.isScrollInProgress) continue
            val next = (pagerState.currentPage + 1) % items.size
            runCatching { pagerState.animateScrollToPage(next) }
        }
    }

    Column {
        HorizontalPager(
            state = pagerState,
            pageSize = remember(pageFraction) { FractionPageSize(pageFraction) },
            pageSpacing = 12.dp,
            contentPadding = PaddingValues(horizontal = 20.dp),
            modifier = Modifier.fillMaxWidth().height(height),
        ) { page -> itemContent(items[page]) }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(items.size) { i ->
                val active = pagerState.currentPage == i
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .height(8.dp)
                        .width(if (active) 20.dp else 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (active) c.primary else c.onSurfaceVariant.copy(alpha = 0.4f)),
                )
            }
        }
    }
}

// ── VIGNETTE CATÉGORIE ───────────────────────────────────────────────────────
@Composable
private fun CategoryTile(cat: FoodCategory, c: VeloxColors, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(c.surfaceLow).clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = cat.imageUrl,
            contentDescription = cat.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))),
            ),
        )
        Text(
            cat.name.uppercase(),
            color = Color.White,
            fontFamily = Poppins,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
        )
    }
}

// ── BANNIÈRE PROMO ───────────────────────────────────────────────────────────
@Composable
private fun PromoBanner(c: VeloxColors) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(c.primary)
            .height(120.dp),
    ) {
        Icon(
            Icons.Filled.TrackChanges,
            null,
            tint = c.onPrimary.copy(alpha = 0.15f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 4.dp).size(120.dp),
        )
        Column(Modifier.padding(24.dp)) {
            Text(stringResource(R.string.promo_title), color = c.onPrimary, fontFamily = Poppins, fontSize = 21.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp, lineHeight = 24.sp)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.promo_subtitle), color = c.onPrimary.copy(alpha = 0.8f), fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

// ── CARTE « MEILLEUR CHOIX » (compacte, horizontale) ─────────────────────────
@Composable
private fun PopularCard(restaurant: Restaurant, c: VeloxColors, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .border(1.dp, c.outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Box {
            AsyncImage(
                model = restaurant.imageUrl,
                contentDescription = restaurant.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(115.dp).clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
            )
            Box(
                Modifier.align(Alignment.TopEnd).padding(7.dp).size(28.dp).clip(CircleShape).background(c.surfaceTop.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.FavoriteBorder, stringResource(R.string.favorites), tint = c.onSurfaceVariant, modifier = Modifier.size(15.dp))
            }
            Box(
                Modifier.align(Alignment.BottomStart).padding(7.dp).clip(RoundedCornerShape(6.dp))
                    .background(if (restaurant.isOpen) c.primary else c.surfaceTop.copy(alpha = 0.8f))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(
                    if (restaurant.isOpen) stringResource(R.string.open_label).uppercase() else stringResource(R.string.closed_label).uppercase(),
                    color = if (restaurant.isOpen) c.onPrimary else c.onSurfaceVariant,
                    fontFamily = Inter, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                )
            }
        }
        Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 9.dp, bottom = 10.dp)) {
            Text(restaurant.name, color = c.onSurface, fontFamily = Poppins, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Star, null, tint = c.primary, modifier = Modifier.size(12.dp))
                Spacer(Modifier.size(3.dp))
                Text("%.1f".format(restaurant.rating), color = c.onSurface, fontFamily = Inter, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.size(8.dp))
                Icon(Icons.Filled.AccessTime, null, tint = c.onSurfaceVariant, modifier = Modifier.size(11.dp))
                Spacer(Modifier.size(3.dp))
                Text("25 min", color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 10.sp)
            }
        }
    }
}

// ── CARTE RESTAURANT (liste complète) ────────────────────────────────────────
@Composable
private fun RestaurantCard(restaurant: Restaurant, c: VeloxColors, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(18.dp))
            .background(c.surfaceLow)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(if (restaurant.isOpen) c.primary else Color.Transparent))
        Column(Modifier.weight(1f)) {
            Box {
                AsyncImage(
                    model = restaurant.imageUrl,
                    contentDescription = restaurant.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
                )
                Box(
                    Modifier.padding(12.dp).size(36.dp).clip(CircleShape).background(c.surfaceTop.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.FavoriteBorder, stringResource(R.string.favorites), tint = c.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
                Row(
                    Modifier.align(Alignment.TopEnd).padding(12.dp).clip(RoundedCornerShape(6.dp))
                        .background(c.surfaceTop.copy(alpha = 0.8f)).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Star, null, tint = c.primary, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("%.1f".format(restaurant.rating), color = c.onSurface, fontFamily = Inter, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(restaurant.name.uppercase(), color = c.onSurface, fontFamily = Poppins, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.AccessTime, null, tint = c.onSurfaceVariant, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("25 MIN", color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Spacer(Modifier.height(10.dp))
                Row {
                    Pill(if (restaurant.isOpen) stringResource(R.string.open_label).uppercase() else stringResource(R.string.closed_label).uppercase(), if (restaurant.isOpen) c.primary else c.onSurfaceVariant, if (restaurant.isOpen) c.primary.copy(alpha = 0.1f) else c.surfaceHigh)
                    Spacer(Modifier.size(6.dp))
                    Pill("${restaurant.totalOrders} ${stringResource(R.string.orders_abbrev)}", c.onSurfaceVariant, c.surfaceHigh)
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, textColor: Color, bg: Color) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, color = textColor, fontFamily = Inter, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
    }
}

/** Taille de page = fraction de la largeur visible (équivalent `viewportFraction` Flutter). */
private class FractionPageSize(private val fraction: Float) : PageSize {
    override fun Density.calculateMainAxisPageSize(availableSpace: Int, pageSpacing: Int): Int =
        ((availableSpace - pageSpacing) * fraction).toInt()
}
