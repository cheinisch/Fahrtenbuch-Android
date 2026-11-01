package app.fahrtenbuch.mobile.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.fahrtenbuch.mobile.data.api.ApiFactory
import app.fahrtenbuch.mobile.data.api.SessionStore
import app.fahrtenbuch.mobile.data.local.OfflineDatabase
import app.fahrtenbuch.mobile.data.model.TripType
import app.fahrtenbuch.mobile.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** Setzt die Fahrtart, wenn in der Fahrtende-Benachrichtigung ein Button getippt wird. */
class TripClassifyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CLASSIFY) return
        val localId = intent.getStringExtra(EXTRA_TRIP_ID) ?: return
        // Nur bekannte Werte annehmen, nie "unclassified" setzen
        val type = TripType.entries.firstOrNull {
            it.apiValue == intent.getStringExtra(EXTRA_TYPE) && it != TripType.UNCLASSIFIED
        } ?: return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                classify(context.applicationContext, localId, type)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun classify(context: Context, localId: String, type: TripType) {
        val store = SessionStore(context)
        val dao = OfflineDatabase.get(context).dao()
        val local = dao.trip(localId)
        if (local == null) {
            TripEndNotifier.cancel(context, localId)
            return
        }
        dao.setLocalTripType(localId, type.apiValue)

        if (store.isServerMode) {
            val remoteId = local.remoteId
            if (local.state == "SYNCED" && remoteId != null) {
                // Schon hochgeladen: der Sync schickt nichts mehr, also direkt klassifizieren
                // (über /classify, damit GPS-Daten und Adressen unangetastet bleiben)
                val body = JSONObject().put("category", type.apiValue).toString()
                    .toRequestBody("application/json".toMediaType())
                val sent = runCatching { ApiFactory.create(store).classifyTripJson(remoteId, body) }
                    .onFailure { Log.w(TAG, "Classify from notification failed: trip=$localId", it) }
                    .isSuccess
                if (!sent) {
                    // Offline o. Ä.: Benachrichtigung bleibt, damit man es erneut versuchen kann
                    return
                }
            }
            // Noch nicht synchron: der Sync-Worker sendet die Fahrtart beim Stop mit
            SyncScheduler.pushIfOnline(context)
        }
        TripEndNotifier.cancel(context, localId)
    }

    companion object {
        const val ACTION_CLASSIFY = "app.fahrtenbuch.mobile.CLASSIFY_TRIP"
        const val EXTRA_TRIP_ID = "trip_id"
        const val EXTRA_TYPE = "trip_type"
        private const val TAG = "FahrtenbuchTracking"
    }
}
