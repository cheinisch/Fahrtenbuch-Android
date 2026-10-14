package app.fahrtenbuch.mobile.ui.screens.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.fahrtenbuch.mobile.R
import app.fahrtenbuch.mobile.data.model.Trip

/**
 * Fahrtzweck, Kontakt und Notiz einer Fahrt bearbeiten. Leere Felder werden gelöscht.
 * Der Dialog bleibt offen, bis das Speichern geklappt hat (Fehler werden hier angezeigt).
 */
@Composable
internal fun TripPurposeDialog(
    trip: Trip,
    saving: Boolean,
    error: String?,
    onSave: (purpose: String, contact: String, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    var purpose by rememberSaveable(trip.id) { mutableStateOf(trip.purpose.orEmpty()) }
    var contact by rememberSaveable(trip.id) { mutableStateOf(trip.contact.orEmpty()) }
    var note by rememberSaveable(trip.id) { mutableStateOf(trip.note.orEmpty()) }
    val sentences = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next)

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.trip_purpose_edit_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = purpose,
                    onValueChange = { if (it.length <= 1000) purpose = it },
                    label = { Text(stringResource(R.string.trip_detail_purpose)) },
                    placeholder = { Text(stringResource(R.string.trip_purpose_hint)) },
                    leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
                    singleLine = true,
                    enabled = !saving,
                    keyboardOptions = sentences,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = contact,
                    onValueChange = { if (it.length <= 1000) contact = it },
                    label = { Text(stringResource(R.string.trip_detail_contact)) },
                    placeholder = { Text(stringResource(R.string.trip_contact_hint)) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    enabled = !saving,
                    keyboardOptions = sentences.copy(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 20_000) note = it },
                    label = { Text(stringResource(R.string.trip_detail_note)) },
                    leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null) },
                    minLines = 3,
                    maxLines = 6,
                    enabled = !saving,
                    keyboardOptions = sentences.copy(imeAction = ImeAction.Default),
                    modifier = Modifier.fillMaxWidth()
                )
                if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = { onSave(purpose, contact, note) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
