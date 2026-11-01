package app.fahrtenbuch.mobile.ui.screens.vehicles

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fahrtenbuch.mobile.R
import app.fahrtenbuch.mobile.data.api.ApiFactory
import app.fahrtenbuch.mobile.data.model.OdometerReminderSettings
import app.fahrtenbuch.mobile.ui.screens.settings.SegmentedSelector
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

private val frequencies = listOf("weekly", "monthly", "month_end")

/**
 * Einstellungen der Kilometerstand-Erinnerung eines Fahrzeugs. Der Server entscheidet anhand
 * dieser Werte, wann eine Erinnerung fällig ist; die App zeigt sie dann als Benachrichtigung.
 */
@Composable
fun OdometerReminderDialog(
    vehicleName: String,
    load: suspend () -> Result<OdometerReminderSettings>,
    save: suspend (OdometerReminderSettings) -> Result<OdometerReminderSettings>,
    onSaved: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf<OdometerReminderSettings?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        load()
            .onSuccess { settings = it }
            .onFailure { error = ApiFactory.readableError(it) }
    }
    val loadFailed = settings == null && error != null

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.vehicles_odometer_reminder)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(vehicleName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                val current = settings
                when {
                    current != null -> ReminderForm(current, enabledInput = !saving) { settings = it }
                    loadFailed -> Text(
                        stringResource(R.string.odometer_reminder_load_error, error.orEmpty()),
                        color = MaterialTheme.colorScheme.error
                    )
                    else -> LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (current != null) {
                    error?.let { Text(stringResource(R.string.odometer_reminder_save_error, it), color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = settings != null && !saving,
                onClick = {
                    val value = settings ?: return@TextButton
                    saving = true
                    error = null
                    scope.launch {
                        save(value)
                            .onSuccess { onSaved() }
                            .onFailure { error = ApiFactory.readableError(it) }
                        saving = false
                    }
                }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun ReminderForm(settings: OdometerReminderSettings, enabledInput: Boolean, onChange: (OdometerReminderSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.odometer_reminder_enabled),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Switch(checked = settings.enabled, enabled = enabledInput, onCheckedChange = { onChange(settings.copy(enabled = it)) })
        }
        if (settings.enabled) {
            SegmentedSelector(
                options = frequencies,
                selected = settings.frequency.takeIf { it in frequencies } ?: "monthly",
                label = { stringResource(frequencyLabel(it)) },
                onSelect = { onChange(settings.copy(frequency = it)) }
            )
            when (settings.frequency) {
                "weekly" -> {
                    Text(stringResource(R.string.odometer_reminder_weekday), style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..7).forEach { day ->
                            FilterChip(
                                selected = settings.weekday == day,
                                onClick = { onChange(settings.copy(weekday = day)) },
                                label = { Text(DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, Locale.getDefault())) }
                            )
                        }
                    }
                }
                "monthly" -> Stepper(
                    label = stringResource(R.string.odometer_reminder_day),
                    value = settings.dayOfMonth,
                    range = 1..28,
                    format = { it.toString() },
                    onChange = { onChange(settings.copy(dayOfMonth = it)) }
                )
            }
            Stepper(
                label = stringResource(R.string.odometer_reminder_hour),
                value = settings.hour,
                range = 0..23,
                format = { "%02d:00".format(it) },
                onChange = { onChange(settings.copy(hour = it)) }
            )
            Text(
                stringResource(R.string.odometer_reminder_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun frequencyLabel(frequency: String): Int = when (frequency) {
    "weekly" -> R.string.odometer_reminder_frequency_weekly
    "month_end" -> R.string.odometer_reminder_frequency_month_end
    else -> R.string.odometer_reminder_frequency_monthly
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, format: (Int) -> String, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        IconButton(onClick = { onChange((value - 1).coerceIn(range)) }, enabled = value > range.first) {
            Icon(Icons.Default.Remove, contentDescription = null)
        }
        Text(format(value), modifier = Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        IconButton(onClick = { onChange((value + 1).coerceIn(range)) }, enabled = value < range.last) {
            Icon(Icons.Default.Add, contentDescription = null)
        }
    }
}
