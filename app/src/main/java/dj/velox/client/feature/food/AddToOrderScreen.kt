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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dj.velox.client.domain.model.ExtraOption
import dj.velox.client.domain.model.MenuItem
import dj.velox.client.domain.model.OptionGroup
import dj.velox.client.domain.model.OptionSelection
import dj.velox.client.domain.model.OrderItem
import dj.velox.client.domain.model.Restaurant
import dj.velox.client.domain.model.SauceOption
import dj.velox.client.ui.theme.Inter
import dj.velox.client.ui.theme.Poppins
import dj.velox.client.ui.theme.VeloxColors
import dj.velox.client.ui.theme.VeloxTheme

// Options par défaut (plats sans optionGroups) — miroir de `add_to_order_screen.dart`.
private val FALLBACK_EXTRAS = listOf(
    ExtraOption("Frites", 500), ExtraOption("Tomates", 500), ExtraOption("Oignons", 500),
    ExtraOption("Salade", 500), ExtraOption("Taille L", 500), ExtraOption("Taille XL", 500),
    ExtraOption("Taille XXL", 500),
)
private val FALLBACK_SAUCES = listOf(
    SauceOption("Samouraï", 50), SauceOption("Mayonnaise", 50), SauceOption("Ketchup", 50),
    SauceOption("Barbecue", 50), SauceOption("Harissa", 50), SauceOption("Moutarde", 50),
)

/**
 * Écran d'ajout au panier avec options (port de `add_to_order_screen.dart`).
 * Data-driven via `item.optionGroups` (FORMULE single = radios, SUPPLÉMENTS multiple = cases) ;
 * sinon repli sur extras/sauces par défaut. Gère le changement de restaurant.
 */
@Composable
fun AddToOrderScreen(
    cartViewModel: CartViewModel,
    onBack: () -> Unit,
    vm: AddToOrderViewModel = hiltViewModel(),
) {
    val c = VeloxTheme.colors
    val item by vm.item.collectAsStateWithLifecycle()
    val restaurant by vm.restaurant.collectAsStateWithLifecycle()

    val loadedItem = item
    val loadedRestaurant = restaurant
    if (loadedItem == null || loadedRestaurant == null) {
        Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp)
        }
        return
    }

    AddToOrderContent(loadedItem, loadedRestaurant, cartViewModel, onBack, c)
}

