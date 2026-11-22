package app.fahrtenbuch.mobile.location

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.fahrtenbuch.mobile.R
import kotlin.math.roundToInt

/**
 * Debug-Modus während einer Fahrt:
 * - zweite Benachrichtigung mit Provider, GPS-Status, Fix und Satelliten
 * - Logdatei pro Fahrt ([GpsDebugLog]) mit Positionen, Fix-Wechseln und Satelliten-Snapshots
 *
 * Alle Zugriffe laufen auf dem Main-Looper; der Service ruft [onLocation] aus seinem Callback auf.
 */
object GpsDebugMonitor {
    private const val CHANNEL_ID = "gps_debug"
    private const val NOTIFICATION_ID = 1002
    private const val TICK_MS = 2_000L
    private const val SNAPSHOT_EVERY_TICKS = 5 // alle 10 s ein Status-Snapshot im Log
    private const val FIX_MAX_AGE_MS = 10_000L
    private const val TAG = "FahrtenbuchGpsDebug"

    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var locationManager: LocationManager? = null
    private var active = false
    private var gnssRegistered = false

    private var lastFixAtMs: Long? = null
    private var lastFixProvider: String? = null
    private var lastFixAccuracy: Float? = null
    private var gnssReceived = false
    private var satellitesInView = 0
    private var satellitesInFix = 0
    private var tickCount = 0

    private var prevHasFix: Boolean? = null
    private var prevGpsEnabled: Boolean? = null
    private var startedAtMs = 0L
    private var firstFixAfterMs: Long? = null
    private var fixCount = 0
    private var fixLostCount = 0

    /** Fahrt, für die der Bluetooth-Receiver die Diagnose schon geschrieben hat. */
    @Volatile
    private var diagnosticsTripId: String? = null

    fun diagnosticsWritten(tripId: String) {
        diagnosticsTripId = tripId
    }

