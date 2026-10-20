package dj.velox.client.domain.model

import com.google.firebase.firestore.DocumentSnapshot

/** Bannière promo pilotée depuis l'admin (collection `banners`). */
data class Banner(
    val id: String = "",
    val type: String = "text",       // "text" | "image"
    val order: Int = 0,
    val active: Boolean = true,
    val title: String? = null,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    val actionType: String = "none", // "none" | "whatsapp" | "url" | "restaurant" | "category"
    val actionValue: String? = null,
) {
    val isImage: Boolean get() = type == "image"

    companion object {
        fun fromFirestore(doc: DocumentSnapshot): Banner {
            val data = doc.data ?: emptyMap()
            return Banner(
                id = doc.id,
                type = data["type"] as? String ?: "text",
                order = (data["order"] as? Number)?.toInt() ?: 0,
                active = data["active"] as? Boolean ?: true,
                title = data["title"] as? String,
                subtitle = data["subtitle"] as? String,
                imageUrl = data["imageUrl"] as? String,
                actionType = data["actionType"] as? String ?: "none",
                actionValue = data["actionValue"] as? String,
            )
        }
    }
}
