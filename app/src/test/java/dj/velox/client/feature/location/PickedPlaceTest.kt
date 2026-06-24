package dj.velox.client.feature.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PickedPlaceTest {

    @Test
    fun `encode then decode round-trips`() {
        val raw = PickedPlace.encode(11.588, 43.145, "Cité de l'aviation, Djibouti")
        val decoded = PickedPlace.decode(raw)!!
        assertEquals(11.588, decoded.lat, 0.0)
        assertEquals(43.145, decoded.lng, 0.0)
        assertEquals("Cité de l'aviation, Djibouti", decoded.address)
    }

    @Test
    fun `address may contain the separator and stays intact`() {
        // limit=3 sur le split → l'adresse garde ses éventuels '|'
        val raw = PickedPlace.encode(1.0, 2.0, "A|B|C")
        val decoded = PickedPlace.decode(raw)!!
        assertEquals("A|B|C", decoded.address)
    }

    @Test
    fun `decode returns null on malformed input`() {
        assertNull(PickedPlace.decode(null))
        assertNull(PickedPlace.decode(""))
        assertNull(PickedPlace.decode("only-two|parts"))
        assertNull(PickedPlace.decode("not-a-number|2.0|addr"))
    }
}