    /** Konstellation -> (im Fix, sichtbar) */
    private var perConstellation: Map<String, Pair<Int, Int>> = emptyMap()

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            val counts = linkedMapOf<String, Pair<Int, Int>>()
            var used = 0
            for (i in 0 until status.satelliteCount) {
                val name = constellationName(status.getConstellationType(i))
                val usedHere = status.usedInFix(i)
                if (usedHere) used++
                val (usedSoFar, visibleSoFar) = counts[name] ?: (0 to 0)
                counts[name] = (usedSoFar + if (usedHere) 1 else 0) to (visibleSoFar + 1)
            }
            if (!gnssReceived) log("GNSS-Status erstmals empfangen")
            perConstellation = counts
            satellitesInView = status.satelliteCount
            satellitesInFix = used
            gnssReceived = true
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!active) return
            publish()
            handler.postDelayed(this, TICK_MS)
        }
    }

    fun start(context: Context, tripId: String) {
        if (active) return
        val app = context.applicationContext
        appContext = app
        active = true
        resetState()
        createChannel(app)
        startedAtMs = System.currentTimeMillis()
        GpsDebugLog.open(app, tripId)
        log("Monitor gestartet, Fahrt=$tripId")
        // Manueller Start (ohne Bluetooth): Diagnose hier schreiben
        if (diagnosticsTripId != tripId) {
            GpsDebugLog.logBlock(GpsDebugDiagnostics.snapshot(app))
            diagnosticsTripId = tripId
        }

        val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager = lm
        if (ActivityCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                lm.registerGnssStatusCallback(gnssCallback, handler)
                gnssRegistered = true
                log("GNSS-Callback registriert")
            } catch (e: SecurityException) {
                Log.w(TAG, "GNSS status callback not registered: ${e.message}")
                log("GNSS-Callback fehlgeschlagen: ${e.message}")
            }
        } else {
            log("Keine genaue Standortberechtigung (FINE fehlt), kein GNSS-Callback")
        }
        handler.post(tick)
    }

    /** Schreibt eine Zeile in die Logdatei, sofern der Monitor läuft. */
    fun log(message: String) {
        if (!active) return
        GpsDebugLog.log(message)
    }

    /** Wird bei jeder Positionsmeldung des Services aufgerufen, auch vor dem Speichern. */
    fun onLocation(location: Location) {
        if (!active) return
        val now = System.currentTimeMillis()
        lastFixAtMs = now
        lastFixProvider = location.provider
        lastFixAccuracy = if (location.hasAccuracy()) location.accuracy else null
        fixCount++
        if (firstFixAfterMs == null) {
            firstFixAfterMs = now - startedAtMs
            log(
                "ERSTER FIX nach ${firstFixAfterMs!! / 1000} s: provider=${location.provider} " +
                    "acc=${lastFixAccuracy ?: "-"} sats=$satellitesInFix/$satellitesInView"
            )
        }
        log(
            "FIX #$fixCount provider=${location.provider} lat=${location.latitude} lon=${location.longitude} " +
                "acc=${if (location.hasAccuracy()) location.accuracy else "-"} " +
                "speed=${if (location.hasSpeed()) location.speed else "-"} " +
                "sats=$satellitesInFix/$satellitesInView time=${location.time}"
        )
    }

    fun stop(context: Context) {
        if (!active) return
        val durationS = (System.currentTimeMillis() - startedAtMs) / 1000
        log(
            "ZUSAMMENFASSUNG Dauer=${durationS} s Fixes=$fixCount " +
                "ersterFix=${firstFixAfterMs?.let { "${it / 1000} s" } ?: "KEINER"} fixVerloren=$fixLostCount"
        )
        log("Monitor gestoppt")
        diagnosticsTripId = null
        active = false
        handler.removeCallbacks(tick)
        if (gnssRegistered) {
            runCatching { locationManager?.unregisterGnssStatusCallback(gnssCallback) }
            gnssRegistered = false
        }
        GpsDebugLog.close()
        NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
        resetState()
    }

    private fun publish() {
        val ctx = appContext ?: return
        val now = System.currentTimeMillis()
        val gpsEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
        val fixAgeMs = lastFixAtMs?.let { now - it }
        val hasFix = fixAgeMs != null && fixAgeMs <= FIX_MAX_AGE_MS

        if (hasFix != prevHasFix) {
            if (prevHasFix != null) {
                if (!hasFix) fixLostCount++
                log(if (hasFix) "FIX gewonnen" else "FIX verloren (letzte Position vor ${fixAgeMs?.div(1000)} s)")
            }
            prevHasFix = hasFix
        }
        if (gpsEnabled != prevGpsEnabled) {
            if (prevGpsEnabled != null) log("GPS aktiviert=$gpsEnabled")
            prevGpsEnabled = gpsEnabled
        }
        if (tickCount++ % SNAPSHOT_EVERY_TICKS == 0) {
            val consts = perConstellation.entries.joinToString(" ") { "${it.key}=${it.value.first}/${it.value.second}" }
            log(
                "STATUS gps=$gpsEnabled fix=$hasFix fixAgeS=${fixAgeMs?.div(1000)} " +
                    "sats=$satellitesInView/$satellitesInFix $consts"
            )
        }

        val lines = mutableListOf<String>()
        lines += ctx.getString(R.string.gps_debug_provider, lastFixProvider ?: "–")
        lines += ctx.getString(if (gpsEnabled) R.string.gps_debug_gps_on else R.string.gps_debug_gps_off)
        lines += when {
            hasFix -> ctx.getString(
                R.string.gps_debug_fix_yes,
                (fixAgeMs!! / 1000).toInt(),
                lastFixAccuracy?.let { ctx.getString(R.string.gps_debug_accuracy, it.roundToInt()) }
                    ?: ctx.getString(R.string.gps_debug_accuracy_unknown)
            )
            fixAgeMs != null -> ctx.getString(R.string.gps_debug_fix_stale, (fixAgeMs / 1000).toInt())
            else -> ctx.getString(R.string.gps_debug_fix_none)
        }
        lines += when {
            !gnssRegistered -> ctx.getString(R.string.gps_debug_no_permission)
            !gnssReceived -> ctx.getString(R.string.gps_debug_no_status)
            else -> ctx.getString(R.string.gps_debug_sats, satellitesInView, satellitesInFix) + "\n" +
                perConstellation.entries.joinToString(" · ") { (name, counts) ->
                    ctx.getString(R.string.gps_debug_const, name, counts.first, counts.second)
                }
        }

        val manager = NotificationManagerCompat.from(ctx)
        if (!manager.areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(ctx.getString(R.string.gps_debug_title))
            .setContentText(lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private fun resetState() {
        lastFixAtMs = null
        lastFixProvider = null
        lastFixAccuracy = null
        gnssReceived = false
        satellitesInView = 0
        satellitesInFix = 0
        tickCount = 0
        prevHasFix = null
        prevGpsEnabled = null
        firstFixAfterMs = null
        fixCount = 0
        fixLostCount = 0
        perConstellation = emptyMap()
    }

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.gps_debug_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun constellationName(type: Int): String = when (type) {
        GnssStatus.CONSTELLATION_GPS -> "GPS"
        GnssStatus.CONSTELLATION_GALILEO -> "Galileo"
        GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
        GnssStatus.CONSTELLATION_BEIDOU -> "BeiDou"
        GnssStatus.CONSTELLATION_QZSS -> "QZSS"
        GnssStatus.CONSTELLATION_SBAS -> "SBAS"
        GnssStatus.CONSTELLATION_IRNSS -> "IRNSS"
        else -> "Other"
    }
}
