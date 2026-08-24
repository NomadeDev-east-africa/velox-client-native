package dj.velox.client.feature.taxi.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dj.velox.client.R
import dj.velox.client.ui.theme.VeloxColors
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap

/**
 * Boutons zoom avant/arrière superposés à une [VeloxMap], pilotant directement le
 * [MapLibreMap] récupéré via `onMapReady`. `map == null` (carte pas encore montée, cf. le
 * montage différé de [VeloxMap]) désactive silencieusement les clics plutôt que de planter.
 * Anime la caméra sur 200 ms — un saut sec serait plus dur à suivre pour l'œil qu'un pan/zoom
 * classique, notamment sur la carte d'itinéraire (2ᵉ carte) où le tracé bouge en même temps.
 */
@Composable
fun MapZoomControls(map: MapLibreMap?, c: VeloxColors, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        MapControlButton(Icons.Filled.Add, stringResource(R.string.map_zoom_in), c) {
            map?.easeCamera(CameraUpdateFactory.zoomIn(), 200)
        }
        Spacer(Modifier.height(8.dp))
        MapControlButton(Icons.Filled.Remove, stringResource(R.string.map_zoom_out), c) {
            map?.easeCamera(CameraUpdateFactory.zoomOut(), 200)
        }
    }
}

/** Bouton rond « centrer sur ma position » — même gabarit que [MapZoomControls]. */
@Composable
fun MapRecenterButton(c: VeloxColors, modifier: Modifier = Modifier, onClick: () -> Unit) {
    MapControlButton(Icons.Filled.MyLocation, stringResource(R.string.taxi_recenter), c, modifier, onClick)
}

@Composable
private fun MapControlButton(
    icon: ImageVector,
    contentDescription: String,
    c: VeloxColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier.size(40.dp).clip(CircleShape).background(c.bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = c.primary, modifier = Modifier.size(20.dp))
    }
}
