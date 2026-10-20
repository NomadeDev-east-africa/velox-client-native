package dj.velox.client.feature.food

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dj.velox.client.domain.model.MenuItem
import dj.velox.client.ui.theme.Inter
import dj.velox.client.ui.theme.Poppins
import dj.velox.client.ui.theme.VeloxColors
import kotlin.math.roundToInt

private val PromoRed = Color(0xFFE53935)

/**
 * Prix d'un plat avec promotion : quand le plat a une remise (`discountPercentage > 0`),
 * affiche le prix remisé (vert) + le prix d'origine barré. Sinon, le prix simple.
 */
@Composable
fun DishPriceRow(item: MenuItem, c: VeloxColors, priceSize: TextUnit = 14.sp) {
    if (item.hasDiscount) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${item.discountedPrice.roundToInt()} FDJ", color = c.primary, fontFamily = Poppins, fontSize = priceSize, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(6.dp))
            Text(
                "${item.price.roundToInt()} FDJ",
                color = c.onSurfaceVariant,
                fontFamily = Inter,
                fontSize = (priceSize.value - 2f).sp,
                textDecoration = TextDecoration.LineThrough,
            )
        }
    } else {
        Text("${item.price.roundToInt()} FDJ", color = c.primary, fontFamily = Poppins, fontSize = priceSize, fontWeight = FontWeight.Bold)
    }
}

/** Badge « -X% » (rouge) posé sur l'image d'un plat en promotion ; rien sinon. */
@Composable
fun DishPromoBadge(item: MenuItem, modifier: Modifier = Modifier) {
    if (item.hasDiscount) {
        Box(modifier.clip(RoundedCornerShape(6.dp)).background(PromoRed).padding(horizontal = 6.dp, vertical = 2.dp)) {
            Text("-${item.discountPercentage}%", color = Color.White, fontFamily = Poppins, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}
