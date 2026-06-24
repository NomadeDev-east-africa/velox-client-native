package dj.velox.client.core.i18n

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Gestion de la langue de l'application (équivalent de AppTranslations côté Flutter).
 *
 * Contrairement à Flutter (où `tr()` était statique et imposait un rebuild keyé par
 * langue), Android gère nativement la localisation : il suffit de fournir
 * `strings.xml` par locale (values/, values-en/, values-ar/, values-so/, values-aa/)
 * et de changer la locale applicative — le système recompose avec les bonnes chaînes
 * et applique automatiquement le RTL pour l'arabe.
 *
 * `setApplicationLocales` persiste le choix (via AppLocalesMetadataHolderService
 * déclaré au manifest) : pas besoin de SharedPreferences/DataStore pour la langue.
 */
object LocaleManager {

    /** Langues supportées — codes alignés sur l'app Flutter (Afar = "aa"). */
    val supportedLanguages = listOf(
        Language("so", "Somali", "Af-Soomaali"),
        Language("aa", "Afar", "Qafar af"),
        Language("fr", "Français", "Français"),
        Language("en", "English", "English"),
        Language("ar", "Arabic", "العربية"),
    )

    data class Language(val code: String, val name: String, val nativeName: String)

    /** Code de la langue active (défaut "fr" si aucune sélection). */
    fun currentLanguageCode(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) "fr" else locales[0]?.language ?: "fr"
    }

    /** Change la langue de l'app et persiste le choix. */
    fun setLanguage(code: String) {
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(code)
        )
    }
}
