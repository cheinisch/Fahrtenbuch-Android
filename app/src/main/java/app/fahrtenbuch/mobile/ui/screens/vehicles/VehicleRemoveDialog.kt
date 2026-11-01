package app.fahrtenbuch.mobile.ui.screens.vehicles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.fahrtenbuch.mobile.R

/**
 * Fragt beim Entfernen, ob das Fahrzeug nur abgemeldet (deaktiviert) oder gelöscht wird.
 * Die Fahrten bleiben in beiden Fällen erhalten, daher gibt es dazu keine Auswahl.
 */
@Composable
fun VehicleRemoveDialog(
    vehicleName: String,
    loading: Boolean,
    alreadyInactive: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (deactivateOnly: Boolean) -> Unit
) {
    // Ein bereits abgemeldetes Fahrzeug kann nur noch gelöscht werden.
    var deactivateOnly by remember(vehicleName, alreadyInactive) { mutableStateOf(!alreadyInactive) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(stringResource(R.string.vehicles_remove_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.vehicles_remove_question, vehicleName))

                if (!alreadyInactive) {
                    Column(
                        modifier = Modifier.selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        RemoveOption(
                            selected = deactivateOnly,
                            enabled = !loading,
                            title = stringResource(R.string.vehicles_remove_deactivate),
                            description = stringResource(R.string.vehicles_remove_deactivate_hint),
                            onSelect = { deactivateOnly = true }
                        )
                        RemoveOption(
                            selected = !deactivateOnly,
                            enabled = !loading,
                            title = stringResource(R.string.vehicles_remove_delete),
                            description = stringResource(R.string.vehicles_remove_delete_hint),
                            onSelect = { deactivateOnly = false }
                        )
                    }
                }

                Text(
                    if (deactivateOnly) {
                        stringResource(R.string.vehicles_remove_trips_hint)
                    } else {
                        stringResource(R.string.vehicles_remove_trips_hint_delete)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(deactivateOnly) },
                enabled = !loading,
                colors = if (deactivateOnly) {
                    ButtonDefaults.buttonColors()
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                }
            ) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        stringResource(
                            if (deactivateOnly) R.string.vehicles_remove_deactivate
                            else R.string.vehicles_remove_delete
                        )
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun RemoveOption(
    selected: Boolean,
    enabled: Boolean,
    title: String,
    description: String,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column {
            Text(title)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
