package app.fahrtenbuch.mobile.location

import android.Manifest
import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.fahrtenbuch.mobile.BuildConfig
import app.fahrtenbuch.mobile.data.api.SessionStore
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/**
 * Momentaufnahme aller Geräte- und App-Einstellungen, die das GPS-Tracking beeinflussen.
 * Wird beim Fahrtstart (Bluetooth verbunden bzw. Tracking gestartet) ins Debug-Log geschrieben.
 * Jede Abfrage ist einzeln abgesichert, damit ein Fehler nicht den ganzen Block verhindert.
 */
object GpsDebugDiagnostics {

    fun snapshot(context: Context): List<String> {
        val ctx = context.applicationContext
        val lines = mutableListOf<String>()
        fun add(label: String, value: () -> Any?) {
            lines += "  $label: ${runCatching { value() ?: "-" }.getOrElse { "Fehler (${it.javaClass.simpleName})" }}"
        }
        fun granted(permission: String) =
            ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED

        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val store = SessionStore(ctx)

        lines += "== Gerät / App"
        add("App-Version") { "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})" }
        add("Android") { "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})" }
        add("Gerät") { "${Build.MANUFACTURER} ${Build.MODEL}" }
        add("Play-Dienste") {
            when (val code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ctx)) {
                ConnectionResult.SUCCESS -> "verfügbar"
                else -> "NICHT verfügbar (Code $code)"
            }
        }

        lines += "== Berechtigungen"
        add("Standort genau (FINE)") { granted(Manifest.permission.ACCESS_FINE_LOCATION) }
        add("Standort ungefähr (COARSE)") { granted(Manifest.permission.ACCESS_COARSE_LOCATION) }
        add("Standort im Hintergrund") { granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
        add("Bluetooth verbinden") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) granted(Manifest.permission.BLUETOOTH_CONNECT) else "n/a"
        }
        add("Benachrichtigungen") { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }

        lines += "== Standort (System)"
        add("Standort aktiviert") { lm.isLocationEnabled }
        add("GPS-Provider aktiv") { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }
        add("Netzwerk-Provider aktiv") { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }
        add("Fused-Provider aktiv") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) lm.isProviderEnabled(LocationManager.FUSED_PROVIDER) else "n/a"
        }
        add("Provider") { lm.getProviders(false).joinToString() }
        add("GNSS-Chip") { lm.gnssHardwareModelName ?: "unbekannt" }
        add("GNSS-Jahrgang") { lm.gnssYearOfHardware }
        add("Letzte GPS-Position") {
            if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) "keine Berechtigung"
            else @Suppress("MissingPermission") lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let {
                "vor ${(System.currentTimeMillis() - it.time) / 1000} s, ±${it.accuracy} m"
            } ?: "keine"
        }
        add("Flugmodus") { Settings.Global.getInt(ctx.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1 }

        lines += "== Energie"
        add("Energiesparmodus") { pm.isPowerSaveMode }
        add("Standort im Energiesparmodus") { locationPowerSaveMode(pm.locationPowerSaveMode) }
        add("Akku-Optimierung ignoriert") { pm.isIgnoringBatteryOptimizations(ctx.packageName) }
        add("Doze aktiv") { pm.isDeviceIdleMode }
        add("Bildschirm an") { pm.isInteractive }
        add("Hintergrund eingeschränkt") { am.isBackgroundRestricted }
        add("Standby-Bucket") {
            standbyBucket((ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager).appStandbyBucket)
        }
        add("Akku") {
            val battery = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val plugged = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
            "${level * 100 / scale} %, ${if (plugged) "lädt" else "nicht am Strom"}"
        }

        lines += "== App-Einstellungen"
        add("Modus") { store.appMode }
        add("GPS-Intervall") { "${store.trackingIntervalSeconds} s" }
        add("Pause zum Zusammenführen") { "${store.tripMergeGapSeconds} s" }
        return lines
    }

    private fun locationPowerSaveMode(mode: Int) = when (mode) {
        PowerManager.LOCATION_MODE_NO_CHANGE -> "keine Änderung"
        PowerManager.LOCATION_MODE_GPS_DISABLED_WHEN_SCREEN_OFF -> "GPS AUS bei Bildschirm aus"
        PowerManager.LOCATION_MODE_ALL_DISABLED_WHEN_SCREEN_OFF -> "Standort AUS bei Bildschirm aus"
        PowerManager.LOCATION_MODE_FOREGROUND_ONLY -> "nur im Vordergrund"
        PowerManager.LOCATION_MODE_THROTTLE_REQUESTS_WHEN_SCREEN_OFF -> "gedrosselt bei Bildschirm aus"
        else -> "unbekannt ($mode)"
    }

    private fun standbyBucket(bucket: Int) = when (bucket) {
        UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "aktiv"
        UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "Working Set"
        UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "häufig"
        UsageStatsManager.STANDBY_BUCKET_RARE -> "selten"
        UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> "eingeschränkt"
        else -> "unbekannt ($bucket)"
    }
}
