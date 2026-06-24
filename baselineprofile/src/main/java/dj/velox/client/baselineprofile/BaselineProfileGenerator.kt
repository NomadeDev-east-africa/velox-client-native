package dj.velox.client.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Génère le Baseline Profile de l'app Velox client.
 *
 * Le profil capture les méthodes « chaudes » du démarrage et du 1er rendu pour les compiler en
 * AOT dès l'installation (via ProfileInstaller) → moins de jank au premier lancement / au scroll.
 *
 * À lancer sur un **appareil/émulateur API 28+ connecté** :
 *     ./gradlew :app:generateReleaseBaselineProfile
 * Le plugin `androidx.baselineprofile` injecte ensuite automatiquement le profil produit dans
 * `app/src/release/generated/baselineProfiles/` (embarqué dans l'AAB/APK release).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = "dj.velox.client",
        includeInStartupProfile = true,
    ) {
        // Parcours critique : démarrage à froid jusqu'à la 1ère frame interactive.
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
    }
}
