package app.fahrtenbuch.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fahrtenbuch.mobile.ui.AppViewModel

@Composable
fun VehiclesScreen(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Fahrzeuge", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        items(state.vehicles, key = { it.id }) { vehicle ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Icon(Icons.Default.DirectionsCar, contentDescription = null)
                    Text(vehicle.name, fontWeight = FontWeight.Bold)
                    Text(vehicle.licensePlate ?: "Kein Kennzeichen", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
