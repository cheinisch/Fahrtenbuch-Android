package app.fahrtenbuch.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fahrtenbuch.mobile.R
import app.fahrtenbuch.mobile.ui.permissions.PermissionChecklist
import app.fahrtenbuch.mobile.ui.permissions.PermissionStatus
import app.fahrtenbuch.mobile.ui.permissions.openAppSettings

/**
 * Berechtigungen prüfen und bei Bedarf neu anfordern. Der Status wird beim Öffnen und nach
 * jeder Rückkehr aus den Systemeinstellungen neu gelesen. Entziehen geht nur in den
 * Android-Einstellungen, daher der Button „App-Einstellungen“.
 */
@Composable
fun PermissionsDialog(
    status: PermissionStatus,
    onChanged: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.permissions_settings_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    if (status.allGranted) stringResource(R.string.permissions_all_granted)
                    else pluralStringResource(R.plurals.permissions_missing, status.missingCount, status.missingCount),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (status.allGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                PermissionChecklist(status = status, onChanged = onChanged, showBattery = true)
                Text(
                    stringResource(R.string.permissions_settings_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
        dismissButton = {
            TextButton(onClick = { openAppSettings(context) }) { Text(stringResource(R.string.permissions_app_settings)) }
        }
    )
}
