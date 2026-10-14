package app.fahrtenbuch.mobile.data.model

import com.squareup.moshi.Json

typealias Uuid = String

data class User(
    val id: Uuid,
    val displayName: String? = null,
    val role: String,
    val email: String? = null,
    val loginName: String? = null,
    val username: String? = null,
    val status: String? = null,
    val locale: String? = null,
    val timezone: String? = null,
    val themeMode: String? = null,
    val totpEnabled: Boolean = false,
    val lastLoginAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)
data class LoginRequest(
    val email: String,
    val password: String,
    val totpCode: String? = null,
    val deviceName: String,
    val deviceId: String? = null,
    val platform: String = "android",
    val appVersion: String? = null
)
data class LoginResponse(val accessToken: String, val refreshToken: String, val expiresIn: Long, val user: User)
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val tokenType: String? = "Bearer"
)
data class RefreshRequest(val refreshToken: String)
data class RefreshResponse(val accessToken: String, val refreshToken: String? = null, val expiresIn: Long)
data class LogoutRequest(val refreshToken: String? = null)
data class ForgotPasswordRequest(val email: String)
data class ResetPasswordRequest(val token: String, val password: String)

/**
 * QR payload expected by the Android app. The server URL is the public root URL;
 * SessionStore appends /api/v1 automatically.
 */
data class PairQrPayload(
    val version: Int = 1,
    val type: String = "pair",
    val server: String,
    val pairId: Uuid,
    val pairToken: String,
    val username: String? = null,
    val email: String? = null,
    val expiresAt: String? = null
)

data class PairOptionsRequest(
    val deviceName: String,
    val deviceId: String,
    val platform: String = "android",
    val appVersion: String
)

data class PairOptionsResponse(
    val pairId: Uuid,
    val pairToken: String? = null,
    val expiresAt: String? = null
)

data class PairLoginRequest(
    val pairId: Uuid,
    val pairToken: String,
    val deviceName: String,
    val deviceId: String,
    val platform: String = "android",
    val appVersion: String
)

data class PairStatusResponse(
    val pairId: Uuid,
    val status: String,
    val expiresAt: String? = null
)

data class CreateVehicleRequest(val name: String, val licensePlate: String? = null)
data class UpdateVehicleRequest(val name: String? = null, val licensePlate: String? = null)
data class BluetoothMacRequest(val bluetoothMac: String?)

/** Zweite Klassifizierungsebene des Servers (trips.special_category). */
enum class SpecialCategory(val apiValue: String) {
    MEDICAL("medical"),
    VOLUNTEER("volunteer"),
    EDUCATION("education"),
    APPLICATION("application"),
    MOVING("moving"),
    OTHER("other");

    companion object {
        fun fromApi(value: String?): SpecialCategory? = entries.firstOrNull { it.apiValue == value }
    }
}

enum class TripType(val apiValue: String, val label: String) {
    @Json(name = "business") BUSINESS("business", "Dienstlich"),
    @Json(name = "private") PRIVATE("private", "Privat"),
    @Json(name = "commute") COMMUTE("commute", "Arbeitsweg"),
    @Json(name = "unclassified") UNCLASSIFIED("unclassified", "Nicht zugeordnet")
}

data class Vehicle(
    val id: Uuid,
    val name: String,
    val licensePlate: String? = null,
    val bluetoothMac: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    /** Standardfahrzeug des Benutzers (Server: is_default, setzen per PUT /vehicles/{id}/default) */
    val isDefault: Boolean = false
)

data class Trip(
    val id: Uuid,
    val vehicleId: Uuid,
    @Json(name = "type") val tripType: TripType,
    /** Optionale Sonderkategorie (API: specialCategory), unabhängig vom Fahrttyp. Als String, damit neue Serverwerte nicht crashen. */
    val specialCategory: String? = null,
    val status: String = "completed",
    val startedAt: String,
    val endedAt: String? = null,
    val startAddress: String? = null,
    val endAddress: String? = null,
    val purpose: String? = null,
    val contact: String? = null,
    @Json(name = "notes") val note: String? = null,
    val distanceKm: Double? = null,
    val durationSeconds: Int? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val vehicleName: String? = null,
    val tags: List<String> = emptyList(),
    val syncState: String = "SYNCED"
)

