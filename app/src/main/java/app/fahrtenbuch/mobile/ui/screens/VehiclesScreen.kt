package app.fahrtenbuch.mobile.ui.screens

import androidx.compose.ui.res.stringResource
import app.fahrtenbuch.mobile.R
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.fahrtenbuch.mobile.data.model.Vehicle
import app.fahrtenbuch.mobile.ui.AppViewModel

data class PairedBluetoothDevice(val name: String, val address: String)

@Composable
fun VehiclesScreen(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var editVehicle by remember { mutableStateOf<Vehicle?>(null) }
    var pairedDevices by remember { mutableStateOf<List<PairedBluetoothDevice>>(emptyList()) }
    var bluetoothError by remember { mutableStateOf<String?>(null) }
    var showVehicleWizard by rememberSaveable { mutableStateOf(false) }
    var autoWizardShown by rememberSaveable { mutableStateOf(false) }

    fun readBluetoothDevices() {
        runCatching { pairedBluetoothDevices(context) }
            .onSuccess {
                pairedDevices = it
                bluetoothError = null
            }
            .onFailure {
                bluetoothError = it.message ?: context.getString(R.string.vehicles_bt_read_failed)
            }
    }

    val bluetoothPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) readBluetoothDevices()
        else bluetoothError = context.getString(R.string.vehicles_bt_permission_denied)
    }

    LaunchedEffect(Unit) {
        viewModel.loadVehicles()
    }

    LaunchedEffect(state.loading, state.vehiclesLoadedFromServer, state.vehicles.size) {
        if (!state.loading && state.vehiclesLoadedFromServer && state.vehicles.isEmpty() && !autoWizardShown) {
            autoWizardShown = true
            showVehicleWizard = true
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(stringResource(R.string.vehicles), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.vehicles_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row {
                    IconButton(onClick = { showVehicleWizard = true }, enabled = !state.loading) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.vehicles_add))
                    }
                    IconButton(onClick = viewModel::loadVehicles, enabled = !state.loading) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.vehicles_reload))
                    }
                }
            }
        }

        state.error?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }

        if (state.loading && state.vehicles.isEmpty()) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }

        if (!state.loading && state.vehicles.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            if (state.vehiclesLoadedFromServer)
                                stringResource(R.string.vehicles_empty_server)
                            else
                                stringResource(R.string.vehicles_empty_local)
                        )
                        Button(onClick = { showVehicleWizard = true }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.setup_first_vehicle))
                        }
                    }
                }
            }
        }

        items(state.vehicles, key = { it.id }) { vehicle ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.DirectionsCar, contentDescription = null)
                        Column {
                            Text(vehicle.name, fontWeight = FontWeight.Bold)
                            Text(vehicle.licensePlate ?: stringResource(R.string.vehicles_no_plate), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        vehicle.bluetoothMac?.let { stringResource(R.string.vehicles_bt_assigned, it) } ?: stringResource(R.string.vehicles_bt_none),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            editVehicle = vehicle
                            bluetoothError = null
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                            ) {
                                bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                            } else {
                                readBluetoothDevices()
                            }
                        },
                        enabled = !state.loading
                    ) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (vehicle.bluetoothMac == null) R.string.vehicles_bt_assign else R.string.vehicles_bt_change))
                    }
                }
            }
        }
    }

    if (showVehicleWizard) {
        VehicleSetupWizard(
            loading = state.loading,
            error = state.error,
            onDismiss = { if (!state.loading) showVehicleWizard = false },
            onCreate = { name, licensePlate ->
                viewModel.createVehicle(name, licensePlate) { created ->
                    showVehicleWizard = false
                    editVehicle = created
                    bluetoothError = null
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                    ) {
                        bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    } else {
                        readBluetoothDevices()
                    }
                }
            }
        )
    }

    editVehicle?.let { vehicle ->
        BluetoothAssignmentDialog(
            vehicle = vehicle,
            devices = pairedDevices,
            error = bluetoothError,
            loading = state.loading,
            onDismiss = { if (!state.loading) editVehicle = null },
            onRefresh = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                ) bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                else readBluetoothDevices()
            },
            onSave = { mac ->
                viewModel.setVehicleBluetooth(vehicle.id, mac) { editVehicle = null }
            }
        )
    }
}

