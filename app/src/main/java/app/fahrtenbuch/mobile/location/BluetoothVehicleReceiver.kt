package app.fahrtenbuch.mobile.location

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import app.fahrtenbuch.mobile.data.api.SessionStore
import app.fahrtenbuch.mobile.data.local.OfflineDatabase
import app.fahrtenbuch.mobile.data.local.OfflineTripEntity
import app.fahrtenbuch.mobile.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/**
 * Starts/stops local-first tracking when a Bluetooth MAC assigned to a vehicle
 * connects/disconnects. No network is required for either transition.
 */
class BluetoothVehicleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) return

        val device = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        } ?: return

        val mac = runCatching { device.address.uppercase() }.getOrNull() ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> onConnected(context, mac)
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> onDisconnected(context, mac)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun onConnected(context: Context, mac: String) {
        val dao = OfflineDatabase.get(context).dao()
        val vehicle = dao.vehicleByBluetooth(mac) ?: return
        val store = SessionStore(context)
        if (store.activeTripId != null) return

        val localId = UUID.randomUUID().toString()
        dao.putTrip(
            OfflineTripEntity(
                localId = localId,
                remoteId = null,
                vehicleId = vehicle.id,
                type = "unclassified",
                startedAt = Instant.now().toString(),
                endedAt = null,
                state = "RECORDING",
                startTimeZone = java.time.ZoneId.systemDefault().id
            )
        )
        store.activeVehicleId = vehicle.id
        store.activeBluetoothMac = mac
        store.activeTripId = localId

        val service = Intent(context, TripTrackingService::class.java)
            .setAction(TripTrackingService.ACTION_START)
        ContextCompat.startForegroundService(context, service)
        SyncScheduler.enqueue(context)
    }

    private suspend fun onDisconnected(context: Context, mac: String) {
        val store = SessionStore(context)
        if (!store.activeBluetoothMac.equals(mac, ignoreCase = true)) return
        val localId = store.activeTripId ?: return
        val dao = OfflineDatabase.get(context).dao()
        val trip = dao.trip(localId) ?: return

        dao.markTripStopped(trip.localId, Instant.now().toString(), java.time.ZoneId.systemDefault().id)
        store.activeTripId = null
        store.activeVehicleId = null
        store.activeBluetoothMac = null

        context.startService(
            Intent(context, TripTrackingService::class.java)
                .setAction(TripTrackingService.ACTION_STOP)
        )
        SyncScheduler.pushIfOnline(context)
    }
}
