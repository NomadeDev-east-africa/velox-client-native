package dj.velox.client.domain.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.serialization.Serializable

/** Catégorie de la notification (miroir de l'écran équivalent iOS/Flutter). */
enum class NotificationType {
    ORDER, RIDE, PROMO;

    companion object {
        fun parse(value: String?): NotificationType = when (value) {
            "order" -> ORDER
            "ride" -> RIDE
            else -> PROMO
        }
    }
}

/** Destination de navigation associée au tap sur la notification. */
enum class NotificationActionType {
    NONE, ORDER, RIDE, RESTAURANT, URL, WHATSAPP;

    companion object {
        fun parse(value: String?): NotificationActionType = when (value) {
            "order" -> ORDER
            "ride" -> RIDE
            "restaurant" -> RESTAURANT
            "url" -> URL
            "whatsapp" -> WHATSAPP
            else -> NONE
        }
    }
}

/**
 * Notification persistée — sous-collection Firestore `users/{uid}/notifications`.
 * Lecture seule côté app sauf `read`/`readAt` (règles Firestore) : pas de `toMap()`
 * complet, seul un update partiel `{read, readAt}` est jamais envoyé (voir
 * `NotificationsService.markAsRead`).
 */
@Serializable
data class AppNotification(
    val id: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val createdAt: Long,
    val read: Boolean,
    val readAt: Long?,
    val actionType: NotificationActionType,
    val actionValue: String?,
    val imageUrl: String?,
    val campaignId: String?,
) {
    companion object {
        private fun parseTs(value: Any?): Long? = when (value) {
            null -> null
            is Timestamp -> value.toDate().time
            is java.util.Date -> value.time
            is Number -> value.toLong()
            else -> null
        }

        fun fromFirestore(doc: DocumentSnapshot): AppNotification {
            val data = doc.data ?: emptyMap<String, Any?>()
            return AppNotification(
                id = doc.id,
                type = NotificationType.parse(data["type"] as? String),
                title = data["title"] as? String ?: "",
                body = data["body"] as? String ?: "",
                createdAt = parseTs(data["createdAt"]) ?: System.currentTimeMillis(),
                read = data["read"] as? Boolean ?: false,
                readAt = parseTs(data["readAt"]),
                actionType = NotificationActionType.parse(data["actionType"] as? String),
                actionValue = data["actionValue"] as? String,
                imageUrl = data["imageUrl"] as? String,
                campaignId = data["campaignId"] as? String,
            )
        }
    }
}
