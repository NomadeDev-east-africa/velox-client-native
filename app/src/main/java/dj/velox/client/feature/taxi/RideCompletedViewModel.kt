package dj.velox.client.feature.taxi

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dj.velox.client.data.remote.FavoriteDriversService
import dj.velox.client.data.remote.RatingService
import dj.velox.client.data.remote.RideService
import dj.velox.client.domain.model.Ride
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Fin de course + notation (port de la logique `tracking_screen` completion + rating). */
@HiltViewModel
class RideCompletedViewModel @Inject constructor(
    private val rideService: RideService,
    private val ratingService: RatingService,
    private val favoritesService: FavoriteDriversService,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val rideId: String = savedStateHandle["rideId"] ?: ""

    private val _ride = MutableStateFlow<Ride?>(null)
    val ride: StateFlow<Ride?> = _ride.asStateFlow()

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    private val _sent = MutableStateFlow(false)
    val sent: StateFlow<Boolean> = _sent.asStateFlow()

    init {
        viewModelScope.launch { _ride.value = runCatching { rideService.getRideById(rideId) }.getOrNull() }
    }

    /** Soumet la note + avis (et ajoute le chauffeur aux favoris si demandé). */
    fun submit(rating: Int, review: String?, addFavorite: Boolean, onDone: () -> Unit) {
        val r = _ride.value
        if (r == null || rating < 1) return onDone()
        viewModelScope.launch {
            _submitting.value = true
            runCatching {
                r.driverId?.let { driverId ->
                    ratingService.rateDriver(driverId, r.rideId, r.userId, rating, review)
                    if (addFavorite) {
                        favoritesService.addToFavorites(
                            userId = r.userId, driverId = driverId,
                            driverName = r.driverName ?: "Chauffeur",
                            driverPhotoUrl = r.driverPhotoUrl, driverPhone = r.driverPhone,
                            vehicleType = r.vehicleType, rideId = r.rideId,
                        )
                    }
                }
            }
            _submitting.value = false
            _sent.value = true
            onDone()
        }
    }
}
