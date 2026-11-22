package app.fahrtenbuch.mobile.location

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.fahrtenbuch.mobile.MainActivity
import app.fahrtenbuch.mobile.R
import app.fahrtenbuch.mobile.data.api.SessionStore
import app.fahrtenbuch.mobile.data.local.OfflineDatabase
import app.fahrtenbuch.mobile.data.local.TrackPointEntity
import app.fahrtenbuch.mobile.sync.SyncScheduler
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import java.time.Instant

/**
 * Offline-first location service. Every point is committed to Room before any
 * upload is attempted. A process crash or network outage therefore cannot lose
 * the recorded route.
 */
class TripTrackingService : Service() {
    private lateinit var locationClient: FusedLocationProviderClient
    private lateinit var store: SessionStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var waitingForFirstFix = true
    private var pointCount = 0L

    /** Nur auf dem Main-Thread benutzt (Callback und Handler laufen auf mainLooper). */
    private val handler = Handler(Looper.getMainLooper())
    private var anyFixReceived = false

    /** Kein erster Fix nach [FIRST_FIX_TIMEOUT_MS]: trotzdem auf normales Tracking umschalten. */
    private val firstFixTimeout = Runnable {
        if (!waitingForFirstFix) return@Runnable
        waitingForFirstFix = false
        Log.w(TAG, "No first fix after ${FIRST_FIX_TIMEOUT_MS / 1000}s; switching to normal tracking anyway")
        GpsDebugMonitor.log("Kein erster Fix nach ${FIRST_FIX_TIMEOUT_MS / 1000} s, schalte trotzdem auf normales Tracking")
        switchToNormalTracking()
    }

