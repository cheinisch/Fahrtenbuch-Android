package app.fahrtenbuch.mobile.data.api

import android.content.Context
import app.fahrtenbuch.mobile.BuildConfig
import java.util.UUID

enum class AppMode { UNSET, LOCAL, SERVER }

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("fahrtenbuch_session", Context.MODE_PRIVATE)

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
            .remove("access_token")
            .remove("refresh_token")
            .apply()
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
        get() = prefs.getString("access_token", null)
        set(value) { prefs.edit().putString("access_token", value).apply() }

    var refreshToken: String?
        get() = prefs.getString("refresh_token", null)
        set(value) { prefs.edit().putString("refresh_token", value).apply() }

    val deviceId: String
        get() {
            val existing = prefs.getString("device_id", null)
            if (existing != null) return existing
            val generated = UUID.randomUUID().toString()
            prefs.edit().putString("device_id", generated).apply()
            return generated
        }

    val hasAuthenticatedSession: Boolean
        get() = isServerMode && serverConfigured && !refreshToken.isNullOrBlank()

    fun completeLogin(accessToken: String, refreshToken: String): Boolean =
        prefs.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .commit()

    fun completePairing(serverUrl: String, accessToken: String, refreshToken: String): Boolean =
        prefs.edit()
            .putString("base_url", normalize(serverUrl))
            .putBoolean("server_configured", true)
            .putString("app_mode", AppMode.SERVER.name)
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

    var timeFormat: String
        get() = prefs.getString("time_format", "24h") ?: "24h"
        set(value) { prefs.edit().putString("time_format", value).apply() }

    var trackingIntervalSeconds: Int
        get() = prefs.getInt("tracking_interval_seconds", 5).coerceIn(5, 60)
        set(value) { prefs.edit().putInt("tracking_interval_seconds", value.coerceIn(5, 60)).apply() }

    var activeVehicleId: String?
        get() = prefs.getString("active_vehicle_id", null)
        set(value) { prefs.edit().putString("active_vehicle_id", value).apply() }

    var activeBluetoothMac: String?
        get() = prefs.getString("active_bluetooth_mac", null)
        set(value) { prefs.edit().putString("active_bluetooth_mac", value).apply() }

    var activeTripId: String?
        get() = prefs.getString("active_trip_id", null)
        set(value) { prefs.edit().putString("active_trip_id", value).apply() }

    fun clearTokens() = prefs.edit()
        .remove("access_token")
        .remove("refresh_token")
        .remove("active_trip_id")
        .apply()

    fun clearServer() = prefs.edit()
        .remove("base_url")
        .putBoolean("server_configured", false)
        .remove("access_token")
        .remove("refresh_token")
        .remove("active_trip_id")
        .remove("allow_invalid_certificates")
        .apply()

    private fun normalize(value: String): String {
        val root = value.trim().trimEnd('/')
        return when {
            root.endsWith("/api/v1") -> "$root/"
            root.endsWith("/api") -> "$root/v1/"
            else -> "$root/api/v1/"
        }
    }
}
