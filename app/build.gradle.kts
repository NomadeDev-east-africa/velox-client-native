import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    // Kotlin intégré à AGP 9 : on applique seulement les plugins compilateur additionnels.
    // KSP ≥ 2.3.1 supporte le built-in Kotlin → pas de kotlin-android.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.baselineprofile)
}

// Configuration de signature release (clé Velox réutilisée du projet Flutter)
// key.properties et le keystore ne sont PAS versionnés (voir .gitignore)
val keyPropertiesFile = rootProject.file("key.properties")
val keyProperties = Properties()
if (keyPropertiesFile.exists()) {
    keyProperties.load(FileInputStream(keyPropertiesFile))
}

// Clé OpenRouteService lue depuis local.properties (NON versionné). Repli "" si absente
// → getRoute retombe alors sur le tracé en ligne droite (voir LocationService.getRoute).
val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties()
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}
val orsApiKey: String = (localProperties["ORS_API_KEY"] as String?) ?: ""

android {
    namespace = "dj.velox.client"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "dj.velox.client"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Clé ORS injectée au build depuis local.properties (jamais versionnée).
        buildConfigField("String", "ORS_API_KEY", "\"$orsApiKey\"")
    }

    signingConfigs {
        create("release") {
            if (keyPropertiesFile.exists()) {
                keyAlias = keyProperties["keyAlias"] as String
                keyPassword = keyProperties["keyPassword"] as String
                storeFile = (keyProperties["storeFile"] as String?)?.let { file(it) }
                storePassword = keyProperties["storePassword"] as String
            }
        }
    }

    buildTypes {
        release {
            // Signe avec la clé Velox uniquement si key.properties est présent ;
            // sinon (clone sans secrets) on retombe sur la signature debug pour builder quand même.
            signingConfig = if (keyPropertiesFile.exists())
                signingConfigs.getByName("release")
            else
                signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // appcompat : requis pour le switch de langue runtime (AppCompatDelegate.setApplicationLocales)
    implementation(libs.androidx.appcompat)
    // material (Views) : fournit le thème hôte XML Theme.Material3.* de l'activité
    implementation(libs.material)

    // ── Compose ──
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // ── Hilt (DI — équivalent Riverpod côté injection) ──
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // ── Firebase (backend partagé avec l'app Flutter iOS) ──
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.analytics)
    // Crashlytics réintroduit AVEC son plugin Gradle (`com.google.firebase.crashlytics`),
    // qui injecte le « build ID » nécessaire au démarrage + upload le mapping R8 pour
    // dé-obfusquer les stack traces release.
    implementation(libs.firebase.crashlytics)
    // App Check désactivé (comme l'app Flutter) — dépendances retirées pour ne jamais
    // émettre de token App Check ni risquer un blocage des Cloud Functions / Firestore.

    // ── Async / Serialization ──
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    // ── Persistance locale (équivalent Hive) ──
    implementation(libs.androidx.datastore.preferences)

    // ── Images (équivalent cached_network_image / flutter_svg) ──
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

    // ── Cartes (équivalent flutter_map / OSM) ──
    implementation(libs.maplibre)

    // ── Connexion Google (Credential Manager) ──
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // ── Localisation GPS ──
    implementation(libs.play.services.location)

    // ── Baseline Profile ──
    // ProfileInstaller : installe le Baseline Profile généré au 1er lancement (AOT du code chaud).
    implementation(libs.androidx.profileinstaller)
    // Relie le module producteur : sans cette dépendance, le plugin baselineprofile est appliqué
    // mais aucun profil n'est généré/packagé. Avec elle, `:app:generateBaselineProfile` (sur
    // appareil API 28+) produit le profil empaqueté en release → cold start nettement plus fluide.
    baselineProfile(project(":baselineprofile"))

    // ── Tests ──

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
