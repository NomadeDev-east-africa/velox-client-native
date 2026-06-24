package dj.velox.client.feature.profile

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dj.velox.client.R
import dj.velox.client.domain.model.Address
import dj.velox.client.ui.theme.Inter
import dj.velox.client.ui.theme.Poppins
import dj.velox.client.ui.theme.VeloxColors
import dj.velox.client.ui.theme.VeloxTheme

private val HomeBlue = Color(0xFF6AB2E1)
private val WorkOrange = Color(0xFFFFA726)
private const val DJIBOUTI_LAT = 11.5880
private const val DJIBOUTI_LNG = 43.1450

private fun typeIcon(type: String): ImageVector = when (type) {
    "home" -> Icons.Filled.Home
    "work" -> Icons.Filled.Work
    else -> Icons.Filled.LocationOn
}

private fun typeColor(type: String, c: VeloxColors): Color = when (type) {
    "home" -> HomeBlue
    "work" -> WorkOrange
    else -> c.primary
}

// ════════════════════════════════════════════════════════════════
// LISTE DES ADRESSES
// ════════════════════════════════════════════════════════════════

@Composable
fun MyAddressesScreen(
    vm: AddressViewModel,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val c = VeloxTheme.colors
    val state by vm.state.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<Address?>(null) }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(c.surfaceHigh).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = c.onSurface, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.size(14.dp))
                Text(stringResource(R.string.my_addresses), color = c.onSurface, fontFamily = Poppins, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp)
                }
                state.addresses.isEmpty() -> EmptyAddresses(c)
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                ) {
                    items(state.addresses, key = { it.id }) { addr ->
                        AddressCard(
                            addr, c,
                            onSetDefault = { vm.setDefault(addr.id) },
                            onEdit = { onEdit(addr.id) },
                            onDelete = { deleteTarget = addr },
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }

        // FAB Ajouter
        Row(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp)
                .clip(RoundedCornerShape(50)).background(c.primary).clickable(onClick = onAdd)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.AddLocationAlt, null, tint = c.onPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.add_label), color = c.onPrimary, fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = c.surfaceHigh,
            title = { Text(stringResource(R.string.delete_address_title), color = c.onSurface, fontFamily = Poppins, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.delete_address_msg, target.name), color = c.onSurfaceVariant, fontFamily = Inter) },
            confirmButton = { TextButton(onClick = { vm.delete(target.id); deleteTarget = null }) { Text(stringResource(R.string.delete), color = c.error, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel), color = c.onSurfaceVariant) } },
        )
    }
}

