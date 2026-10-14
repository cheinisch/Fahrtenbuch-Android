package app.fahrtenbuch.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import app.fahrtenbuch.mobile.R
import app.fahrtenbuch.mobile.data.model.Trip
import app.fahrtenbuch.mobile.data.model.TripType
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
import app.fahrtenbuch.mobile.ui.common.formatTripTimeRange
import app.fahrtenbuch.mobile.ui.common.localizedTripType
import app.fahrtenbuch.mobile.ui.common.shortRoute
import app.fahrtenbuch.mobile.ui.common.today
import app.fahrtenbuch.mobile.ui.common.tripLocalDate
import app.fahrtenbuch.mobile.ui.screens.trips.PeriodFilterDialog
import java.time.LocalDate

/** Anfrage von außen (z. B. Startseite): Liste vorfiltern oder eine Fahrt direkt öffnen. */
data class TripsRequest(val category: TripType? = null, val tripId: String? = null)

@Composable
fun TripsScreen(
    viewModel: AppViewModel,
    request: TripsRequest? = null,
    onRequestHandled: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    var vehicleFilter by rememberSaveable { mutableStateOf("") }
    var tagFilter by rememberSaveable { mutableStateOf("") }
    var categoryFilter by rememberSaveable { mutableStateOf<TripType?>(null) }
    var fromEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var toEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var showPeriodDialog by rememberSaveable { mutableStateOf(false) }
    var selectedTripId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(request) {
        val pending = request ?: return@LaunchedEffect
        categoryFilter = pending.category
        pending.tripId?.let { id ->
            selectedTripId = id
            state.trips.firstOrNull { it.id == id }?.let(viewModel::loadTripDetails)
        }
        onRequestHandled()
    }

    val selectedTrip = state.trips.firstOrNull { it.id == selectedTripId }
    if (selectedTrip != null) {
        TripDetailsScreen(
            trip = selectedTrip,
            points = state.tripPoints,
            loading = state.tripDetailsLoading,
            error = state.tripDetailsError,
            onBack = { selectedTripId = null },
            onCategory = { viewModel.updateTripCategory(selectedTrip, it) },
            onSpecialCategory = { viewModel.updateTripCategory(selectedTrip, selectedTrip.tripType, it) },
            onPurpose = { purpose, contact, note, onDone -> viewModel.updateTripPurpose(selectedTrip, purpose, contact, note, onDone) },
            availableTags = state.availableTags,
            onLoadTags = viewModel::loadTags,
            onCreateTag = viewModel::createTag,
            onTags = { tags, onDone -> viewModel.setTripTags(selectedTrip, tags, onDone) }
        )
        return
    }

    val fromDate = fromEpochDay?.let(LocalDate::ofEpochDay)
    val toDate = toEpochDay?.let(LocalDate::ofEpochDay)
    // Laufende Aufzeichnungen zeigt die Startseite, nicht die Liste.
    val completed = state.trips.filter { it.status != "recording" }
    val filtered = completed.filter { trip ->
        val day = tripLocalDate(trip, LocalUserPreferences.current)
        (vehicleFilter.isBlank() || trip.vehicleId == vehicleFilter) &&
            (tagFilter.isBlank() || trip.tags.any { it.name == tagFilter }) &&
            (categoryFilter == null || trip.tripType == categoryFilter) &&
            (fromDate == null || (day != null && !day.isBefore(fromDate))) &&
            (toDate == null || (day != null && !day.isAfter(toDate)))
    }
    val groups = filtered.groupBy { tripLocalDate(it, LocalUserPreferences.current) }.toList()
    val tags = completed.flatMap { tripTags -> tripTags.tags.map { it.name } }.distinct().sorted()
    val vehicleName: (Trip) -> String = { trip ->
        state.vehicles.firstOrNull { it.id == trip.vehicleId }?.name ?: trip.vehicleName.orEmpty()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.trips), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Surface(
                    onClick = viewModel::syncNow,
                    enabled = !state.loading,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (state.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Sync, contentDescription = stringResource(R.string.trips_sync))
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterButton(
                    icon = Icons.Default.CalendarMonth,
                    label = periodLabel(fromDate, toDate),
                    active = fromDate != null,
                    onClick = { showPeriodDialog = true },
                    modifier = Modifier.weight(1f)
                )
                Box(Modifier.weight(1f)) {
                    var expanded by remember { mutableStateOf(false) }
                    FilterButton(
                        icon = Icons.Default.DirectionsCar,
                        label = state.vehicles.firstOrNull { it.id == vehicleFilter }?.name
                            ?: stringResource(R.string.filter_all_vehicles),
                        active = vehicleFilter.isNotBlank(),
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.filter_all_vehicles)) },
                            onClick = { vehicleFilter = ""; expanded = false }
                        )
                        state.vehicles.forEach { vehicle ->
                            DropdownMenuItem(
                                text = { Text(vehicle.name) },
                                onClick = { vehicleFilter = vehicle.id; expanded = false }
                            )
                        }
                    }
                }
                if (tags.isNotEmpty()) {
                    Box {
                        var expanded by remember { mutableStateOf(false) }
                        FilterIconButton(
                            icon = Icons.AutoMirrored.Filled.Label,
                            contentDescription = stringResource(R.string.trips_filter_tags),
                            active = tagFilter.isNotBlank(),
                            onClick = { expanded = true }
                        )
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.filter_all_tags)) },
                                onClick = { tagFilter = ""; expanded = false }
                            )
                            tags.forEach { tag ->
                                DropdownMenuItem(text = { Text(tag) }, onClick = { tagFilter = tag; expanded = false })
                            }
                        }
                    }
                }
            }
        }

        item {
            CategoryFilterRow(selected = categoryFilter, onSelect = { categoryFilter = it })
        }

        item {
            val km = filtered.sumOf { it.distanceKm ?: 0.0 }
            Text(
                pluralStringResource(R.plurals.trip_count, filtered.size, filtered.size) + " · " + formatDistanceWithUnit(km, LocalUserPreferences.current),
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        state.error?.let { message ->
            item {
                Text(
                    message,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        if (filtered.isEmpty()) {
            item {
                Text(
                    stringResource(if (completed.isEmpty()) R.string.trips_empty else R.string.trips_empty_filtered),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        groups.forEach { (day, dayTrips) ->
            item(key = "header-${day ?: "unknown"}") {
                DayHeader(day, dayTrips.sumOf { it.distanceKm ?: 0.0 })
            }
            item(key = "group-${day ?: "unknown"}") {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column {
                        dayTrips.forEachIndexed { index, trip ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            TripRow(
                                trip = trip,
                                vehicleName = vehicleName(trip),
                                onClick = {
                                    selectedTripId = trip.id
                                    viewModel.loadTripDetails(trip)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPeriodDialog) {
        PeriodFilterDialog(
            initialFrom = fromDate,
            initialTo = toDate,
            onDismiss = { showPeriodDialog = false },
            onConfirm = { from, to ->
                fromEpochDay = from?.toEpochDay()
                toEpochDay = (to ?: from)?.toEpochDay()
                showPeriodDialog = false
            }
        )
    }
}

@Composable
private fun periodLabel(from: LocalDate?, to: LocalDate?): String {
    if (from == null) return stringResource(R.string.trips_filter_all_periods)
    val end = to ?: from
    return when {
        from == end -> formatLocalDate(from, "dMMMyyyy")
        from.dayOfMonth == 1 && end == from.withDayOfMonth(from.lengthOfMonth()) -> formatLocalDate(from, "MMMMyyyy")
        from.year == end.year -> "${formatLocalDate(from, "dMMM")} – ${formatLocalDate(end, "dMMMyyyy")}"
        else -> "${formatLocalDate(from, "dMMMyyyy")} – ${formatLocalDate(end, "dMMMyyyy")}"
    }
}

@Composable
private fun FilterButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (active) colors.primaryContainer else colors.surfaceContainer,
        border = BorderStroke(1.dp, if (active) colors.primary.copy(alpha = 0.5f) else colors.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun FilterIconButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (active) colors.primaryContainer else colors.surfaceContainer,
        border = BorderStroke(1.dp, if (active) colors.primary.copy(alpha = 0.5f) else colors.outlineVariant.copy(alpha = 0.5f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = if (active) colors.primary else colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun CategoryFilterRow(selected: TripType?, onSelect: (TripType?) -> Unit) {
    val options: List<TripType?> = listOf(null) + TripType.entries
    val colors = MaterialTheme.colorScheme
    LazyRow(
        modifier = Modifier.padding(top = 10.dp).selectableGroup(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(options) { option ->
            val isSelected = option == selected
            Surface(
                selected = isSelected,
                onClick = { onSelect(option) },
                modifier = Modifier.height(36.dp).semantics { role = Role.RadioButton },
                shape = RoundedCornerShape(50),
                color = if (isSelected) colors.primary else Color.Transparent,
                contentColor = if (isSelected) colors.onPrimary else colors.onSurface,
                border = if (isSelected) null else BorderStroke(1.dp, colors.outlineVariant)
            ) {
                Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Text(
                        option?.let { localizedTripType(it) } ?: stringResource(R.string.filter_all_categories),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeader(day: LocalDate?, totalKm: Double) {
    val today = today(LocalUserPreferences.current)
    val label = when {
        day == null -> "–"
        day == today -> stringResource(R.string.trips_today)
        day == today.minusDays(1) -> stringResource(R.string.trips_yesterday)
        day.year == today.year -> formatLocalDate(day, "EEEEdMMMM")
        else -> formatLocalDate(day, "EEEEdMMMMyyyy")
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            formatDistanceWithUnit(totalKm, LocalUserPreferences.current),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TripRow(trip: Trip, vehicleName: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryIcon(trip.tripType)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                shortRoute(
                    trip.startAddress,
                    trip.endAddress,
                    stringResource(R.string.trip_start),
                    stringResource(R.string.trip_destination)
                ),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOf(formatTripTimeRange(trip, LocalUserPreferences.current), vehicleName)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                SyncBadge(trip.syncState)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CategoryChip(trip.tripType)
                SpecialCategory.fromApi(trip.specialCategory)?.let { SpecialCategoryChip(it) }
            }
        }
        Text(
            buildAnnotatedString {
                append(formatDistance(trip.distanceKm, LocalUserPreferences.current))
                withStyle(SpanStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant)) {
                    append(" " + distanceUnitLabel(LocalUserPreferences.current))
                }
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
}

/** Nur sichtbar, wenn etwas aussteht oder fehlgeschlagen ist – synchronisierte und lokale Fahrten bleiben ruhig. */
@Composable
private fun SyncBadge(syncState: String) {
    val (icon, text, color) = when (syncState) {
        "PENDING" -> Triple(Icons.Default.Schedule, stringResource(R.string.sync_state_pending), Color(0xFFD99A2B))
        "FAILED" -> Triple(Icons.Default.ErrorOutline, stringResource(R.string.sync_state_failed), MaterialTheme.colorScheme.error)
        else -> return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
    }
}
