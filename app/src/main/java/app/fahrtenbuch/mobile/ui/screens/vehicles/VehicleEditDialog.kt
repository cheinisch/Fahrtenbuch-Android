package app.fahrtenbuch.mobile.ui.screens.vehicles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import app.fahrtenbuch.mobile.R
import app.fahrtenbuch.mobile.data.model.AcquisitionType
import app.fahrtenbuch.mobile.data.model.Vehicle
import app.fahrtenbuch.mobile.data.model.VehicleDetails
import app.fahrtenbuch.mobile.ui.common.LocalUserPreferences
import app.fahrtenbuch.mobile.ui.common.distanceUnitLabel
import app.fahrtenbuch.mobile.ui.common.formatCalendarDate
import app.fahrtenbuch.mobile.ui.common.wholeDistanceInPreferredUnit
import app.fahrtenbuch.mobile.ui.common.wholeDistanceToKm
import app.fahrtenbuch.mobile.ui.screens.trips.PeriodFilterDialog
import java.time.LocalDate

/** Name, Kennzeichen, Finanzierungsart, Vertragslaufzeit und Kilometerlimit ändern. */
@Composable
fun VehicleEditDialog(
    vehicle: Vehicle,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSave: (VehicleDetails) -> Unit
) {
    val prefs = LocalUserPreferences.current
    var name by remember(vehicle.id) { mutableStateOf(vehicle.name) }
    var licensePlate by remember(vehicle.id) { mutableStateOf(vehicle.licensePlate.orEmpty()) }
    var acquisition by remember(vehicle.id) { mutableStateOf(vehicle.acquisitionType) }
    var startDate by remember(vehicle.id) { mutableStateOf(parseDate(vehicle.leaseStartDate)) }
    var endDate by remember(vehicle.id) { mutableStateOf(parseDate(vehicle.leaseEndDate)) }
    var includedKm by remember(vehicle.id) {
        mutableStateOf(
            vehicle.leaseIncludedKm?.let { wholeDistanceInPreferredUnit(it, prefs).toString() }.orEmpty()
        )
    }
    var showPeriodPicker by remember { mutableStateOf(false) }

    // Lokale Kopien: auf die delegierten remember-Werte greift kein Smart-Cast.
    val start = startDate
    val end = endDate
    val contract = acquisition.hasContract
    val limitValue = includedKm.trim().replace(',', '.').toDoubleOrNull()
    val limitFilled = includedKm.isNotBlank()
    // Der Server nimmt Vertragsdaten nur vollständig an: Zeitraum und Kilometerlimit zusammen.
    val periodComplete = start != null && end != null && end.isAfter(start)
    val periodFilled = start != null || end != null
    val contractDataGiven = contract && (periodFilled || limitFilled)
    val limitNeedsPeriod = contractDataGiven && !periodComplete
    val periodNeedsLimit = contractDataGiven && !limitFilled
    val limitInvalid = limitFilled && (limitValue == null || limitValue <= 0)
    val periodInvalid = contract && start != null && end != null && !end.isAfter(start)
    val canSave = name.isNotBlank() && !loading &&
        !limitInvalid && !limitNeedsPeriod && !periodNeedsLimit && !periodInvalid

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(stringResource(R.string.vehicles_edit_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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

                Text(
                    stringResource(R.string.vehicles_acquisition),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AcquisitionType.entries.forEach { type ->
                        FilterChip(
                            selected = acquisition == type,
                            onClick = { acquisition = type },
                            enabled = !loading,
                            label = { Text(stringResource(acquisitionLabel(type))) }
                        )
                    }
                }

                if (contract) {
                    OutlinedButton(
                        onClick = { showPeriodPicker = true },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (start != null && end != null) {
                                stringResource(
                                    R.string.vehicles_contract_period_value,
                                    formatCalendarDate(start, prefs),
                                    formatCalendarDate(end, prefs)
                                )
                            } else {
                                stringResource(R.string.vehicles_contract_period_choose)
                            }
                        )
                    }
                    if (periodInvalid || periodNeedsLimit) {
                        Text(
                            stringResource(
                                if (periodInvalid) R.string.vehicles_contract_period_invalid
                                else R.string.vehicles_contract_period_needs_km
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    OutlinedTextField(
                        value = includedKm,
                        onValueChange = { input -> includedKm = input.filter { it.isDigit() || it == '.' || it == ',' } },
                        label = { Text(stringResource(R.string.vehicles_included_km, distanceUnitLabel(prefs))) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        enabled = !loading,
                        isError = limitInvalid || limitNeedsPeriod,
                        supportingText = {
                            when {
                                limitNeedsPeriod -> Text(stringResource(R.string.vehicles_included_km_needs_period))
                                else -> Text(stringResource(R.string.vehicles_included_km_hint))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        VehicleDetails(
                            name = name.trim(),
                            licensePlate = licensePlate.trim().ifBlank { null },
                            acquisitionType = acquisition,
                            leaseStartDate = start?.takeIf { periodComplete }?.toString(),
                            leaseEndDate = end?.takeIf { periodComplete }?.toString(),
                            leaseIncludedKm = limitValue?.takeIf { it > 0 }?.let { wholeDistanceToKm(it, prefs) }
                        )
                    )
                },
                enabled = canSave
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) { Text(stringResource(R.string.cancel)) }
        }
    )

    if (showPeriodPicker) {
        PeriodFilterDialog(
            initialFrom = startDate,
            initialTo = endDate,
            onDismiss = { showPeriodPicker = false },
            onConfirm = { from, to ->
                startDate = from
                endDate = to
                showPeriodPicker = false
            }
        )
    }
}

internal fun acquisitionLabel(type: AcquisitionType): Int = when (type) {
    AcquisitionType.OWNED -> R.string.vehicles_acquisition_owned
    AcquisitionType.LEASING -> R.string.vehicles_acquisition_leasing
    AcquisitionType.FINANCING -> R.string.vehicles_acquisition_financing
}

private fun parseDate(value: String?): LocalDate? =
    value?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
