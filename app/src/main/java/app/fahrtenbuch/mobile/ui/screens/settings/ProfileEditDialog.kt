package app.fahrtenbuch.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.fahrtenbuch.mobile.R
import app.fahrtenbuch.mobile.data.model.User

/** Dialog für Anzeigename, Anmeldename und E-Mail. Server-Einstellungen liegen in [ServerSettingsDialog]. */
@Composable
fun ProfileEditDialog(
    user: User?,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (displayName: String, username: String, email: String) -> Unit
) {
    var displayName by rememberSaveable { mutableStateOf(user?.displayName.orEmpty()) }
    var username by rememberSaveable { mutableStateOf((user?.loginName ?: user?.username).orEmpty()) }
    var email by rememberSaveable { mutableStateOf(user?.email.orEmpty()) }
    // Fehler erst nach einem Speicherversuch zeigen, nicht ältere aus anderen Bereichen.
    var submitted by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(stringResource(R.string.edit_profile)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                formatLastLogin(user?.lastLoginAt)?.let {
                    Text(
                        stringResource(R.string.settings_last_login, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (submitted && !loading) {
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !loading && username.isNotBlank() && email.isNotBlank(),
                onClick = {
                    submitted = true
                    onSave(displayName.trim(), username.trim(), email.trim())
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

private fun formatLastLogin(value: String?): String? {
    if (value.isNullOrBlank()) return null
    return runCatching {
        java.time.format.DateTimeFormatter
            .ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM, java.time.format.FormatStyle.SHORT)
            .withLocale(java.util.Locale.getDefault())
            .withZone(java.time.ZoneId.systemDefault())
            .format(java.time.Instant.parse(value))
    }.getOrElse { value }
}
