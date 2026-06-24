package dj.velox.client.domain.model

/**
 * Adresse de livraison (sous-collection `users/{uid}/addresses`).
 * Miroir d'`AddressModel` (address_notifier.dart). `type` ∈ {home, work, other}.
 */
data class Address(
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val details: String = "",
    val type: String = "other",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val isDefault: Boolean = false,
) {
    /** Champs métier (sans isDefault/createdAt, ajoutés par le service). */
    fun toMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "address" to address,
        "details" to details,
        "type" to type,
        "latitude" to latitude,
        "longitude" to longitude,
    )

    companion object {
        fun fromFirestore(id: String, data: Map<String, Any?>): Address = Address(
            id = id,
            name = data["name"] as? String ?: "",
            address = data["address"] as? String ?: "",
            details = data["details"] as? String ?: "",
            type = data["type"] as? String ?: "other",
            latitude = (data["latitude"] as? Number)?.toDouble() ?: 0.0,
            longitude = (data["longitude"] as? Number)?.toDouble() ?: 0.0,
            isDefault = data["isDefault"] as? Boolean ?: false,
        )
    }
}