@Composable
private fun AddToOrderContent(
    item: MenuItem,
    restaurant: Restaurant,
    cartViewModel: CartViewModel,
    onBack: () -> Unit,
    c: VeloxColors,
) {
    val context = LocalContext.current
    val groups = item.optionGroups
    val dataDriven = groups.isNotEmpty()

    var quantity by remember { mutableIntStateOf(1) }
    // Sélections data-driven : une entrée (Set d'index) par groupe ; présélection single+required.
    var selections by remember(item.id) {
        mutableStateOf<List<Set<Int>>>(
            groups.map { g -> if (g.isSingle && g.required && g.choices.isNotEmpty()) setOf(0) else emptySet() },
        )
    }
    // Repli extras/sauces : ensembles d'index sélectionnés.
    var extrasSel by remember(item.id) { mutableStateOf(emptySet<Int>()) }
    var saucesSel by remember(item.id) { mutableStateOf(emptySet<Int>()) }

    var showDialog by remember { mutableStateOf(false) }

    val surcharge = if (dataDriven) OptionSelection.surcharge(groups, selections)
    else extrasSel.sumOf { FALLBACK_EXTRAS[it].price } + saucesSel.sumOf { FALLBACK_SAUCES[it].price }
    val totalPrice = (item.price.toInt() + surcharge) * quantity

    fun toggleChoice(gi: Int, ci: Int) {
        selections = selections.mapIndexed { i, set ->
            if (i != gi) set
            else {
                val g = groups[gi]
                if (g.isSingle) { if (set.contains(ci) && !g.required) emptySet() else setOf(ci) }
                else { if (set.contains(ci)) set - ci else set + ci }
            }
        }
    }

    fun buildOrderItem(): OrderItem {
        val mapping = if (dataDriven) OptionSelection.toCart(groups, selections)
        else dj.velox.client.domain.model.OptionCartMapping(
            extras = extrasSel.map { FALLBACK_EXTRAS[it].copy(isSelected = true) },
            sauces = saucesSel.map { FALLBACK_SAUCES[it].copy(isSelected = true) },
        )
        return OrderItem(
            menuId = item.id, name = item.name, description = item.description,
            imageUrl = item.imageUrl ?: "", category = item.category,
            basePrice = item.price.toInt(), quantity = quantity,
            extras = mapping.extras, sauces = mapping.sauces,
        )
    }

    fun addAndClose() {
        if (cartViewModel.state.value.restaurant == null) cartViewModel.setRestaurant(restaurant)
        cartViewModel.addItem(buildOrderItem())
        onBack()
    }

    fun proceed() {
        if (dataDriven) {
            val missing = groups.indices.firstOrNull { groups[it].required && selections[it].isEmpty() }
            if (missing != null) {
                Toast.makeText(context, context.getString(R.string.please_choose, groups[missing].name), Toast.LENGTH_SHORT).show()
                return
            }
        }
        if (cartViewModel.state.value.isDifferentRestaurant(restaurant.id)) showDialog = true
        else addAndClose()
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        // Barre supérieure
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = c.primary)
            }
            Spacer(Modifier.weight(1f))
            Text(restaurant.name.uppercase(), color = c.primary, fontFamily = Poppins, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ShoppingCart, null, tint = c.onSurfaceVariant)
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            HeroImage(item, c)
            PriceAndQuantity(item, quantity, onMinus = { if (quantity > 1) quantity-- }, onPlus = { quantity++ }, c = c)

            if (dataDriven) {
                groups.forEachIndexed { gi, group ->
                    SectionHeader(if (group.name.isBlank()) stringResource(R.string.options_label).uppercase() else group.name.uppercase(), group.required, c)
                    group.choices.forEachIndexed { ci, choice ->
                        ChoiceRow(
                            label = choice.name.uppercase(),
                            trailing = if (choice.price > 0) "+ ${choice.price} FDJ" else stringResource(R.string.included_label).uppercase(),
                            selected = selections[gi].contains(ci),
                            isSingle = group.isSingle,
                            priced = choice.price > 0,
                            c = c,
                            onClick = { toggleChoice(gi, ci) },
                        )
                    }
                }
            } else {
                SectionHeader(stringResource(R.string.choose_extras).uppercase(), true, c)
                FALLBACK_EXTRAS.forEachIndexed { i, e ->
                    ChoiceRow(e.name.uppercase(), "+ ${e.price} FDJ", extrasSel.contains(i), isSingle = false, priced = true, c = c) {
                        extrasSel = if (extrasSel.contains(i)) extrasSel - i else extrasSel + i
                    }
                }
                SectionHeader(stringResource(R.string.choose_sauces).uppercase(), false, c)
                FALLBACK_SAUCES.forEachIndexed { i, s ->
                    ChoiceRow(s.name.uppercase(), "+ ${s.price} FDJ", saucesSel.contains(i), isSingle = false, priced = true, c = c) {
                        saucesSel = if (saucesSel.contains(i)) saucesSel - i else saucesSel + i
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // Bouton ajouter
        Box(Modifier.background(c.bg).navigationBarsPadding().padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(4.dp)).background(c.primary).clickable { proceed() },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${stringResource(R.string.add_to_cart).uppercase()} ($totalPrice FDJ)", color = c.onPrimary, fontFamily = Poppins, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                Spacer(Modifier.size(10.dp))
                Icon(Icons.Filled.Bolt, null, tint = c.onPrimary, modifier = Modifier.size(20.dp))
            }
        }
    }

    if (showDialog) {
        val currentName = cartViewModel.state.value.restaurant?.name ?: stringResource(R.string.another_restaurant)
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = c.surface,
            title = { Text(stringResource(R.string.different_restaurant_title), color = c.onSurface, fontFamily = Poppins, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.different_restaurant_msg, currentName, restaurant.name),
                    color = c.onSurfaceVariant, fontFamily = Inter,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    cartViewModel.clearCart()
                    cartViewModel.setRestaurant(restaurant)
                    cartViewModel.addItem(buildOrderItem())
                    onBack()
                }) { Text(stringResource(R.string.clear_cart), color = c.primary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.cancel), color = c.onSurfaceVariant) }
            },
        )
    }
}

