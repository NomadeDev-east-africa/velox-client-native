package dj.velox.client.feature.taxi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dj.velox.client.data.local.VeloxLocalStore
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
    /**
     * Consentement à la divulgation proéminente « localisation ».
     * `null` = préférence pas encore relue depuis DataStore : l'UI n'affiche rien et
     * ne demande aucune permission tant qu'on est dans cet état, sinon la boîte de
     * dialogue système pourrait précéder l'explication (motif de rejet Google Play).
     */
    val disclosureAccepted: Boolean? = null,
)

/** Position courante de l'utilisateur (équivalent LocationNotifier, simplifié). */
@HiltViewModel
class LocationViewModel @Inject constructor(
    private val service: LocationService,
    private val localStore: VeloxLocalStore,
) : ViewModel() {

    private val _state = MutableStateFlow(LocationUiState())
    val state: StateFlow<LocationUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            localStore.locationDisclosureAcceptedFlow.collect { accepted ->
                _state.value = _state.value.copy(disclosureAccepted = accepted)
            }
        }
    }

    /** L'utilisateur a lu la divulgation et accepté : la permission peut être demandée. */
    fun onDisclosureAccepted() {
        viewModelScope.launch { localStore.setLocationDisclosureAccepted(true) }
    }

    /**
     * Refus de la divulgation : aucune permission n'est demandée. L'utilisateur garde
     * l'accès à l'app et saisit son départ à la main (Google interdit de bloquer l'app
     * sur un refus). Le choix n'est pas persisté, la divulgation sera reproposée.
     */
    fun onDisclosureDeclined() {
        _state.value = _state.value.copy(permissionDenied = true)
    }

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
