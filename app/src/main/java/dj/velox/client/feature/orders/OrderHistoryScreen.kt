package dj.velox.client.feature.orders

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dj.velox.client.R
import dj.velox.client.domain.model.Order
import dj.velox.client.domain.model.Ride
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
private fun fmt(ms: Long) = dateFmt.format(Date(ms))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderHistoryScreen(
    onBack: () -> Unit,
    vm: OrdersViewModel = hiltViewModel(),
) {
    val foodOrders by vm.foodOrders.collectAsStateWithLifecycle()
    val rides by vm.rides.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_orders)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        ) {
            item { SectionTitle("${stringResource(R.string.tab_food)} (${foodOrders.size})") }
            items(foodOrders, key = { it.id }) { FoodOrderCard(it); Spacer(Modifier.height(8.dp)) }

            item { Spacer(Modifier.height(16.dp)); SectionTitle("${stringResource(R.string.rides)} (${rides.size})") }
            items(rides, key = { it.rideId }) { RideCard(it); Spacer(Modifier.height(8.dp)) }

            if (foodOrders.isEmpty() && rides.isEmpty()) {
                item { Text(stringResource(R.string.no_active_orders), style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun FoodOrderCard(order: Order) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(order.restaurantName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text("${order.total} FDJ", style = MaterialTheme.typography.titleSmall)
            }
            Text(
                "${Order.statusText(order.status)} · ${order.itemCount} ${stringResource(R.string.items)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(fmt(order.createdAt), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RideCard(ride: Ride) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(ride.destination.address.ifBlank { "Course" },
                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text("${(ride.finalFare ?: ride.estimatedFare).toInt()} FDJ",
                    style = MaterialTheme.typography.titleSmall)
            }
            Text(ride.status.name.lowercase(), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(fmt(ride.requestedAt), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
