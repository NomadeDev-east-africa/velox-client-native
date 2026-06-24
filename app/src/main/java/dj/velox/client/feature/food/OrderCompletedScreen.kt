package dj.velox.client.feature.food

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dj.velox.client.R
import dj.velox.client.ui.theme.Inter
import dj.velox.client.ui.theme.Poppins
import dj.velox.client.ui.theme.VeloxColors
import dj.velox.client.ui.theme.VeloxTheme

private val RatingGreen = Color(0xFF22A82E)
private val DriverBlue = Color(0xFF5B9BD5)
private val StarAmber = Color(0xFFFFC107)

/**
 * Commande livrée + notation (port de `order_completed_screen.dart`).
 * Icône de succès, notation resto (étoiles + commentaire) et livreur, envoi des notes.
 */
@Composable
fun OrderCompletedScreen(
    onFinished: () -> Unit,
    vm: OrderCompletedViewModel = hiltViewModel(),
) {
    val c = VeloxTheme.colors
    val context = LocalContext.current
    val order by vm.order.collectAsStateWithLifecycle()
    val submitting by vm.submitting.collectAsStateWithLifecycle()

    var restaurantRating by remember { mutableIntStateOf(0) }
    var driverRating by remember { mutableIntStateOf(0) }
    var comment by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState())
            .statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))
        Box(Modifier.size(120.dp).clip(CircleShape).background(c.primary.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.CheckCircle, null, tint = c.primary, modifier = Modifier.size(80.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.order_delivered_title), color = c.onSurface, fontFamily = Poppins, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.thanks_delivery),
            color = c.onSurface.copy(alpha = 0.64f), fontFamily = Inter, fontSize = 16.sp, lineHeight = 24.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        order?.let { o ->
            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(c.surfaceHigh).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.order_number, o.id.take(8)), color = c.onSurface.copy(alpha = 0.64f), fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.W500)
            }
        }
        Spacer(Modifier.height(40.dp))

        RatingCard(
            title = stringResource(R.string.rate_restaurant),
            subtitle = order?.restaurantName ?: stringResource(R.string.restaurant),
            icon = Icons.Filled.Restaurant,
            iconColor = c.primary,
            rating = restaurantRating,
            onRating = { restaurantRating = it },
            comment = comment,
            onComment = { comment = it },
            c = c,
        )
        Spacer(Modifier.height(24.dp))
        RatingCard(
            title = stringResource(R.string.rate_driver),
            subtitle = order?.deliveryDriverName ?: stringResource(R.string.driver),
            icon = Icons.Filled.DeliveryDining,
            iconColor = DriverBlue,
            rating = driverRating,
            onRating = { driverRating = it },
            comment = null,
            onComment = {},
            c = c,
        )
        Spacer(Modifier.height(36.dp))

        Box(
            Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp)).background(RatingGreen)
                .clickable(enabled = !submitting) {
                    if (restaurantRating == 0 || driverRating == 0) {
                        Toast.makeText(context, context.getString(R.string.please_rate_both), Toast.LENGTH_SHORT).show()
                    } else {
                        vm.submit(restaurantRating, driverRating, comment.trim().ifBlank { null }) {
                            Toast.makeText(context, context.getString(R.string.thanks_review), Toast.LENGTH_SHORT).show()
                            onFinished()
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (submitting) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
            else Text(stringResource(R.string.submit_ratings), color = Color.White, fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.back_to_home),
            color = c.onSurface.copy(alpha = 0.64f), fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.W500,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onFinished).padding(8.dp),
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun RatingCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    rating: Int,
    onRating: (Int) -> Unit,
    comment: String?,
    onComment: (String) -> Unit,
    c: VeloxColors,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface)
            .border(1.5.dp, c.onSurface.copy(alpha = 0.12f), RoundedCornerShape(16.dp)).padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(iconColor.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = c.onSurface, fontFamily = Poppins, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = c.onSurface.copy(alpha = 0.64f), fontFamily = Inter, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            (1..5).forEach { star ->
                Icon(
                    if (star <= rating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "$star",
                    tint = if (star <= rating) StarAmber else c.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 4.dp).size(42.dp).clip(CircleShape).clickable { onRating(star) },
                )
            }
        }
        if (rating > 0) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(ratingTextRes(rating)), color = c.onSurface.copy(alpha = 0.64f), fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.W500, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        if (comment != null) {
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = comment,
                onValueChange = { if (it.length <= 300) onComment(it) },
                placeholder = { Text(stringResource(R.string.comment_optional), color = Color(0xFF9E9E9E)) },
                maxLines = 3,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedBorderColor = RatingGreen,
                    unfocusedBorderColor = Color(0xFFE0E0E0),
                    cursorColor = RatingGreen,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun ratingTextRes(rating: Int): Int = when (rating) {
    1 -> R.string.rating_1
    2 -> R.string.rating_2
    3 -> R.string.rating_3
    4 -> R.string.rating_4
    else -> R.string.rating_5
}
