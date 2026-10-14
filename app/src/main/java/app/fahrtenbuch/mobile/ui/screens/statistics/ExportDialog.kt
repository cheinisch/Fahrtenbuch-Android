package app.fahrtenbuch.mobile.ui.screens.statistics

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.fahrtenbuch.mobile.R
import app.fahrtenbuch.mobile.data.model.ExportFormat
import app.fahrtenbuch.mobile.data.model.ExportRequest
import app.fahrtenbuch.mobile.data.model.ExportResult
import app.fahrtenbuch.mobile.data.model.SpecialCategory
import app.fahrtenbuch.mobile.data.model.TripType
import app.fahrtenbuch.mobile.ui.common.formatLocalDate
import app.fahrtenbuch.mobile.ui.common.localizedSpecialCategory
import app.fahrtenbuch.mobile.ui.common.localizedTripType
import app.fahrtenbuch.mobile.ui.screens.settings.SegmentedSelector
import app.fahrtenbuch.mobile.ui.screens.trips.PeriodFilterDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Export aus der Statistik. Zeitraum (vorbelegt mit dem Statistik-Filter) und Format (PDF
 * als Standard, CSV) wählen; Fahrzeug- und Kategorie-Filter der Statistik werden übernommen.
 * Im Server-Modus erzeugt der Server den Steuer-Report, sonst die App selbst. Die Datei wird
 * über den Android-Speicherdialog abgelegt und kann danach direkt geteilt werden.
 */
@Composable
fun ExportDialog(
    initialFrom: LocalDate,
    initialTo: LocalDate,
    vehicleId: String?,
    vehicleName: String?,
    tripType: TripType?,
    specialCategory: SpecialCategory?,
    serverMode: Boolean,
    onExport: suspend (ExportRequest) -> ExportResult,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var from by rememberSaveable { mutableStateOf(initialFrom.toEpochDay()) }
    var to by rememberSaveable { mutableStateOf(initialTo.toEpochDay()) }
    var format by rememberSaveable { mutableStateOf(ExportFormat.PDF) }
    var showPeriod by rememberSaveable { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<ExportResult?>(null) }
    var saved by remember { mutableStateOf<Pair<Uri, ExportResult>?>(null) }
    val writeFailed = stringResource(R.string.export_error_write)
    val exportFailed = stringResource(R.string.export_error)

    // Speicherdialog von Android: der Nutzer wählt Ort und Dateinamen
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(pending?.mimeType ?: format.mimeType)
    ) { uri ->
        val result = pending
        pending = null
        if (uri == null || result == null) {
            working = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(result.bytes) } ?: error("no stream")
                }
            }.onSuccess { saved = uri to result }
                .onFailure { error = writeFailed }
            working = false
        }
    }

    val fromDate = LocalDate.ofEpochDay(from)
    val toDate = LocalDate.ofEpochDay(to)

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(stringResource(R.string.export_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val done = saved
                if (done != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.export_saved), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(done.second.fileName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (done.second.generatedOnDevice && serverMode) {
                        Text(stringResource(R.string.export_fallback_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedButton(onClick = {
                        val share = Intent(Intent.ACTION_SEND)
                            .setType(done.second.mimeType)
                            .putExtra(Intent.EXTRA_STREAM, done.first)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(share, null))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.export_share), modifier = Modifier.padding(start = 8.dp))
                    }
                    return@Column
                }

                // Zeitraum
                Text(stringResource(R.string.export_period), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Surface(
                    onClick = { showPeriod = true },
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text(
                            "${formatLocalDate(fromDate, "dMMMyyyy")} – ${formatLocalDate(toDate, "dMMMyyyy")}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Format
                Text(stringResource(R.string.export_format), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                SegmentedSelector(
                    options = ExportFormat.entries,
                    selected = format,
                    label = { stringResource(if (it == ExportFormat.PDF) R.string.export_format_pdf else R.string.export_format_csv) },
                    onSelect = { format = it }
                )

                // Übernommene Filter
                val filters = listOfNotNull(
                    vehicleName,
                    tripType?.let { localizedTripType(it) },
                    specialCategory?.let { localizedSpecialCategory(it) }
                )
                Text(
                    if (filters.isEmpty()) stringResource(R.string.export_filters_none)
                    else stringResource(R.string.export_filters, filters.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(if (serverMode) R.string.export_hint_server else R.string.export_hint_local),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (working) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.export_working), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            if (saved != null) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            } else {
                TextButton(
                    enabled = !working,
                    onClick = {
                        error = null
                        working = true
                        scope.launch {
                            val request = ExportRequest(
                                format = format,
                                from = fromDate.toString(),
                                to = toDate.toString(),
                                vehicleId = vehicleId,
                                tripType = tripType,
                                specialCategory = specialCategory
                            )
                            runCatching { onExport(request) }
                                .onSuccess { result ->
                                    pending = result
                                    createDocument.launch(result.fileName)
                                }
                                .onFailure {
                                    working = false
                                    error = exportFailed
                                }
                        }
                    }
                ) { Text(stringResource(R.string.export_action)) }
            }
        },
        dismissButton = {
            if (saved == null) {
                TextButton(enabled = !working, onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )

    if (showPeriod) {
        PeriodFilterDialog(
            initialFrom = fromDate,
            initialTo = toDate,
            onDismiss = { showPeriod = false },
            onConfirm = { newFrom, newTo ->
                // „Zurücksetzen“ liefert null: dann bleibt der bisherige Zeitraum
                if (newFrom != null) {
                    from = newFrom.toEpochDay()
                    to = (newTo ?: newFrom).toEpochDay()
                }
                showPeriod = false
            }
        )
    }
}
