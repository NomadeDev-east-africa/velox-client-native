package dj.velox.client.core.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import dj.velox.client.MainActivity
import dj.velox.client.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Réception des push FCM (équivalent NotificationService.dart côté réception).
 * - onNewToken : ré-enregistre le token pour l'utilisateur connecté.
 * - onMessageReceived : affiche une notification locale ; le tap rouvre MainActivity
 *   avec les extras (type/orderId/rideId) → deep-link via NotificationRouter.
 */
@AndroidEntryPoint
class VeloxMessagingService : FirebaseMessagingService() {

    @Inject lateinit var tokenManager: NotificationTokenManager
    @Inject lateinit var auth: FirebaseAuth

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        auth.currentUser?.uid?.let { uid ->
            scope.launch { runCatching { tokenManager.saveToken(uid, token) } }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val notif = message.notification
        showNotification(
            title = notif?.title ?: data["title"] ?: "Velox",
            body = notif?.body ?: data["body"] ?: "",
            type = data["type"],
            orderId = data["orderId"],
            rideId = data["rideId"],
        )
    }

    private fun showNotification(
        title: String,
        body: String,
        type: String?,
        orderId: String?,
        rideId: String?,
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_TYPE, type)
            putExtra(EXTRA_ORDER_ID, orderId)
            putExtra(EXTRA_RIDE_ID, rideId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, VeloxNotifications.channelFor(rideId, orderId))
            // Petite icône (barre d'état) : logo Velox → silhouette monochrome teintée en vert néon.
            .setSmallIcon(R.drawable.logo_velox_bg)
            .setColor(ContextCompat.getColor(this, R.color.velox_green))
            // Grande icône (notif déroulée) : logo Velox en couleur.
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.logo_velox_bg))
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            runCatching {
                NotificationManagerCompat.from(this)
                    .notify(System.currentTimeMillis().toInt(), notification)
            }
        }
    }

    companion object {
        const val EXTRA_TYPE = "velox_type"
        const val EXTRA_ORDER_ID = "velox_orderId"
        const val EXTRA_RIDE_ID = "velox_rideId"
    }
}
