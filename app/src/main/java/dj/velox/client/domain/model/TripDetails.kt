package dj.velox.client.domain.model

import kotlin.math.roundToInt

/** Détails complets d'un trajet avant confirmation (miroir trip_details.dart). */
data class TripDetails(
    val departure: LatLng,
    val destination: LatLng,
    val departureAddress: String,
    val destinationAddress: String,
    val distance: Double,        // km
    val duration: Int,           // minutes
    val selectedRide: RideChoice,
) {
    val totalPrice: Double get() = selectedRide.calculatePrice(distance)

    val formattedDistance: String get() = "%.1f km".format(distance)

    val formattedDuration: String
        get() = if (duration < 60) "$duration min"
        else "${duration / 60}h ${duration % 60}min"

    val formattedPrice: String get() = "${totalPrice.roundToInt()} FDJ"
}
