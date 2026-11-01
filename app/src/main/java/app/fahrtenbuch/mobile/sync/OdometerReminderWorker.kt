package app.fahrtenbuch.mobile.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import app.fahrtenbuch.mobile.data.api.ApiFactory
import app.fahrtenbuch.mobile.data.api.SessionStore
import app.fahrtenbuch.mobile.location.OdometerReminderNotifier
import java.util.concurrent.TimeUnit

/**
 * Holt offene Kilometerstand-Erinnerungen vom Server und zeigt je Erinnerung einmal eine
 * Benachrichtigung. Ein echter Push (FCM) ist dafür nicht nötig: der Server legt die
 * Erinnerung an, die App fragt sie ab – etwa alle 15 Minuten.
 */
class OdometerReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = SessionStore(applicationContext)
        if (!store.isServerMode || !store.serverConfigured || store.refreshToken == null || !store.notificationsEnabled) {
            return Result.success()
        }
        return try {
            val reminders = ApiFactory.create(store).odometerReminders()
            val shown = store.shownOdometerReminders
            reminders.filter { it.id !in shown }.forEach { OdometerReminderNotifier.show(applicationContext, it) }
            // Erinnerungen, die der Server nicht mehr offen führt (z. B. Stand im Web eingetragen)
            (shown - reminders.map { it.id }.toSet()).forEach { OdometerReminderNotifier.cancel(applicationContext, it) }
            // Nur noch offene merken, damit die Liste nicht wächst
            store.shownOdometerReminders = reminders.map { it.id }.toSet()
            Result.success()
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            // Kein Retry nötig: der nächste periodische Lauf versucht es erneut
            Log.w(TAG, "Odometer reminder check failed", error)
            Result.success()
        }
    }

    private companion object { const val TAG = "FahrtenbuchReminder" }
}

object OdometerReminderScheduler {
    private const val WORK_NAME = "fahrtenbuch-odometer-reminders"

    /** Plant die Abfrage im Server-Modus mit eingeschalteten Benachrichtigungen, sonst nicht. */
    fun sync(context: Context) {
        val store = SessionStore(context)
        val manager = WorkManager.getInstance(context)
        if (!store.isServerMode || !store.notificationsEnabled) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<OdometerReminderWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
