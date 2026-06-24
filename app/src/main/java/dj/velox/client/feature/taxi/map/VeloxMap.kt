package dj.velox.client.feature.taxi.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dj.velox.client.domain.model.LatLng
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.android.geometry.LatLng as MlLatLng

/**
 * Tuiles raster CartoDB (mêmes que l'app Flutter `nomade_client`, sans clé API) :
 * `dark_all` en thème sombre (colle au design Kinetic Monolith), `light_all` en clair.
 * Le style démo MapLibre n'a aucune donnée au-delà du zoom monde → carte vide en ville.
 */
private fun cartoStyleJson(dark: Boolean): String {
    val variant = if (dark) "dark_all" else "light_all"
    return """
    {
      "version": 8,
      "sources": {
        "carto": {
          "type": "raster",
          "tiles": [
            "https://a.basemaps.cartocdn.com/$variant/{z}/{x}/{y}@2x.png",
            "https://b.basemaps.cartocdn.com/$variant/{z}/{x}/{y}@2x.png",
            "https://c.basemaps.cartocdn.com/$variant/{z}/{x}/{y}@2x.png",
            "https://d.basemaps.cartocdn.com/$variant/{z}/{x}/{y}@2x.png"
          ],
          "tileSize": 512,
          "minzoom": 0,
          "maxzoom": 20,
          "attribution": "© OpenStreetMap © CARTO"
        }
      },
      "layers": [
        { "id": "background", "type": "background", "paint": { "background-color": "${if (dark) "#0E0E0E" else "#F5F5F5"}" } },
        { "id": "carto", "type": "raster", "source": "carto" }
      ]
    }
    """.trimIndent()
}

private const val ROUTE_SRC = "velox-route-src"
private const val ROUTE_LAYER = "velox-route-layer"
private const val POINTS_SRC = "velox-points-src"
private const val POINTS_LAYER = "velox-points-layer"

/**
 * Carte MapLibre intégrée à Compose (remplace flutter_map/OSM).
 * - [center] : caméra centrée sur ce point.
 * - [onCenterIdle] : remonte le centre après déplacement (pin fixe → choix de destination).
 * - [routeStart]/[routeEnd] : si les deux sont fournis, trace une ligne + 2 marqueurs
 *   (départ vert, arrivée bleu) entre les points.
 */
@Composable
fun VeloxMap(
    center: LatLng?,
    modifier: Modifier = Modifier,
    zoom: Double = 14.0,
    routeStart: LatLng? = null,
    routeEnd: LatLng? = null,
    routePolyline: List<LatLng>? = null,
    onMapReady: (MapLibreMap) -> Unit = {},
    onCenterIdle: ((LatLng) -> Unit)? = null,
) {
    val darkTiles = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // Montage différé : on rend d'abord un placeholder léger (couleur de surface), puis on
    // monte la MapView (init OpenGL coûteuse) au frame SUIVANT. La composition/scroll initiale
    // n'est donc plus bloquée par l'init GL — c'est la cause des freezes ~800 ms à l'ouverture.
    var mounted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { } // laisse le 1er frame se dessiner
        mounted = true
    }
    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .background(if (darkTiles) Color(0xFF0E0E0E) else Color(0xFFF5F5F5)),
        )
        if (mounted) {
            VeloxMapContent(
                center = center,
                modifier = Modifier.fillMaxSize(),
                zoom = zoom,
                routeStart = routeStart,
                routeEnd = routeEnd,
                routePolyline = routePolyline,
                darkTiles = darkTiles,
                onMapReady = onMapReady,
                onCenterIdle = onCenterIdle,
            )
        }
    }
}

@Composable
private fun VeloxMapContent(
    center: LatLng?,
    modifier: Modifier,
    zoom: Double,
    routeStart: LatLng?,
    routeEnd: LatLng?,
    routePolyline: List<LatLng>?,
    darkTiles: Boolean,
    onMapReady: (MapLibreMap) -> Unit,
    onCenterIdle: ((LatLng) -> Unit)?,
) {
    val context = LocalContext.current
    val styleJson = remember(darkTiles) { cartoStyleJson(darkTiles) }
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    var mlMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var mlStyle by remember { mutableStateOf<Style?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = {
            mapView.getMapAsync { map ->
                map.setStyle(Style.Builder().fromJson(styleJson)) { style ->
                    mlMap = map
                    // Sources + couches route/marqueurs (vides au départ).
                    style.addSource(GeoJsonSource(ROUTE_SRC))
                    style.addLayer(
                        LineLayer(ROUTE_LAYER, ROUTE_SRC).withProperties(
                            PropertyFactory.lineColor("#2D7FF9"),
                            PropertyFactory.lineWidth(4f),
                            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                        ),
                    )
                    style.addSource(GeoJsonSource(POINTS_SRC))
                    style.addLayer(
                        CircleLayer(POINTS_LAYER, POINTS_SRC).withProperties(
                            PropertyFactory.circleRadius(8f),
                            PropertyFactory.circleColor(Expression.get("color")),
                            PropertyFactory.circleStrokeColor("#FFFFFF"),
                            PropertyFactory.circleStrokeWidth(2.5f),
                        ),
                    )
                    mlStyle = style

                    center?.let {
                        map.cameraPosition = CameraPosition.Builder()
                            .target(MlLatLng(it.latitude, it.longitude)).zoom(zoom).build()
                    }
                    onMapReady(map)
                    if (onCenterIdle != null) {
                        map.addOnCameraIdleListener {
                            map.cameraPosition.target?.let { t -> onCenterIdle(LatLng(t.latitude, t.longitude)) }
                        }
                    }
                }
            }
            mapView
        },
        modifier = modifier,
    )

    // Recentre la caméra quand [center] change (ex. position GPS obtenue).
    LaunchedEffect(center, mlMap) {
        val map = mlMap ?: return@LaunchedEffect
        val c = center ?: return@LaunchedEffect
        map.cameraPosition = CameraPosition.Builder()
            .target(MlLatLng(c.latitude, c.longitude)).zoom(zoom).build()
    }

    // Met à jour le tracé + marqueurs quand la route change.
    // Tracé = polyline routière ORS si fournie (≥ 2 points), sinon segment droit départ→arrivée.
    LaunchedEffect(routeStart, routeEnd, routePolyline, mlStyle) {
        val style = mlStyle ?: return@LaunchedEffect
        val routeSrc = style.getSourceAs<GeoJsonSource>(ROUTE_SRC)
        val pointsSrc = style.getSourceAs<GeoJsonSource>(POINTS_SRC)
        val s = routeStart
        val e = routeEnd
        val line: List<LatLng>? = when {
            routePolyline != null && routePolyline.size >= 2 -> routePolyline
            s != null && e != null -> listOf(s, e)
            else -> null
        }
        routeSrc?.setGeoJson(
            if (line != null) {
                LineString.fromLngLats(line.map { Point.fromLngLat(it.longitude, it.latitude) })
            } else {
                LineString.fromLngLats(emptyList())
            },
        )
        if (s != null && e != null) {
            pointsSrc?.setGeoJson(
                FeatureCollection.fromFeatures(
                    listOf(
                        Feature.fromGeometry(Point.fromLngLat(s.longitude, s.latitude)).apply { addStringProperty("color", "#22C55E") },
                        Feature.fromGeometry(Point.fromLngLat(e.longitude, e.latitude)).apply { addStringProperty("color", "#2D7FF9") },
                    ),
                ),
            )
        } else {
            pointsSrc?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        }
    }
}
