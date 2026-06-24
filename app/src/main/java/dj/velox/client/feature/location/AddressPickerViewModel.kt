package dj.velox.client.feature.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dj.velox.client.data.location.LocationService
import dj.velox.client.data.location.PlaceResult
import dj.velox.client.domain.model.LatLng
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Adresse choisie renvoyée par le picker (encodée en `lat|lng|adresse` via savedStateHandle). */
data class PickedPlace(val lat: Double, val lng: Double, val address: String) {
    companion object {
        const val KEY = "picked_address"
        fun decode(raw: String?): PickedPlace? {
            val parts = raw?.split('|', limit = 3) ?: return null
            if (parts.size < 3) return null
            val lat = parts[0].toDoubleOrNull() ?: return null
            val lng = parts[1].toDoubleOrNull() ?: return null
            return PickedPlace(lat, lng, parts[2])
        }
        fun encode(lat: Double, lng: Double, address: String): String = "$lat|$lng|$address"
    }
}

/** Recherche d'adresse (Nominatim, debounce 500 ms) + géocodage inverse pour le picker carte. */
@OptIn(FlowPreview::class)
@HiltViewModel
class AddressPickerViewModel @Inject constructor(
    private val location: LocationService,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<PlaceResult>>(emptyList())
    val results: StateFlow<List<PlaceResult>> = _results.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _center = MutableStateFlow<LatLng?>(null)
    val center: StateFlow<LatLng?> = _center.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { location.getCurrentLocation() }.onSuccess { _center.value = it }
        }
        viewModelScope.launch {
            _query.debounce(500).collect { q ->
                if (q.isBlank()) {
                    _results.value = emptyList()
                } else {
                    _searching.value = true
                    _results.value = location.searchPlaces(q)
                    _searching.value = false
                }
            }
        }
    }

    fun onQueryChange(value: String) { _query.value = value }
    fun clearQuery() { _query.value = ""; _results.value = emptyList() }

    /** Recentre programmatique (sélection d'un résultat) — sans relancer une recherche. */
    fun setCenter(latLng: LatLng) { _center.value = latLng }

    suspend fun reverseGeocode(lat: Double, lng: Double): String? = location.reverseGeocode(lat, lng)
}
