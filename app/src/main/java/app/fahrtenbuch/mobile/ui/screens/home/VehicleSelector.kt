package app.fahrtenbuch.mobile.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.fahrtenbuch.mobile.R
import app.fahrtenbuch.mobile.data.model.Vehicle

/**
 * Fahrzeugauswahl auf der Startseite, gestaffelt nach Anzahl:
 * - 0: Hinweis mit „Fahrzeug einrichten“
 * - 1: nur Anzeige, keine Auswahl nötig
 * - 2–4: Kachelraster mit zwei Spalten (bei ungerader Anzahl nutzt die letzte Kachel die volle Breite)
 * - ab 5: horizontal wischbare Reihe mit fester Höhe, damit die Karte nicht wächst
 *
 * Während einer Aufzeichnung ([locked]) ist die Auswahl gesperrt.
 */
@Composable
fun VehicleSelector(
    vehicles: List<Vehicle>,
    selectedId: String?,
    locked: Boolean,
    onSelect: (String) -> Unit,
    onSetupVehicle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.vehicle).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (locked && vehicles.size > 1) {
                Text(
                    stringResource(R.string.home_vehicle_locked),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when {
            vehicles.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.home_no_vehicle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onSetupVehicle) { Text(stringResource(R.string.setup_vehicle)) }
            }

            vehicles.size == 1 -> VehicleTile(
                vehicle = vehicles.first(),
                selected = true,
                interactive = false,
                dimmed = false,
                onClick = {},
                modifier = Modifier.fillMaxWidth()
            )

            vehicles.size <= 4 -> Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                vehicles.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { vehicle ->
                            val isSelected = vehicle.id == selectedId
                            VehicleTile(
                                vehicle = vehicle,
                                selected = isSelected,
                                interactive = !locked,
                                dimmed = locked && !isSelected,
                                onClick = { onSelect(vehicle.id) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            else -> {
                val listState = rememberLazyListState()
                val selectedIndex = vehicles.indexOfFirst { it.id == selectedId }
                LaunchedEffect(selectedIndex) {
                    if (selectedIndex >= 0) listState.animateScrollToItem(selectedIndex)
                }
                LazyRow(
                    state = listState,
                    modifier = Modifier.selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 24.dp)
                ) {
                    itemsIndexed(vehicles, key = { _, vehicle -> vehicle.id }) { _, vehicle ->
                        val isSelected = vehicle.id == selectedId
                        VehicleTile(
                            vehicle = vehicle,
                            selected = isSelected,
                            interactive = !locked,
                            dimmed = locked && !isSelected,
                            onClick = { onSelect(vehicle.id) },
                            modifier = Modifier.width(160.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleTile(
    vehicle: Vehicle,
    selected: Boolean,
    interactive: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val hasBluetooth = !vehicle.bluetoothMac.isNullOrBlank()
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(if (selected) colors.primary else colors.surfaceVariant, RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.DirectionsCar,
                    contentDescription = null,
                    tint = if (selected) colors.onPrimary else colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    vehicle.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (hasBluetooth) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(13.dp))
                    }
                    Text(
                        if (hasBluetooth) stringResource(R.string.home_bluetooth_auto)
                        else vehicle.licensePlate?.takeIf { it.isNotBlank() } ?: stringResource(R.string.home_no_bluetooth),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(0.dp))
        }
    }
    val tileModifier = modifier
        .heightIn(min = 64.dp)
        .alpha(if (dimmed) 0.45f else 1f)
    val shape = RoundedCornerShape(18.dp)
    val border = if (selected) BorderStroke(2.dp, colors.primary) else BorderStroke(1.dp, colors.outlineVariant)
    val container = if (selected) colors.primaryContainer else colors.surface
    if (interactive) {
        Surface(
            selected = selected,
            onClick = onClick,
            modifier = tileModifier.semantics { role = Role.RadioButton },
            shape = shape,
            color = container,
            border = border
        ) { content() }
    } else {
        Surface(modifier = tileModifier, shape = shape, color = container, border = border) { content() }
    }
}
