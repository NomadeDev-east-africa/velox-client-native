package dj.velox.client.feature.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dj.velox.client.data.location.LocationService
import dj.velox.client.data.location.RouteResult
import dj.velox.client.domain.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Récupère l'itinéraire routier réel (OpenRouteService) entre deux points et l'expose à l'UI.
 * Partagé par les écrans taxi (home/confirm/suivi) et le suivi livreur food. Mémorise le dernier
 * couple départ→arrivée pour éviter de recharger inutilement (et économiser le quota ORS).
 */
@HiltViewModel
class RouteViewModel @Inject constructor(
    private val location: LocationService,
) : ViewModel() {

    private val _route = MutableStateFlow<RouteResult?>(null)
    val route: StateFlow<RouteResult?> = _route.asStateFlow()

    private var lastKey: String? = null

    /** Charge le tracé start→end (idempotent sur un même couple). */
    fun load(start: LatLng, end: LatLng) {
        val key = "${start.latitude},${start.longitude}>${end.latitude},${end.longitude}"
        if (key == lastKey) return
        lastKey = key
        viewModelScope.launch {
            _route.value = runCatching { location.getRoute(start, end) }.getOrNull()
        }
    }
}
