package app.fahrtenbuch.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Commute
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fahrtenbuch.mobile.R
import app.fahrtenbuch.mobile.data.model.SpecialCategory
import app.fahrtenbuch.mobile.data.model.Trip
import app.fahrtenbuch.mobile.ui.common.localizedSpecialCategory
import app.fahrtenbuch.mobile.ui.common.specialCategoryColors
import app.fahrtenbuch.mobile.ui.common.specialCategoryIcon
import app.fahrtenbuch.mobile.data.model.TripType
import app.fahrtenbuch.mobile.data.model.UserPreferences
import app.fahrtenbuch.mobile.ui.AppViewModel
import app.fahrtenbuch.mobile.ui.common.LocalUserPreferences
import app.fahrtenbuch.mobile.ui.common.categoryColors
import app.fahrtenbuch.mobile.ui.common.distanceUnitLabel
import app.fahrtenbuch.mobile.ui.common.formatDistance
import app.fahrtenbuch.mobile.ui.common.formatDistanceWithUnit
import app.fahrtenbuch.mobile.ui.common.localizedTripType
import app.fahrtenbuch.mobile.ui.common.splitDuration
import app.fahrtenbuch.mobile.ui.common.tripLocalDate
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Auswertung der Fahrten mit Filtern (Jahr, Monat, Kategorie, Fahrzeug): Gesamtstrecke,
 * Verteilung nach Kategorie und Strecke pro Monat bzw. pro Tag. Gerechnet wird lokal aus
 * den Fahrten der App, damit die Seite in beiden Modi und offline funktioniert.
 */
@Composable
fun StatisticsScreen(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsState()
    val prefs = LocalUserPreferences.current
    val today = LocalDate.now()

    // Laufende Aufzeichnungen zählen erst nach dem Beenden.
    val completed = remember(state.trips) { state.trips.filter { it.status != "recording" } }
    val years = remember(completed) {
        (completed.mapNotNull { tripLocalDate(it.startedAt)?.year } + today.year).distinct().sortedDescending()
    }

    var year by rememberSaveable { mutableIntStateOf(today.year) }
    var month by rememberSaveable { mutableStateOf<Int?>(null) }
    var category by rememberSaveable { mutableStateOf<TripType?>(null) }
    var vehicleId by rememberSaveable { mutableStateOf("") }

    // Jahr, Fahrzeug und Kategorie gelten für alles; der Monat nur für Summen und Kategorien,
    // damit das Monatsdiagramm den Jahresverlauf weiter zeigt.
    val yearTrips = completed.filter { trip ->
        tripLocalDate(trip.startedAt)?.year == year &&
            (vehicleId.isBlank() || trip.vehicleId == vehicleId) &&
            (category == null || trip.tripType == category)
    }
    val periodTrips = month?.let { m -> yearTrips.filter { tripLocalDate(it.startedAt)?.monthValue == m } } ?: yearTrips

    val vehicleName = state.vehicles.firstOrNull { it.id == vehicleId }?.name
    val subtitle = listOfNotNull(
        year.toString(),
        month?.let { monthName(it, TextStyle.FULL_STANDALONE) },
        vehicleName
    ).joinToString(" · ")

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.statistics), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Filter
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterDropdown(
                icon = Icons.Default.CalendarMonth,
                label = year.toString(),
                active = year != today.year,
                options = years.map { it to it.toString() },
                onSelect = { year = it },
                modifier = Modifier.weight(1f)
            )
            FilterDropdown(
                icon = Icons.Default.DateRange,
                label = month?.let { monthName(it, TextStyle.FULL_STANDALONE) } ?: stringResource(R.string.statistics_all_months),
                active = month != null,
                options = listOf<Pair<Int?, String>>(null to stringResource(R.string.statistics_all_months)) +
                    (1..12).map { it to monthName(it, TextStyle.FULL_STANDALONE) },
                onSelect = { month = it },
                modifier = Modifier.weight(1.3f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterDropdown(
                icon = Icons.Default.Category,
                label = category?.let { localizedTripType(it) } ?: stringResource(R.string.statistics_all_categories),
                active = category != null,
                options = listOf<Pair<TripType?, String>>(null to stringResource(R.string.statistics_all_categories)) +
                    TripType.entries.map { it to localizedTripType(it) },
                onSelect = { category = it },
                modifier = Modifier.weight(1f)
            )
            FilterDropdown(
                icon = Icons.Default.DirectionsCar,
                label = vehicleName ?: stringResource(R.string.filter_all_vehicles),
                active = vehicleName != null,
                options = listOf("" to stringResource(R.string.filter_all_vehicles)) + state.vehicles.map { it.id to it.name },
                onSelect = { vehicleId = it },
                modifier = Modifier.weight(1f)
            )
        }

        SummaryCard(periodTrips, prefs)
        CategoryCard(periodTrips, prefs)
        SpecialCategoryCard(periodTrips, prefs)

        if (month == null) {
            BarChartCard(
                title = stringResource(R.string.statistics_per_month),
                values = (1..12).map { m -> yearTrips.filter { tripLocalDate(it.startedAt)?.monthValue == m }.sumOf { it.distanceKm ?: 0.0 } },
                labels = (1..12).map { monthName(it, TextStyle.NARROW_STANDALONE) },
                // Aktueller Monat hervorgehoben; Tippen auf einen Balken filtert auf diesen Monat
                highlightIndex = if (year == today.year) today.monthValue - 1 else null,
                accessibilityLabels = (1..12).map { monthName(it, TextStyle.FULL_STANDALONE) },
                prefs = prefs,
                onBarClick = { index -> month = index + 1 }
            )
        } else {
            val days = YearMonth.of(year, month!!).lengthOfMonth()
            BarChartCard(
                title = stringResource(R.string.statistics_per_day),
                values = (1..days).map { d -> periodTrips.filter { tripLocalDate(it.startedAt)?.dayOfMonth == d }.sumOf { it.distanceKm ?: 0.0 } },
                labels = (1..days).map { d -> if (d == 1 || d % 5 == 0) d.toString() else "" },
                highlightIndex = if (year == today.year && month == today.monthValue) today.dayOfMonth - 1 else null,
                accessibilityLabels = (1..days).map { it.toString() },
                prefs = prefs,
                onBarClick = null
            )
        }
    }
}

