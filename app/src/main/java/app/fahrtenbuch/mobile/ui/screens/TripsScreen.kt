package app.fahrtenbuch.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
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
fun TripsScreen(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Fahrten", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        items(state.trips, key = { it.id }) { trip ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${trip.startAddress ?: "Start"} → ${trip.endAddress ?: "Ziel"}", fontWeight = FontWeight.Bold)
                    Text("${trip.tripType.label} · ${trip.vehicleName}")
                    Text("${trip.distanceKm ?: 0.0} km · ${trip.startedAt}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (trip.tags.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            trip.tags.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                        }
                    }
                }
            }
        }
    }
}
