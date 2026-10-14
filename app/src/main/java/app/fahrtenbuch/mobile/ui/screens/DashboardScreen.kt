package app.fahrtenbuch.mobile.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fahrtenbuch.mobile.R
import app.fahrtenbuch.mobile.data.model.TrackPoint
import app.fahrtenbuch.mobile.data.model.Trip
import app.fahrtenbuch.mobile.data.model.TripType
import app.fahrtenbuch.mobile.location.TripTrackingService
import app.fahrtenbuch.mobile.ui.AppViewModel
import app.fahrtenbuch.mobile.ui.common.CategoryChip
import app.fahrtenbuch.mobile.ui.common.CategoryIcon
import app.fahrtenbuch.mobile.ui.common.SpecialCategoryChip
import app.fahrtenbuch.mobile.data.model.SpecialCategory
import app.fahrtenbuch.mobile.ui.common.LocalUserPreferences
import app.fahrtenbuch.mobile.ui.common.distanceUnitLabel
import app.fahrtenbuch.mobile.ui.common.formatDistance
import app.fahrtenbuch.mobile.ui.common.formatDistanceWithUnit
import app.fahrtenbuch.mobile.ui.common.formatLocalDate
import app.fahrtenbuch.mobile.ui.common.formatTripTime
import app.fahrtenbuch.mobile.ui.common.formatTripTimeRange
import app.fahrtenbuch.mobile.ui.common.shortRoute
import app.fahrtenbuch.mobile.ui.common.tripLocalDate
import app.fahrtenbuch.mobile.ui.screens.home.VehicleSelector
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

@Composable
fun DashboardScreen(
    viewModel: AppViewModel,
    onOpenTrips: (TripType?) -> Unit = {},
    onOpenTrip: (Trip) -> Unit = {},
    onSetupVehicle: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val completed = state.trips.filter { it.status != "recording" }
    val recordingTrip = state.trips.firstOrNull { it.status == "recording" }
    val latestTrip = completed.firstOrNull()

    LaunchedEffect(latestTrip?.id, latestTrip?.syncState) {
        if (latestTrip?.syncState == "SYNCED") viewModel.refreshLatestTripDetails()
    }

    // Standardfahrzeug zuerst, danach die zuletzt genutzten – wichtig bei vielen Fahrzeugen.
    val vehicles = remember(state.vehicles, completed) {
        val usageOrder = completed.map { it.vehicleId }.distinct()
        state.vehicles.sortedWith(
            compareByDescending<app.fahrtenbuch.mobile.data.model.Vehicle> { it.isDefault }
                .thenBy { vehicle -> usageOrder.indexOf(vehicle.id).takeIf { it >= 0 } ?: Int.MAX_VALUE }
        )
    }
    // Ohne Auswahl ließe sich keine Fahrt starten: dann das Standardfahrzeug (bzw. das erste) wählen.
    LaunchedEffect(vehicles, state.selectedVehicleId) {
        if (vehicles.isNotEmpty() && vehicles.none { it.id == state.selectedVehicleId }) {
            viewModel.selectVehicle(vehicles.first().id)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) viewModel.startTracking { startTrackingService(context) }
    }

    val today = LocalDate.now()
    val todayTrips = completed.filter { tripLocalDate(it.startedAt) == today }
    val monthTrips = completed.filter {
        val day = tripLocalDate(it.startedAt)
        day != null && day.year == today.year && day.month == today.month
    }
    val unclassifiedCount = completed.count { it.tripType == TripType.UNCLASSIFIED }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 24.dp)
    ) {
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                formatLocalDate(today, "EEEEdMMMM"),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(if (state.tracking) R.string.home_recording_title else R.string.home_ready),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )
        }

        // Hauptkarte: Fahrzeug, Aufzeichnungsstatus, Start/Stopp
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                VehicleSelector(
                    vehicles = vehicles,
                    selectedId = state.selectedVehicleId,
                    locked = state.tracking,
                    onSelect = viewModel::selectVehicle,
                    onSetupVehicle = onSetupVehicle
                )

                if (state.tracking) {
                    RecordingStatus(
                        trip = recordingTrip,
                        vehicleName = state.vehicles.firstOrNull { it.id == recordingTrip?.vehicleId }?.name
                            ?: state.vehicles.firstOrNull { it.id == state.selectedVehicleId }?.name
                    )
                }

                val stopColors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
                Button(
                    onClick = {
                        if (state.tracking) viewModel.stopTracking { stopTrackingService(context) }
                        else {
                            val permissions = buildList {
                                add(Manifest.permission.ACCESS_FINE_LOCATION)
                                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                            }.toTypedArray()
                            permissionLauncher.launch(permissions)
                        }
                    },
                    enabled = state.tracking || state.selectedVehicleId != null,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = if (state.tracking) stopColors else ButtonDefaults.buttonColors()
                ) {
                    Icon(if (state.tracking) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
                    Text(
                        stringResource(if (state.tracking) R.string.home_stop_trip else R.string.home_start_trip),
                        modifier = Modifier.padding(start = 10.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
        }

        // Heute / aktueller Monat
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                label = stringResource(R.string.trips_today),
                km = todayTrips.sumOf { it.distanceKm ?: 0.0 },
                count = todayTrips.size,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = formatLocalDate(today, "LLLL"),
                km = monthTrips.sumOf { it.distanceKm ?: 0.0 },
                count = monthTrips.size,
                modifier = Modifier.weight(1f)
            )
        }

        if (unclassifiedCount > 0) {
            UnclassifiedBanner(
                count = unclassifiedCount,
                onClick = { onOpenTrips(TripType.UNCLASSIFIED) },
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        if (latestTrip != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 22.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.latest_trip).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { onOpenTrips(null) }) {
                    Text(stringResource(R.string.home_all_trips), fontWeight = FontWeight.Bold)
                }
            }
            LatestTripCard(
                trip = latestTrip,
                points = state.tripPoints.takeIf { points -> points.isNotEmpty() && points.all { it.tripId == null || it.tripId == latestTrip.id } },
                onClick = { onOpenTrip(latestTrip) }
            )
        }
    }
}

