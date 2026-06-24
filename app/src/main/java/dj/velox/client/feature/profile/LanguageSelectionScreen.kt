package dj.velox.client.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dj.velox.client.R
import dj.velox.client.core.i18n.LocaleManager
import dj.velox.client.ui.theme.Inter
import dj.velox.client.ui.theme.Poppins
import dj.velox.client.ui.theme.VeloxColors
import dj.velox.client.ui.theme.VeloxTheme

/**
 * Sélecteur de langue (port de `language_selection_screen.dart`).
 * S'appuie sur [LocaleManager] : changer la langue applique la locale via
 * AppCompatDelegate (persistée) et recompose l'app avec les bonnes `strings.xml`.
 */
@Composable
fun LanguageSelectionScreen(onBack: () -> Unit) {
    val c = VeloxTheme.colors
    var selected by remember { mutableStateOf(LocaleManager.currentLanguageCode()) }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(c.surfaceHigh).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = c.onSurface, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.size(14.dp))
            Text(stringResource(R.string.choose_language), color = c.onSurface, fontFamily = Poppins, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        LazyColumn(
            Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            items(LocaleManager.supportedLanguages, key = { it.code }) { lang ->
                LanguageRow(lang, selected == lang.code, c) {
                    selected = lang.code
                    LocaleManager.setLanguage(lang.code)
                    onBack()
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun LanguageRow(lang: LocaleManager.Language, isSelected: Boolean, c: VeloxColors, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) c.primary.copy(alpha = 0.1f) else c.surface)
            .border(if (isSelected) 2.dp else 1.dp, if (isSelected) c.primary else c.outlineVariant, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(lang.nativeName, color = c.onSurface, fontFamily = Poppins, fontSize = 16.sp, fontWeight = FontWeight.W600)
            Spacer(Modifier.height(4.dp))
            Text(lang.name, color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 14.sp)
        }
        if (isSelected) {
            Icon(Icons.Filled.CheckCircle, null, tint = c.primary, modifier = Modifier.size(28.dp))
        }
    }
}
