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
        val root = value.trim().trimEnd('/')
        return when {
            root.endsWith("/api/v1") -> "$root/"
            root.endsWith("/api") -> "$root/v1/"
            else -> "$root/api/v1/"
        }
    }
}