@Composable
private fun RecordingStatus(trip: Trip?, vehicleName: String?) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(trip?.id) {
        while (true) {
            now = Instant.now()
            delay(15_000)
        }
    }
    val started = trip?.startedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
    val elapsed = started?.let { Duration.between(it, now).coerceAtLeast(Duration.ZERO) }
    val pulse by rememberInfiniteTransition(label = "recording").animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulse"
    )
    val errorColor = MaterialTheme.colorScheme.error
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(12.dp).alpha(pulse).background(errorColor, CircleShape))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.home_recording_status), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                if (started != null) {
                    Text(
                        stringResource(R.string.home_recording_since, formatTripTime(trip?.startedAt, LocalUserPreferences.current), vehicleName.orEmpty()).trimEnd(' ', '·'),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else if (vehicleName != null) {
                    Text(vehicleName, style = MaterialTheme.typography.bodyMedium)
                }
            }
            elapsed?.let {
                Text(
                    "%d:%02d".format(it.toHours(), it.toMinutes() % 60),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun StatTile(label: String, km: Double, count: Int, modifier: Modifier = Modifier) {
    val prefs = LocalUserPreferences.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                buildAnnotatedString {
                    append(formatDistance(km, prefs))
                    withStyle(SpanStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                        append(" " + distanceUnitLabel(prefs))
                    }
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                pluralStringResource(R.plurals.trip_count, count, count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UnclassifiedBanner(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val container = if (dark) Color(0xFF2A2213) else Color(0xFFFDF2DC)
    val accent = if (dark) Color(0xFFF2B45A) else Color(0xFF8A5A00)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = container,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = accent)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    pluralStringResource(R.plurals.home_unclassified_title, count, count),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = accent
                )
                Text(
                    stringResource(R.string.home_unclassified_action),
                    style = MaterialTheme.typography.bodySmall,
                    color = accent.copy(alpha = 0.8f)
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = accent)
        }
    }
}

@Composable
private fun LatestTripCard(trip: Trip, points: List<TrackPoint>?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (points != null && points.size > 1) MiniRoute(points) else CategoryIcon(trip.tripType, size = 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    shortRoute(trip.startAddress, trip.endAddress, stringResource(R.string.trip_start), stringResource(R.string.trip_destination)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${formatTripTimeRange(trip.startedAt, trip.endedAt, LocalUserPreferences.current)} · ${formatDistanceWithUnit(trip.distanceKm, LocalUserPreferences.current)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryChip(trip.tripType)
                    SpecialCategory.fromApi(trip.specialCategory)?.let { SpecialCategoryChip(it) }
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Kleines Routenbild aus den GPS-Punkten, ohne Kartenkacheln. */
@Composable
private fun MiniRoute(points: List<TrackPoint>) {
    val line = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        Modifier
            .size(64.dp)
            .background(background, RoundedCornerShape(16.dp))
            .padding(10.dp)
    ) {
        val minLat = points.minOf { it.lat }
        val maxLat = points.maxOf { it.lat }
        val minLon = points.minOf { it.lon }
        val maxLon = points.maxOf { it.lon }
        val spanLat = (maxLat - minLat).takeIf { it > 0 } ?: 1e-6
        val spanLon = (maxLon - minLon).takeIf { it > 0 } ?: 1e-6
        val scale = minOf(size.width / spanLon, size.height / spanLat)
        val offsetX = (size.width - spanLon * scale) / 2
        val offsetY = (size.height - spanLat * scale) / 2
        fun toOffset(p: TrackPoint) = Offset(
            (offsetX + (p.lon - minLon) * scale).toFloat(),
            (offsetY + (maxLat - p.lat) * scale).toFloat()
        )
        val path = Path().apply {
            points.forEachIndexed { index, p ->
                val o = toOffset(p)
                if (index == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
            }
        }
        drawPath(path, line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(background, radius = 4.dp.toPx(), center = toOffset(points.first()))
        drawCircle(line, radius = 4.dp.toPx(), center = toOffset(points.first()), style = Stroke(2.dp.toPx()))
        drawCircle(line, radius = 4.5.dp.toPx(), center = toOffset(points.last()))
    }
}

private fun startTrackingService(context: Context) {
    val intent = Intent(context, TripTrackingService::class.java).setAction(TripTrackingService.ACTION_START)
    context.startForegroundService(intent)
}

private fun stopTrackingService(context: Context) {
    val intent = Intent(context, TripTrackingService::class.java).setAction(TripTrackingService.ACTION_STOP)
    context.startService(intent)
}
