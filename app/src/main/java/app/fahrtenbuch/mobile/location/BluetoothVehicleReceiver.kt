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
        // Alarm der Pause: endet die Fahrt, wenn niemand wieder verbunden hat
        if (intent.action == TripPauseManager.ACTION_PAUSE_TIMEOUT) {
            val pendingTimeout = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    TripPauseManager.finishIfExpired(context)
                } finally {
                    pendingTimeout.finish()
                }
            }
            return
        }
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
        // Offene Pause: gleiches Fahrzeug innerhalb der Zeit setzt die Fahrt fort, sonst endet sie jetzt
        if (TripPauseManager.resumeOrFinish(context, mac)) {
            startTracking(context)
            return
        }
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

        startTracking(context)
    }

    private fun startTracking(context: Context) {
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

        // Mit Pause: Aufzeichnung stoppen, Fahrt offen lassen, bis die Pause abläuft oder wieder verbunden wird
        if (store.tripMergeGapSeconds > 0) {
            TripPauseManager.pause(context, store, trip.localId, mac, Instant.now())
            context.startService(
                Intent(context, TripTrackingService::class.java)
                    .setAction(TripTrackingService.ACTION_STOP)
            )
            return
        }

        dao.markTripStopped(trip.localId, Instant.now().toString(), java.time.ZoneId.systemDefault().id)
        store.activeTripId = null
        store.activeVehicleId = null
        store.activeBluetoothMac = null

        context.startService(
            Intent(context, TripTrackingService::class.java)
                .setAction(TripTrackingService.ACTION_STOP)
        )
        // Vor dem Sync, damit der Tipp auf einen Button den Stop sicher noch vor sich hat
        TripEndNotifier.show(context, trip.localId)
        SyncScheduler.pushIfOnline(context)
    }
}
