package app.fahrtenbuch.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import app.fahrtenbuch.mobile.R

/** Dialog für Serveradresse und Zertifikatsprüfung – bewusst getrennt vom Profil. */
@Composable
fun ServerSettingsDialog(
    serverUrl: String,
    allowInvalidCertificates: Boolean,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (serverUrl: String, allowInvalidCertificates: Boolean) -> Unit
) {
    var url by rememberSaveable { mutableStateOf(serverUrl) }
    var allowInvalid by rememberSaveable { mutableStateOf(allowInvalidCertificates) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val isHttps = url.trim().startsWith("https://", ignoreCase = true)

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(stringResource(R.string.settings_server_settings)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.server_address)) },
                    supportingText = { Text(stringResource(R.string.protocol_note)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                if (isHttps) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.disable_cert_check), fontWeight = FontWeight.Medium)
                            Text(
                                stringResource(R.string.settings_cert_warning),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(checked = allowInvalid, onCheckedChange = { allowInvalid = it })
                    }
                } else if (url.trim().startsWith("http://", ignoreCase = true)) {
                    Text(
                        stringResource(R.string.settings_http_warning),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (submitted && !loading) {
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !loading && url.isNotBlank(),
                onClick = {
                    submitted = true
                    onSave(url.trim(), isHttps && allowInvalid)
                }
            ) {
                Text(stringResource(if (loading) R.string.settings_saving else R.string.save))
            }
        },
        dismissButton = {
            TextButton(enabled = !loading, onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
