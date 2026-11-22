package app.fahrtenbuch.mobile.ui.screens.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fahrtenbuch.mobile.R
import app.fahrtenbuch.mobile.data.api.SessionStore
import app.fahrtenbuch.mobile.location.GpsDebugLog
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Modal für den GPS-Debug-Modus: Schalter und Liste der Logdateien (eine pro Fahrt)
 * mit Herunterladen und Löschen.
 */
@Composable
fun GpsDebugDialog(
    store: SessionStore,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(store.gpsDebugMode) }
    var files by remember { mutableStateOf(GpsDebugLog.list(context)) }
    var pendingFile by remember { mutableStateOf<GpsDebugLog.LogFile?>(null) }
    var exportFailed by remember { mutableStateOf(false) }
    val exportFailedText = stringResource(R.string.gps_debug_log_export_failed)

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val file = pendingFile
        pendingFile = null
        if (uri == null || file == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                file.file.inputStream().use { input -> input.copyTo(out) }
            } ?: error("no output stream")
        }.isSuccess
        exportFailed = !ok
    }
    val dateFormat = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gps_debug_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            stringResource(R.string.gps_debug_dialog_toggle),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.gps_debug_dialog_toggle_text),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { value ->
                            enabled = value
                            store.gpsDebugMode = value
                            // Die Debug-Benachrichtigung braucht ab Android 13 die Benachrichtigungsberechtigung
                            if (value && Build.VERSION.SDK_INT >= 33) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )
                }

                HorizontalDivider()

                Text(
                    stringResource(R.string.gps_debug_logs_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (exportFailed) {
                    Text(exportFailedText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (files.isEmpty()) {
                    Text(
                        stringResource(R.string.gps_debug_logs_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                files.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "${
                                    LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(entry.modifiedMs), ZoneId.systemDefault())
                                        .format(dateFormat)
                                } · ${entry.sizeBytes / 1024} KB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            pendingFile = entry
                            exportFailed = false
                            saveLauncher.launch(entry.name)
                        }) {
                            Icon(
                                Icons.Outlined.FileDownload,
                                contentDescription = stringResource(R.string.gps_debug_log_download)
                            )
                        }
                        IconButton(onClick = {
                            GpsDebugLog.delete(entry.file)
                            files = files.filterNot { it.file == entry.file }
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.gps_debug_log_delete)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}
