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
/** GET /auth/oauth/providers – Google/Microsoft, aktiv wenn auf dem Server eingerichtet. */
data class OAuthProvider(val id: String, val name: String, val enabled: Boolean)
/** GET /auth/methods – welche Anmeldeverfahren der Server anbietet. */
data class AuthMethods(
    val password: Boolean = true,
    val totp: Boolean = true,
    val passkeys: Boolean = true,
    val oauthProviders: List<String> = emptyList()
)
data class OAuthLoginRequest(val appRedirectUri: String, val appCodeChallenge: String)
data class OAuthAuthorizationResponse(val authorizationUrl: String)
data class OAuthExchangeRequest(
    val ticket: String,
    val appCodeVerifier: String,
    val totpCode: String? = null,
    val deviceId: String? = null,
    val deviceName: String? = null,
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

/** Abmelden muss der Server ausdrücklich bestätigt bekommen. */
data class DeregisterVehicleRequest(val confirmed: Boolean = true)

/** Erinnerung an den Kilometerstand je Fahrzeug (Server: /odometer/reminder-settings/{vehicleId}). */
data class OdometerReminderSettings(
    val enabled: Boolean = false,
    /** weekly | monthly | month_end – als String, damit neue Serverwerte nicht crashen */
    val frequency: String = "monthly",
    /** 1 = Montag … 7 = Sonntag, nur bei weekly */
    val weekday: Int = 1,
    /** 1–28, nur bei monthly */
    val dayOfMonth: Int = 1,
    /** Ab dieser Stunde (Ortszeit des Kontos) ist die Erinnerung fällig */
    val hour: Int = 18
)

/** Offene Erinnerung vom Server (GET /odometer/reminders). */
data class OdometerReminder(
    val id: Uuid,
    val vehicleId: Uuid,
    val vehicleName: String,
    val licensePlate: String? = null,
    val dueDate: String,
    val lastOdometerKm: Double? = null
)

data class OdometerReadingRequest(val odometerKm: Double, val readingDate: String? = null)

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
    val isDefault: Boolean = false,
    /** Abgemeldetes ("deaktiviertes") Fahrzeug: Fahrten bleiben, es wird aber nicht mehr genutzt. */
    val isDeregistered: Boolean = false,
    val deregisteredAt: String? = null,
    /** Freigegebene Fahrzeuge anderer Nutzer dürfen nicht bearbeitet oder entfernt werden. */
    val isOwner: Boolean = true
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
    /** IANA-Zeitzone, in der die Fahrt begann/endete (Server bzw. Gerät bei der Aufzeichnung). */
    val startTimeZone: String? = null,
    val endTimeZone: String? = null,
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
    /** Der Server liefert Tags als Objekte ({id, name, color}). */
    val tags: List<Tag> = emptyList(),
    /** Map-Matching-Ergebnis vom Server (null = nicht gematcht oder älterer Server). */
    val mapMatching: TripMapMatching? = null,
    val syncState: String = "SYNCED"
) {
    /** Die Fahrt wurde auf das Straßennetz gelegt und die Distanz übernommen. */
    val isMapMatched: Boolean get() = mapMatching?.status == "applied"
}

/** Zusammenfassung des Map-Matchings je Fahrt (API: Trip.mapMatching). */
data class TripMapMatching(
    /** applied | rejected | failed – als String, damit neue Serverwerte nicht crashen. */
    val status: String,
    val provider: String? = null,
    val matchedAt: String? = null,
    val rawDistanceMeters: Long? = null,
    val matchedDistanceMeters: Long? = null,
    val message: String? = null
)

