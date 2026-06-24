package dj.velox.client.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.google.firebase.Timestamp
import dj.velox.client.R
import dj.velox.client.feature.auth.SessionState
import dj.velox.client.ui.theme.Inter
import dj.velox.client.ui.theme.Poppins
import dj.velox.client.ui.theme.VeloxColors
import dj.velox.client.ui.theme.VeloxTheme
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    session: SessionState,
    onBack: () -> Unit,
    vm: EditProfileViewModel = hiltViewModel(),
) {
    val c = VeloxTheme.colors
    val context = LocalContext.current
    val ui by vm.ui.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.initPhoto(session.profile?.photoUrl ?: session.firebaseUser?.photoUrl?.toString())
    }

    var name by rememberSaveable { mutableStateOf(session.displayName) }
    var phone by rememberSaveable {
        mutableStateOf(session.profile?.phone ?: session.firebaseUser?.phoneNumber ?: "")
    }
    val initialBirth = remember(session) {
        (session.profile?.raw?.get("birthDate") as? Timestamp)?.toDate()?.time
    }
    var birthMillis by rememberSaveable { mutableStateOf(initialBirth) }
    var showDatePicker by remember { mutableStateOf(false) }

    val email = session.profile?.email ?: session.firebaseUser?.email ?: stringResource(R.string.not_available)
    val verified = session.firebaseUser?.isEmailVerified == true

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
                .getOrNull()?.let { vm.uploadPhoto(it) }
        }
    }
    val launchPicker = {
        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        // Barre supérieure
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(c.surfaceHigh).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = c.onSurface, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.size(14.dp))
            Text(stringResource(R.string.edit_profile), color = c.onSurface, fontFamily = Poppins, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
            // ── Photo ──
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        Modifier.size(118.dp).clip(CircleShape).background(c.surfaceHigh).border(2.5.dp, c.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        val photo = ui.photoUrl
                        if (photo != null) {
                            AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                        } else {
                            Icon(Icons.Filled.Person, null, tint = c.primary, modifier = Modifier.size(56.dp))
                        }
                        if (ui.isUploadingPhoto) {
                            Box(Modifier.fillMaxSize().clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp)
                            }
                        }
                    }
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(c.primary).border(3.dp, c.bg, CircleShape).clickable(enabled = !ui.isUploadingPhoto) { launchPicker() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.PhotoCamera, stringResource(R.string.change_photo), tint = c.onPrimary, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.tap_change_photo), color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 13.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)

            Spacer(Modifier.height(32.dp))

            // ── Email (lecture seule) ──
            FieldLabelE(stringResource(R.string.email), c)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surfaceHigh).border(1.dp, c.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Email, null, tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(12.dp))
                Text(email, color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 15.sp, modifier = Modifier.weight(1f))
                if (verified) {
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(c.primary.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(stringResource(R.string.verified), color = c.primary, fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.W600)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            FieldLabelE(stringResource(R.string.auth_name), c)
            Spacer(Modifier.height(8.dp))
            EditField(name, { name = it }, stringResource(R.string.your_full_name), Icons.Filled.Person, c)

            Spacer(Modifier.height(16.dp))
            FieldLabelE(stringResource(R.string.phone_number), c)
            Spacer(Modifier.height(8.dp))
            EditField(phone, { phone = it }, "+253 XX XX XX XX", Icons.Outlined.Phone, c, keyboard = KeyboardType.Phone)

            Spacer(Modifier.height(16.dp))
            FieldLabelE(stringResource(R.string.birth_date), c)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surfaceLow).border(1.dp, c.outlineVariant, RoundedCornerShape(14.dp)).clickable { showDatePicker = true }.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Cake, null, tint = c.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(12.dp))
                Text(
                    birthMillis?.let { formatDate(it) } ?: stringResource(R.string.select_date),
                    color = if (birthMillis != null) c.onSurface else c.outlineVariant,
                    fontFamily = Inter, fontSize = 15.sp, modifier = Modifier.weight(1f),
                )
                Icon(Icons.Filled.CalendarToday, null, tint = c.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }

            Spacer(Modifier.height(28.dp))
            // Info card
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.primary.copy(alpha = 0.06f)).border(1.dp, c.primary.copy(alpha = 0.18f), RoundedCornerShape(14.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Info, null, tint = c.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(12.dp))
                Text(stringResource(R.string.profile_info_hint), color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp)
            }

            ui.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.error_prefix, it), color = c.error, fontFamily = Inter, fontSize = 13.sp)
            }

            Spacer(Modifier.height(28.dp))
            val enabled = name.isNotBlank() && !ui.isSaving
            Box(
                Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp)).background(if (enabled) c.primary else c.primary.copy(alpha = 0.4f))
                    .clickable(enabled = enabled) { vm.save(name.trim(), phone.trim().ifBlank { null }, birthMillis, onBack) },
                contentAlignment = Alignment.Center,
            ) {
                if (ui.isSaving) CircularProgressIndicator(Modifier.size(22.dp), color = c.onPrimary, strokeWidth = 2.dp)
                else Text(stringResource(R.string.save_changes), color = c.onPrimary, fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.navigationBarsPadding())
            Spacer(Modifier.height(40.dp))
        }
    }

    if (showDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = birthMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = { birthMillis = dateState.selectedDateMillis; showDatePicker = false }) { Text("OK", color = c.primary, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel), color = c.onSurfaceVariant) } },
        ) { DatePicker(state = dateState) }
    }
}

@Composable
private fun FieldLabelE(text: String, c: VeloxColors) {
    Text(text, color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.W600)
}

@Composable
private fun EditField(value: String, onValueChange: (String) -> Unit, placeholder: String, icon: ImageVector, c: VeloxColors, keyboard: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = c.outlineVariant, fontFamily = Inter) },
        leadingIcon = { Icon(icon, null, tint = c.primary, modifier = Modifier.size(20.dp)) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboard),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = c.surfaceLow,
            unfocusedContainerColor = c.surfaceLow,
            focusedBorderColor = c.primary,
            unfocusedBorderColor = c.outlineVariant,
            focusedTextColor = c.onSurface,
            unfocusedTextColor = c.onSurface,
            cursorColor = c.primary,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

private val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
private fun formatDate(millis: Long): String = dateFmt.format(java.util.Date(millis))
