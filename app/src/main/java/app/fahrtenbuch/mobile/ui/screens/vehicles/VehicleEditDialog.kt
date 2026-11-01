package app.fahrtenbuch.mobile.ui.screens.vehicles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.fahrtenbuch.mobile.R
import app.fahrtenbuch.mobile.data.model.Vehicle

/** Name und Kennzeichen eines Fahrzeugs ändern. */
@Composable
fun VehicleEditDialog(
    vehicle: Vehicle,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, licensePlate: String?) -> Unit
) {
    var name by remember(vehicle.id) { mutableStateOf(vehicle.name) }
    var licensePlate by remember(vehicle.id) { mutableStateOf(vehicle.licensePlate.orEmpty()) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(stringResource(R.string.vehicles_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.vehicle_name)) },
                    singleLine = true,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = licensePlate,
                    onValueChange = { licensePlate = it.uppercase() },
                    label = { Text(stringResource(R.string.license_plate_optional)) },
                    placeholder = { Text(stringResource(R.string.license_plate_example)) },
                    singleLine = true,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim(), licensePlate.trim().ifBlank { null }) },
                enabled = !loading && name.isNotBlank()
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) { Text(stringResource(R.string.cancel)) }
        }
    )
}