/** Auf die Straße gelegte Route (GET /trips/{id}/matched-route), Koordinaten als [lat, lon]. */
data class TripMatchedRoute(
    val status: String,
    val provider: String? = null,
    /** Stand des Matchings; ändert er sich, wird die gespeicherte Route ersetzt. */
    val matchedAt: String? = null,
    val coordinates: List<List<Double>> = emptyList()
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

data class StartTrackingRequest(
    val vehicleId: Uuid,
    val startedAt: String? = null,
    val timeZone: String? = null,
    /** Beim Start auf dem Gerät erzeugte Fahrt-ID (localId): macht den Start wiederholbar. */
    val clientTripId: Uuid? = null
)
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
data class StopTrackingRequest(val tripId: Uuid, val endedAt: String? = null, val timeZone: String? = null)

data class Tag(val id: Uuid, val name: String, val color: String? = null)
data class TagRequest(val name: String, val color: String? = null)
data class TripTagsRequest(val tagIds: List<Uuid>)

/** Tags als JSON für Room; liest auch das alte Format (nur Namen). */
fun List<Tag>.toTagsJson(): String = org.json.JSONArray(
    map { org.json.JSONObject().put("id", it.id).put("name", it.name).put("color", it.color ?: org.json.JSONObject.NULL) }
).toString()

fun parseTagsJson(json: String?): List<Tag> = runCatching {
    val array = org.json.JSONArray(json ?: "[]")
    List(array.length()) { index ->
        when (val value = array.get(index)) {
            is org.json.JSONObject -> Tag(
                id = value.optString("id").ifBlank { value.optString("name") },
                name = value.optString("name"),
                color = value.optString("color").takeIf { !value.isNull("color") && it.isNotBlank() }
            )
            else -> Tag(id = value.toString(), name = value.toString())
        }
    }.filter { it.name.isNotBlank() }
}.getOrDefault(emptyList())
data class Dashboard(val todayKm: Double, val monthKm: Double, val businessKm: Double, val privateKm: Double, val commuteKm: Double, val tripCount: Int)
data class SyncResponse(val vehicles: List<Vehicle>, val trips: List<Trip>, val tags: List<Tag>, val deleted: List<Uuid>)
data class SessionInfo(val id: Uuid, val deviceName: String? = null, val createdAt: String? = null, val lastUsedAt: String? = null)
data class TotpStatus(val enabled: Boolean, val secret: String? = null, val qrCode: String? = null)
data class PasskeyInfo(
    val id: Uuid,
    val name: String? = null,
    val createdAt: String? = null,
    val lastUsedAt: String? = null,
    /** In Google Passwortmanager/iCloud o. Ä. synchronisiert (Mehrgeräte-Passkey). */
    val backedUp: Boolean? = null,
    val deviceType: String? = null,
    val transports: List<String>? = null
)
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
    val timeFormat: TimeFormatPreference = TimeFormatPreference.H24,
    /**
     * Zeitzone für die Anzeige von Uhrzeiten und Tagen (IANA-ID, z. B. Europe/Berlin).
     * Server-Modus: Zeitzone des Benutzerkontos; lokal: gewählt oder null = Gerätezeitzone.
     * So stimmen die Ortszeiten unabhängig von der Zeitzone des Geräts.
     */
    val timeZone: String? = null,
    /** Profilbild von Gravatar laden (persönliche Einstellung, Standard an). */
    val gravatarEnabled: Boolean = true,
    /** False, wenn der Server Gravatar abgeschaltet hat (GRAVATAR_ENABLED=false). */
    val gravatarAvailable: Boolean = true,
    /** „Map-Matching verwenden“ im Benutzerprofil (API: mapMatchingEnabled). */
    val mapMatchingEnabled: Boolean = true,
    /** False, wenn auf dem Server kein Map-Matching eingerichtet ist (API: mapMatchingAvailable). */
    val mapMatchingAvailable: Boolean = true,
    /** Max. Pause in Sekunden, bis zu der der Server Aufzeichnungen verschiedener Geräte zu einer Fahrt zusammenführt (API: tripMergeGapSeconds, 0–900). */
    val tripMergeGapSeconds: Int = 120
) {
    /** Gravatar tatsächlich verwenden: Nutzer und Server erlauben es. */
    val useGravatar: Boolean get() = gravatarEnabled && gravatarAvailable

    /**
     * Straßenroute und Map-Matching-Hinweis nur zeigen, wenn die App-Funktion an ist und
     * Server und Profil es erlauben. Vorerst aus: die App zeigt immer die GPS-Route.
     */
    val showMapMatching: Boolean get() = AppFeatures.SHOW_MAP_MATCHING && mapMatchingEnabled && mapMatchingAvailable

    /** Gültige Zeitzone; ungültige oder fehlende IDs fallen auf das Gerät zurück. */
    fun zoneId(): java.time.ZoneId =
        timeZone?.let { runCatching { java.time.ZoneId.of(it) }.getOrNull() } ?: java.time.ZoneId.systemDefault()

    companion object {
        fun fromApi(
            distance: String?,
            speed: String?,
            date: String?,
            time: String?,
            zone: String? = null,
            gravatarEnabled: Boolean = true,
            gravatarAvailable: Boolean = true,
            mapMatchingEnabled: Boolean = true,
            mapMatchingAvailable: Boolean = true,
            tripMergeGapSeconds: Int = 120
        ) = UserPreferences(
            distanceUnit = DistanceUnit.entries.firstOrNull { it.apiValue == distance } ?: DistanceUnit.KM,
            speedUnit = SpeedUnit.entries.firstOrNull { it.apiValue == speed } ?: SpeedUnit.KMH,
            dateFormat = DateFormatPreference.entries.firstOrNull { it.apiValue == date } ?: DateFormatPreference.EU,
            timeFormat = TimeFormatPreference.entries.firstOrNull { it.apiValue == time } ?: TimeFormatPreference.H24,
            timeZone = zone?.takeIf { it.isNotBlank() },
            gravatarEnabled = gravatarEnabled,
            gravatarAvailable = gravatarAvailable,
            mapMatchingEnabled = mapMatchingEnabled,
            mapMatchingAvailable = mapMatchingAvailable,
            tripMergeGapSeconds = tripMergeGapSeconds
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
    val timeFormat: String? = null,
    val gravatarEnabled: Boolean? = null,
    val gravatarAvailable: Boolean? = null,
    /** Fehlt bei älteren Servern – dann bleibt der zuletzt bekannte Wert (Standard an). */
    val mapMatchingEnabled: Boolean? = null,
    /** Vorabversion des Feldes (nur noch lesend als Rückfall). */
    val autoMapMatchingEnabled: Boolean? = null,
    val mapMatchingAvailable: Boolean? = null,
    /** Fehlt bei älteren Servern – dann bleibt der zuletzt bekannte Wert (Standard 120 s). */
    val tripMergeGapSeconds: Int? = null
)

/** GET /health – Systemstatus und Version des Servers. */
data class HealthResponse(
    val status: String? = null,
    val version: String? = null,
    val database: String? = null
)

// ---------------------------------------------------------------------
// Export
// ---------------------------------------------------------------------

enum class ExportFormat(val mimeType: String, val extension: String) {
    PDF("application/pdf", "pdf"),
    CSV("text/csv", "csv")
}

/** Was exportiert werden soll; Datumsangaben als ISO-Datum (yyyy-MM-dd), Zeitraum inklusive. */
data class ExportRequest(
    val format: ExportFormat,
    val from: String,
    val to: String,
    val vehicleId: Uuid? = null,
    val tripType: TripType? = null,
    val specialCategory: SpecialCategory? = null
)

data class ExportResult(
    val bytes: ByteArray,
    val fileName: String,
    val mimeType: String,
    /** true, wenn die Datei auf dem Gerät erzeugt wurde (lokaler Modus oder Server nicht erreichbar). */
    val generatedOnDevice: Boolean
)
