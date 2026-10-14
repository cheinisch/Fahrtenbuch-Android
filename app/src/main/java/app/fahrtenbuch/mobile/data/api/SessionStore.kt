package app.fahrtenbuch.mobile.data.api

import android.content.Context
import app.fahrtenbuch.mobile.BuildConfig
import java.util.UUID

enum class AppMode { UNSET, LOCAL, SERVER }

class SessionStore(context: Context) {
    /** Einstellungen, Server-Adresse usw. – dürfen gesichert werden (Google-Backup, ZIP). */
    private val prefs = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)

    /**
     * Gerätegebundenes: Zugangs-Tokens, Geräte-ID, laufende Aufzeichnung. Wird nie gesichert –
     * Refresh-Tokens rotieren und laufen ab, und eine kopierte Geräte-ID würde zwei Geräte
     * als eines erscheinen lassen. Nach einer Wiederherstellung meldet man sich neu an.
     */
    private val device = context.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)

    init {
        // Ältere Installationen hatten alles in einer Datei: gerätegebundene Werte umziehen
        val moving = DEVICE_KEYS.filter { prefs.contains(it) }
        if (moving.isNotEmpty()) {
            val target = device.edit()
            moving.forEach { key -> prefs.all[key]?.let { target.putString(key, it.toString()) } }
            target.commit()
            prefs.edit().apply { moving.forEach(::remove) }.commit()
        }
    }

    companion object {
        const val PREFS_SETTINGS = "fahrtenbuch_session"
        const val PREFS_DEVICE = "fahrtenbuch_device"
        private val DEVICE_KEYS = listOf(
            "access_token", "refresh_token", "device_id", "pending_oauth_verifier",
            "active_vehicle_id", "active_bluetooth_mac", "active_trip_id"
        )
    }

    var appMode: AppMode
        get() {
            val saved = prefs.getString("app_mode", null)
            if (saved != null) return runCatching { AppMode.valueOf(saved) }.getOrDefault(AppMode.UNSET)
            // Existing installations before app modes were introduced are server installations.
            return if (prefs.getBoolean("server_configured", false)) AppMode.SERVER else AppMode.UNSET
        }
        set(value) { prefs.edit().putString("app_mode", value.name).apply() }

    val setupCompleted: Boolean get() = appMode != AppMode.UNSET
    val isLocalMode: Boolean get() = appMode == AppMode.LOCAL
    val isServerMode: Boolean get() = appMode == AppMode.SERVER
    var migrationPending: Boolean
        get() = prefs.getBoolean("migration_pending", false)
        set(value) { prefs.edit().putBoolean("migration_pending", value).apply() }

    fun selectLocalMode() {
        prefs.edit()
            .putString("app_mode", AppMode.LOCAL.name)
            .putBoolean("server_configured", false)
            .apply()
        device.edit().remove("access_token").remove("refresh_token").apply()
    }

    fun beginServerMigration() {
        migrationPending = true
        appMode = AppMode.UNSET
    }

    fun selectServerMode() {
        if (isLocalMode) migrationPending = true
        appMode = AppMode.SERVER
    }

    fun completeMigration() { migrationPending = false }

    var baseUrl: String
        get() = prefs.getString("base_url", BuildConfig.DEFAULT_API_URL) ?: BuildConfig.DEFAULT_API_URL
        set(value) {
            prefs.edit()
                .putString("base_url", normalize(value))
                .putBoolean("server_configured", true)
                .putString("app_mode", AppMode.SERVER.name)
                .apply()
        }

    var allowInvalidCertificates: Boolean
        get() = prefs.getBoolean("allow_invalid_certificates", false)
        set(value) { prefs.edit().putBoolean("allow_invalid_certificates", value).apply() }

    var serverConfigured: Boolean
        get() = prefs.getBoolean("server_configured", false)
        set(value) { prefs.edit().putBoolean("server_configured", value).apply() }

    var accessToken: String?
        get() = device.getString("access_token", null)
        set(value) { device.edit().putString("access_token", value).apply() }

    var refreshToken: String?
        get() = device.getString("refresh_token", null)
        set(value) { device.edit().putString("refresh_token", value).apply() }

    val deviceId: String
        get() {
            val existing = device.getString("device_id", null)
            if (existing != null) return existing
            val generated = UUID.randomUUID().toString()
            device.edit().putString("device_id", generated).apply()
            return generated
        }

    val hasAuthenticatedSession: Boolean
        get() = isServerMode && serverConfigured && !refreshToken.isNullOrBlank()

    /** PKCE-Verifier einer laufenden Google/Microsoft-Anmeldung (überlebt Prozess-Neustart im Browser). */
    var pendingOAuthVerifier: String?
        get() = device.getString("pending_oauth_verifier", null)
        set(value) { device.edit().putString("pending_oauth_verifier", value).commit() }

    fun completeLogin(accessToken: String, refreshToken: String): Boolean =
        device.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .commit()

    fun completePairing(serverUrl: String, accessToken: String, refreshToken: String): Boolean =
        prefs.edit()
            .putString("base_url", normalize(serverUrl))
            .putBoolean("server_configured", true)
            .putString("app_mode", AppMode.SERVER.name)
            .commit() &&
            device.edit()
                .putString("access_token", accessToken)
                .putString("refresh_token", refreshToken)
                .commit()

    var distanceUnit: String
        get() = prefs.getString("distance_unit", "km") ?: "km"
        set(value) { prefs.edit().putString("distance_unit", value).apply() }

    var speedUnit: String
        get() = prefs.getString("speed_unit", "kmh") ?: "kmh"
        set(value) { prefs.edit().putString("speed_unit", value).apply() }

    var dateFormat: String
        get() = prefs.getString("date_format", "EU") ?: "EU"
        set(value) { prefs.edit().putString("date_format", value).apply() }

    /** Anzeige-Zeitzone; null = Zeitzone des Geräts (nur im lokalen Modus sinnvoll). */
    var timeZone: String?
        get() = prefs.getString("time_zone", null)
        set(value) { prefs.edit().putString("time_zone", value).apply() }

    var timeFormat: String
        get() = prefs.getString("time_format", "24h") ?: "24h"
        set(value) { prefs.edit().putString("time_format", value).apply() }

    var gravatarEnabled: Boolean
        get() = prefs.getBoolean("gravatar_enabled", true)
        set(value) { prefs.edit().putBoolean("gravatar_enabled", value).apply() }

    var gravatarAvailable: Boolean
        get() = prefs.getBoolean("gravatar_available", true)
        set(value) { prefs.edit().putBoolean("gravatar_available", value).apply() }

    var trackingIntervalSeconds: Int
        get() = prefs.getInt("tracking_interval_seconds", 5).coerceIn(5, 60)
        set(value) { prefs.edit().putInt("tracking_interval_seconds", value.coerceIn(5, 60)).apply() }

    var activeVehicleId: String?
        get() = device.getString("active_vehicle_id", null)
        set(value) { device.edit().putString("active_vehicle_id", value).apply() }

    var activeBluetoothMac: String?
        get() = device.getString("active_bluetooth_mac", null)
        set(value) { device.edit().putString("active_bluetooth_mac", value).apply() }

    var activeTripId: String?
        get() = device.getString("active_trip_id", null)
        set(value) { device.edit().putString("active_trip_id", value).apply() }

    fun clearTokens() = device.edit()
        .remove("access_token")
        .remove("refresh_token")
        .remove("active_trip_id")
        .apply()

    fun clearServer() {
        prefs.edit()
            .remove("base_url")
            .putBoolean("server_configured", false)
            .remove("allow_invalid_certificates")
            .apply()
        clearTokens()
    }

    /** Zuletzt verwendeter Anmeldename – wird gesichert, damit der Login nach einer Wiederherstellung vorausgefüllt ist. */
    var lastLoginName: String?
        get() = prefs.getString("last_login_name", null)
        set(value) { prefs.edit().putString("last_login_name", value).apply() }

    /** Nutzer erlaubt die Sicherung in sein Google-Konto (Android-Backup). Standard: aus. */
    var googleBackupEnabled: Boolean
        get() = prefs.getBoolean("google_backup_enabled", false)
        set(value) { prefs.edit().putBoolean("google_backup_enabled", value).commit() }

    /** Alle sicherbaren Einstellungen (ohne Tokens/Geräte-ID) für das ZIP-Backup. */
    fun exportSettings(): Map<String, Any?> = prefs.all.toMap()

    /** Einstellungen aus einem ZIP-Backup übernehmen; gerätegebundene Schlüssel werden ignoriert. */
    fun importSettings(values: Map<String, Any?>) {
        val editor = prefs.edit().clear()
        values.filterKeys { it !in DEVICE_KEYS }.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Double -> editor.putInt(key, value.toInt())
                is String -> editor.putString(key, value)
                null -> {}
                else -> editor.putString(key, value.toString())
            }
        }
        editor.commit()
        // Neue Anmeldung nötig: alte Tokens gehören nicht zum wiederhergestellten Stand
        clearTokens()
    }

    private fun normalize(value: String): String {
        val root = value.trim().trimEnd('/')
        return when {
            root.endsWith("/api/v1") -> "$root/"
            root.endsWith("/api") -> "$root/v1/"
            else -> "$root/api/v1/"
        }
    }
}
