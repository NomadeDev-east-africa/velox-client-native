package dj.velox.client.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import dj.velox.client.domain.model.GeoUtils
import dj.velox.client.domain.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** Résultat de recherche d'adresse (miroir PlaceResult Flutter). */
data class PlaceResult(val name: String, val latitude: Double, val longitude: Double)

@Serializable
private data class NominatimResult(val display_name: String, val lat: String, val lon: String)

@Serializable
private data class NominatimReverse(val display_name: String? = null)

/** Itinéraire routier (port de `RouteResult` Flutter). [points] = tracé complet (lat/lng). */
data class RouteResult(val points: List<LatLng>, val distanceKm: Double, val durationMin: Int)

// ── Réponse GeoJSON OpenRouteService (/v2/directions/driving-car/geojson) ──
@Serializable private data class OrsResponse(val features: List<OrsFeature> = emptyList())
@Serializable private data class OrsFeature(val geometry: OrsGeometry = OrsGeometry(), val properties: OrsProperties? = null)
@Serializable private data class OrsGeometry(val coordinates: List<List<Double>> = emptyList())
@Serializable private data class OrsProperties(val summary: OrsSummary? = null)
@Serializable private data class OrsSummary(val distance: Double = 0.0, val duration: Double = 0.0)

/**
 * Localisation GPS (équivalent location_service.dart).
 * FusedLocationProvider pour le GPS, Geocoder Android pour le géocodage inverse,
 * Location.distanceBetween pour les distances. Les permissions sont demandées
 * côté UI ; les appels @SuppressLint lèveront SecurityException sinon (à attraper).
 */
@Singleton
class LocationService @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val fused = LocationServices.getFusedLocationProviderClient(context)
    private val nominatimJson = Json { ignoreUnknownKeys = true }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): LatLng {
        val loc = fused.getCurrentLocation(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            CancellationTokenSource().token,
        ).await() ?: fused.lastLocation.await()
        ?: throw IllegalStateException("Position GPS indisponible")
        return LatLng(loc.latitude, loc.longitude)
    }

    @SuppressLint("MissingPermission")
    fun locationUpdates(intervalMs: Long = 5_000L): Flow<LatLng> = callbackFlow {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY, intervalMs,
        ).setMinUpdateDistanceMeters(15f).build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(LatLng(it.latitude, it.longitude)) }
            }
        }
        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { fused.removeLocationUpdates(callback) }
    }.distinctUntilChanged()

    /**
     * Géocodage inverse → adresse lisible complète (null si indisponible).
     *
     * Port de `LocationService.getAddressFromCoordinates` (Flutter) : on interroge
     * **Nominatim `/reverse`** et on renvoie le `display_name` complet (rue, quartier,
     * ville…). Le `Geocoder` Android ne sert que de repli — il renvoyait des adresses
     * trop grossières à Djibouti (« Djibouti, Djibouti »).
     */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): String? =
        withContext(Dispatchers.IO) {
            nominatimReverse(latitude, longitude) ?: androidReverse(latitude, longitude)
        }

    /** Géocodage inverse via Nominatim OSM (adresse détaillée, comme l'app Flutter). */
    private fun nominatimReverse(latitude: Double, longitude: Double): String? = runCatching {
        val url = URL(
            "https://nominatim.openstreetmap.org/reverse" +
                "?lat=$latitude&lon=$longitude&format=json&addressdetails=1",
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", "VeloxApp/1.0 (dj.velox.client)")
            setRequestProperty("Accept-Language", "fr")
            connectTimeout = 8_000
            readTimeout = 8_000
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        nominatimJson.decodeFromString<NominatimReverse>(body)
            .display_name?.takeIf { it.isNotBlank() }
    }.getOrNull()

    /** Repli : Geocoder Android (grossier mais hors-ligne). */
    private fun androidReverse(latitude: Double, longitude: Double): String? = runCatching {
        @Suppress("DEPRECATION")
        Geocoder(context, Locale.getDefault())
            .getFromLocation(latitude, longitude, 1)
            ?.firstOrNull()
            ?.let { a ->
                listOfNotNull(
                    a.thoroughfare,
                    a.subLocality ?: a.locality,
                    a.countryName,
                ).joinToString(", ").ifEmpty { null }
            }
    }.getOrNull()

    /**
     * Recherche d'adresse (géocodage direct) via Nominatim OSM, biaisée Djibouti.
     * Port de `LocationService.searchPlaces`. Le debounce/rate-limit est géré côté appelant.
     */
    suspend fun searchPlaces(query: String): List<PlaceResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            val q = URLEncoder.encode(query.trim(), "UTF-8")
            val url = URL("https://nominatim.openstreetmap.org/search?q=$q&format=json&limit=8&countrycodes=dj&addressdetails=0")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", "VeloxApp/1.0 (dj.velox.client)")
                connectTimeout = 8_000
                readTimeout = 8_000
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            nominatimJson.decodeFromString<List<NominatimResult>>(body)
                .mapNotNull { r ->
                    val lat = r.lat.toDoubleOrNull(); val lon = r.lon.toDoubleOrNull()
                    if (lat != null && lon != null) PlaceResult(r.display_name, lat, lon) else null
                }
        }.getOrDefault(emptyList())
    }

    /** Distance en kilomètres entre deux points. */
    fun distanceKm(a: LatLng, b: LatLng): Double {
        val result = FloatArray(1)
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, result)
        return result[0] / 1000.0
    }

    /**
     * Itinéraire routier réel via **OpenRouteService** (port de `LocationService.getRoute` Flutter).
     * POST `/v2/directions/driving-car/geojson` → tracé GeoJSON (suit les routes) + distance/durée.
     * Repli ligne droite en cas d'erreur réseau / quota / clé absente.
     */
    suspend fun getRoute(start: LatLng, end: LatLng): RouteResult = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("https://api.openrouteservice.org/v2/directions/driving-car/geojson")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Authorization", dj.velox.client.BuildConfig.ORS_API_KEY)
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json, application/geo+json")
                connectTimeout = 15_000
                readTimeout = 15_000
            }
            val payload =
                """{"coordinates":[[${start.longitude},${start.latitude}],[${end.longitude},${end.latitude}]]}"""
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val resp = conn.inputStream.bufferedReader().use { it.readText() }
            val feature = nominatimJson.decodeFromString<OrsResponse>(resp).features.firstOrNull()
            val pts = feature?.geometry?.coordinates.orEmpty()
                .mapNotNull { if (it.size >= 2) LatLng(it[1], it[0]) else null }
            if (pts.size >= 2) {
                val summary = feature?.properties?.summary
                RouteResult(
                    points = pts,
                    distanceKm = (summary?.distance ?: 0.0) / 1000.0,
                    durationMin = ((summary?.duration ?: 0.0) / 60.0).roundToInt().coerceAtLeast(1),
                )
            } else {
                null
            }
        }.getOrNull() ?: simpleRoute(start, end)
    }

    /** Repli : segment droit + estimation (équivalent `_getSimpleRoute` Flutter). */
    private fun simpleRoute(start: LatLng, end: LatLng): RouteResult {
        val d = distanceKm(start, end)
        return RouteResult(listOf(start, end), d, GeoUtils.estimatedDurationMin(d))
    }
}
