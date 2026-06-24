package dj.velox.client.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Accès à la palette étendue Velox depuis un composable :
 *   `VeloxTheme.colors.surfaceHigh`
 * (pendant de `ref.watch(themeNotifierProvider) ? AppColors.dark : AppColors.light`
 * côté Flutter). Pour les rôles standard, préférer `MaterialTheme.colorScheme`.
 */
object VeloxTheme {
    val colors: VeloxColors
        @Composable
        get() = LocalVeloxColors.current
}

val LocalVeloxColors = staticCompositionLocalOf { VeloxDarkColors }

/**
 * Thème racine Velox — design « Kinetic Monolith ».
 * - Dark = palette néon sombre (identique à l'app Flutter), **défaut** ;
 * - Light = thème clair séparé (les deux palettes existent).
 * Comme côté Flutter (`ThemeState(isDarkMode = true)` + toggle « Apparence »),
 * le dark est forcé par défaut au lieu de suivre le réglage système ; un futur
 * toggle profil passera `darkTheme = false` pour basculer en clair.
 * Couleurs dynamiques (Material You) volontairement désactivées pour garder
 * l'identité de marque cohérente sur tous les appareils.
 */
@Composable
fun VeloxTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val veloxColors = if (darkTheme) VeloxDarkColors else VeloxLightColors
    val colorScheme =
        if (darkTheme) veloxColors.toDarkColorScheme() else veloxColors.toLightColorScheme()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Edge-to-edge (enableEdgeToEdge) rend les barres système transparentes : le fond de
            // l'app (c.bg) se dessine derrière. On ne règle donc PLUS statusBarColor/navigationBarColor
            // (dépréciés API 35+), seulement la couleur des icônes selon le thème.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalVeloxColors provides veloxColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = VeloxTypography,
            content = content,
        )
    }
}
