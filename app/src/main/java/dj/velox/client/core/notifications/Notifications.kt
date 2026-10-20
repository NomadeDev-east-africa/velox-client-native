package dj.velox.client.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import dj.velox.client.R
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
 * par les Cloud Functions (`android.notification.channelId`) : quand l'app est en
 * arrière-plan/tuée, c'est l'OS qui affiche la notif via ce channelId. Sans canaux
 * correspondants, les notifs retombaient sur le canal générique de secours FCM.
 *
 * Suffixe **`_v2`** sur les trois ids : le son d'un `NotificationChannel` est figé au
 * moment de sa création sur l'appareil et ne peut plus être modifié ensuite (ni par l'app,
 * ni par une mise à jour) — recréer le canal avec le même id n'a aucun effet pour les
 * utilisateurs qui l'ont déjà. Même schéma que côté Velox_Restaurant
 * (`orders_channel` → `orders_channel_v2`). Les anciens canaux sont supprimés pour ne pas
 * laisser deux entrées dupliquées dans les réglages système de l'utilisateur.
 *
 * ⚠️ Les Cloud Functions envoient encore `channelId: "rides"` / `"orders"` pour les push
 * affichées par l'OS (app en arrière-plan/tuée) — ce fichier ne peut pas les changer.
 * Tant que le backend n'envoie pas `"rides_v2"` / `"orders_v2"`, ces notifications-là
 * utiliseront le canal `_v2` (créé ici, silencieux via son par défaut du canal — puisque
 * l'OS cherchera le channelId "rides"/"orders", retombera sur le canal générique). Seules
 * les notifs reçues app ouverte (premier plan, `onMessageReceived`) sont garanties
 * d'utiliser le son ici, car c'est ce fichier qui choisit alors le canal.
 */
object VeloxNotifications {
    const val CHANNEL_ID = "velox_default_v2"   // général / premier plan / secours
    const val CHANNEL_RIDES = "rides_v2"        // suivi des courses
    const val CHANNEL_ORDERS = "orders_v2"      // suivi des commandes

    private val LEGACY_CHANNEL_IDS = listOf("velox_default", "rides", "orders")

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return

        LEGACY_CHANNEL_IDS.forEach { mgr.deleteNotificationChannel(it) }

        val soundUri = Uri.parse("android.resource://${context.packageName}/${R.raw.client_notif}")
        val soundAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Velox", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notifications générales Velox"
                setSound(soundUri, soundAttributes)
            },
        )
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_RIDES, "Courses", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Suivi de vos courses (chauffeur assigné, arrivée, trajet)"
                setSound(soundUri, soundAttributes)
            },
        )
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ORDERS, "Commandes", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Suivi de vos commandes (préparation, prête, livrée)"
                setSound(soundUri, soundAttributes)
            },
        )
    }

    /** Canal à utiliser pour une notif affichée au premier plan, d'après les ids du payload. */
    fun channelFor(rideId: String?, orderId: String?): String = when {
        !rideId.isNullOrBlank() -> CHANNEL_RIDES
        !orderId.isNullOrBlank() -> CHANNEL_ORDERS
        else -> CHANNEL_ID
    }
}
