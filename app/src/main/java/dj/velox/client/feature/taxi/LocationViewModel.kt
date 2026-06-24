package dj.velox.client.feature.taxi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dj.velox.client.data.location.LocationService
import dj.velox.client.domain.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LocationUiState(
    val position: LatLng? = null,
    val address: String? = null,
    val isLoading: Boolean = false,
    val permissionDenied: Boolean = false,
    val error: String? = null,
)

/** Position courante de l'utilisateur (équivalent LocationNotifier, simplifié). */
@HiltViewModel
class LocationViewModel @Inject constructor(
    private val service: LocationService,
) : ViewModel() {

    private val _state = MutableStateFlow(LocationUiState())
    val state: StateFlow<LocationUiState> = _state.asStateFlow()

    /** À appeler une fois la permission accordée. */
    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, permissionDenied = false)
            try {
                val pos = service.getCurrentLocation()
                _state.value = _state.value.copy(position = pos, isLoading = false)
                val address = service.reverseGeocode(pos.latitude, pos.longitude)
                _state.value = _state.value.copy(address = address)
            } catch (e: SecurityException) {
                _state.value = _state.value.copy(isLoading = false, permissionDenied = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun onPermissionDenied() {
        _state.value = _state.value.copy(permissionDenied = true, isLoading = false)
    }
}
