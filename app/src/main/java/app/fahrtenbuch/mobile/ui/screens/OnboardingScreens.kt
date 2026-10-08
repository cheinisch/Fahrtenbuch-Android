package app.fahrtenbuch.mobile.ui.screens

import androidx.compose.ui.res.stringResource
import app.fahrtenbuch.mobile.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import app.fahrtenbuch.mobile.data.model.PairQrPayload
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.json.JSONObject

@Composable
fun WelcomeScreen(onConnect: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Icon(Icons.Outlined.DirectionsCar, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(62.dp))
        Spacer(Modifier.height(24.dp))
        Text("Fahrtenbuch", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        Text(stringResource(R.string.your_trips_data), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(22.dp))
        Text(
            "Verbinde die App mit deinem selbst gehosteten Fahrtenbuch-Server und melde dieses Gerät sicher an.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.weight(1f))
        Button(onClick = onConnect, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(16.dp)) {
            Text(stringResource(R.string.connect_server), fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.self_hosted), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
fun ConnectServerScreen(onBack: () -> Unit, onQrCode: () -> Unit, onManual: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp)) {
        BackButton(onBack)
        Spacer(Modifier.height(32.dp))
        Text(stringResource(R.string.connect_your_server), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Text(
            "Scanne den Anmelde-QR-Code aus deinem Web-Fahrtenbuch oder richte den Server manuell ein.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(42.dp))
        ConnectionCard(Icons.Outlined.QrCodeScanner, "QR-Code scannen", "Server verbinden und dieses Gerät direkt anmelden", onQrCode)
        Spacer(Modifier.height(20.dp))
        ConnectionCard(Icons.Outlined.Settings, "Manuell einrichten", "Serveradresse eingeben und anschließend klassisch anmelden", onManual)
    }
}

@Composable
private fun ConnectionCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
            Spacer(Modifier.width(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ManualServerSetupScreen(onBack: () -> Unit, onSave: (String) -> Result<Unit>) {
    var serverUrl by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp)) {
        BackButton(onBack)
        Spacer(Modifier.height(32.dp))
        Text(stringResource(R.string.manual_server_setup), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Text(
            "Trage die öffentliche Adresse deiner Fahrtenbuch-Installation ein. /api/v1 wird automatisch ergänzt.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(36.dp))
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it; error = null },
            label = { Text(stringResource(R.string.server_address)) },
            placeholder = { Text("https://fahrtenbuch.example.de") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true,
            isError = error != null,
            supportingText = { Text(error ?: "HTTPS wird für externe Server empfohlen.") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onSave(serverUrl).onFailure { error = it.message ?: "Ungültige Serveradresse." } },
            enabled = serverUrl.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text(stringResource(R.string.save_server_login), fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
fun QrPairingScreen(
    onBack: () -> Unit,
    loading: Boolean,
    apiError: String?,
    onPairCode: (PairQrPayload) -> Unit
) {
    var scanning by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(ScanContract()) { result ->
        scanning = false
        val raw = result.contents
        if (raw.isNullOrBlank()) {
            localError = "Es wurde kein QR-Code erkannt."
        } else {
            runCatching { parsePairQrPayload(raw) }
                .onSuccess { onPairCode(it) }
                .onFailure { localError = it.message ?: "Ungültiger Anmelde-QR-Code." }
        }
    }

    fun startScan() {
        localError = null
        scanning = true
        launcher.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Anmelde-QR-Code scannen")
                .setBeepEnabled(false)
                .setOrientationLocked(true)
        )
    }

    LaunchedEffect(Unit) { startScan() }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp)) {
        BackButton(onBack)
        Spacer(Modifier.height(32.dp))
        Text(stringResource(R.string.pair_device), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Text(
            "Öffne im Web-Fahrtenbuch „Mobile App verbinden“ und scanne den dort angezeigten einmaligen QR-Code.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(48.dp))

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.device_signing_in))
                }
                scanning -> CircularProgressIndicator()
                else -> OutlinedButton(onClick = { startScan() }, modifier = Modifier.height(54.dp), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Outlined.QrCodeScanner, null)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.scan_again))
                }
            }
        }

        val error = localError ?: apiError
        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(28.dp))
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}


@Composable
fun PairConfirmationScreen(
    payload: PairQrPayload,
    loading: Boolean,
    apiError: String?,
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    Scaffold(
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        enabled = !loading,
                        modifier = Modifier.weight(1f).height(54.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Abbrechen")
                    }
                    Button(
                        onClick = onConfirm,
                        enabled = !loading,
                        modifier = Modifier.weight(1f).height(54.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(stringResource(R.string.connect), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 18.dp)
        ) {
            BackButton(onBack)
            Spacer(Modifier.height(24.dp))
            Icon(
                Icons.Outlined.VerifiedUser,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(52.dp)
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "Verbindung bestätigen",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Prüfe, ob Server und Benutzerkonto korrekt sind. Erst danach wird dieses Gerät vollständig angemeldet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(28.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    PairConfirmationRow(Icons.Outlined.Dns, "Server", payload.server)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    PairConfirmationRow(
                        Icons.Outlined.Person,
                        "Benutzer",
                        payload.username?.takeIf { it.isNotBlank() } ?: "Nicht angegeben"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    PairConfirmationRow(
                        Icons.Outlined.Email,
                        "E-Mail",
                        payload.email?.takeIf { it.isNotBlank() } ?: "Nicht angegeben"
                    )
                    payload.expiresAt?.takeIf { it.isNotBlank() }?.let {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        PairConfirmationRow(Icons.Outlined.Schedule, "Gültig bis", formatPairExpiry(it))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Outlined.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Pairing-ID und Anmeldetoken werden aus Sicherheitsgründen nicht angezeigt.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!apiError.isNullOrBlank()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    apiError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun PairConfirmationRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
    }
}

private fun formatPairExpiry(value: String): String = runCatching {
    val formatter = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.GERMANY)
        .withZone(ZoneId.systemDefault())
    formatter.format(Instant.parse(value))
}.getOrDefault(value)

private fun parsePairQrPayload(raw: String): PairQrPayload {
    val json = JSONObject(raw)
    val type = json.optString("type", "pair")
    require(type == "pair") { "Der QR-Code ist kein Fahrtenbuch-Anmeldecode." }

    val server = json.optString("server").trim()
    val pairId = json.optString("pairId").trim()
    val pairToken = json.optString("pairToken").trim()
    require(server.startsWith("https://") || server.startsWith("http://")) { "Im QR-Code fehlt eine gültige Serveradresse." }
    require(pairId.isNotBlank()) { "Im QR-Code fehlt die Pairing-ID." }
    require(pairToken.isNotBlank()) { "Im QR-Code fehlt der einmalige Pairing-Token." }

    return PairQrPayload(
        version = json.optInt("version", 1),
        type = type,
        server = server,
        pairId = pairId,
        pairToken = pairToken,
        username = json.optString("username").ifBlank { null },
        email = json.optString("email").ifBlank { null },
        expiresAt = json.optString("expiresAt").ifBlank { json.optString("expires").ifBlank { null } }
    ).also { payload ->
        require(payload.version == 1) { "Diese Version des Anmelde-QR-Codes wird nicht unterstützt." }
        payload.expiresAt?.let { expiry ->
            val expired = runCatching { Instant.parse(expiry).isBefore(Instant.now()) }.getOrDefault(false)
            require(!expired) { "Der Anmelde-QR-Code ist abgelaufen." }
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Zurück") }
}
