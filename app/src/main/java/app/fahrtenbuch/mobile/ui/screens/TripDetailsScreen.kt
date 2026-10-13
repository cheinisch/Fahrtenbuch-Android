package app.fahrtenbuch.mobile.ui.screens

import android.graphics.drawable.GradientDrawable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.fahrtenbuch.mobile.R
import app.fahrtenbuch.mobile.data.model.TrackPoint
import app.fahrtenbuch.mobile.data.model.Trip
import app.fahrtenbuch.mobile.data.model.TripType
import app.fahrtenbuch.mobile.ui.AppViewModel
import app.fahrtenbuch.mobile.ui.common.ParsedAddress
import app.fahrtenbuch.mobile.ui.map.MapPadding
import app.fahrtenbuch.mobile.ui.map.TripRouteMap
import app.fahrtenbuch.mobile.ui.common.formatDistance
import app.fahrtenbuch.mobile.ui.common.formatTripDate
import app.fahrtenbuch.mobile.ui.common.formatTripTime
import app.fahrtenbuch.mobile.ui.common.localizedTripType
import app.fahrtenbuch.mobile.ui.common.parseAddress
import app.fahrtenbuch.mobile.ui.common.splitDuration
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TripDetailsScreen(
    trip: Trip,
    points: List<TrackPoint>,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onCategory: (TripType) -> Unit
) {
    BackHandler(onBack = onBack)
    var mapFullscreen by rememberSaveable(trip.id) { mutableStateOf(false) }
    // Später registriert und damit vorrangig: Zurück schließt zuerst die Vollbildkarte.
    BackHandler(enabled = mapFullscreen) { mapFullscreen = false }
    if (mapFullscreen && points.isNotEmpty()) {
        FullscreenTripMap(points = points, onClose = { mapFullscreen = false })
        return
    }
    val scrollState = rememberScrollState()
    val colors = MaterialTheme.colorScheme
    val start = remember(trip.startAddress) { parseAddress(trip.startAddress) }
    val end = remember(trip.endAddress) { parseAddress(trip.endAddress) }
    val title = when {
        start?.city != null && end?.city != null -> "${start.city} → ${end.city}"
        start?.city != null -> start.city
        end?.city != null -> end.city
        else -> stringResource(R.string.trip_details)
    }
    val meta = listOfNotNull(
        formatTripDate(trip.startedAt),
        trip.vehicleName?.takeIf { it.isNotBlank() }
    ).joinToString("  ·  ")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(scrollState)
    ) {
        // Karte mit Verlauf in den Hintergrund, Titel liegt unten darauf.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .background(colors.surfaceContainer)
        ) {
            when {
                points.isNotEmpty() -> TripRouteMap(
                    points = points,
                    modifier = Modifier.fillMaxSize(),
                    // Oben schwebende Buttons, unten Titel und Verlauf über der Karte
                    contentPadding = MapPadding(top = 88.dp, bottom = 150.dp),
                    attributionTopOffset = 64.dp
                )
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> Box(Modifier.fillMaxSize().padding(bottom = 72.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_gps_route), color = colors.onSurfaceVariant)
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(Brush.verticalGradient(listOf(colors.background.copy(alpha = 0f), colors.background)))
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = colors.surface.copy(alpha = 0.9f),
                border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.5f)),
                shadowElevation = 4.dp
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.trip_detail_back))
                }
            }

            if (points.isNotEmpty()) {
                MapOverlayButton(
                    icon = Icons.Default.Fullscreen,
                    contentDescription = stringResource(R.string.trip_detail_fullscreen),
                    onClick = { mapFullscreen = true },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (meta.isNotBlank()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TripMetricsCard(trip)
            TripTimelineCard(trip, start, end)

            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodyMedium) }

            SectionLabel(stringResource(R.string.category))
            CategorySelector(selected = trip.tripType, enabled = !loading, onSelect = onCategory)

            SectionLabel(stringResource(R.string.trip_detail_purpose))
            TripPurposeCard(trip)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MapOverlayButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shadowElevation = 4.dp
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            Icon(icon, contentDescription = contentDescription)
        }
    }
}

@Composable
private fun FullscreenTripMap(points: List<TrackPoint>, onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        TripRouteMap(points = points, modifier = Modifier.fillMaxSize(), contentPadding = MapPadding(top = 88.dp))
        MapOverlayButton(
            icon = Icons.Default.Close,
            contentDescription = stringResource(R.string.trip_detail_close_map),
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        )
    }
}

