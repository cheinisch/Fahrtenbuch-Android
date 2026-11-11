package app.fahrtenbuch.mobile.location

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
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
    private companion object {
        const val TAG = "FahrtenbuchBt"
    }

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
        Log.i(TAG, "BT verbunden: mac=$mac")
        // Offene Pause: gleiches Fahrzeug innerhalb der Zeit setzt die Fahrt fort, sonst endet sie jetzt
        if (TripPauseManager.resumeOrFinish(context, mac)) {
            Log.i(TAG, "BT verbunden: Pause war offen, Fahrt wird fortgesetzt")
            startTracking(context)
            return
        }
        val dao = OfflineDatabase.get(context).dao()
        val vehicle = dao.vehicleByBluetooth(mac)
        if (vehicle == null) {
            Log.i(TAG, "BT verbunden: kein Fahrzeug mit dieser MAC lokal gespeichert, keine Fahrt gestartet")
            return
        }
        val store = SessionStore(context)
        if (store.activeTripId != null) {
            Log.i(TAG, "BT verbunden: es läuft schon eine Fahrt (${store.activeTripId}), keine neue Fahrt")
            return
        }
        Log.i(TAG, "BT verbunden: neue Fahrt für Fahrzeug ${vehicle.id}")

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
        Log.i(TAG, "BT getrennt: mac=$mac aktive-mac=${store.activeBluetoothMac} aktive-fahrt=${store.activeTripId}")
        if (!store.activeBluetoothMac.equals(mac, ignoreCase = true)) {
            Log.i(TAG, "BT getrennt: nicht das aktive Fahrzeug, Fahrt bleibt unverändert")
            return
        }
        val localId = store.activeTripId
        if (localId == null) {
            Log.i(TAG, "BT getrennt: keine aktive Fahrt vorhanden")
            return
        }
        val dao = OfflineDatabase.get(context).dao()
        val trip = dao.trip(localId)
        if (trip == null) {
            Log.w(TAG, "BT getrennt: Fahrt $localId nicht in der Datenbank gefunden")
            return
        }

        // Mit Pause: Aufzeichnung stoppen, Fahrt offen lassen, bis die Pause abläuft oder wieder verbunden wird
        if (store.tripMergeGapSeconds > 0) {
            val gap = store.tripMergeGapSeconds
            Log.i(
                TAG,
                "BT getrennt, lasse Fahrt $localId weiterlaufen, weil Pause $gap s (${gap / 60} min " +
                    "${gap % 60} s) läuft; Ende nach Ablauf oder bei erneuter Verbindung"
            )
            TripPauseManager.pause(context, store, trip.localId, mac, Instant.now())
            context.startService(
                Intent(context, TripTrackingService::class.java)
                    .setAction(TripTrackingService.ACTION_STOP)
            )
            return
        }

        Log.i(TAG, "BT getrennt: keine Pausenzeit konfiguriert, Fahrt $localId wird sofort beendet")
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
