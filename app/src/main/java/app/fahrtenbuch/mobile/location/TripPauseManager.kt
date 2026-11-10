package app.fahrtenbuch.mobile.location

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import app.fahrtenbuch.mobile.data.api.SessionStore
import app.fahrtenbuch.mobile.data.local.OfflineDatabase
import app.fahrtenbuch.mobile.sync.SyncScheduler
import java.time.Instant
import java.time.ZoneId

/**
 * Pausenerkennung für die Bluetooth-Fahrzeugverbindung. Lokal und ohne Netz:
 * Trennt das Fahrzeug, läuft die Pause (Einstellung „Pause bis Fahrtende“) als Timer.
 * Verbindet sich dasselbe Fahrzeug innerhalb der Zeit wieder, geht die Fahrt weiter.
 * Sonst endet sie mit der Uhrzeit der Trennung.
 *
 * Der Alarm ist ungenau (Doze), das Ende der Fahrt wird aber immer auf die Trennung gesetzt,
 * also bleibt die Zeit korrekt, auch wenn der Alarm spät kommt.
 */
object TripPauseManager {
    const val ACTION_PAUSE_TIMEOUT = "app.fahrtenbuch.mobile.PAUSE_TIMEOUT"
    private const val TAG = "FahrtenbuchPause"

    private fun gapMillis(store: SessionStore) = store.tripMergeGapSeconds * 1_000L

    /** Startet die Pause nach einer Trennung. Die Aufzeichnung wird gestoppt, die Fahrt bleibt offen. */
    fun pause(context: Context, store: SessionStore, tripId: String, mac: String, now: Instant) {
        store.pausedTripId = tripId
        store.pausedAt = now.toString()
        store.pausedVehicleId = store.activeVehicleId
        store.pausedBluetoothMac = mac
        store.activeTripId = null
        store.activeVehicleId = null
        store.activeBluetoothMac = null
        scheduleTimeout(context, now.toEpochMilli() + gapMillis(store))
        Log.i(TAG, "Paused: trip=$tripId since=$now gap=${store.tripMergeGapSeconds}s")
    }

    /**
     * Bei einer neuen Verbindung: Ist eine Pause offen und es ist dasselbe Fahrzeug innerhalb der Zeit,
     * geht die Fahrt weiter (true). Sonst wird die Pause mit der Trennung beendet (false).
     */
    suspend fun resumeOrFinish(context: Context, mac: String): Boolean {
        val store = SessionStore(context)
        val tripId = store.pausedTripId ?: return false
        val pausedAt = store.pausedAt?.let { Instant.parse(it) } ?: return false.also { clear(context, store) }
        val withinPause = Instant.now().isBefore(pausedAt.plusMillis(gapMillis(store)))
        if (withinPause && store.pausedBluetoothMac.equals(mac, ignoreCase = true)) {
            store.activeTripId = tripId
            store.activeVehicleId = store.pausedVehicleId
            store.activeBluetoothMac = mac
            clear(context, store)
            Log.i(TAG, "Resumed: trip=$tripId")
            return true
        }
        finish(context, store, tripId, pausedAt)
        return false
    }

    /** Vom Alarm oder beim Sync aufgerufen: beendet die Pause, wenn sie abgelaufen ist. */
    suspend fun finishIfExpired(context: Context) {
        val store = SessionStore(context)
        val tripId = store.pausedTripId ?: return
        val pausedAt = store.pausedAt?.let { Instant.parse(it) } ?: return clear(context, store)
        val expiresAt = pausedAt.plusMillis(gapMillis(store))
        if (Instant.now().isBefore(expiresAt)) {
            scheduleTimeout(context, expiresAt.toEpochMilli())
            return
        }
        finish(context, store, tripId, pausedAt)
    }

    private suspend fun finish(context: Context, store: SessionStore, tripId: String, endedAt: Instant) {
        val dao = OfflineDatabase.get(context).dao()
        if (dao.trip(tripId) != null) {
            dao.markTripStopped(tripId, endedAt.toString(), ZoneId.systemDefault().id)
            TripEndNotifier.show(context, tripId)
            Log.i(TAG, "Pause expired: trip=$tripId ended at $endedAt")
        }
        clear(context, store)
        SyncScheduler.pushIfOnline(context)
    }

    private fun clear(context: Context, store: SessionStore) {
        store.pausedTripId = null
        store.pausedAt = null
        store.pausedVehicleId = null
        store.pausedBluetoothMac = null
        cancelTimeout(context)
    }

    private fun scheduleTimeout(context: Context, atMillis: Long) {
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, timeoutIntent(context))
    }

    private fun cancelTimeout(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(timeoutIntent(context))
    }

    private fun timeoutIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, BluetoothVehicleReceiver::class.java).setAction(ACTION_PAUSE_TIMEOUT),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
