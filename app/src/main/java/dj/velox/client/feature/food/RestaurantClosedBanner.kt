package dj.velox.client.feature.food

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dj.velox.client.R
import dj.velox.client.domain.model.Restaurant
import dj.velox.client.ui.theme.Inter
import dj.velox.client.ui.theme.Poppins
import androidx.compose.ui.text.font.FontWeight
import dj.velox.client.ui.theme.VeloxColors

/**
 * Bandeau rouge « restaurant fermé » — affiché sur la fiche resto, l'écran d'ajout au panier
 * et le checkout. Montre l'horaire du jour quand il est configuré (voir
 * [Restaurant.todayOpeningHoursLabel]).
 */
@Composable
fun RestaurantClosedBanner(restaurant: Restaurant, c: VeloxColors, modifier: Modifier = Modifier) {
    val hours = restaurant.todayOpeningHoursLabel()
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(c.error.copy(alpha = 0.12f))
            .border(1.dp, c.error.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Warning, null, tint = c.error, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                if (restaurant.exceptionallyClosed) {
                    stringResource(R.string.restaurant_closed_exceptional_title)
                } else {
                    stringResource(R.string.restaurant_closed_title)
                },
                color = c.error, fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            )
            Text(
                if (hours != null) stringResource(R.string.restaurant_closed_hours, hours)
                else stringResource(R.string.restaurant_closed_no_hours),
                color = c.error, fontFamily = Inter, fontSize = 12.sp,
            )
        }
    }
}
