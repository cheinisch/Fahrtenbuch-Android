package app.fahrtenbuch.mobile.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trip_details)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "${trip.startAddress ?: "Start unbekannt"} → ${trip.endAddress ?: "Ziel unbekannt"}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text("Start: ${trip.startedAt}")
                Text("Ende: ${trip.endedAt ?: "Läuft"}")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text("Strecke: ${trip.distanceKm ?: "–"} km")
                    Text("Dauer: ${trip.durationSeconds ?: "–"} Sekunden")
                }
                FilterMenu(
                    stringResource(R.string.category),
                    TripType.entries.map { it.apiValue to localizedTripType(it) },
                    trip.tripType.apiValue
                ) { chosen ->
                    TripType.entries.firstOrNull { it.apiValue == chosen }?.let(onCategory)
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }

            Text(
                "Strecke",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                when {
                    points.isNotEmpty() -> TripRouteMap(
                        points,
                        Modifier.fillMaxSize()
                    )
                    loading -> CircularProgressIndicator()
                    else -> Text(stringResource(R.string.no_gps_route))
                }
            }
        }
    }
}

@Composable
private fun TripRouteMap(
    points: List<TrackPoint>,
    modifier: Modifier = Modifier.fillMaxWidth().height(240.dp)
) {
    val geoPoints = remember(points) { points.map { GeoPoint(it.lat, it.lon) } }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).apply {
                setMultiTouchControls(true)
                minZoomLevel = 3.0
                maxZoomLevel = 20.0
            }
        },
        update = { map ->
            map.overlays.clear()

            if (geoPoints.isNotEmpty()) {
                val route = Polyline(map).apply {
                    setPoints(geoPoints)
                    outlinePaint.strokeWidth = 8f
                    outlinePaint.color = android.graphics.Color.rgb(37, 99, 235)
                }
                map.overlays.add(route)

                Marker(map).apply {
                    position = geoPoints.first()
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Start"
                    map.overlays.add(this)
                }

                if (geoPoints.size > 1) {
                    Marker(map).apply {
                        position = geoPoints.last()
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "Ziel"
                        map.overlays.add(this)
                    }
                }

                if (geoPoints.size == 1) {
                    map.controller.setZoom(16.0)
                    map.controller.setCenter(geoPoints.first())
                } else {
                    map.post {
                        map.zoomToBoundingBox(route.bounds, true, 64)
                    }
                }
            }

            map.invalidate()
        }
    )
}

@Composable
private fun localizedTripType(type: TripType): String = when (type) {
    TripType.BUSINESS -> stringResource(R.string.trip_business)
    TripType.PRIVATE -> stringResource(R.string.trip_private)
    TripType.COMMUTE -> stringResource(R.string.trip_commute)
    TripType.UNCLASSIFIED -> stringResource(R.string.trip_unclassified)
}
