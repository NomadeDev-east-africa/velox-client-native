package dj.velox.client.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// ════════════════════════════════════════════════════════════════
// PALETTE VELOX — « KINETIC MONOLITH »
// Port fidèle de la source canonique Flutter : lib/theme/app_colors.dart
// (dark = design néon sombre ; light = thème clair séparé).
// Ne pas diverger sans synchroniser les deux apps.
// ════════════════════════════════════════════════════════════════

/**
 * Palette étendue (rôles surfaceLow / surfaceHigh / surfaceTop absents du
 * [androidx.compose.material3.ColorScheme] standard). Pendant exact de
 * `AppColors` côté Flutter — les composants la lisent via `VeloxTheme.colors`.
 */
@Immutable
data class VeloxColors(
    val bg: Color,
    val surfaceLow: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val surfaceTop: Color,
    val primary: Color,
    val onPrimary: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outlineVariant: Color,
    val error: Color,
)

// ── Dark : « Kinetic Monolith » (identique à AppColors.dark) ───────
val VeloxDarkColors = VeloxColors(
    bg = Color(0xFF0E0E0E),
    surfaceLow = Color(0xFF131313),
    surface = Color(0xFF1A1919),
    surfaceHigh = Color(0xFF20201F),
    surfaceTop = Color(0xFF262626),
    primary = Color(0xFF9FFF88),
    onPrimary = Color(0xFF026400),
    onSurface = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFFADAAAB),
    outlineVariant = Color(0xFF484847),
    error = Color(0xFFFF7351),
)

// ── Light : thème clair séparé (identique à AppColors.light) ───────
val VeloxLightColors = VeloxColors(
    bg = Color(0xFFF5F5F5),
    surfaceLow = Color(0xFFFFFFFF),
    surface = Color(0xFFF0F0F0),
    surfaceHigh = Color(0xFFE8E8E8),
    surfaceTop = Color(0xFFDDDDDD),
    primary = Color(0xFF12AD2B),
    onPrimary = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0E0E0E),
    onSurfaceVariant = Color(0xFF6B6B6B),
    outlineVariant = Color(0xFFCCCCCC),
    error = Color(0xFFE53935),
)

// ════════════════════════════════════════════════════════════════
// ColorScheme Material 3 dérivés des palettes ci-dessus, afin que les
// composants M3 standards (boutons, champs, etc.) prennent les bonnes
// couleurs. Les rôles « surfaceContainer* » sont mappés sur les niveaux
// de surface Velox pour rester cohérents.
// ════════════════════════════════════════════════════════════════

internal fun VeloxColors.toDarkColorScheme() = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = surfaceHigh,
    onPrimaryContainer = primary,
    secondary = primary,
    onSecondary = onPrimary,
    tertiary = primary,
    onTertiary = onPrimary,
    background = bg,
    onBackground = onSurface,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceHigh,
    onSurfaceVariant = onSurfaceVariant,
    surfaceContainerLowest = bg,
    surfaceContainerLow = surfaceLow,
    surfaceContainer = surface,
    surfaceContainerHigh = surfaceHigh,
    surfaceContainerHighest = surfaceTop,
    outline = outlineVariant,
    outlineVariant = outlineVariant,
    error = error,
    onError = Color(0xFF000000),
)

internal fun VeloxColors.toLightColorScheme() = lightColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = surfaceHigh,
    onPrimaryContainer = primary,
    secondary = primary,
    onSecondary = onPrimary,
    tertiary = primary,
    onTertiary = onPrimary,
    background = bg,
    onBackground = onSurface,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceHigh,
    onSurfaceVariant = onSurfaceVariant,
    surfaceContainerLowest = surfaceLow,
    surfaceContainerLow = surfaceLow,
    surfaceContainer = surface,
    surfaceContainerHigh = surfaceHigh,
    surfaceContainerHighest = surfaceTop,
    outline = outlineVariant,
    outlineVariant = outlineVariant,
    error = error,
    onError = Color(0xFFFFFFFF),
)
