package app.fahrtenbuch.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fahrtenbuch.mobile.BuildConfig
import app.fahrtenbuch.mobile.data.api.SessionStore
import app.fahrtenbuch.mobile.ui.AppViewModel
import app.fahrtenbuch.mobile.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenVehicles: () -> Unit = {}
) {
    var apiUrl by remember { mutableStateOf(BuildConfig.DEFAULT_API_URL) }
    val context = LocalContext.current
    val sessionStore = remember { SessionStore(context) }
    var trackingInterval by remember { mutableStateOf(sessionStore.trackingIntervalSeconds) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            "Einstellungen",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Darstellung",
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            fontWeight = FontWeight.SemiBold
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            ThemeMode.entries.forEach { mode ->
                ThemeModeRow(
                    label = when (mode) {
                        ThemeMode.SYSTEM -> "Systemeinstellung"
                        ThemeMode.LIGHT -> "Hell"
                        ThemeMode.DARK -> "Dunkel"
                    },
                    selected = themeMode == mode,
                    onClick = { onThemeModeChange(mode) }
                )
            }
        }

        Text(
            "Fahrzeuge",
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            fontWeight = FontWeight.SemiBold
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Fahrzeuge & Bluetooth", fontWeight = FontWeight.SemiBold)
                Text(
                    "Fahrzeuge vom Server laden und ein gekoppeltes Bluetooth-Gerät zuweisen.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                OutlinedButton(onClick = onOpenVehicles) {
                    Text("Fahrzeuge verwalten")
                }
            }
        }

        Text(
            "Tracking",
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            fontWeight = FontWeight.SemiBold
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("GPS-Intervall", fontWeight = FontWeight.SemiBold)
                Text(
                    "Nach dem ersten GPS-Fix wird die Position in diesem Intervall gespeichert.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
                listOf(5, 10, 15, 30, 60).forEach { seconds ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = trackingInterval == seconds,
                            onClick = {
                                trackingInterval = seconds
                                sessionStore.trackingIntervalSeconds = seconds
                            }
                        )
                        Text(if (seconds < 60) "$seconds Sekunden" else "60 Sekunden")
                    }
                }
            }
        }

        Text(
            "Server",
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            fontWeight = FontWeight.SemiBold
        )
        OutlinedTextField(
            value = apiUrl,
            onValueChange = { apiUrl = it },
            label = { Text("Server- oder API-URL") },
            supportingText = { Text("Beispiel: https://fahrtenbuch.example.de oder …/api/v1/") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { viewModel.changeServerUrl(apiUrl) },
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text("Speichern und verbinden")
        }

        Text(
            "Versionsinformationen",
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            fontWeight = FontWeight.SemiBold
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                VersionInfoRow("App-Version", BuildConfig.VERSION_NAME)
                VersionInfoRow("Build", BuildConfig.VERSION_CODE.toString())
                VersionInfoRow("Paket", BuildConfig.APPLICATION_ID)
            }
        }
    }
}

@Composable
private fun ThemeModeRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun VersionInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
