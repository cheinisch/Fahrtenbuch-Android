package app.fahrtenbuch.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import app.fahrtenbuch.mobile.R
import app.fahrtenbuch.mobile.data.model.DateFormatPreference
import app.fahrtenbuch.mobile.data.model.DistanceUnit
import app.fahrtenbuch.mobile.data.model.SpeedUnit
import app.fahrtenbuch.mobile.data.model.TimeFormatPreference
import app.fahrtenbuch.mobile.data.model.UserPreferences
import app.fahrtenbuch.mobile.ui.common.formatDistanceWithUnit
import app.fahrtenbuch.mobile.ui.common.formatSpeed
import app.fahrtenbuch.mobile.ui.common.formatTripDateTime
import app.fahrtenbuch.mobile.ui.common.speedUnitLabel

/**
 * Einheiten für Strecke und Geschwindigkeit sowie das Datumsformat. Im Server-Modus
 * werden die Werte im Benutzerkonto gespeichert (wie im Web-Fahrtenbuch), sonst auf dem
 * Gerät. Gespeicherte Fahrten werden nicht verändert, nur ihre Anzeige.
 */
@Composable
fun FormatPreferencesDialog(
    current: UserPreferences,
    serverMode: Boolean,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (UserPreferences) -> Unit
) {
    var distanceUnit by rememberSaveable { mutableStateOf(current.distanceUnit) }
    var speedUnit by rememberSaveable { mutableStateOf(current.speedUnit) }
    var dateFormat by rememberSaveable { mutableStateOf(current.dateFormat) }
    var timeFormat by rememberSaveable { mutableStateOf(current.timeFormat) }
    // Server-Modus: immer eine feste Zeitzone (Benutzerkonto); lokal auch „Gerät“ (null) möglich
    var timeZone by rememberSaveable {
        mutableStateOf(current.timeZone ?: if (serverMode) java.time.ZoneId.systemDefault().id else null)
    }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val draft = UserPreferences(distanceUnit, speedUnit, dateFormat, timeFormat, timeZone)

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(stringResource(R.string.format_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(stringResource(R.string.format_distance), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                SegmentedSelector(
                    options = DistanceUnit.entries,
                    selected = distanceUnit,
                    label = { stringResource(if (it == DistanceUnit.KM) R.string.format_km else R.string.format_mi) },
                    onSelect = { distanceUnit = it }
                )
                Text(stringResource(R.string.format_speed), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                SegmentedSelector(
                    options = SpeedUnit.entries,
                    selected = speedUnit,
                    label = { if (it == SpeedUnit.KMH) "km/h" else "mph" },
                    onSelect = { speedUnit = it }
                )
                Text(stringResource(R.string.format_date), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                SegmentedSelector(
                    options = DateFormatPreference.entries,
                    selected = dateFormat,
                    label = { stringResource(if (it == DateFormatPreference.EU) R.string.format_date_eu else R.string.format_date_us) },
                    onSelect = { dateFormat = it }
                )
                Text(stringResource(R.string.format_time), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                SegmentedSelector(
                    options = TimeFormatPreference.entries,
                    selected = timeFormat,
                    label = { stringResource(if (it == TimeFormatPreference.H24) R.string.format_time_24 else R.string.format_time_12) },
                    onSelect = { timeFormat = it }
                )
                Text(stringResource(R.string.format_time_zone), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                TimeZoneDropdown(selected = timeZone, allowDevice = !serverMode, onSelect = { timeZone = it })
                Text(
                    stringResource(R.string.format_time_zone_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Vorschau mit einer Beispielfahrt
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.format_preview), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            listOfNotNull(
                                formatTripDateTime(PREVIEW_TIMESTAMP, draft),
                                formatDistanceWithUnit(PREVIEW_DISTANCE_KM, draft),
                                "${formatSpeed(PREVIEW_SPEED_KMH, draft)} ${speedUnitLabel(draft)}"
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Text(
                    stringResource(if (serverMode) R.string.format_hint_server else R.string.format_hint_local),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (submitted && !loading) {
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !loading,
                onClick = {
                    submitted = true
                    onSave(draft)
                }
            ) {
                Text(stringResource(if (loading) R.string.settings_saving else R.string.save))
            }
        },
        dismissButton = {
            TextButton(enabled = !loading, onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** Häufige Zeitzonen; die aktuelle Auswahl und die des Geräts werden immer ergänzt. */
private val commonTimeZones = listOf(
    "Europe/Berlin", "Europe/Vienna", "Europe/Zurich", "Europe/Amsterdam", "Europe/Brussels", "Europe/Paris",
    "Europe/Luxembourg", "Europe/Copenhagen", "Europe/Warsaw", "Europe/Prague", "Europe/Rome", "Europe/Madrid",
    "Europe/Lisbon", "Europe/London", "Europe/Dublin", "Europe/Stockholm", "Europe/Helsinki", "Europe/Athens",
    "Europe/Istanbul", "America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles",
    "Asia/Dubai", "Asia/Tokyo", "Australia/Sydney", "UTC"
)

@Composable
private fun TimeZoneDropdown(selected: String?, allowDevice: Boolean, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val deviceZone = java.time.ZoneId.systemDefault().id
    val deviceLabel = stringResource(R.string.format_time_zone_device, deviceZone)
    val zones = (listOfNotNull(selected, deviceZone) + commonTimeZones).distinct()
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                selected?.let { zoneLabel(it) } ?: deviceLabel,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
            if (allowDevice) {
                DropdownMenuItem(text = { Text(deviceLabel) }, onClick = { onSelect(null); expanded = false })
            }
            zones.forEach { zone ->
                DropdownMenuItem(text = { Text(zoneLabel(zone)) }, onClick = { onSelect(zone); expanded = false })
            }
        }
    }
}

/** z. B. „Europe/Berlin (UTC+2)“ – Offset zum aktuellen Zeitpunkt. */
private fun zoneLabel(id: String): String = runCatching {
    val offset = java.time.ZoneId.of(id).rules.getOffset(java.time.Instant.now())
    val text = if (offset.totalSeconds == 0) "UTC" else "UTC" + offset.id.replace(":00", "")
    "$id ($text)"
}.getOrDefault(id)

private const val PREVIEW_TIMESTAMP = "2026-10-13T14:12:00Z"
private const val PREVIEW_DISTANCE_KM = 110.8
private const val PREVIEW_SPEED_KMH = 50.0
