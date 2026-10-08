package app.fahrtenbuch.mobile.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fahrtenbuch.mobile.location.TripTrackingService
import app.fahrtenbuch.mobile.ui.AppViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val latestTrip = state.trips.firstOrNull()
    LaunchedEffect(latestTrip?.id, latestTrip?.syncState) {
        if (latestTrip?.syncState == "SYNCED") viewModel.refreshLatestTripDetails()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) viewModel.startTracking { startTrackingService(context) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Tracking", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Fahrt starten, aufzeichnen und beenden", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Fahrzeug", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.vehicles.forEach { vehicle ->
                            FilterChip(
                                selected = state.selectedVehicleId == vehicle.id,
                                onClick = { viewModel.selectVehicle(vehicle.id) },
                                label = { Text(vehicle.name) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Button(
                modifier = Modifier.fillMaxWidth().height(58.dp),
                onClick = {
                    if (state.tracking) viewModel.stopTracking { stopTrackingService(context) }
                    else {
                        val permissions = buildList {
                            add(Manifest.permission.ACCESS_FINE_LOCATION)
                            add(Manifest.permission.ACCESS_COARSE_LOCATION)
                            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                        }.toTypedArray()
                        permissionLauncher.launch(permissions)
                    }
                }
            ) {
                Icon(if (state.tracking) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.tracking) "Fahrt beenden" else "Fahrt starten")
            }
        }

        item {
            Text("Letzte Fahrt", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val trip = state.trips.firstOrNull()
            if (trip != null) {
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "${trip.startAddress?.takeIf { it.isNotBlank() } ?: "Startort nicht verfügbar"} → " +
                                (trip.endAddress?.takeIf { it.isNotBlank() } ?: "Zielort nicht verfügbar"),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text("${trip.tripType.label} · ${trip.vehicleName}")
                        Text("${trip.distanceKm ?: 0.0} km · ${formatTripDate(trip.startedAt)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun startTrackingService(context: Context) {
    val intent = Intent(context, TripTrackingService::class.java).setAction(TripTrackingService.ACTION_START)
    context.startForegroundService(intent)
}

private fun stopTrackingService(context: Context) {
    val intent = Intent(context, TripTrackingService::class.java).setAction(TripTrackingService.ACTION_STOP)
    context.startService(intent)
}

private fun formatTripDate(value: String): String = runCatching {
    val date = Instant.parse(value).atZone(ZoneId.systemDefault())
    DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'", Locale.GERMANY).format(date)
}.getOrElse { value }
