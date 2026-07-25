package dj.velox.client.feature.food

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.SearchOff
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
import dj.velox.client.ui.theme.Inter
import dj.velox.client.ui.theme.Poppins
import dj.velox.client.ui.theme.VeloxColors
import dj.velox.client.ui.theme.VeloxTheme

/**
 * Recherche **scopée au menu du restaurant courant** (nom + catégorie du plat). Ouverte
 * depuis la loupe de la page détail restaurant — distincte de la recherche globale.
 */
@Composable
fun RestaurantMenuSearchScreen(
    onOpenItem: (restaurantId: String, menuId: String) -> Unit,
    onBack: () -> Unit,
    vm: RestaurantMenuSearchViewModel = hiltViewModel(),
) {
    val c = VeloxTheme.colors
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val restaurantName by vm.restaurantName.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = c.onSurface)
            }
            Spacer(Modifier.size(4.dp))
            Text(
                restaurantName ?: stringResource(R.string.search),
                color = c.onSurface, fontFamily = Poppins, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = vm::onQueryChange,
            placeholder = { Text(stringResource(R.string.search_hint_dish), color = c.onSurfaceVariant, fontFamily = Inter) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(),
            leadingIcon = { Icon(Icons.Filled.Fastfood, null, tint = c.onSurfaceVariant) },
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

        if (results.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.SearchOff, null, tint = c.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(56.dp))
                    Spacer(Modifier.size(12.dp))
                    Text(stringResource(R.string.no_results), color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                items(results, key = { it.id }) { m ->
                    DishRow(m, c) { onOpenItem(m.restaurantId, m.id) }
                    Spacer(Modifier.size(8.dp))
                }
            }
        }
    }
}

@Composable
private fun DishRow(m: MenuItem, c: VeloxColors, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surfaceLow).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(c.surfaceHigh), contentAlignment = Alignment.Center) {
            if (m.imageUrl.isNullOrEmpty()) {
                Icon(Icons.Filled.Fastfood, null, tint = c.onSurfaceVariant, modifier = Modifier.size(24.dp))
            } else {
                AsyncImage(model = m.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(m.name, color = c.onSurface, fontFamily = Poppins, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.size(3.dp))
            Text(m.category, color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.size(8.dp))
        Text("${m.price.toInt()} FDJ", color = c.primary, fontFamily = Poppins, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
