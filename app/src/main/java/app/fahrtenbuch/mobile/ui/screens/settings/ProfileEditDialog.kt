package app.fahrtenbuch.mobile.ui.screens.settings

import app.fahrtenbuch.mobile.data.model.AppFeatures
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import app.fahrtenbuch.mobile.R
import app.fahrtenbuch.mobile.data.model.User

/** Dialog für Anzeigename, Anmeldename und E-Mail. Server-Einstellungen liegen in [ServerSettingsDialog]. */
@Composable
fun ProfileEditDialog(
    user: User?,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (displayName: String, username: String, email: String) -> Unit,
    gravatarEnabled: Boolean = true,
    gravatarAvailable: Boolean = true,
    /** Wird sofort gespeichert, unabhängig vom Speichern-Button. */
    onGravatarChange: (Boolean) -> Unit = {},
    mapMatchingEnabled: Boolean = true,
    /** False: auf dem Server ist kein Map-Matching eingerichtet – Schalter gesperrt. */
    mapMatchingAvailable: Boolean = false,
    /** Wird sofort gespeichert, wie Gravatar. */
    onMapMatchingChange: (Boolean) -> Unit = {},
    /** Max. Pause in Sekunden fürs Zusammenführen von Fahrten (0–900); wird wie Gravatar sofort gespeichert. */
    tripMergeGapSeconds: Int = 120,
    onTripMergeGapChange: (Int) -> Unit = {}
) {
    var displayName by rememberSaveable { mutableStateOf(user?.displayName.orEmpty()) }
    var username by rememberSaveable { mutableStateOf((user?.loginName ?: user?.username).orEmpty()) }
    var email by rememberSaveable { mutableStateOf(user?.email.orEmpty()) }
    // Fehler erst nach einem Speicherversuch zeigen, nicht ältere aus anderen Bereichen.
    var submitted by rememberSaveable { mutableStateOf(false) }
    // Zeitfenster fürs Zusammenführen: gespeichert bei „Fertig“, Fokusverlust und Speichern
    var mergeGapText by rememberSaveable(tripMergeGapSeconds) { mutableStateOf(tripMergeGapSeconds.toString()) }
    val mergeGapValue = mergeGapText.toIntOrNull()
    val mergeGapValid = mergeGapValue != null && mergeGapValue in 0..900
    fun commitMergeGap() {
        if (mergeGapValue != null && mergeGapValid && mergeGapValue != tripMergeGapSeconds) onTripMergeGapChange(mergeGapValue)
    }

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
                // Profilbild von Gravatar: sofort umschaltbar, Server kann es instanzweit sperren
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.gravatar_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(if (gravatarAvailable) R.string.gravatar_text else R.string.gravatar_disabled_by_server),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = gravatarEnabled && gravatarAvailable,
                        enabled = gravatarAvailable && !loading,
                        onCheckedChange = onGravatarChange
                    )
                }
                // Map-Matching verwenden: automatisch matchen und Straßenroute zeigen (vorerst ausgeblendet)
                if (AppFeatures.SHOW_MAP_MATCHING) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(R.string.map_matching_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(if (mapMatchingAvailable) R.string.map_matching_text else R.string.map_matching_unavailable),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = mapMatchingEnabled && mapMatchingAvailable,
                            enabled = mapMatchingAvailable && !loading,
                            onCheckedChange = onMapMatchingChange
                        )
                    }
                }
                OutlinedTextField(
                    value = mergeGapText,
                    onValueChange = { mergeGapText = it.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.trip_merge_gap_label)) },
                    supportingText = { Text(stringResource(if (mergeGapValid) R.string.trip_merge_gap_text else R.string.trip_merge_gap_invalid)) },
                    isError = !mergeGapValid,
                    singleLine = true,
                    enabled = !loading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commitMergeGap() }),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).onFocusChanged { if (!it.isFocused) commitMergeGap() }
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
                    commitMergeGap()
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
