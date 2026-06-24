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
        // Tarifs alignés sur mock_taxi_data.dart (Flutter).
        RideChoice(
            id = "standard",
            name = "Taxi Standard",
            type = RideType.STANDARD,
            seats = 4,
            basePrice = 200.0,
            pricePerKm = 50.0,
            estimatedArrivalTime = "5 min",
            description = "Économique et rapide",
        ),
        RideChoice(
            id = "comfort",
            name = "Taxi Confort",
            type = RideType.COMFORT,
            seats = 4,
            basePrice = 300.0,
            pricePerKm = 70.0,
            estimatedArrivalTime = "7 min",
            description = "Plus de confort",
            features = listOf("Climatisation"),
        ),
        RideChoice(
            id = "van",
            name = "Taxi Van",
            type = RideType.VAN,
            seats = 7,
            basePrice = 400.0,
            pricePerKm = 80.0,
            estimatedArrivalTime = "10 min",
            description = "Idéal pour les groupes",
            features = listOf("7 places"),
        ),
    )

    fun byId(id: String): RideChoice = choices.firstOrNull { it.id == id } ?: choices.first()
}
