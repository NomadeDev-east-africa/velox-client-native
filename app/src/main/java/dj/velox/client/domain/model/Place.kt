package dj.velox.client.domain.model

/** Type de lieu (miroir place.dart). */
enum class PlaceType { SEARCH, RECENT, SAVED, SUGGESTION }

/**
 * Lieu (destination, départ, lieu sauvegardé…). Miroir place.dart sans l'IconData
 * (qui relevait de l'UI Flutter — l'icône est choisie côté Compose selon le type).
 */
data class Place(
    val id: String,
    val name: String,
    val location: LatLng,
    val address: String? = null,
    val type: PlaceType = PlaceType.SEARCH,
    val distance: Double? = null,
)
