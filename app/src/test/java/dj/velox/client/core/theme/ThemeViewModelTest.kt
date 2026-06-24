package dj.velox.client.core.theme

import dj.velox.client.data.local.VeloxLocalStore
import dj.velox.client.util.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()
    private val store: VeloxLocalStore = mockk(relaxed = true)

    @Test
    fun `setDarkMode delegates to the store`() = runTest {
        every { store.darkModeFlow } returns flowOf(true)
        val vm = ThemeViewModel(store)
        vm.setDarkMode(false)
        coVerify(exactly = 1) { store.setDarkMode(false) }
    }

    @Test
    fun `dark mode defaults to true before the store emits`() = runTest {
        every { store.darkModeFlow } returns flowOf(false)
        val vm = ThemeViewModel(store)
        // Sans collecteur actif, le StateFlow renvoie sa valeur initiale (dark forcé par défaut).
        assertTrue(vm.darkMode.value)
    }
}
