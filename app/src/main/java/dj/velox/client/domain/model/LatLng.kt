package dj.velox.client.domain.model

import kotlinx.serialization.Serializable

/**
 * Coordonnée géographique du domaine (équivalent de latlong2.LatLng côté Flutter).
 * Sérialisable pour DataStore ; convertie vers/depuis Firestore GeoPoint ou
 * MapLibre LatLng au niveau des couches data/UI.
 */
@Serializable
data class LatLng(
    val latitude: Double,
    val longitude: Double,
)
