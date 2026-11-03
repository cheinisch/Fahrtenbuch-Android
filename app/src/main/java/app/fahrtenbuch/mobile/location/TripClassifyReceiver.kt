package app.fahrtenbuch.mobile.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.fahrtenbuch.mobile.data.api.ApiFactory
import app.fahrtenbuch.mobile.data.api.SessionStore
import app.fahrtenbuch.mobile.data.repository.FahrtenbuchRepository
import app.fahrtenbuch.mobile.data.model.TripType
import app.fahrtenbuch.mobile.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
        Log.i(TAG, "Classify from notification: trip=$localId type=${type.apiValue}")
        val saved = try {
            val repository = FahrtenbuchRepository(context, ApiFactory.create(store), store)
            repository.classifyFromNotification(localId, type)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            Log.e(TAG, "Classify from notification crashed: trip=$localId", error)
            false
        }
        if (saved) {
            if (store.isServerMode) SyncScheduler.pushIfOnline(context)
            TripEndNotifier.cancel(context, localId)
        } else {
            // Ein Tipp auf einen Button schließt die Benachrichtigung nicht zuverlässig und
            // sonst bliebe der Fehler unsichtbar: neu zeigen, mit Hinweis zum erneuten Versuch
            TripEndNotifier.show(context, localId, failed = true)
        }
    }

    companion object {
        const val ACTION_CLASSIFY = "app.fahrtenbuch.mobile.CLASSIFY_TRIP"
        const val EXTRA_TRIP_ID = "trip_id"
        const val EXTRA_TYPE = "trip_type"
        private const val TAG = "FahrtenbuchTracking"
    }
}