@Composable
private fun SummaryCard(trips: List<Trip>, prefs: UserPreferences) {
    val totalKm = trips.sumOf { it.distanceKm ?: 0.0 }
    val totalSeconds = trips.sumOf { it.durationSeconds ?: 0 }
    val duration = splitDuration(totalSeconds.takeIf { trips.isNotEmpty() })
    StatCard {
        Text(
            stringResource(R.string.statistics_total_distance),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            buildAnnotatedString {
                append(formatDistance(totalKm, prefs))
                withStyle(SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    append(" " + distanceUnitLabel(prefs))
                }
            },
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth()) {
            SummaryValue(trips.size.toString(), pluralStringResource(R.plurals.statistics_trips_label, trips.size), Modifier.weight(1f))
            SummaryValue(
                if (duration.second.isEmpty()) duration.first else "${duration.first} ${duration.second}",
                stringResource(R.string.statistics_driving_time),
                Modifier.weight(1f)
            )
            SummaryValue(
                if (trips.isEmpty()) "–" else formatDistanceWithUnit(totalKm / trips.size, prefs),
                stringResource(R.string.statistics_avg_per_trip),
                Modifier.weight(1f)
            )
        }
        if (trips.isEmpty()) {
            Text(
                stringResource(R.string.statistics_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SummaryValue(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun CategoryCard(trips: List<Trip>, prefs: UserPreferences) {
    val perCategory = TripType.entries
        .map { type -> type to trips.filter { it.tripType == type }.sumOf { it.distanceKm ?: 0.0 } }
        .sortedByDescending { it.second }
    val max = perCategory.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
    StatCard {
        Text(
            stringResource(R.string.statistics_by_category),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        perCategory.forEach { (type, km) ->
            val colors = categoryColors(type)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(categoryIcon(type), contentDescription = null, tint = colors.content, modifier = Modifier.size(20.dp))
                    Text(localizedTripType(type), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(
                        if (km > 0) formatDistanceWithUnit(km, prefs) else "–",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                ) {
                    if (km > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth((km / max).toFloat().coerceIn(0.02f, 1f))
                                .fillMaxHeight()
                                .background(colors.content, RoundedCornerShape(50))
                        )
                    }
                }
            }
        }
    }
}

/** Nur Sonderkategorien mit Strecke im Zeitraum; ohne solche Fahrten entfällt die Karte. */
@Composable
private fun SpecialCategoryCard(trips: List<Trip>, prefs: UserPreferences) {
    val perCategory = SpecialCategory.entries
        .map { category -> category to trips.filter { it.specialCategory == category.apiValue }.sumOf { it.distanceKm ?: 0.0 } }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
    if (perCategory.isEmpty()) return
    val max = perCategory.maxOf { it.second }
    StatCard {
        Text(
            stringResource(R.string.statistics_by_special_category),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        perCategory.forEach { (category, km) ->
            val colors = specialCategoryColors(category)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(specialCategoryIcon(category), contentDescription = null, tint = colors.content, modifier = Modifier.size(20.dp))
                    Text(localizedSpecialCategory(category), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(formatDistanceWithUnit(km, prefs), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((km / max).toFloat().coerceIn(0.02f, 1f))
                            .fillMaxHeight()
                            .background(colors.content, RoundedCornerShape(50))
                    )
                }
            }
        }
    }
}

@Composable
private fun BarChartCard(
    title: String,
    values: List<Double>,
    labels: List<String>,
    highlightIndex: Int?,
    accessibilityLabels: List<String>,
    prefs: UserPreferences,
    onBarClick: ((Int) -> Unit)?
) {
    val max = values.maxOrNull()?.takeIf { it > 0 }
    val barColor = MaterialTheme.colorScheme.primary
    val highlightColor = categoryColors(TripType.UNCLASSIFIED).content
    StatCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            max?.let {
                Text(
                    stringResource(R.string.statistics_max, formatDistanceWithUnit(it, prefs)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(140.dp),
            horizontalArrangement = Arrangement.spacedBy(if (values.size > 12) 2.dp else 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            values.forEachIndexed { index, value ->
                val fraction = if (max == null || value <= 0) 0f else (value / max).toFloat().coerceIn(0.03f, 1f)
                val description = "${accessibilityLabels[index]}: ${formatDistanceWithUnit(value, prefs)}"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(if (onBarClick != null) Modifier.clickable { onBarClick(index) } else Modifier)
                        .semantics { contentDescription = description },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            // Leere Monate/Tage als dünne Grundlinie
                            .then(if (fraction == 0f) Modifier.height(2.dp) else Modifier.fillMaxHeight(fraction))
                            .background(
                                when {
                                    fraction == 0f -> MaterialTheme.colorScheme.surfaceVariant
                                    index == highlightIndex -> highlightColor
                                    else -> barColor
                                },
                                RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                            )
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (values.size > 12) 2.dp else 6.dp)) {
            labels.forEach { label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun StatCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

@Composable
private fun <T> FilterDropdown(
    icon: ImageVector,
    label: String,
    active: Boolean,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Box(modifier) {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(16.dp),
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
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = {
                    onSelect(value)
                    expanded = false
                })
            }
        }
    }
}

private fun categoryIcon(type: TripType): ImageVector = when (type) {
    TripType.BUSINESS -> Icons.Default.Work
    TripType.PRIVATE -> Icons.Default.Home
    TripType.COMMUTE -> Icons.Default.Commute
    TripType.UNCLASSIFIED -> Icons.Default.QuestionMark
}

private fun monthName(month: Int, style: TextStyle): String =
    Month.of(month).getDisplayName(style, Locale.getDefault())
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