data class TripInput(
    val vehicleId: Uuid,
    @Json(name = "type") val tripType: String = "unclassified",
    val startedAt: String,
    val endedAt: String? = null,
    val startAddress: String? = null,
    val endAddress: String? = null,
    val purpose: String? = null,
    val contact: String? = null,
    val notes: String? = null
)

data class StartTrackingRequest(val vehicleId: Uuid, val startedAt: String? = null)
data class TrackPoint(
    val id: Uuid? = null,
    val tripId: Uuid? = null,
    val lat: Double,
    val lon: Double,
    val altitude: Double? = null,
    val accuracy: Double? = null,
    val speed: Double? = null,
    val bearing: Double? = null,
    val recordedAt: String
)
data class TrackingPointsRequest(val tripId: Uuid, val points: List<TrackPoint>)
data class StopTrackingRequest(val tripId: Uuid, val endedAt: String? = null)

data class Tag(val id: Uuid, val name: String)
data class TagRequest(val name: String)
data class Dashboard(val todayKm: Double, val monthKm: Double, val businessKm: Double, val privateKm: Double, val commuteKm: Double, val tripCount: Int)
data class SyncResponse(val vehicles: List<Vehicle>, val trips: List<Trip>, val tags: List<Tag>, val deleted: List<Uuid>)
data class SessionInfo(val id: Uuid, val deviceName: String? = null, val createdAt: String? = null, val lastUsedAt: String? = null)
data class TotpStatus(val enabled: Boolean, val secret: String? = null, val qrCode: String? = null)
data class PasskeyInfo(val id: Uuid, val name: String? = null, val createdAt: String? = null)
data class DeviceInfo(
    val id: Uuid,
    val name: String? = null,
    val platform: String? = null,
    val appVersion: String? = null,
    val createdAt: String? = null,
    val lastUsedAt: String? = null,
    val current: Boolean = false
)
data class ApiMessage(val message: String? = null)
data class ApiError(
    val code: String? = null,
    val message: String? = null,
    val errors: Map<String, List<String>>? = null
)

// ---------------------------------------------------------------------
// Anzeige-Einstellungen (Server: GET/PATCH /users/me/settings)
// ---------------------------------------------------------------------

enum class DistanceUnit(val apiValue: String) { KM("km"), MI("mi") }
enum class SpeedUnit(val apiValue: String) { KMH("kmh"), MPH("mph") }
/** EU: 13.10.2026 · US: 10/13/2026 */
enum class DateFormatPreference(val apiValue: String) { EU("EU"), US("US") }
/** 24h: 16:12 · 12h: 4:12 PM */
enum class TimeFormatPreference(val apiValue: String) { H24("24h"), H12("12h") }

data class UserPreferences(
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    val dateFormat: DateFormatPreference = DateFormatPreference.EU,
    val timeFormat: TimeFormatPreference = TimeFormatPreference.H24
) {
    companion object {
        fun fromApi(distance: String?, speed: String?, date: String?, time: String?) = UserPreferences(
            distanceUnit = DistanceUnit.entries.firstOrNull { it.apiValue == distance } ?: DistanceUnit.KM,
            speedUnit = SpeedUnit.entries.firstOrNull { it.apiValue == speed } ?: SpeedUnit.KMH,
            dateFormat = DateFormatPreference.entries.firstOrNull { it.apiValue == date } ?: DateFormatPreference.EU,
            timeFormat = TimeFormatPreference.entries.firstOrNull { it.apiValue == time } ?: TimeFormatPreference.H24
        )
    }
}

/** Antwort von GET /users/me/settings (weitere Felder werden ignoriert). */
data class MeSettingsResponse(val settings: UserSettingsDto? = null)

/** Persönliche Einstellungen; PATCH /users/me/settings liefert dieses Objekt direkt zurück. */
data class UserSettingsDto(
    val distanceUnit: String? = null,
    val speedUnit: String? = null,
    val dateFormat: String? = null,
    val timeFormat: String? = null
)

/** GET /health – Systemstatus und Version des Servers. */
data class HealthResponse(
    val status: String? = null,
    val version: String? = null,
    val database: String? = null
)
