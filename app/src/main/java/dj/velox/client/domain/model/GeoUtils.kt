package dj.velox.client.domain.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Utilitaires géographiques (distance, durée estimée). */
object GeoUtils {
    private const val EARTH_RADIUS_KM = 6371.0

    /** Distance Haversine en kilomètres entre deux points. */
    fun distanceKm(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            sin(dLon / 2) * sin(dLon / 2) * cos(lat1) * cos(lat2)
        return EARTH_RADIUS_KM * 2 * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Durée estimée (min) pour une distance, à vitesse moyenne urbaine (~25 km/h). */
    fun estimatedDurationMin(distanceKm: Double, avgSpeedKmh: Double = 25.0): Int =
        ((distanceKm / avgSpeedKmh) * 60).roundToInt().coerceAtLeast(1)
}
