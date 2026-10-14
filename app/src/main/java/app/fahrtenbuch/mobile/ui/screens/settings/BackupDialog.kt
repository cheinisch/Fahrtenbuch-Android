package app.fahrtenbuch.mobile.ui.screens.settings

import android.app.backup.BackupManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fahrtenbuch.mobile.R
import app.fahrtenbuch.mobile.backup.BackupArchive
import app.fahrtenbuch.mobile.data.api.SessionStore
import kotlinx.coroutines.launch

/**
 * Sicherung: optional ins Google-Konto (Android-Backup) und jederzeit als ZIP-Datei.
 * Anmeldedaten (Tokens, Geräte-ID) werden nie gesichert – nach einer Wiederherstellung
 * meldet man sich im Server-Modus neu an (Server-Adresse und Anmeldename sind vorausgefüllt).
 */
@Composable
fun BackupDialog(
    store: SessionStore,
    serverMode: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var googleEnabled by remember { mutableStateOf(store.googleBackupEnabled) }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    var restored by remember { mutableStateOf(false) }

    val exportFailed = stringResource(R.string.backup_error_export)
    val restoreFailed = stringResource(R.string.backup_error_restore)
    val invalidFile = stringResource(R.string.backup_error_invalid)
    val newerFile = stringResource(R.string.backup_error_newer)
    val res = context.resources

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupArchive.MIME_TYPE)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        working = true
        error = null
        scope.launch {
            runCatching { BackupArchive.export(context, uri) }
                .onSuccess { summary ->
                    message = res.getString(R.string.backup_export_done) + " " +
                        res.getQuantityString(R.plurals.backup_summary, summary.trips, summary.vehicles, summary.trips)
                }
                .onFailure { error = exportFailed }
            working = false
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = uri
    }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(stringResource(R.string.backup_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Google-Konto (Android-Backup)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.backup_google_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(if (serverMode) R.string.backup_google_text_server else R.string.backup_google_text_local),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = googleEnabled,
                        enabled = !working,
                        onCheckedChange = { enabled ->
                            googleEnabled = enabled
                            store.googleBackupEnabled = enabled
                            // Android sichert dann beim nächsten Lauf (meist nachts, im WLAN, beim Laden)
                            BackupManager(context).dataChanged()
                        }
                    )
                }
                Text(stringResource(R.string.backup_google_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                HorizontalDivider()

                // ZIP-Datei
                Text(stringResource(R.string.backup_zip_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.backup_zip_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(
                    onClick = { exportLauncher.launch(BackupArchive.fileName()) },
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.backup_export), modifier = Modifier.weight(1f))
                }
                OutlinedButton(
                    onClick = { restoreLauncher.launch(arrayOf(BackupArchive.MIME_TYPE, "application/octet-stream")) },
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.backup_restore), modifier = Modifier.weight(1f))
                }

                if (working) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.backup_working))
                    }
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(enabled = !working, onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )

    // Wiederherstellung bestätigen: ersetzt alle lokalen Daten
    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { if (!working && !restored) pendingRestore = null },
            title = { Text(stringResource(if (restored) R.string.backup_restore_done_title else R.string.backup_restore_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        when {
                            restored -> R.string.backup_restore_done_text
                            serverMode -> R.string.backup_restore_confirm_server
                            else -> R.string.backup_restore_confirm_local
                        }
                    )
                )
            },
            confirmButton = {
                if (restored) {
                    TextButton(onClick = { BackupArchive.restartApp(context) }) { Text(stringResource(R.string.backup_restart)) }
                } else {
                    TextButton(
                        enabled = !working,
                        onClick = {
                            working = true
                            error = null
                            scope.launch {
                                runCatching { BackupArchive.restore(context, uri) }
                                    .onSuccess { restored = true }
                                    .onFailure {
                                        pendingRestore = null
                                        error = when (it) {
                                            is BackupArchive.InvalidBackup -> invalidFile
                                            is BackupArchive.UnsupportedBackup -> newerFile
                                            else -> restoreFailed
                                        }
                                    }
                                working = false
                            }
                        }
                    ) { Text(stringResource(R.string.backup_restore_action)) }
                }
            },
            dismissButton = {
                if (!restored) TextButton(enabled = !working, onClick = { pendingRestore = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
