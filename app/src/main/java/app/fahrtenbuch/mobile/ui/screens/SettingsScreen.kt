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
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.fahrtenbuch.mobile.R
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fahrtenbuch.mobile.BuildConfig
import app.fahrtenbuch.mobile.data.api.SessionStore
import app.fahrtenbuch.mobile.data.api.AppMode
import app.fahrtenbuch.mobile.ui.AppViewModel
import app.fahrtenbuch.mobile.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenVehicles: () -> Unit = {}
) {
    val appState by viewModel.state.collectAsState()
    var editingAccount by rememberSaveable { mutableStateOf(false) }
    var displayName by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var serverUrl by rememberSaveable { mutableStateOf(appState.serverUrl) }
    val context = LocalContext.current
    val sessionStore = remember { SessionStore(context) }
    var allowInvalidCertificates by rememberSaveable {
        mutableStateOf(sessionStore.allowInvalidCertificates)
    }
    var trackingInterval by remember { mutableStateOf(sessionStore.trackingIntervalSeconds) }

    LaunchedEffect(Unit) { viewModel.loadAccount() }
    LaunchedEffect(appState.user?.id, editingAccount) {
        if (!editingAccount) {
            displayName = appState.user?.displayName.orEmpty()
            username = (appState.user?.loginName ?: appState.user?.username).orEmpty()
            email = appState.user?.email.orEmpty()
            serverUrl = appState.serverUrl
        }
    }

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
            "Konto",
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            fontWeight = FontWeight.SemiBold
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                AccountInfoRow("Anzeigename", appState.user?.displayName ?: "–")
                AccountInfoRow(
                    "Anmeldename",
                    appState.user?.loginName ?: appState.user?.username ?: "–"
                )
                AccountInfoRow("E-Mail", appState.user?.email ?: "–")
                AccountInfoRow("Rolle", appState.user?.role ?: "–")
                AccountInfoRow("Server", appState.serverUrl)
                if (appState.serverUrl.startsWith("https://", ignoreCase = true)) {
                    AccountInfoRow(
                        "Zertifikatsprüfung",
                        if (sessionStore.allowInvalidCertificates) "Deaktiviert" else "Aktiv"
                    )
                }
                appState.user?.lastLoginAt?.let { AccountInfoRow("Letzte Anmeldung", it) }
                OutlinedButton(
                    onClick = { editingAccount = true },
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Text(stringResource(R.string.edit_profile))
                }
            }
        }

        if (appState.appMode == AppMode.LOCAL) {
            Text(
                stringResource(R.string.storage_mode),
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                fontWeight = FontWeight.SemiBold
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.local_mode_title), fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.local_mode_migration_help),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    OutlinedButton(onClick = { viewModel.startServerMigrationSetup() }) {
                        Text(stringResource(R.string.migrate_to_server))
                    }
                }
            }
        }

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
                Text(stringResource(R.string.vehicles_bluetooth), fontWeight = FontWeight.SemiBold)
                Text(
                    "Fahrzeuge vom Server laden und ein gekoppeltes Bluetooth-Gerät zuweisen.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                OutlinedButton(onClick = onOpenVehicles) {
                    Text(stringResource(R.string.manage_vehicles))
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
                Text(stringResource(R.string.gps_interval), fontWeight = FontWeight.SemiBold)
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

    if (editingAccount) {
        AlertDialog(
            onDismissRequest = {
                if (!appState.loading) editingAccount = false
            },
            title = { Text(stringResource(R.string.edit_profile)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text(stringResource(R.string.display_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.login_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(stringResource(R.string.email_address)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text(stringResource(R.string.server_address)) },
                        supportingText = { Text(stringResource(R.string.protocol_note)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (serverUrl.trim().startsWith("https://", ignoreCase = true)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.disable_cert_check), fontWeight = FontWeight.Medium)
                                Text(
                                    "Nur für selbstsignierte oder ungültige Zertifikate.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Switch(
                                checked = allowInvalidCertificates,
                                onCheckedChange = { allowInvalidCertificates = it }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !appState.loading && username.isNotBlank() && email.isNotBlank(),
                    onClick = {
                        viewModel.updateAccount(
                            displayName,
                            username,
                            email,
                            serverUrl,
                            allowInvalidCertificates
                        ) {
                            editingAccount = false
                        }
                    }
                ) {
                    Text(if (appState.loading) "Speichern …" else "Speichern")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !appState.loading,
                    onClick = {
                        editingAccount = false
                        displayName = appState.user?.displayName.orEmpty()
                        username = (appState.user?.loginName ?: appState.user?.username).orEmpty()
                        email = appState.user?.email.orEmpty()
                        serverUrl = appState.serverUrl
                        allowInvalidCertificates = sessionStore.allowInvalidCertificates
                    }
                ) {
                    Text("Abbrechen")
                }
            }
        )
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

@Composable
private fun AccountInfoRow(label: String, value: String) {
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
