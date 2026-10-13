package app.fahrtenbuch.mobile.ui.screens.trips

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.fahrtenbuch.mobile.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Zeitraum für die Fahrtenliste wählen. „Zurücksetzen“ liefert null/null (alle Zeiträume). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodFilterDialog(
    initialFrom: LocalDate?,
    initialTo: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (from: LocalDate?, to: LocalDate?) -> Unit
) {
    // Der DatePicker arbeitet mit UTC-Mitternacht in Millisekunden.
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialFrom?.toPickerMillis(),
        initialSelectedEndDateMillis = initialTo?.toPickerMillis()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    onConfirm(
                        state.selectedStartDateMillis?.toPickerDate(),
                        state.selectedEndDateMillis?.toPickerDate()
                    )
                }
            ) {
                Text(stringResource(R.string.period_apply))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onConfirm(null, null) }) {
                    Text(stringResource(R.string.period_reset))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    ) {
        DateRangePicker(state = state, modifier = Modifier.weight(1f))
    }
}

private fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toPickerDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
