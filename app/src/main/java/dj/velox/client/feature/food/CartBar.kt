package dj.velox.client.feature.food

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dj.velox.client.R
import dj.velox.client.ui.theme.Inter
import dj.velox.client.ui.theme.Poppins
import dj.velox.client.ui.theme.VeloxTheme

/**
 * Bouton panier unifié des écrans food (accueil + détail restaurant).
 * Rectangle vert pleine largeur (coins nets) + libellé « VOIR LE PANIER · total FDJ »,
 * badge du nombre d'articles, et éclair ⚡ — calqué sur le bouton « AJOUTER AU PANIER »
 * de l'écran d'options, pour un design cohérent sur tout le parcours food delivery.
 *
 * À placer dans un Box parent et aligné en bas : `Modifier.align(Alignment.BottomCenter)`.
 */
@Composable
fun CartBar(
    itemCount: Int,
    total: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VeloxTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(16.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(c.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.ShoppingCart, null, tint = c.onPrimary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(10.dp))
        Text(
            "${stringResource(R.string.view_cart).uppercase()} · $total FDJ",
            color = c.onPrimary,
            fontFamily = Poppins,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
        )
        Spacer(Modifier.size(10.dp))
        Box(
            Modifier.clip(CircleShape).background(c.onPrimary.copy(alpha = 0.2f)).padding(horizontal = 9.dp, vertical = 2.dp),
        ) {
            Text("$itemCount", color = c.onPrimary, fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.size(10.dp))
        Icon(Icons.Filled.Bolt, null, tint = c.onPrimary, modifier = Modifier.size(20.dp))
    }
}
