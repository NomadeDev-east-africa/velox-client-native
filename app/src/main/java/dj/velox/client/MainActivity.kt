package dj.velox.client

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dj.velox.client.core.notifications.DeepLink
import dj.velox.client.core.notifications.NotificationRouter
import dj.velox.client.core.notifications.VeloxMessagingService
import dj.velox.client.core.theme.ThemeViewModel
import dj.velox.client.navigation.RootContent
import dj.velox.client.ui.theme.VeloxTheme
import javax.inject.Inject

/**
 * Activité hôte unique (single-activity + Compose).
 * @AndroidEntryPoint : injection Hilt dans les ViewModels de l'arbre Compose
 * et dans l'Activity (NotificationRouter pour le deep-link des notifications).
 *
 * Étend **AppCompatActivity** (et non ComponentActivity) : c'est obligatoire pour que
 * `AppCompatDelegate.setApplicationLocales` (changement de langue runtime) applique
 * réellement la locale sur Android < 13. AppCompatDelegate n'override la configuration
 * que pour les activités AppCompat ; le thème hôte Material3.DayNight reste compatible.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var notificationRouter: NotificationRouter

    // Android 13+ : POST_NOTIFICATIONS est une permission runtime. Sans elle,
    // l'OS bloque l'affichage de TOUTES les notifs (y compris « commande prête »),
    // même si le token FCM est bien enregistré et le push envoyé côté serveur.
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* choix respecté ; le token est déjà enregistré */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        maybeRequestNotificationPermission()
        handleDeepLink(intent)
        setContent {
            // Thème piloté par la préférence persistée (Apparence → Mode sombre).
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val darkMode by themeViewModel.darkMode.collectAsStateWithLifecycle()
            VeloxTheme(darkTheme = darkMode) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RootContent(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    /** Demande POST_NOTIFICATIONS sur API 33+ si pas déjà accordée (no-op avant Android 13). */
    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * Convertit les extras d'une notification tapée en cible de navigation.
     *
     * Deux origines possibles selon l'état de l'app :
     * - **Foreground** : `VeloxMessagingService` construit la notif et pose les clés custom
     *   `velox_type` / `velox_orderId` / `velox_rideId`.
     * - **Arrière-plan / app tuée** : les messages FCM portent un bloc `notification`, donc
     *   `onMessageReceived` n'est PAS appelé — c'est l'OS qui affiche la notif et, au tap,
     *   lance l'Activity avec les **clés brutes du data payload** (`type`/`orderId`/`rideId`).
     *   Sans les lire ici, le deep-link était perdu → démarrage à froid sur l'accueil.
     */
    private fun handleDeepLink(intent: Intent?) {
        intent ?: return
        val extras = intent.extras
        val type = intent.getStringExtra(VeloxMessagingService.EXTRA_TYPE)
            ?: extras?.getString("type")
        val orderId = intent.getStringExtra(VeloxMessagingService.EXTRA_ORDER_ID)
            ?: extras?.getString("orderId")
        val rideId = intent.getStringExtra(VeloxMessagingService.EXTRA_RIDE_ID)
            ?: extras?.getString("rideId")
        if (type != null || orderId != null || rideId != null) {
            notificationRouter.post(DeepLink(type, orderId, rideId))
        }
    }
}
