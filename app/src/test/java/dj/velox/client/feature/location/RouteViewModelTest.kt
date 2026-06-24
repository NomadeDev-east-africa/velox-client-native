package dj.velox.client.feature.location

import dj.velox.client.data.location.LocationService
import dj.velox.client.data.location.RouteResult
import dj.velox.client.domain.model.LatLng
import dj.velox.client.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RouteViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()
    private val location: LocationService = mockk()

    private val a = LatLng(11.59, 43.14)
    private val b = LatLng(11.53, 43.23)
    private val result = RouteResult(points = listOf(a, b), distanceKm = 7.5, durationMin = 18)

    @Test
    fun `load fetches and exposes the route`() = runTest {
        coEvery { location.getRoute(a, b) } returns result
        val vm = RouteViewModel(location)
        vm.load(a, b)
        assertEquals(result, vm.route.value)
        coVerify(exactly = 1) { location.getRoute(a, b) }
    }

    @Test
    fun `load is idempotent for the same start-end pair`() = runTest {
        coEvery { location.getRoute(a, b) } returns result
        val vm = RouteViewModel(location)
        vm.load(a, b)
        vm.load(a, b)
        vm.load(a, b)
        coVerify(exactly = 1) { location.getRoute(a, b) }
    }

    @Test
    fun `load refetches when the destination changes`() = runTest {
        val c = LatLng(11.60, 43.10)
        coEvery { location.getRoute(a, b) } returns result
        coEvery { location.getRoute(a, c) } returns result.copy(distanceKm = 3.0)
        val vm = RouteViewModel(location)
        vm.load(a, b)
        vm.load(a, c)
        coVerify(exactly = 1) { location.getRoute(a, b) }
        coVerify(exactly = 1) { location.getRoute(a, c) }
    }
}
