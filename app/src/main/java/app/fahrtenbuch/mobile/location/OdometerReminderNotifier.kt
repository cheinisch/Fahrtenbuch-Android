package app.fahrtenbuch.mobile.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import app.fahrtenbuch.mobile.MainActivity
import app.fahrtenbuch.mobile.R
import app.fahrtenbuch.mobile.data.api.SessionStore
import app.fahrtenbuch.mobile.data.model.OdometerReminder

/**
 * Erinnerung an den Kilometerstand. Der Stand lässt sich direkt in der Benachrichtigung
 * eintippen ([OdometerReminderReceiver]), ohne die App zu öffnen.
 */
object OdometerReminderNotifier {
    private const val CHANNEL_ID = "odometer_reminder"
    const val KEY_REPLY = "odometer_reply"

    /** [error] ersetzt den Text, wenn das Eintragen aus der Benachrichtigung fehlgeschlagen ist. */
    fun show(context: Context, reminder: OdometerReminder, error: String? = null) {
        if (!SessionStore(context).notificationsEnabled) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        createChannel(context)

        val openApp = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = error?.let { context.getString(R.string.odometer_notification_error, it) }
            ?: context.getString(R.string.odometer_notification_text, reminder.vehicleName)

        val remoteInput = RemoteInput.Builder(KEY_REPLY)
            .setLabel(context.getString(R.string.odometer_notification_hint))
            .build()
        val reply = NotificationCompat.Action.Builder(
            0, context.getString(R.string.odometer_notification_action), receiverIntent(context, reminder, OdometerReminderReceiver.ACTION_REPLY, mutable = true)
        ).addRemoteInput(remoteInput).build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.odometer_notification_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            // Wegwischen heißt: nicht mehr erinnern
            .setDeleteIntent(receiverIntent(context, reminder, OdometerReminderReceiver.ACTION_DISMISS, mutable = false))
            .addAction(reply)
            .build()
        runCatching { manager.notify(notificationId(reminder.id), notification) }
    }

    fun cancel(context: Context, reminderId: String) =
        NotificationManagerCompat.from(context).cancel(notificationId(reminderId))

    private fun notificationId(reminderId: String) = 3000 + (reminderId.hashCode() and 0x7FFF)

    private fun receiverIntent(context: Context, reminder: OdometerReminder, action: String, mutable: Boolean): PendingIntent {
        val intent = Intent(action)
            .setComponent(ComponentName(context, OdometerReminderReceiver::class.java))
            .putExtra(OdometerReminderReceiver.EXTRA_REMINDER_ID, reminder.id)
            .putExtra(OdometerReminderReceiver.EXTRA_VEHICLE_ID, reminder.vehicleId)
            .putExtra(OdometerReminderReceiver.EXTRA_VEHICLE_NAME, reminder.vehicleName)
            .putExtra(OdometerReminderReceiver.EXTRA_DUE_DATE, reminder.dueDate)
        // RemoteInput braucht einen veränderbaren PendingIntent, sonst kommt der Text nicht an
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, (reminder.id + action).hashCode(), intent, flags)
    }

    private fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.odometer_notification_channel), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
}
