package app.fahrtenbuch.mobile.location

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
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

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val localTripId = store.activeTripId ?: return
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
                if (waitingForFirstFix) {
                    waitingForFirstFix = false
                    Log.i(TAG, "First GPS fix received: trip=$localTripId; switching to normal tracking")
                    withContext(Dispatchers.Main) { switchToNormalTracking() }
                }
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

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) { stopSelf(); return }
        waitingForFirstFix = true
        val firstFixRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
            .setMinUpdateIntervalMillis(500L)
            .setWaitForAccurateLocation(true)
            .build()
        Log.d(TAG, "Requesting first GPS fix: interval=1000ms minInterval=500ms highAccuracy=true")
        locationClient.requestLocationUpdates(firstFixRequest, callback, mainLooper)
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
        locationClient.requestLocationUpdates(request, callback, mainLooper)
    }

    private fun stopServiceTracking() {
        Log.i(TAG, "Tracking service stopping: trip=${store.activeTripId} recordedPoints=$pointCount")
        locationClient.removeLocationUpdates(callback)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() { locationClient.removeLocationUpdates(callback); scope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
    private fun createNotificationChannel() = getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL_ID, "Fahrtenaufzeichnung", NotificationManager.IMPORTANCE_LOW))

    companion object {
        const val ACTION_START = "app.fahrtenbuch.mobile.START_TRACKING"
        const val ACTION_STOP = "app.fahrtenbuch.mobile.STOP_TRACKING"
        private const val CHANNEL_ID = "trip_tracking"
        private const val NOTIFICATION_ID = 1001
        const val TAG = "FahrtenbuchTracking"
    }
}
