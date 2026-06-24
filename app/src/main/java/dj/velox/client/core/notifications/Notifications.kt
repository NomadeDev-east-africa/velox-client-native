package dj.velox.client.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Cible de navigation issue d'une notification (type/ids du data payload FCM). */
data class DeepLink(
    val type: String? = null,
    val orderId: String? = null,
    val rideId: String? = null,
)

/**
 * Pont en mémoire entre l'Activity (qui reçoit l'Intent de la notification tapée)
 * et la navigation Compose. Évite le besoin d'un BuildContext (comme appNavigatorKey
 * côté Flutter).
 */
@Singleton
class NotificationRouter @Inject constructor() {
    private val _pending = MutableStateFlow<DeepLink?>(null)
    val pending: StateFlow<DeepLink?> = _pending.asStateFlow()

    fun post(link: DeepLink) { _pending.value = link }
    fun consume() { _pending.value = null }
}

/**
 * Canaux de notifications Velox (créés au démarrage de l'app).
 *
 * Les ids [CHANNEL_RIDES] et [CHANNEL_ORDERS] doivent matcher **exactement** ceux envoyés
 * par les Cloud Functions (`android.notification.channelId` = "rides" / "orders") : quand
 * l'app est en arrière-plan/tuée, c'est l'OS qui affiche la notif via ce channelId. Sans
 * canaux correspondants, les notifs retombaient sur le canal générique de secours FCM.
 */
object VeloxNotifications {
    const val CHANNEL_ID = "velox_default"   // général / premier plan / secours
    const val CHANNEL_RIDES = "rides"        // suivi des courses  (aligné Cloud Functions)
    const val CHANNEL_ORDERS = "orders"      // suivi des commandes (aligné Cloud Functions)

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Velox", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Notifications générales Velox" },
        )
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_RIDES, "Courses", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Suivi de vos courses (chauffeur assigné, arrivée, trajet)" },
        )
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ORDERS, "Commandes", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Suivi de vos commandes (préparation, prête, livrée)" },
        )
    }

    /** Canal à utiliser pour une notif affichée au premier plan, d'après les ids du payload. */
    fun channelFor(rideId: String?, orderId: String?): String = when {
        !rideId.isNullOrBlank() -> CHANNEL_RIDES
        !orderId.isNullOrBlank() -> CHANNEL_ORDERS
        else -> CHANNEL_ID
    }
}
