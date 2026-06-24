package dj.velox.client.domain

import dj.velox.client.domain.model.RideType
import dj.velox.client.domain.model.TaxiCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

class RideChoiceTest {

    @Test
    fun `standard price = base + perKm x distance`() {
        val standard = TaxiCatalog.byId("standard")
        // base 200 + 50/km · 10 km = 700
        assertEquals(700.0, standard.calculatePrice(10.0), 0.0001)
    }

    @Test
    fun `zero distance returns base price`() {
        val comfort = TaxiCatalog.byId("comfort")
        assertEquals(comfort.basePrice, comfort.calculatePrice(0.0), 0.0001)
    }

    @Test
    fun `catalog tariffs match Flutter mock data`() {
        val standard = TaxiCatalog.byId("standard")
        val comfort = TaxiCatalog.byId("comfort")
        val van = TaxiCatalog.byId("van")
        assertEquals(200.0, standard.basePrice, 0.0); assertEquals(50.0, standard.pricePerKm, 0.0)
        assertEquals(300.0, comfort.basePrice, 0.0); assertEquals(70.0, comfort.pricePerKm, 0.0)
        assertEquals(400.0, van.basePrice, 0.0); assertEquals(80.0, van.pricePerKm, 0.0)
    }

    @Test
    fun `unknown id falls back to first choice`() {
        assertEquals(TaxiCatalog.choices.first(), TaxiCatalog.byId("does_not_exist"))
    }

    @Test
    fun `vehicleType is lowercase enum name`() {
        assertEquals("van", TaxiCatalog.byId("van").vehicleType)
        assertEquals(RideType.VAN, TaxiCatalog.byId("van").type)
    }
}
