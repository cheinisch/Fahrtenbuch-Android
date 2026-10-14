package app.fahrtenbuch.mobile.ui.screens.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import app.fahrtenbuch.mobile.data.model.Tag
import app.fahrtenbuch.mobile.data.model.Trip
import app.fahrtenbuch.mobile.ui.common.tagColor

/**
 * Tags einer Fahrt wählen (Mehrfachauswahl) und bei Bedarf direkt neue anlegen,
 * z. B. „Urlaub“. Gespeichert wird erst mit „Speichern“; neue Tags werden sofort angelegt
 * und ausgewählt.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TripTagsDialog(
    trip: Trip,
    available: List<Tag>,
    saving: Boolean,
    error: String?,
    onCreate: (name: String, onCreated: (Tag) -> Unit) -> Unit,
    onSave: (List<Tag>) -> Unit,
    onDismiss: () -> Unit
) {
    // Auswahl über die IDs; Tags der Fahrt, die (noch) nicht in der Liste sind, bleiben sichtbar
    var selectedIds by rememberSaveable(trip.id) { mutableStateOf(trip.tags.map { it.id }) }
    var newName by rememberSaveable(trip.id) { mutableStateOf("") }
    val all = (available + trip.tags)
        .distinctBy { it.id }
        .sortedBy { it.name.lowercase() }

    fun addNew() {
        val name = newName.trim()
        if (name.isEmpty()) return
        val existing = all.firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (existing != null) {
            if (existing.id !in selectedIds) selectedIds = selectedIds + existing.id
            newName = ""
            return
        }
        onCreate(name) { tag ->
            if (tag.id !in selectedIds) selectedIds = selectedIds + tag.id
            newName = ""
        }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.trip_tags_edit_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (all.isEmpty()) {
                    Text(
                        stringResource(R.string.trip_tags_none_available),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        all.forEach { tag ->
                            val selected = tag.id in selectedIds
                            FilterChip(
                                selected = selected,
                                enabled = !saving,
                                onClick = {
                                    selectedIds = if (selected) selectedIds - tag.id else selectedIds + tag.id
                                },
                                label = { Text(tag.name) },
                                leadingIcon = if (selected) {
                                    @Composable { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                                } else {
                                    tagColor(tag)?.let { color -> @Composable { Box(Modifier.size(10.dp).background(color, CircleShape)) } }
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = newName,
                    onValueChange = { if (it.length <= 100) newName = it },
                    label = { Text(stringResource(R.string.trip_tags_new)) },
                    placeholder = { Text(stringResource(R.string.trip_tags_new_hint)) },
                    singleLine = true,
                    enabled = !saving,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addNew() }),
                    trailingIcon = {
                        IconButton(enabled = !saving && newName.isNotBlank(), onClick = { addNew() }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.trip_tags_add))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = { onSave(all.filter { it.id in selectedIds }) }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
