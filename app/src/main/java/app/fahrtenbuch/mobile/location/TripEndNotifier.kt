package app.fahrtenbuch.mobile.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.fahrtenbuch.mobile.MainActivity
import app.fahrtenbuch.mobile.R
import app.fahrtenbuch.mobile.data.model.TripType

/**
 * Benachrichtigung am Fahrtende mit drei Buttons (Privat, Arbeitsweg, Dienstlich).
 * Der Tipp auf einen Button geht an [TripClassifyReceiver], ohne die App zu öffnen.
 */
object TripEndNotifier {
    private const val CHANNEL_ID = "trip_end"

    fun show(context: Context, localTripId: String) {
        val manager = NotificationManagerCompat.from(context)
        // Deckt auch die fehlende POST_NOTIFICATIONS-Berechtigung ab: dann bleibt es still
        if (!manager.areNotificationsEnabled()) return
        createChannel(context)

        val openApp = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.trip_end_notification_title))
            .setContentText(context.getString(R.string.trip_end_notification_text))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)

        listOf(
            TripType.PRIVATE to R.string.trip_private,
            TripType.COMMUTE to R.string.trip_commute,
            TripType.BUSINESS to R.string.trip_business
        ).forEach { (type, label) ->
            builder.addAction(0, context.getString(label), actionIntent(context, localTripId, type))
        }

        runCatching { manager.notify(notificationId(localTripId), builder.build()) }
    }

    fun cancel(context: Context, localTripId: String) =
        NotificationManagerCompat.from(context).cancel(notificationId(localTripId))

    /** Pro Fahrt eine eigene ID, damit sich mehrere offene Fahrten nicht überschreiben. */
    private fun notificationId(localTripId: String) = 2000 + (localTripId.hashCode() and 0x7FFF)

    private fun actionIntent(context: Context, localTripId: String, type: TripType): PendingIntent {
        val intent = Intent(context, TripClassifyReceiver::class.java)
            .setAction(TripClassifyReceiver.ACTION_CLASSIFY)
            .putExtra(TripClassifyReceiver.EXTRA_TRIP_ID, localTripId)
            .putExtra(TripClassifyReceiver.EXTRA_TYPE, type.apiValue)
        // Eigener requestCode je Fahrt und Typ, sonst teilen sich die Buttons einen PendingIntent
        val requestCode = (localTripId + type.apiValue).hashCode()
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.trip_end_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }
}
