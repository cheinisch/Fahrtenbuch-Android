package app.fahrtenbuch.mobile.ui.screens

import android.graphics.drawable.GradientDrawable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import app.fahrtenbuch.mobile.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.fahrtenbuch.mobile.data.model.Trip
import app.fahrtenbuch.mobile.data.model.TripType
import app.fahrtenbuch.mobile.data.model.TrackPoint
import app.fahrtenbuch.mobile.ui.AppViewModel
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import kotlin.math.roundToInt

@Composable
private fun FilterMenu(label: String, options: List<Pair<String, String>>, value: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(options.firstOrNull { it.first == value }?.second ?: label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    onSelect(id)
                    expanded = false
                })
            }
        }
    }
}

@Composable
fun TripsScreen(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsState()
    var vehicleFilter by remember { mutableStateOf("") }
    var tagFilter by remember { mutableStateOf("") }
    var fromDate by remember { mutableStateOf("") }
    var toDate by remember { mutableStateOf("") }
    var selectedTripId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedTrip = state.trips.firstOrNull { it.id == selectedTripId }

    if (selectedTrip != null) {
        TripDetailsScreen(
            trip = selectedTrip,
            points = state.tripPoints,
            loading = state.tripDetailsLoading,
            error = state.tripDetailsError,
            onBack = { selectedTripId = null },
            onCategory = { viewModel.updateTripCategory(selectedTrip, it) }
        )
        return
    }

    val filtered = state.trips.filter { trip ->
        (vehicleFilter.isBlank() || trip.vehicleId == vehicleFilter) &&
        (tagFilter.isBlank() || tagFilter in trip.tags) &&
        (fromDate.isBlank() || trip.startedAt.take(10) >= fromDate) &&
        (toDate.isBlank() || trip.startedAt.take(10) <= toDate)
    }
    val tags = state.trips.flatMap { it.tags }.distinct().sorted()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.trips), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = viewModel::syncNow, enabled = !state.loading) {
                    if (state.loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Sync, contentDescription = "Fahrten synchronisieren")
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterMenu("Fahrzeug", listOf("" to "Alle Fahrzeuge") + state.vehicles.map { it.id to it.name }, vehicleFilter) { vehicleFilter = it }
                    FilterMenu("Tag", listOf("" to "Alle Tags") + tags.map { it to it }, tagFilter) { tagFilter = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(fromDate, { fromDate = it.take(10) }, modifier = Modifier.weight(1f), label = { Text(stringResource(R.string.date_from)) }, singleLine = true)
                    OutlinedTextField(toDate, { toDate = it.take(10) }, modifier = Modifier.weight(1f), label = { Text(stringResource(R.string.date_to)) }, singleLine = true)
                }
                Text("${filtered.size} Fahrten", style = MaterialTheme.typography.labelMedium)
            }
        }
        state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        items(filtered, key = { it.id }) { trip ->
            Card(Modifier.fillMaxWidth().clickable {
                selectedTripId = trip.id
                viewModel.loadTripDetails(trip)
            }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${trip.startAddress ?: "Start"} → ${trip.endAddress ?: "Ziel"}", fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${localizedTripType(trip.tripType)} · ${state.vehicles.firstOrNull { it.id == trip.vehicleId }?.name ?: trip.vehicleName.orEmpty()}")
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (trip.syncState == "SYNCED") MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                when (trip.syncState) {
                                    "SYNCED" -> "Gesynct"
                                    "FAILED" -> "Sync fehlgeschlagen"
                                    else -> "Ausstehend"
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    Text("${trip.distanceKm ?: 0.0} km · ${trip.startedAt}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripDetailsScreen(
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
                points.isNotEmpty() -> TripRouteMap(points, Modifier.fillMaxSize())
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
        TripRouteMap(points, Modifier.fillMaxSize())
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
            modifier = Modifier.width(46.dp),
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

/** Zerlegt eine Adresse wie „TÜV Hessen, Am Römerhof 15, 60486 Frankfurt am Main, Hessen, Deutschland“. */
private data class ParsedAddress(val name: String, val detail: String?, val city: String?)

private val postalCodePattern = Regex("^\\d{4,5}\\s+(.+)$")

private fun parseAddress(raw: String?): ParsedAddress? {
    val parts = raw?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    if (parts.isEmpty()) return null
    val postalIndex = parts.indexOfFirst { postalCodePattern.matches(it) }
    if (postalIndex < 0) {
        return ParsedAddress(parts.first(), parts.drop(1).take(2).joinToString(", ").ifBlank { null }, null)
    }
    val postalPart = parts[postalIndex]
    val city = postalCodePattern.find(postalPart)?.groupValues?.get(1)
    return when (postalIndex) {
        0 -> ParsedAddress(postalPart, null, city)
        1 -> ParsedAddress(parts[0], postalPart, city)
        else -> ParsedAddress(
            name = parts.subList(0, postalIndex - 1).joinToString(", "),
            detail = "${parts[postalIndex - 1]}, $postalPart",
            city = city
        )
    }
}

private fun formatDistance(distanceKm: Double?): String =
    distanceKm?.let { String.format(java.util.Locale.getDefault(), "%.1f", it) } ?: "–"

/** Liefert Wert und Einheit, z. B. „2:12“ + „h“ oder „45“ + „min“. */
private fun splitDuration(seconds: Int?): Pair<String, String> {
    if (seconds == null) return "–" to ""
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}" to "h" else "$minutes" to "min"
}

private fun formatTripInstant(value: String?, pattern: String): String? {
    if (value.isNullOrBlank()) return null
    return runCatching {
        java.time.format.DateTimeFormatter
            .ofPattern(pattern, java.util.Locale.getDefault())
            .withZone(java.time.ZoneId.systemDefault())
            .format(java.time.Instant.parse(value))
    }.getOrNull()
}

private fun formatTripDate(value: String?): String? = formatTripInstant(value, "EEE, d. MMM yyyy")

private fun formatTripTime(value: String?): String = formatTripInstant(value, "HH:mm") ?: "–"

@Composable
private fun TripRouteMap(
    points: List<TrackPoint>,
    modifier: Modifier = Modifier.fillMaxWidth().height(240.dp)
) {
    val geoPoints = remember(points) { points.map { GeoPoint(it.lat, it.lon) } }
    val routeColor = MaterialTheme.colorScheme.primary.toArgb()
    val ringColor = MaterialTheme.colorScheme.background.toArgb()
    val darkMap = MaterialTheme.colorScheme.background.luminance() < 0.5f

    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).apply {
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                minZoomLevel = 3.0
                maxZoomLevel = 20.0
            }
        },
        update = { map ->
            map.overlayManager.tilesOverlay.setColorFilter(if (darkMap) TilesOverlay.INVERT_COLORS else null)
            map.overlays.clear()

            if (geoPoints.isNotEmpty()) {
                val density = map.resources.displayMetrics.density
                val route = Polyline(map).apply {
                    setPoints(geoPoints)
                    outlinePaint.apply {
                        strokeWidth = 5f * density
                        color = routeColor
                        strokeCap = android.graphics.Paint.Cap.ROUND
                        strokeJoin = android.graphics.Paint.Join.ROUND
                        isAntiAlias = true
                    }
                }
                map.overlays.add(route)

                Marker(map).apply {
                    position = geoPoints.first()
                    icon = routeDot(fill = ringColor, stroke = routeColor, density = density)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = "Start"
                    map.overlays.add(this)
                }

                if (geoPoints.size > 1) {
                    Marker(map).apply {
                        position = geoPoints.last()
                        icon = routeDot(fill = routeColor, stroke = ringColor, density = density)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = "Ziel"
                        map.overlays.add(this)
                    }
                }

                if (geoPoints.size == 1) {
                    map.controller.setZoom(16.0)
                    map.controller.setCenter(geoPoints.first())
                } else {
                    map.post {
                        // Etwas mehr Rand, weil unten Titel und Verlauf über der Karte liegen.
                        map.zoomToBoundingBox(route.bounds, true, (48 * density).toInt())
                    }
                }
            }

            map.invalidate()
        }
    )
}

private fun routeDot(fill: Int, stroke: Int, density: Float): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
        setStroke((4 * density).toInt(), stroke)
        val size = (20 * density).toInt()
        setSize(size, size)
    }

@Composable
private fun localizedTripType(type: TripType): String = when (type) {
    TripType.BUSINESS -> stringResource(R.string.trip_business)
    TripType.PRIVATE -> stringResource(R.string.trip_private)
    TripType.COMMUTE -> stringResource(R.string.trip_commute)
    TripType.UNCLASSIFIED -> stringResource(R.string.trip_unclassified)
}
