package dj.velox.client

import android.app.Application
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp
import dj.velox.client.core.notifications.VeloxNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Point d'entrée applicatif Velox Client.
 *
 * - @HiltAndroidApp active le conteneur d'injection Hilt (équivalent du
 *   ProviderScope racine de Riverpod côté Flutter).
 * - Firebase s'initialise automatiquement via le plugin google-services
 *   (ContentProvider FirebaseInitProvider) — aucun init manuel requis.
 *
 * Backend Firebase PARTAGÉ avec l'app Flutter iOS (mêmes collections Firestore).
 */
@HiltAndroidApp
class VeloxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        VeloxNotifications.ensureChannel(this)
        // Init Crashlytics HORS du main thread : `getInstance()` initialise le SDK, ce qui
        // alourdissait le cold start sur le thread UI. Le réglage de collecte (release-only)
        // n'a pas besoin d'être synchrone au démarrage.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
        }
    }
}
