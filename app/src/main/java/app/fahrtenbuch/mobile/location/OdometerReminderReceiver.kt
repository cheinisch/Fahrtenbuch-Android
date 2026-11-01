package app.fahrtenbuch.mobile.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import app.fahrtenbuch.mobile.R
import app.fahrtenbuch.mobile.data.api.ApiFactory
import app.fahrtenbuch.mobile.data.api.SessionStore
import app.fahrtenbuch.mobile.data.model.OdometerReadingRequest
import app.fahrtenbuch.mobile.data.model.OdometerReminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Antwort oder Wegwischen der Kilometerstand-Benachrichtigung. */
class OdometerReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminder = OdometerReminder(
            id = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return,
            vehicleId = intent.getStringExtra(EXTRA_VEHICLE_ID) ?: return,
            vehicleName = intent.getStringExtra(EXTRA_VEHICLE_NAME).orEmpty(),
            dueDate = intent.getStringExtra(EXTRA_DUE_DATE).orEmpty()
        )
        val typed = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(OdometerReminderNotifier.KEY_REPLY)?.toString()
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_REPLY -> save(context.applicationContext, reminder, typed)
                    ACTION_DISMISS -> acknowledge(context.applicationContext, reminder.id)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun save(context: Context, reminder: OdometerReminder, typed: String?) {
        val km = parseKilometers(typed)
        if (km == null) {
            // Benachrichtigung neu zeigen, sonst bleibt der Lade-Kreis der Eingabe stehen
            OdometerReminderNotifier.show(context, reminder, error = context.getString(R.string.odometer_notification_invalid))
            return
        }
        val store = SessionStore(context)
        val today = LocalDate.now()
        val month = "%04d-%02d".format(today.year, today.monthValue)
        try {
            val api = ApiFactory.create(store)
            api.saveOdometerReading(reminder.vehicleId, month, OdometerReadingRequest(km, today.toString()))
            acknowledge(context, reminder.id)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            Log.w(TAG, "Odometer reading from notification failed: reminder=${reminder.id}", error)
            OdometerReminderNotifier.show(context, reminder, error = ApiFactory.readableError(error))
        }
    }

    private suspend fun acknowledge(context: Context, reminderId: String) {
        OdometerReminderNotifier.cancel(context, reminderId)
        // Schlägt das fehl (offline), bleibt die Erinnerung offen, wird aber nicht erneut gezeigt
        runCatching { ApiFactory.create(SessionStore(context)).ackOdometerReminder(reminderId) }
            .onFailure { Log.w(TAG, "Acknowledging reminder failed: $reminderId", it) }
    }

    companion object {
        const val ACTION_REPLY = "app.fahrtenbuch.mobile.ODOMETER_REPLY"
        const val ACTION_DISMISS = "app.fahrtenbuch.mobile.ODOMETER_DISMISS"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_VEHICLE_ID = "vehicle_id"
        const val EXTRA_VEHICLE_NAME = "vehicle_name"
        const val EXTRA_DUE_DATE = "due_date"
        private const val TAG = "FahrtenbuchReminder"

        /** Akzeptiert "123456", "123 456", "123.456", "123,5" und "123.456,7"; sonst null. */
        fun parseKilometers(input: String?): Double? {
            var text = input?.filterNot { it.isWhitespace() || it == ' ' || it == '\'' } ?: return null
            if (text.isEmpty()) return null
            text = when {
                text.contains(',') && text.contains('.') -> text.replace(".", "").replace(',', '.')
                text.contains(',') -> text.replace(',', '.')
                // "123.456" ist hier ein Tausenderpunkt, kein Dezimalpunkt
                Regex("""^\d{1,3}(\.\d{3})+$""").matches(text) -> text.replace(".", "")
                else -> text
            }
            return text.toDoubleOrNull()?.takeIf { it in 0.0..10_000_000.0 }
        }
    }
}
