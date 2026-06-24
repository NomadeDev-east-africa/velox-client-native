package dj.velox.client.feature.location

import dj.velox.client.data.location.LocationService
import dj.velox.client.data.location.PlaceResult
import dj.velox.client.domain.model.LatLng
import dj.velox.client.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddressPickerViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()
    private val location: LocationService = mockk(relaxed = true)

    @Test
    fun `query change triggers a debounced search`() = runTest(mainRule.dispatcher.scheduler) {
        coEvery { location.getCurrentLocation() } returns LatLng(11.5, 43.1)
        coEvery { location.searchPlaces("pizza") } returns listOf(PlaceResult("Pizza Place", 11.5, 43.1))

        val vm = AddressPickerViewModel(location)
        vm.onQueryChange("pizza")
        advanceUntilIdle() // laisse passer le debounce de 500 ms (temps virtuel)

        coVerify(exactly = 1) { location.searchPlaces("pizza") }
        assertEquals(1, vm.results.value.size)
    }

    @Test
    fun `blank query is not searched`() = runTest(mainRule.dispatcher.scheduler) {
        coEvery { location.getCurrentLocation() } returns LatLng(11.5, 43.1)
        val vm = AddressPickerViewModel(location)
        vm.onQueryChange("   ")
        advanceUntilIdle()
        coVerify(exactly = 0) { location.searchPlaces(any()) }
    }

    @Test
    fun `setCenter updates the center without searching`() = runTest(mainRule.dispatcher.scheduler) {
        coEvery { location.getCurrentLocation() } returns LatLng(11.5, 43.1)
        val vm = AddressPickerViewModel(location)
        val target = LatLng(11.6, 43.2)
        vm.setCenter(target)
        assertEquals(target, vm.center.value)
    }
}
