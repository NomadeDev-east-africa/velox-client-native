package dj.velox.client.feature.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dj.velox.client.R
import dj.velox.client.core.i18n.LocaleManager
import dj.velox.client.core.theme.ThemeViewModel
import dj.velox.client.feature.auth.SessionState
import dj.velox.client.ui.theme.Inter
import dj.velox.client.ui.theme.Poppins
import dj.velox.client.ui.theme.VeloxColors
import dj.velox.client.ui.theme.VeloxTheme

/**
 * Profil — refonte « Kinetic Monolith » (port de `profile_screen.dart`).
 * Avatar bordure néon, sections (Apparence avec **toggle Mode sombre persisté**,
 * Infos perso, Adresses, Notifications, Historique/Favoris, Support, Compte).
 * Les actions non encore portées affichent un toast « Bientôt disponible ».
 */
@Composable
fun ProfileScreen(
    session: SessionState,
    onOpenOrders: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
    onManageAddresses: () -> Unit,
    onAddAddress: () -> Unit,
    onEditProfile: () -> Unit,
    onSelectLanguage: () -> Unit,
    onOpenSupport: () -> Unit,
    themeViewModel: ThemeViewModel = hiltViewModel(),
) {
    val c = VeloxTheme.colors
    val context = LocalContext.current
    val profile = session.profile
    val darkMode by themeViewModel.darkMode.collectAsStateWithLifecycle()
    val currentLanguageName = remember {
        LocaleManager.supportedLanguages.firstOrNull { it.code == LocaleManager.currentLanguageCode() }?.nativeName ?: "Français"
    }

    val soonText = stringResource(R.string.coming_soon)
    val soon = { Toast.makeText(context, soonText, Toast.LENGTH_SHORT).show() }

    // Toggles notifications : état local (le branchement Firestore est un item à part).
    var pushEnabled by rememberSaveable { mutableStateOf(profile?.notificationsEnabled ?: true) }
    var trackingEnabled by rememberSaveable { mutableStateOf(true) }
    var promosEnabled by rememberSaveable { mutableStateOf(false) }

    val orange = Color(0xFFFF9F45)

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Barre supérieure
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = c.onSurface)
            }
        }

        // ── En-tête : avatar + identité ──────────────────────────────
        Box(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(c.surfaceHigh)
                        .border(3.dp, c.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    val photo = profile?.photoUrl ?: session.firebaseUser?.photoUrl?.toString()
                    if (photo != null) {
                        AsyncImage(
                            model = photo,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                        )
                    } else {
                        Icon(Icons.Filled.Person, null, tint = c.primary, modifier = Modifier.size(56.dp))
                    }
                }
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(c.primary)
                        .border(3.dp, c.bg, CircleShape)
                        .clickable(onClick = soon),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PhotoCamera, stringResource(R.string.change_photo), tint = c.onPrimary, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            session.displayName,
            color = c.onSurface,
            fontFamily = Poppins,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        profile?.email?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                it,
                color = c.onSurfaceVariant,
                fontFamily = Inter,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        Spacer(Modifier.height(28.dp))

        // ── Apparence & Personnalisation ─────────────────────────────
        SectionHeader(Icons.Filled.Palette, stringResource(R.string.appearance_personalization), c)
        SettingsGroup(c) {
            SettingRow(
                icon = Icons.Filled.DarkMode,
                title = stringResource(R.string.dark_mode),
                subtitle = if (darkMode) stringResource(R.string.enabled) else stringResource(R.string.disabled),
                c = c,
                trailing = {
                    Switch(
                        checked = darkMode,
                        onCheckedChange = { themeViewModel.setDarkMode(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = c.onPrimary,
                            checkedTrackColor = c.primary,
                            uncheckedThumbColor = c.onSurfaceVariant,
                            uncheckedTrackColor = c.surfaceTop,
                            uncheckedBorderColor = c.outlineVariant,
                        ),
                    )
                },
            )
            RowDivider(c)
            SettingRow(
                icon = Icons.Filled.Language,
                title = stringResource(R.string.language),
                subtitle = currentLanguageName,
                c = c,
                onClick = onSelectLanguage,
                trailing = { Chevron(c) },
            )
        }

        // ── Informations personnelles ────────────────────────────────
        SectionHeader(Icons.Filled.Person, stringResource(R.string.personal_info), c)
        SettingsGroup(c) {
            SettingRow(
                icon = Icons.Filled.Edit,
                title = stringResource(R.string.edit_profile),
                subtitle = stringResource(R.string.edit_profile_sub),
                c = c,
                onClick = onEditProfile,
                trailing = { Chevron(c) },
            )
            RowDivider(c)
            SettingRow(
                icon = Icons.Filled.Email,
                title = profile?.email ?: stringResource(R.string.email_address),
                subtitle = stringResource(R.string.email_address),
                c = c,
                trailing = { VerifiedBadge(profile?.isVerified == true, c) },
            )
        }

        // ── Mes adresses ─────────────────────────────────────────────
        SectionHeader(Icons.Filled.LocationOn, stringResource(R.string.my_addresses), c)
        SettingsGroup(c) {
            SettingRow(
                icon = Icons.Filled.Home,
                title = stringResource(R.string.manage_addresses),
                subtitle = stringResource(R.string.address_types),
                c = c,
                onClick = onManageAddresses,
                trailing = { Chevron(c) },
            )
            RowDivider(c)
            SettingRow(
                icon = Icons.Filled.AddLocationAlt,
                title = stringResource(R.string.add_address),
                subtitle = stringResource(R.string.new_address),
                c = c,
                tint = c.error,
                onClick = onAddAddress,
                trailing = { Chevron(c) },
            )
        }

        // ── Notifications ────────────────────────────────────────────
        SectionHeader(Icons.Filled.Notifications, stringResource(R.string.notifications), c)
        SettingsGroup(c) {
            SettingRow(
                icon = Icons.Filled.Notifications,
                title = stringResource(R.string.push_notifications),
                subtitle = stringResource(R.string.receive_alerts),
                c = c,
                trailing = { VeloxSwitch(pushEnabled, { pushEnabled = it }, c) },
            )
            RowDivider(c)
            SettingRow(
                icon = Icons.Filled.LocalShipping,
                title = stringResource(R.string.order_tracking),
                subtitle = stringResource(R.string.realtime_updates),
                c = c,
                trailing = { VeloxSwitch(trackingEnabled, { trackingEnabled = it }, c) },
            )
            RowDivider(c)
            SettingRow(
                icon = Icons.Filled.LocalOffer,
                title = stringResource(R.string.promotions),
                subtitle = stringResource(R.string.offers_discounts),
                c = c,
                trailing = { VeloxSwitch(promosEnabled, { promosEnabled = it }, c) },
            )
        }

        // ── Historique & Favoris ─────────────────────────────────────
        SectionHeader(Icons.Filled.History, stringResource(R.string.history_favorites), c)
        SettingsGroup(c) {
            SettingRow(
                icon = Icons.AutoMirrored.Filled.ListAlt,
                title = stringResource(R.string.my_orders),
                subtitle = stringResource(R.string.full_history),
                c = c,
                onClick = onOpenOrders,
                trailing = { Chevron(c) },
            )
            RowDivider(c)
            SettingRow(
                icon = Icons.Filled.Favorite,
                title = stringResource(R.string.favorite_restaurants),
                subtitle = "0 ${stringResource(R.string.restaurants)}",
                c = c,
                onClick = soon,
                trailing = { Chevron(c) },
            )
        }

        // ── Support & Légal ──────────────────────────────────────────
        SectionHeader(Icons.Filled.SupportAgent, stringResource(R.string.support_legal), c)
        SettingsGroup(c) {
            SettingRow(
                icon = Icons.Filled.SupportAgent,
                title = stringResource(R.string.help_center),
                subtitle = "Contact, FAQ",
                c = c,
                onClick = onOpenSupport,
                trailing = { Chevron(c) },
            )
        }

        // ── Compte ───────────────────────────────────────────────────
        Spacer(Modifier.height(20.dp))
        Box(Modifier.padding(horizontal = 20.dp)) {
            Column {
                AccountAction(Icons.AutoMirrored.Filled.Logout, stringResource(R.string.logout_action), orange, c, onLogout)
                Spacer(Modifier.height(12.dp))
                AccountAction(Icons.Filled.DeleteForever, stringResource(R.string.delete_account), c.error, c, soon)
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

// ── Composants ───────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(icon: ImageVector, title: String, c: VeloxColors) {
    Row(
        Modifier.padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(10.dp))
        Text(title, color = c.onSurface, fontFamily = Poppins, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
    }
}

@Composable
private fun SettingsGroup(c: VeloxColors, content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.surface)
            .border(1.dp, c.outlineVariant.copy(alpha = 0.12f), RoundedCornerShape(20.dp)),
    ) { content() }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    c: VeloxColors,
    tint: Color = c.primary,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (tint == c.error) c.error else c.onSurface, fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.W600)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 12.5.sp)
        }
        if (trailing != null) {
            Spacer(Modifier.size(8.dp))
            trailing()
        }
    }
}

@Composable
private fun RowDivider(c: VeloxColors) {
    Box(Modifier.fillMaxWidth().padding(start = 70.dp).height(1.dp).background(c.outlineVariant.copy(alpha = 0.1f)))
}

@Composable
private fun Chevron(c: VeloxColors) {
    Icon(Icons.Outlined.ChevronRight, null, tint = c.onSurfaceVariant, modifier = Modifier.size(22.dp))
}

@Composable
private fun VeloxSwitch(checked: Boolean, onChange: (Boolean) -> Unit, c: VeloxColors) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.onPrimary,
            checkedTrackColor = c.primary,
            uncheckedThumbColor = c.onSurface,
            uncheckedTrackColor = c.surfaceTop,
            uncheckedBorderColor = c.outlineVariant,
        ),
    )
}

@Composable
private fun VerifiedBadge(verified: Boolean, c: VeloxColors) {
    if (!verified) return
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(c.primary.copy(alpha = 0.15f)).padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(stringResource(R.string.verified), color = c.primary, fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.W600)
    }
}

@Composable
private fun AccountAction(icon: ImageVector, label: String, color: Color, c: VeloxColors, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(10.dp))
        Text(label, color = color, fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
