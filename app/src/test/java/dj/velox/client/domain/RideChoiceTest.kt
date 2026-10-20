package dj.velox.client.domain

import dj.velox.client.domain.model.RideType
import dj.velox.client.domain.model.TaxiCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

class RideChoiceTest {

    @Test
    fun `standard price = base + perKm x distance`() {
        val standard = TaxiCatalog.byId("standard")
        // base 500 + 200/km · 10 km = 2500
        assertEquals(2500.0, standard.calculatePrice(10.0), 0.0001)
    }

    @Test
    fun `zero distance returns base price`() {
        val comfort = TaxiCatalog.byId("comfort")
        assertEquals(comfort.basePrice, comfort.calculatePrice(0.0), 0.0001)
    }

    @Test
    fun `catalog has exactly two vehicles with client tariffs`() {
        assertEquals(2, TaxiCatalog.choices.size)
        val standard = TaxiCatalog.byId("standard")
        val comfort = TaxiCatalog.byId("comfort")
        assertEquals(500.0, standard.basePrice, 0.0); assertEquals(200.0, standard.pricePerKm, 0.0)
        assertEquals(650.0, comfort.basePrice, 0.0); assertEquals(200.0, comfort.pricePerKm, 0.0)
    }

    @Test
    fun `unknown id falls back to first choice`() {
        assertEquals(TaxiCatalog.choices.first(), TaxiCatalog.byId("does_not_exist"))
    }

    @Test
    fun `vehicleType is lowercase enum name`() {
        assertEquals("standard", TaxiCatalog.byId("standard").vehicleType)
        assertEquals(RideType.COMFORT, TaxiCatalog.byId("comfort").type)
    }
}
