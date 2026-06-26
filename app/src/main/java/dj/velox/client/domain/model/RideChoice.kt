package dj.velox.client.domain.model

/** Type de véhicule (miroir ride_choice.dart). */
enum class RideType { STANDARD, COMFORT, VAN, PREMIUM }

/** Choix de véhicule pour une course. */
data class RideChoice(
    val id: String,
    val name: String,
    val type: RideType,
    val seats: Int,
    val basePrice: Double,
    val pricePerKm: Double,
    val estimatedArrivalTime: String? = null,
    val description: String? = null,
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
            name = "Taxi Standard",
            type = RideType.STANDARD,
            seats = 4,
            basePrice = 500.0,
            pricePerKm = 50.0,
            estimatedArrivalTime = "5 min",
            description = "Économique et rapide",
        ),
        RideChoice(
            id = "comfort",
            name = "Taxi Confort",
            type = RideType.COMFORT,
            seats = 4,
            basePrice = 650.0,
            pricePerKm = 70.0,
            estimatedArrivalTime = "7 min",
            description = "Plus de confort",
            features = listOf("Climatisation"),
        ),
    )

    fun byId(id: String): RideChoice = choices.firstOrNull { it.id == id } ?: choices.first()
}