@Composable
private fun BluetoothAssignmentDialog(
    vehicle: Vehicle,
    devices: List<PairedBluetoothDevice>,
    error: String?,
    loading: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onSave: (String?) -> Unit
) {
    var selectedMac by remember(vehicle.id, vehicle.bluetoothMac) { mutableStateOf(vehicle.bluetoothMac) }
    var manualMac by remember(vehicle.id, vehicle.bluetoothMac) { mutableStateOf(vehicle.bluetoothMac.orEmpty()) }
    val macRegex = remember { Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$") }
    val normalizedManual = manualMac.trim().uppercase()
    val manualValid = normalizedManual.isBlank() || macRegex.matches(normalizedManual)
    val deviceListState = rememberLazyListState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vehicles_bt_dialog_title, vehicle.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.choose_bluetooth))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                if (devices.isEmpty()) {
                    Text(stringResource(R.string.no_paired_devices), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(
                        state = deviceListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                    ) {
                        items(devices, key = { it.address }) { device ->
                            Row(Modifier.fillMaxWidth()) {
                                RadioButton(
                                    selected = selectedMac.equals(device.address, ignoreCase = true),
                                    onClick = {
                                        selectedMac = device.address
                                        manualMac = device.address
                                    }
                                )
                                Column {
                                    Text(device.name)
                                    Text(device.address, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                TextButton(onClick = onRefresh, enabled = !loading) {
                    Text(stringResource(R.string.reload_paired_devices))
                }

                HorizontalDivider()
                OutlinedTextField(
                    value = manualMac,
                    onValueChange = {
                        manualMac = it
                        selectedMac = it.ifBlank { null }
                    },
                    label = { Text(stringResource(R.string.manual_mac)) },
                    placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                    isError = !manualValid,
                    supportingText = {
                        if (!manualValid) Text(stringResource(R.string.mac_format))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (vehicle.bluetoothMac != null) {
                    TextButton(onClick = { onSave(null) }, enabled = !loading) {
                        Text(stringResource(R.string.remove_assignment))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(normalizedManual.ifBlank { null }) },
                enabled = !loading && normalizedManual.isNotBlank() && manualValid
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@SuppressLint("MissingPermission")
private fun pairedBluetoothDevices(context: Context): List<PairedBluetoothDevice> {
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        ?: error(context.getString(R.string.vehicles_bt_unsupported))
    val adapter: BluetoothAdapter = manager.adapter
        ?: error(context.getString(R.string.vehicles_bt_unsupported))
    if (!adapter.isEnabled) error(context.getString(R.string.vehicles_bt_disabled))

    return adapter.bondedDevices
        .map { device ->
            PairedBluetoothDevice(
                name = device.name?.takeIf { it.isNotBlank() } ?: context.getString(R.string.vehicles_bt_unknown_device),
                address = device.address.uppercase()
            )
        }
        .sortedBy { it.name.lowercase() }
}


@Composable
private fun VehicleSetupWizard(
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onCreate: (String, String?) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var licensePlate by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setup_vehicle)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.setup_vehicle_description))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.vehicle_name)) },
                    placeholder = { Text(stringResource(R.string.vehicle_example)) },
                    singleLine = true,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = licensePlate,
                    onValueChange = { licensePlate = it.uppercase() },
                    label = { Text(stringResource(R.string.license_plate_optional)) },
                    placeholder = { Text(stringResource(R.string.license_plate_example)) },
                    singleLine = true,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text(
                    stringResource(R.string.vehicle_step_one),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name.trim(), licensePlate.trim().ifBlank { null }) },
                enabled = name.isNotBlank() && !loading
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.create_vehicle))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) { Text(stringResource(R.string.cancel)) }
        }
    )
}
