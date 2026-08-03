package dj.velox.client.domain.model

import androidx.annotation.StringRes
import dj.velox.client.R

/** Type de véhicule (miroir ride_choice.dart). */
enum class RideType { STANDARD, COMFORT, VAN, PREMIUM }

/**
 * Choix de véhicule pour une course.
 *
 * Le libellé et la description sont des **références de ressources** ([nameRes],
 * [descriptionRes]) et non des chaînes : le catalogue est un `object` statique, un texte en dur
 * ici resterait en français dans les quatre autres langues de l'app.
 */
data class RideChoice(
    val id: String,
    @param:StringRes val nameRes: Int,
    val type: RideType,
    val seats: Int,
    val basePrice: Double,
    val pricePerKm: Double,
    val estimatedArrivalTime: String? = null,
    @param:StringRes val descriptionRes: Int? = null,
    val features: List<String> = emptyList(),
) {
    /** Prix total estimé selon la distance (FDJ). */
    fun calculatePrice(distanceKm: Double): Double = basePrice + pricePerKm * distanceKm

    /** Clé Firestore du type de véhicule (vehicleType côté backend). */
    val vehicleType: String get() = type.name.lowercase()
}

/**
 * Catalogue des véhicules disponibles (équivalent mock_taxi_data.dart).
 * Tarifs en FDJ. À terme, ces valeurs pourront venir de Firestore (config).
 */
object TaxiCatalog {
    val choices: List<RideChoice> = listOf(
        // 2 véhicules seulement (demande client) : prix de base 500 / 650 FDJ.
        RideChoice(
            id = "standard",
            nameRes = R.string.taxi_vehicle_standard,
            type = RideType.STANDARD,
            seats = 4,
            basePrice = 500.0,
            pricePerKm = 145.0,
            estimatedArrivalTime = "5 min",
            descriptionRes = R.string.taxi_vehicle_standard_desc,
        ),
        RideChoice(
            id = "comfort",
            nameRes = R.string.taxi_vehicle_comfort,
            type = RideType.COMFORT,
            seats = 4,
            basePrice = 650.0,
            pricePerKm = 145.0,
            estimatedArrivalTime = "7 min",
            descriptionRes = R.string.taxi_vehicle_comfort_desc,
            features = listOf("Climatisation"),
        ),
    )

    fun byId(id: String): RideChoice = choices.firstOrNull { it.id == id } ?: choices.first()
}
