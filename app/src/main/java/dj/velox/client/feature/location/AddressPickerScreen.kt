package dj.velox.client.feature.location

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dj.velox.client.R
import dj.velox.client.domain.model.LatLng
import dj.velox.client.feature.taxi.map.VeloxMap
import dj.velox.client.ui.theme.Inter
import dj.velox.client.ui.theme.Poppins
import dj.velox.client.ui.theme.VeloxColors
import dj.velox.client.ui.theme.VeloxTheme
import kotlinx.coroutines.delay

private val DJIBOUTI = LatLng(11.5880, 43.1450)

/**
 * Sélecteur d'adresse sur carte (port de `delivery_address_picker_screen` / destination picker).
 * Carte MapLibre + pin central + recherche Nominatim ; l'adresse du centre est géocodée à l'arrêt.
 * Réutilisé par le taxi (destination) et la livraison food.
 */
@Composable
fun AddressPickerScreen(
    title: String,
    onConfirm: (address: String, lat: Double, lng: Double) -> Unit,
    onBack: () -> Unit,
    vm: AddressPickerViewModel = hiltViewModel(),
) {
    val c = VeloxTheme.colors
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val searching by vm.searching.collectAsStateWithLifecycle()
    val vmCenter by vm.center.collectAsStateWithLifecycle()

    // Centre affiché : programmatique (GPS / résultat) → recadre la carte.
    var camCenter by remember { mutableStateOf<LatLng?>(null) }
    // Centre courant de la carte (déplacements manuels).
    var liveCenter by remember { mutableStateOf<LatLng?>(null) }
    var address by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(vmCenter) { vmCenter?.let { camCenter = it; liveCenter = it } }

    // Géocodage inverse du centre à l'arrêt (léger debounce).
    LaunchedEffect(liveCenter) {
        val ctr = liveCenter ?: return@LaunchedEffect
        delay(400)
        vm.reverseGeocode(ctr.latitude, ctr.longitude)?.let { address = it }
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        VeloxMap(
            center = camCenter ?: DJIBOUTI,
            onCenterIdle = { liveCenter = it },
            modifier = Modifier.fillMaxSize(),
        )

        // Pin central
        Icon(Icons.Filled.Place, null, tint = c.primary, modifier = Modifier.align(Alignment.Center).size(52.dp))

        // ── Barre de recherche (haut) ──
        Column(Modifier.statusBarsPadding().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(c.surfaceLow).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = c.onSurface, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.size(10.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = vm::onQueryChange,
                    placeholder = { Text(stringResource(R.string.search_address), color = c.onSurfaceVariant, fontFamily = Inter) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = c.primary) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = vm::clearQuery) { Icon(Icons.Filled.Clear, stringResource(R.string.clear_label), tint = c.onSurfaceVariant) }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = c.surfaceLow,
                        unfocusedContainerColor = c.surfaceLow,
                        focusedBorderColor = c.primary,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = c.onSurface,
                        unfocusedTextColor = c.onSurface,
                        cursorColor = c.primary,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }

            // Résultats
            if (query.isNotBlank() && (results.isNotEmpty() || searching)) {
                Spacer(Modifier.height(8.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surfaceLow)) {
                    if (searching) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = c.primary, strokeWidth = 2.dp)
                            Spacer(Modifier.size(10.dp))
                            Text(stringResource(R.string.searching_label), color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(Modifier.heightIn(max = 280.dp)) {
                            items(results, key = { "${it.latitude},${it.longitude},${it.name}" }) { place ->
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        val ll = LatLng(place.latitude, place.longitude)
                                        vm.setCenter(ll)
                                        address = place.name
                                        vm.clearQuery()
                                    }.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.primary.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.LocationOn, null, tint = c.primary, modifier = Modifier.size(22.dp))
                                    }
                                    Spacer(Modifier.size(12.dp))
                                    Text(place.name, color = c.onSurface, fontFamily = Inter, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Carte adresse + confirmer (bas) ──
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp)
                .clip(RoundedCornerShape(20.dp)).background(c.surface).padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.primary.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Place, null, tint = c.primary, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.selected_address), color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        address ?: stringResource(R.string.move_map_hint),
                        color = if (address != null) c.onSurface else c.onSurfaceVariant,
                        fontFamily = Poppins, fontSize = 15.sp, fontWeight = FontWeight.W600,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            val ctr = liveCenter
            val enabled = ctr != null
            val selectedAddr = stringResource(R.string.selected_address)
            Box(
                Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (enabled) c.primary else c.surfaceTop)
                    .clickable(enabled = enabled) {
                        onConfirm(address ?: selectedAddr, ctr!!.latitude, ctr.longitude)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(title, color = if (enabled) c.onPrimary else c.onSurfaceVariant, fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
