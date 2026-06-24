package dj.velox.client.domain.model

import com.google.firebase.firestore.DocumentSnapshot

/**
 * Profil utilisateur (document Firestore `users/{uid}`).
 * Miroir des champs écrits par AuthService + UserNotifier côté Flutter (camelCase).
 *
 * `raw` conserve la map brute pour les champs résiduels (roles, redeemedPoints…)
 * comme le faisait `UserState.userData`.
 */
data class VeloxUser(
    val uid: String,
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val photoUrl: String? = null,
    val isActive: Boolean = true,
    val isVerified: Boolean = false,
    // Préférences
    val language: String = "fr",
    val currency: String = "FDJ",
    val notificationsEnabled: Boolean = true,
    val darkMode: Boolean = false,
    // Stats
    val totalTaxiRides: Int = 0,
    val totalFoodOrders: Int = 0,
    val totalSpentFdj: Double = 0.0,
    // Fidélité
    val redeemedPoints: Int = 0,
    // Accès brut résiduel
    val raw: Map<String, Any?> = emptyMap(),
) {
    val displayName: String
        get() = when {
            !name.isNullOrEmpty() -> name
            !email.isNullOrEmpty() -> email.substringBefore('@')
            else -> "Utilisateur"
        }

    @Suppress("UNCHECKED_CAST")
    fun hasRole(role: String): Boolean =
        (raw["roles"] as? List<*>)?.contains(role) ?: false

    companion object {
        fun fromFirestore(doc: DocumentSnapshot): VeloxUser {
            val data = doc.data ?: emptyMap<String, Any?>()

            @Suppress("UNCHECKED_CAST")
            val prefs = data["preferences"] as? Map<String, Any?> ?: emptyMap()
            @Suppress("UNCHECKED_CAST")
            val stats = data["stats"] as? Map<String, Any?> ?: emptyMap()

            return VeloxUser(
                uid = doc.id,
                name = data["name"] as? String ?: data["displayName"] as? String,
                email = data["email"] as? String,
                phone = data["phone"] as? String,
                photoUrl = data["photoUrl"] as? String,
                isActive = data["isActive"] as? Boolean ?: true,
                isVerified = data["isVerified"] as? Boolean ?: false,
                language = prefs["language"] as? String ?: "fr",
                currency = prefs["currency"] as? String ?: "FDJ",
                notificationsEnabled = prefs["notificationsEnabled"] as? Boolean ?: true,
                darkMode = prefs["darkMode"] as? Boolean ?: false,
                totalTaxiRides = (stats["totalTaxiRides"] as? Number)?.toInt() ?: 0,
                totalFoodOrders = (stats["totalFoodOrders"] as? Number)?.toInt() ?: 0,
                totalSpentFdj = (stats["totalSpentFdj"] as? Number)?.toDouble() ?: 0.0,
                redeemedPoints = (data["redeemedPoints"] as? Number)?.toInt() ?: 0,
                raw = data,
            )
        }
    }
}
