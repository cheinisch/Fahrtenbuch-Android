package app.fahrtenbuch.mobile.data.api

import android.content.Context
import app.fahrtenbuch.mobile.BuildConfig
import java.util.UUID

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("fahrtenbuch_session", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString("base_url", BuildConfig.DEFAULT_API_URL) ?: BuildConfig.DEFAULT_API_URL
        set(value) {
            prefs.edit()
                .putString("base_url", normalize(value))
                .putBoolean("server_configured", true)
                .apply()
        }

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
        get() = serverConfigured && !refreshToken.isNullOrBlank()

    fun completeLogin(accessToken: String, refreshToken: String): Boolean =
        prefs.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .commit()

    fun completePairing(serverUrl: String, accessToken: String, refreshToken: String): Boolean =
        prefs.edit()
            .putString("base_url", normalize(serverUrl))
            .putBoolean("server_configured", true)
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .commit()

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
        .apply()

    private fun normalize(value: String): String {
        val trimmed = value.trim()
        val secureValue = if (trimmed.startsWith("http://", ignoreCase = true)) {
            "https://" + trimmed.substring(7)
        } else {
            trimmed
        }
        val root = secureValue.trimEnd('/')
        return when {
            root.endsWith("/api/v1") -> "$root/"
            root.endsWith("/api") -> "$root/v1/"
            else -> "$root/api/v1/"
        }
    }
}