    /** Nach [NO_FIX_WARNING_MS] immer noch kein Fix: Nutzer warnen statt still eine leere Fahrt zu speichern. */
    private val noFixWarning = Runnable {
        if (anyFixReceived || store.activeTripId == null) return@Runnable
        Log.w(TAG, "Still no GPS fix after ${NO_FIX_WARNING_MS / 1000}s; showing warning")
        GpsDebugMonitor.log("WARNUNG: nach ${NO_FIX_WARNING_MS / 1000} s noch kein Fix, Benachrichtigung angezeigt")
        showNoFixWarning()
    }

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { GpsDebugMonitor.onLocation(it) }
            if (result.locations.isNotEmpty() && !anyFixReceived) {
                anyFixReceived = true
                handler.removeCallbacks(noFixWarning)
                NotificationManagerCompat.from(this@TripTrackingService).cancel(NO_FIX_NOTIFICATION_ID)
            }
            val localTripId = store.activeTripId ?: return
            // Erster Fix: auf dem Main-Thread umschalten, damit sich das nicht mit dem Timeout überschneidet
            if (waitingForFirstFix && result.locations.isNotEmpty()) {
                waitingForFirstFix = false
                handler.removeCallbacks(firstFixTimeout)
                Log.i(TAG, "First GPS fix received: trip=$localTripId; switching to normal tracking")
                switchToNormalTracking()
            }
            Log.d(TAG, "Location callback: trip=$localTripId locations=${result.locations.size}")
            val points = result.locations.map { location ->
                TrackPointEntity(
                    localTripId = localTripId,
                    lat = location.latitude,
                    lon = location.longitude,
                    altitude = location.altitude.takeIf { location.hasAltitude() },
                    speed = if (location.hasSpeed()) location.speed.toDouble() else null,
                    accuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
                    bearing = if (location.hasBearing()) location.bearing.toDouble() else null,
                    recordedAt = Instant.ofEpochMilli(location.time).toString()
                )
            }
            if (points.isNotEmpty()) scope.launch {
                OfflineDatabase.get(applicationContext).dao().addPoints(points)
                pointCount += points.size
                points.forEach { point ->
                    Log.d(
                        TAG,
                        "GPS stored: trip=${point.localTripId} #$pointCount " +
                            "lat=${point.lat} lon=${point.lon} accuracy=${point.accuracy}m " +
                            "speed=${point.speed}m/s bearing=${point.bearing} recordedAt=${point.recordedAt}"
                    )
                }
                Log.d(TAG, "Scheduling sync after GPS write: trip=$localTripId totalPoints=$pointCount")
                SyncScheduler.enqueue(applicationContext)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = SessionStore(this)
        locationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) { ACTION_STOP -> stopServiceTracking(); else -> startServiceTracking() }
        return START_STICKY
    }

    private fun startServiceTracking() {
        if (store.activeTripId == null) {
            Log.w(TAG, "Tracking service started without activeTripId; stopping")
            stopSelf()
            return
        }
        pointCount = 0
        Log.i(TAG, "Tracking started: trip=${store.activeTripId}")
        val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startForeground(NOTIFICATION_ID, NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.tracking_notification_title))
            .setContentText(getString(R.string.tracking_notification_text))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
            .setOngoing(true).setContentIntent(pendingIntent).build())

        if (store.gpsDebugMode) GpsDebugMonitor.start(this, store.activeTripId ?: "unknown")
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            GpsDebugMonitor.log("Abbruch: keine Standortberechtigung")
            GpsDebugMonitor.stop(this)
            stopSelf()
            return
        }
        // Nur mit genauer Berechtigung auf eine genaue Position warten: mit „Ungefähr“ käme die nie
        val hasFine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        GpsDebugMonitor.log(
            "Erste Positionsanfrage: HIGH_ACCURACY, Intervall 1000 ms, waitForAccurate=$hasFine, " +
                "Timeout ${FIRST_FIX_TIMEOUT_MS / 1000} s, Warnung nach ${NO_FIX_WARNING_MS / 1000} s"
        )
        waitingForFirstFix = true
        anyFixReceived = false
        handler.removeCallbacks(firstFixTimeout)
        handler.removeCallbacks(noFixWarning)
        NotificationManagerCompat.from(this).cancel(NO_FIX_NOTIFICATION_ID)
        val firstFixRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
            .setMinUpdateIntervalMillis(500L)
            .setWaitForAccurateLocation(hasFine)
            .build()
        Log.d(TAG, "Requesting first GPS fix: interval=1000ms minInterval=500ms highAccuracy=true waitForAccurate=$hasFine")
        locationClient.requestLocationUpdates(firstFixRequest, callback, mainLooper)
        handler.postDelayed(firstFixTimeout, FIRST_FIX_TIMEOUT_MS)
        handler.postDelayed(noFixWarning, NO_FIX_WARNING_MS)
    }

    private fun showNoFixWarning() {
        if (!store.notificationsEnabled) return
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(WARNING_CHANNEL_ID, getString(R.string.gps_no_fix_channel), NotificationManager.IMPORTANCE_DEFAULT)
        )
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, WARNING_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.gps_no_fix_title))
            .setContentText(getString(R.string.gps_no_fix_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.gps_no_fix_text)))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        runCatching { manager.notify(NO_FIX_NOTIFICATION_ID, notification) }
    }

    private fun switchToNormalTracking() {
        if (store.activeTripId == null) return
        locationClient.removeLocationUpdates(callback)
        val intervalMs = store.trackingIntervalSeconds * 1_000L
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs)
            .setMaxUpdateDelayMillis(intervalMs * 2)
            .build()
        Log.i(TAG, "Normal GPS tracking active: trip=${store.activeTripId} interval=${store.trackingIntervalSeconds}s")
        GpsDebugMonitor.log("Normales Tracking aktiv, Intervall ${store.trackingIntervalSeconds} s")
        locationClient.requestLocationUpdates(request, callback, mainLooper)
    }

    private fun stopServiceTracking() {
        Log.i(TAG, "Tracking service stopping: trip=${store.activeTripId} recordedPoints=$pointCount")
        handler.removeCallbacks(firstFixTimeout)
        handler.removeCallbacks(noFixWarning)
        GpsDebugMonitor.stop(this)
        locationClient.removeLocationUpdates(callback)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        handler.removeCallbacks(firstFixTimeout)
        handler.removeCallbacks(noFixWarning)
        GpsDebugMonitor.stop(this)
        locationClient.removeLocationUpdates(callback)
        scope.cancel()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    private fun createNotificationChannel() = getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL_ID, "Fahrtenaufzeichnung", NotificationManager.IMPORTANCE_LOW))

    companion object {
        const val ACTION_START = "app.fahrtenbuch.mobile.START_TRACKING"
        const val ACTION_STOP = "app.fahrtenbuch.mobile.STOP_TRACKING"
        private const val CHANNEL_ID = "trip_tracking"
        private const val NOTIFICATION_ID = 1001
        private const val NO_FIX_NOTIFICATION_ID = 1003
        private const val WARNING_CHANNEL_ID = "gps_no_fix"
        private const val FIRST_FIX_TIMEOUT_MS = 60_000L
        private const val NO_FIX_WARNING_MS = 3 * 60_000L
        const val TAG = "FahrtenbuchTracking"
    }
}
