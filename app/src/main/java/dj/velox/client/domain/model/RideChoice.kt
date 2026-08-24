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
    /** Distance (km) déjà incluse dans [basePrice] — le tarif au km ne s'applique qu'au-delà. */
    val includedKm: Double = 0.0,
    val estimatedArrivalTime: String? = null,
    @param:StringRes val descriptionRes: Int? = null,
    val features: List<String> = emptyList(),
) {
    /** Prix total estimé selon la distance (FDJ) : forfait de base + km au-delà de [includedKm]. */
    fun calculatePrice(distanceKm: Double): Double = basePrice + pricePerKm * (distanceKm - includedKm).coerceAtLeast(0.0)

    /** Clé Firestore du type de véhicule (vehicleType côté backend). */
    val vehicleType: String get() = type.name.lowercase()
}

/**
 * Tarif d'un type de véhicule, tel que peut le fournir la config Firestore
 * `config/taxiPricing` (pilotée depuis l'admin). Miroir de `DEFAULT_TAXI_PRICING`
 * côté Cloud Function (nomade_client/functions/index.js) — le tarif effectif y est de
 * toute façon revalidé côté serveur, cette valeur ne sert qu'à l'estimation affichée.
 */
data class TaxiPricingTier(val basePrice: Double, val includedKm: Double, val pricePerKm: Double)

/**
 * Catalogue des véhicules disponibles (équivalent mock_taxi_data.dart).
 * Tarifs en FDJ. Valeurs par défaut ci-dessous ; [TaxiCatalog.withPricing] permet de les
 * surcharger avec la config Firestore live (voir `TaxiPricingViewModel`).
 */
object TaxiCatalog {

    /** Réapplique un tarif live (Firestore) sur le catalogue statique ; ignore les entrées
     * inconnues et conserve les valeurs par défaut pour tout type de véhicule non présent
     * dans [pricing] (doc absent, partiel, ou en erreur). */
    fun withPricing(pricing: Map<String, TaxiPricingTier>): List<RideChoice> = choices.map { choice ->
        val tier = pricing[choice.id] ?: return@map choice
        choice.copy(basePrice = tier.basePrice, includedKm = tier.includedKm, pricePerKm = tier.pricePerKm)
    }

    val choices: List<RideChoice> = listOf(
        // 2 véhicules seulement (demande client) : forfait 3 km inclus, puis 200 FDJ/km.
        RideChoice(
            id = "standard",
            nameRes = R.string.taxi_vehicle_standard,
            type = RideType.STANDARD,
            seats = 4,
            basePrice = 600.0,
            pricePerKm = 200.0,
            includedKm = 3.0,
            estimatedArrivalTime = "5 min",
            descriptionRes = R.string.taxi_vehicle_standard_desc,
        ),
        RideChoice(
            id = "comfort",
            nameRes = R.string.taxi_vehicle_comfort,
            type = RideType.COMFORT,
            seats = 4,
            basePrice = 750.0,
            pricePerKm = 200.0,
            includedKm = 3.0,
            estimatedArrivalTime = "7 min",
            descriptionRes = R.string.taxi_vehicle_comfort_desc,
            features = listOf("Climatisation"),
        ),
    )

    fun byId(id: String): RideChoice = choices.firstOrNull { it.id == id } ?: choices.first()
}
