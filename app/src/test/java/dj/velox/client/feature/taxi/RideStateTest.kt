package dj.velox.client.feature.taxi

import dj.velox.client.domain.model.Ride
import dj.velox.client.domain.model.RideLocation
import dj.velox.client.domain.model.RideStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vérifie les dérivations d'état de [ActiveRideState] (logique pure du RideViewModel). */
class RideStateTest {

    private fun ride(status: RideStatus, driverId: String? = null) = Ride(
        rideId = "rd1", userId = "u1",
        pickup = RideLocation(11.5, 43.1, "Départ"),
        destination = RideLocation(11.6, 43.2, "Arrivée"),
        status = status, requestedAt = 0L, driverId = driverId,
    )

    @Test
    fun `requested ride is active, waiting, without driver`() {
        val s = ActiveRideState(ride = ride(RideStatus.REQUESTED))
        assertTrue(s.hasActiveRide)
        assertTrue(s.isWaitingForDriver)
        assertFalse(s.hasDriver)
        assertFalse(s.isTerminated)
        assertEquals("rd1", s.rideId)
    }

    @Test
    fun `accepted ride has a driver and is not waiting`() {
        val s = ActiveRideState(ride = ride(RideStatus.ACCEPTED, driverId = "d1"))
        assertTrue(s.hasActiveRide)
        assertFalse(s.isWaitingForDriver)
        assertTrue(s.hasDriver)
    }

    @Test
    fun `completed, cancelled and no-driver are terminated`() {
        for (st in listOf(RideStatus.COMPLETED, RideStatus.CANCELLED, RideStatus.NO_DRIVER_AVAILABLE)) {
            val s = ActiveRideState(ride = ride(st))
            assertFalse("$st should not be active", s.hasActiveRide)
            assertTrue("$st should be terminated", s.isTerminated)
        }
    }

    @Test
    fun `null ride is terminated and inactive`() {
        val s = ActiveRideState(ride = null)
        assertFalse(s.hasActiveRide)
        assertTrue(s.isTerminated)
        assertNull(s.rideId)
    }
}
