package dj.velox.client.feature.taxi

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dj.velox.client.domain.model.Ride
import dj.velox.client.ui.theme.Inter
import dj.velox.client.ui.theme.Poppins
import kotlin.math.roundToInt

// Écran volontairement en THÈME CLAIR (comme la maquette Flutter de fin de course).
private val Bg = Color(0xFFF4F4F4)
private val Card = Color.White
private val TextDark = Color(0xFF1A1A1A)
private val TextGray = Color(0xFF6B6B6B)
private val Green = Color(0xFF22A82E)
private val StarAmber = Color(0xFFFFC107)
private val PinRed = Color(0xFFE53935)
private val Border = Color(0xFFE0E0E0)

@Composable
fun RideCompletedScreen(
    onNewRide: () -> Unit,
    onFinished: () -> Unit,
    vm: RideCompletedViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val ride by vm.ride.collectAsStateWithLifecycle()
    val submitting by vm.submitting.collectAsStateWithLifecycle()
    val sent by vm.sent.collectAsStateWithLifecycle()

    var rating by remember { mutableIntStateOf(0) }
    var comment by remember { mutableStateOf("") }
    var favorite by remember { mutableStateOf(false) }

    val r = ride
    if (r == null) {
        Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Green, strokeWidth = 2.dp)
        }
        return
    }

    Column(
        Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.size(120.dp).clip(CircleShape).background(Green), contentAlignment = Alignment.Center) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, tint = Green, modifier = Modifier.size(40.dp))
            }
        }
        Spacer(Modifier.height(24.dp))

        CompletedCard(r)
        Spacer(Modifier.height(16.dp))
        DriverCard(r, onCall = { r.driverPhone?.let { dialPhone(context, it) } })
        Spacer(Modifier.height(16.dp))
        MerciBanner()
        Spacer(Modifier.height(16.dp))

        // Notation
        WhiteCard {
            Text("Comment s'est passée votre course ?", color = TextDark, fontFamily = Poppins, fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                (1..5).forEach { s ->
                    Icon(
                        if (s <= rating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "$s",
                        tint = if (s <= rating) StarAmber else Color(0xFFBDBDBD),
                        modifier = Modifier.padding(horizontal = 4.dp).size(40.dp).clip(CircleShape).clickable { rating = s },
                    )
                }
            }
            if (rating > 0) {
                Spacer(Modifier.height(10.dp))
                Text(ratingText(rating), color = TextGray, fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.W500, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = comment,
                onValueChange = { if (it.length <= 300) comment = it },
                placeholder = { Text("Commentaire (optionnel)", color = Color(0xFF9E9E9E)) },
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFFAFAFA), unfocusedContainerColor = Color(0xFFFAFAFA),
                    focusedTextColor = TextDark, unfocusedTextColor = TextDark,
                    focusedBorderColor = Green, unfocusedBorderColor = Border, cursorColor = Green,
                ),
                modifier = Modifier.fillMaxWidth().height(120.dp),
            )
            Spacer(Modifier.height(16.dp))
            GreenButton(if (sent) "Évaluation envoyée ✓" else "Envoyer l'évaluation ⭐", enabled = !sent && !submitting && rating > 0, loading = submitting) {
                vm.submit(rating, comment.trim().ifBlank { null }, favorite) {
                    Toast.makeText(context, "Merci pour votre évaluation !", Toast.LENGTH_SHORT).show()
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // Favoris
        WhiteCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFF0F0F0)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.FavoriteBorder, null, tint = TextGray, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Ajouter aux favoris", color = TextDark, fontFamily = Poppins, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text("Retrouvez rapidement ce chauffeur pour vos prochaines courses", color = TextGray, fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp)
                }
                Spacer(Modifier.size(8.dp))
                Switch(
                    checked = favorite, onCheckedChange = { favorite = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Green, uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFF111111)),
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        GreenButton("Nouvelle course", leading = Icons.Filled.Add, onClick = onNewRide)
        Spacer(Modifier.height(16.dp))
        Text(
            "Retour à l'accueil", color = TextGray, fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.W600,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onFinished).padding(8.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

private fun dialPhone(context: android.content.Context, phone: String) {
    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$phone"))) }
}

private fun ratingText(r: Int): String = when (r) {
    1 -> "Très mauvais 😞"; 2 -> "Mauvais 😕"; 3 -> "Correct 😐"; 4 -> "Très bien ! 😊"; 5 -> "Excellent ! 🤩"; else -> ""
}

private fun vehicleLabel(type: String): String = when (type.lowercase()) {
    "comfort", "confort" -> "Confort"
    "van" -> "Van"
    else -> "Standard"
}

@Composable
private fun WhiteCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card).border(1.dp, Border.copy(alpha = 0.6f), RoundedCornerShape(20.dp)).padding(24.dp), content = content)
}

@Composable
private fun CompletedCard(r: Ride) {
    WhiteCard {
        Text("Course terminée !", color = TextDark, fontFamily = Poppins, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        LocRow(Icons.Filled.RadioButtonChecked, Green, "Départ", r.pickup.address)
        Box(Modifier.padding(start = 18.dp).height(20.dp))
        LocRow(Icons.Filled.LocationOn, PinRed, "Arrivée", r.destination.address)
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric(Icons.Filled.Straighten, "Distance", "%.1f km".format(r.distance))
            Metric(Icons.Filled.Schedule, "Durée", "${r.estimatedDuration} min")
            Metric(Icons.Filled.Payments, "Prix", "${(r.finalFare ?: r.estimatedFare).roundToInt()} FDJ")
        }
    }
}

@Composable
private fun LocRow(icon: ImageVector, tint: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = TextGray, fontFamily = Inter, fontSize = 13.sp)
            Text(value, color = TextDark, fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.W600, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Metric(icon: ImageVector, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Green, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, color = TextGray, fontFamily = Inter, fontSize = 13.sp)
        Text(value, color = TextDark, fontFamily = Poppins, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DriverCard(r: Ride, onCall: () -> Unit) {
    WhiteCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(60.dp).clip(CircleShape).background(Color(0xFFF0F0F0)), contentAlignment = Alignment.Center) {
                if (!r.driverPhotoUrl.isNullOrEmpty()) {
                    AsyncImage(model = r.driverPhotoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Filled.Phone, null, tint = TextGray, modifier = Modifier.size(26.dp))
                }
            }
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(r.driverName ?: "Chauffeur", color = TextDark, fontFamily = Poppins, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(vehicleLabel(r.vehicleType), color = TextGray, fontFamily = Inter, fontSize = 14.sp)
            }
            Box(Modifier.size(48.dp).clip(CircleShape).background(Green.copy(alpha = 0.12f)).clickable(enabled = r.driverPhone != null, onClick = onCall), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Phone, "Appeler", tint = Green, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun MerciBanner() {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brush.horizontalGradient(listOf(Color(0xFFBFE6C4), Color(0xFFC2E4EC)))).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Merci d'avoir roulé avec Velox", color = Green, fontFamily = Poppins, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text("💙", fontSize = 20.sp)
        Spacer(Modifier.height(6.dp))
        Text("Votre course s'est bien déroulée et nous espérons vous revoir bientôt !", color = Color(0xFF4A4A4A), fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun GreenButton(text: String, enabled: Boolean = true, loading: Boolean = false, leading: ImageVector? = null, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(14.dp)).background(if (enabled) Green else Green.copy(alpha = 0.4f)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leading != null) { Icon(leading, null, tint = Color.White, modifier = Modifier.size(22.dp)); Spacer(Modifier.size(10.dp)) }
                Text(text, color = Color.White, fontFamily = Inter, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