@Composable
private fun EmptyAddresses(c: VeloxColors) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(140.dp).clip(CircleShape).background(c.primary.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.LocationOff, null, tint = c.primary, modifier = Modifier.size(72.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.no_addresses), color = c.onSurface, fontFamily = Poppins, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.no_addresses_hint), color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun AddressCard(
    addr: Address,
    c: VeloxColors,
    onSetDefault: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val tColor = typeColor(addr.type, c)
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface)
            .border(1.dp, c.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(18.dp)).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(tColor.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(typeIcon(addr.type), null, tint = tColor, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.size(12.dp))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(addr.name, color = c.onSurface, fontFamily = Poppins, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (addr.isDefault) {
                    Spacer(Modifier.size(8.dp))
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(c.primary.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text(stringResource(R.string.default_label), color = c.primary, fontFamily = Inter, fontSize = 11.sp, fontWeight = FontWeight.W600)
                    }
                }
            }
            Box {
                Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options), tint = c.onSurfaceVariant, modifier = Modifier.size(22.dp).clip(CircleShape).clickable { menuOpen = true })
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (!addr.isDefault) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.set_default)) }, leadingIcon = { Icon(Icons.Filled.StarOutline, null) }, onClick = { menuOpen = false; onSetDefault() })
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, leadingIcon = { Icon(Icons.Filled.EditLocationAlt, null) }, onClick = { menuOpen = false; onEdit() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete), color = c.error) }, leadingIcon = { Icon(Icons.Filled.DeleteOutline, null, tint = c.error) }, onClick = { menuOpen = false; onDelete() })
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.outlineVariant.copy(alpha = 0.3f)))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.LocationOn, null, tint = c.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(8.dp))
            Text(addr.address, color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
        }
        if (addr.details.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Info, null, tint = c.outlineVariant, modifier = Modifier.size(14.dp))
                Spacer(Modifier.size(8.dp))
                Text(addr.details, color = c.outlineVariant, fontFamily = Inter, fontSize = 13.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════
// AJOUT / ÉDITION
// ════════════════════════════════════════════════════════════════

@Composable
fun AddAddressScreen(
    vm: AddressViewModel,
    addressId: String?,
    onBack: () -> Unit,
) {
    val c = VeloxTheme.colors
    val editing = remember(addressId) { addressId?.let(vm::addressById) }

    var type by rememberSaveable { mutableStateOf(editing?.type ?: "home") }
    var name by rememberSaveable { mutableStateOf(editing?.name ?: "") }
    var address by rememberSaveable { mutableStateOf(editing?.address ?: "") }
    var details by rememberSaveable { mutableStateOf(editing?.details ?: "") }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(c.surfaceHigh).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = c.onSurface, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.size(14.dp))
            Text(if (editing != null) stringResource(R.string.edit_address) else stringResource(R.string.add_address), color = c.onSurface, fontFamily = Poppins, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            FieldLabel(stringResource(R.string.address_type), c)
            Spacer(Modifier.height(12.dp))
            Row {
                TypeOption("home", stringResource(R.string.home_label), Icons.Filled.Home, type == "home", c, Modifier.weight(1f)) { type = "home" }
                Spacer(Modifier.size(8.dp))
                TypeOption("work", stringResource(R.string.work_label), Icons.Filled.Work, type == "work", c, Modifier.weight(1f)) { type = "work" }
                Spacer(Modifier.size(8.dp))
                TypeOption("other", stringResource(R.string.other_label), Icons.Filled.LocationOn, type == "other", c, Modifier.weight(1f)) { type = "other" }
            }

            Spacer(Modifier.height(24.dp))
            FieldLabel(stringResource(R.string.address_name), c)
            Spacer(Modifier.height(12.dp))
            AddressField(name, { name = it }, stringResource(R.string.address_name_hint), Icons.AutoMirrored.Filled.Label, c)

            Spacer(Modifier.height(24.dp))
            FieldLabel(stringResource(R.string.address), c)
            Spacer(Modifier.height(12.dp))
            AddressField(address, { address = it }, stringResource(R.string.address_hint), Icons.Filled.LocationOn, c, maxLines = 2)

            Spacer(Modifier.height(24.dp))
            FieldLabel(stringResource(R.string.extra_details), c)
            Spacer(Modifier.height(12.dp))
            AddressField(details, { details = it }, stringResource(R.string.details_hint), Icons.Outlined.Info, c, maxLines = 3)

            Spacer(Modifier.height(32.dp))
            val enabled = name.isNotBlank() && address.isNotBlank()
            Box(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (enabled) c.primary else c.surfaceTop)
                    .clickable(enabled = enabled) {
                        val result = Address(
                            id = editing?.id ?: "",
                            name = name.trim(), address = address.trim(), details = details.trim(), type = type,
                            latitude = editing?.latitude ?: DJIBOUTI_LAT,
                            longitude = editing?.longitude ?: DJIBOUTI_LNG,
                            isDefault = editing?.isDefault ?: false,
                        )
                        if (editing != null) vm.update(editing.id, result, onBack) else vm.add(result, onBack)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (editing != null) stringResource(R.string.save_changes) else stringResource(R.string.add_address_action),
                    color = if (enabled) c.onPrimary else c.onSurfaceVariant,
                    fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.navigationBarsPadding())
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun FieldLabel(text: String, c: VeloxColors) {
    Text(text, color = c.onSurface, fontFamily = Poppins, fontSize = 16.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun TypeOption(type: String, label: String, icon: ImageVector, selected: Boolean, c: VeloxColors, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) c.primary.copy(alpha = 0.12f) else c.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.primary else c.outlineVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = if (selected) c.primary else c.onSurfaceVariant, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, color = if (selected) c.primary else c.onSurfaceVariant, fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.W600)
    }
}

@Composable
private fun AddressField(value: String, onValueChange: (String) -> Unit, placeholder: String, icon: ImageVector, c: VeloxColors, maxLines: Int = 1) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = c.onSurfaceVariant, fontFamily = Inter) },
        leadingIcon = { Icon(icon, null, tint = c.primary) },
        singleLine = maxLines == 1,
        maxLines = maxLines,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = c.surface,
            unfocusedContainerColor = c.surface,
            focusedBorderColor = c.primary,
            unfocusedBorderColor = c.outlineVariant,
            focusedTextColor = c.onSurface,
            unfocusedTextColor = c.onSurface,
            cursorColor = c.primary,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