@Composable
private fun DetailCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(content = content)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        modifier = Modifier.padding(start = 4.dp, top = 12.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun TripMetricsCard(trip: Trip) {
    val avgSpeed = remember(trip.distanceKm, trip.durationSeconds) {
        val km = trip.distanceKm
        val seconds = trip.durationSeconds
        if (km != null && seconds != null && seconds >= 60) (km / (seconds / 3600.0)).roundToInt() else null
    }
    val duration = splitDuration(trip.durationSeconds)
    DetailCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            MetricItem(formatDistance(trip.distanceKm), "km", stringResource(R.string.trip_detail_distance), Modifier.weight(1f))
            MetricDivider()
            MetricItem(duration.first, duration.second, stringResource(R.string.trip_detail_duration), Modifier.weight(1f))
            MetricDivider()
            MetricItem(avgSpeed?.toString() ?: "–", "km/h", stringResource(R.string.trip_detail_avg_speed), Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricDivider() {
    Box(
        Modifier
            .padding(horizontal = 12.dp)
            .fillMaxHeight()
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun MetricItem(value: String, unit: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            buildAnnotatedString {
                append(value)
                if (value != "–" && unit.isNotEmpty()) {
                    withStyle(SpanStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                        append(" $unit")
                    }
                }
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun TripTimelineCard(trip: Trip, start: ParsedAddress?, end: ParsedAddress?) {
    DetailCard {
        Column(Modifier.padding(20.dp)) {
            TimelineRow(
                time = formatTripTime(trip.startedAt),
                title = start?.name ?: stringResource(R.string.trip_detail_start_unknown),
                subtitle = start?.detail,
                isLast = false
            )
            TimelineRow(
                time = formatTripTime(trip.endedAt),
                title = end?.name ?: stringResource(R.string.trip_detail_end_unknown),
                subtitle = end?.detail,
                isLast = true
            )
        }
    }
}

@Composable
private fun TimelineRow(time: String, title: String, subtitle: String?, isLast: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    val lineColor = MaterialTheme.colorScheme.outline
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            time,
            modifier = Modifier.widthIn(min = 46.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Column(
            modifier = Modifier.width(16.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLast) {
                Box(Modifier.padding(top = 2.dp).size(16.dp).background(primary, CircleShape))
            } else {
                Box(
                    Modifier
                        .padding(top = 4.dp)
                        .size(12.dp)
                        .border(3.dp, primary, CircleShape)
                )
                Canvas(
                    Modifier
                        .padding(vertical = 6.dp)
                        .weight(1f)
                        .width(2.dp)
                ) {
                    drawLine(
                        color = lineColor,
                        start = Offset(size.width / 2, 0f),
                        end = Offset(size.width / 2, size.height),
                        strokeWidth = size.width,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CategorySelector(selected: TripType, enabled: Boolean, onSelect: (TripType) -> Unit) {
    val colors = MaterialTheme.colorScheme
    DetailCard {
        Column(
            modifier = Modifier.selectableGroup().padding(5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TripType.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { type ->
                        val isSelected = type == selected
                        Surface(
                            selected = isSelected,
                            onClick = { if (!isSelected) onSelect(type) },
                            enabled = enabled,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .semantics { role = Role.RadioButton },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) colors.primary else Color.Transparent,
                            contentColor = if (isSelected) colors.onPrimary else colors.onSurfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    localizedTripType(type),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TripPurposeCard(trip: Trip) {
    val rows = listOfNotNull(
        trip.purpose?.takeIf { it.isNotBlank() }?.let { Triple(Icons.Default.Flag, stringResource(R.string.trip_detail_purpose), it) },
        trip.contact?.takeIf { it.isNotBlank() }?.let { Triple(Icons.Default.Person, stringResource(R.string.trip_detail_contact), it) },
        trip.note?.takeIf { it.isNotBlank() }?.let { Triple(Icons.Default.Notes, stringResource(R.string.trip_detail_note), it) }
    )
    DetailCard {
        if (rows.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.trip_detail_purpose_missing), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            rows.forEachIndexed { index, (icon, label, value) ->
                if (index > 0) HorizontalDivider(Modifier.padding(start = 50.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
