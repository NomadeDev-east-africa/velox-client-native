package dj.velox.client.feature.food

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import dj.velox.client.domain.model.MenuItem
import dj.velox.client.domain.model.Restaurant
import dj.velox.client.ui.theme.Inter
import dj.velox.client.ui.theme.Poppins
import dj.velox.client.ui.theme.VeloxColors
import dj.velox.client.ui.theme.VeloxTheme

/**
 * Recherche food (port de `food_search_screen.dart`) : barre de recherche (debounce 300 ms
 * géré par le ViewModel), résultats en sections RESTAURANTS / PLATS, états vides dédiés.
 */
@Composable
fun FoodSearchScreen(
    onOpenRestaurant: (String) -> Unit,
    onBack: () -> Unit,
    vm: FoodSearchViewModel = hiltViewModel(),
) {
    val c = VeloxTheme.colors
    val query by vm.query.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        // Barre supérieure
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = c.onSurface)
            }
            Spacer(Modifier.size(4.dp))
            Text(stringResource(R.string.search), color = c.onSurface, fontFamily = Poppins, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        // Champ de recherche
        OutlinedTextField(
            value = query,
            onValueChange = vm::onQueryChange,
            placeholder = { Text(stringResource(R.string.search_hint_food), color = c.onSurfaceVariant, fontFamily = Inter) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(),
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = c.onSurfaceVariant) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { vm.onQueryChange("") }) {
                        Icon(Icons.Filled.Clear, stringResource(R.string.clear_label), tint = c.onSurfaceVariant)
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = c.surfaceLow,
                unfocusedContainerColor = c.surfaceLow,
                focusedBorderColor = c.primary,
                unfocusedBorderColor = c.outlineVariant.copy(alpha = 0.4f),
                focusedTextColor = c.onSurface,
                unfocusedTextColor = c.onSurface,
                cursorColor = c.primary,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp)
            }
            query.isBlank() -> Placeholder(Icons.Filled.Search, stringResource(R.string.search_prompt), c)
            results.restaurants.isEmpty() && results.dishes.isEmpty() ->
                Placeholder(Icons.Filled.SearchOff, stringResource(R.string.no_results_for, query.trim()), c)
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                if (results.restaurants.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.restaurants), results.restaurants.size, c) }
                    items(results.restaurants, key = { "r_${it.id}" }) { r ->
                        RestaurantTile(r, c) { onOpenRestaurant(r.id) }
                        Spacer(Modifier.size(8.dp))
                    }
                    item { Spacer(Modifier.size(16.dp)) }
                }
                if (results.dishes.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.dishes), results.dishes.size, c) }
                    items(results.dishes, key = { "d_${it.id}" }) { m ->
                        DishTile(m, vm.restaurantNameFor(m), c) { onOpenRestaurant(m.restaurantId) }
                        Spacer(Modifier.size(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Placeholder(icon: ImageVector, message: String, c: VeloxColors) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = c.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(56.dp))
            Spacer(Modifier.size(12.dp))
            Text(message, color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SectionTitle(title: String, count: Int, c: VeloxColors) {
    Text(
        "${title.uppercase()} ($count)",
        color = c.onSurfaceVariant,
        fontFamily = Poppins,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
    )
}

@Composable
private fun RestaurantTile(r: Restaurant, c: VeloxColors, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surfaceLow).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(r.imageUrl, Icons.Filled.Restaurant, c)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.name, color = c.onSurface, fontFamily = Poppins, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.size(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Star, null, tint = c.primary, modifier = Modifier.size(13.dp))
                Spacer(Modifier.size(3.dp))
                Text("%.1f".format(r.rating), color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 12.sp)
            }
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = c.onSurfaceVariant, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun DishTile(m: MenuItem, restaurantName: String?, c: VeloxColors, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surfaceLow).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(m.imageUrl, Icons.Filled.Fastfood, c)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(m.name, color = c.onSurface, fontFamily = Poppins, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.size(3.dp))
            Text(restaurantName ?: m.category, color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.size(8.dp))
        Text("${m.price.toInt()} FDJ", color = c.primary, fontFamily = Poppins, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Thumb(url: String?, fallback: ImageVector, c: VeloxColors) {
    Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(c.surfaceHigh), contentAlignment = Alignment.Center) {
        if (url.isNullOrEmpty()) {
            Icon(fallback, null, tint = c.onSurfaceVariant, modifier = Modifier.size(24.dp))
        } else {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}
