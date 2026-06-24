package dj.velox.client.domain

import dj.velox.client.domain.model.GeoUtils
import dj.velox.client.domain.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoUtilsTest {

    @Test
    fun `distance between identical points is zero`() {
        val p = LatLng(11.5880, 43.1450)
        assertEquals(0.0, GeoUtils.distanceKm(p, p), 0.0001)
    }

    @Test
    fun `one degree of longitude at equator is about 111 km`() {
        val d = GeoUtils.distanceKm(LatLng(0.0, 0.0), LatLng(0.0, 1.0))
        assertEquals(111.19, d, 0.5)
    }

    @Test
    fun `distance is symmetric`() {
        val a = LatLng(11.59, 43.14)
        val b = LatLng(11.53, 43.23)
        assertEquals(GeoUtils.distanceKm(a, b), GeoUtils.distanceKm(b, a), 0.0001)
    }

    @Test
    fun `estimated duration at 25 kmh`() {
        // 25 km à 25 km/h = 60 min
        assertEquals(60, GeoUtils.estimatedDurationMin(25.0))
        // 12,5 km = 30 min
        assertEquals(30, GeoUtils.estimatedDurationMin(12.5))
    }

    @Test
    fun `estimated duration is at least one minute`() {
        assertEquals(1, GeoUtils.estimatedDurationMin(0.0))
        assertTrue(GeoUtils.estimatedDurationMin(0.1) >= 1)
    }
}