// ── COMPOSANTS ───────────────────────────────────────────────────────────────

@Composable
private fun HeroImage(item: MenuItem, c: VeloxColors) {
    Box(Modifier.fillMaxWidth().height(300.dp)) {
        if (!item.imageUrl.isNullOrEmpty()) {
            AsyncImage(model = item.imageUrl, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(c.surfaceHigh))
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.4f to Color.Transparent, 0.75f to c.bg.copy(alpha = 0.5f), 1f to c.bg),
            ),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Box(
                Modifier.background(c.primary.copy(alpha = 0.15f)).border(1.dp, c.primary.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text("AVAILABLE_UNIT_01", color = c.primary, fontFamily = Inter, fontSize = 10.sp, fontWeight = FontWeight.W600, letterSpacing = 1.5.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(item.name.uppercase(), color = c.onSurface, fontFamily = Poppins, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp, lineHeight = 30.sp)
            if (item.description.isNotEmpty()) {
                Text(item.description, color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun PriceAndQuantity(item: MenuItem, quantity: Int, onMinus: () -> Unit, onPlus: () -> Unit, c: VeloxColors) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, top = 20.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.height(48.dp).width(2.dp).background(c.primary))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text("BASE COST", color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 10.sp, fontWeight = FontWeight.W600, letterSpacing = 1.8.sp)
            Spacer(Modifier.height(2.dp))
            Text("%.1f FDJ".format(item.price), color = c.primary, fontFamily = Poppins, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
        }
        Row(
            Modifier.clip(RoundedCornerShape(4.dp)).background(c.surfaceHigh),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QtyButton(Icons.Filled.Remove, enabled = quantity > 1, c = c, onClick = onMinus)
            Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                Text(quantity.toString().padStart(2, '0'), color = c.onSurface, fontFamily = Poppins, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            QtyButton(Icons.Filled.Add, enabled = true, c = c, onClick = onPlus)
        }
    }
}

@Composable
private fun QtyButton(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, c: VeloxColors, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).background(if (enabled) c.surface else c.surfaceLow).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (enabled) c.onSurface else c.outlineVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SectionHeader(title: String, required: Boolean, c: VeloxColors) {
    Column(Modifier.padding(start = 16.dp, top = 28.dp, end = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = c.onSurfaceVariant, fontFamily = Inter, fontSize = 11.sp, fontWeight = FontWeight.W600, letterSpacing = 2.sp)
            if (required) {
                Box(Modifier.background(c.primary.copy(alpha = 0.12f)).border(1.dp, c.primary.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text(stringResource(R.string.required_label).uppercase(), color = c.primary, fontFamily = Inter, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.outlineVariant.copy(alpha = 0.3f)))
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    trailing: String,
    selected: Boolean,
    isSingle: Boolean,
    priced: Boolean,
    c: VeloxColors,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (selected) c.primary.copy(alpha = 0.05f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(if (isSingle) androidx.compose.foundation.shape.CircleShape else RoundedCornerShape(2.dp))
                .background(if (selected) c.primary else Color.Transparent)
                .border(1.5.dp, if (selected) c.primary else c.outlineVariant, if (isSingle) androidx.compose.foundation.shape.CircleShape else RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Filled.Check, null, tint = c.onPrimary, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.size(14.dp))
        Text(label, color = if (selected) c.onSurface else c.onSurfaceVariant, fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
        Text(
            trailing,
            color = if (priced && selected) c.primary else c.onSurfaceVariant,
            fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.W600,
        )
    }
}
