package app.fahrtenbuch.mobile.data.repository

import android.content.Context
import app.fahrtenbuch.mobile.BuildConfig
import app.fahrtenbuch.mobile.data.api.FahrtenbuchApi
import app.fahrtenbuch.mobile.data.api.SessionStore
import app.fahrtenbuch.mobile.data.api.OAUTH_REDIRECT_URI
import app.fahrtenbuch.mobile.data.api.OAuthMfaRequired
import app.fahrtenbuch.mobile.data.api.Pkce
import app.fahrtenbuch.mobile.data.local.CacheEntity
import app.fahrtenbuch.mobile.data.local.OfflineDatabase
import app.fahrtenbuch.mobile.data.local.OfflineTripEntity
import app.fahrtenbuch.mobile.data.local.VehicleEntity
import app.fahrtenbuch.mobile.data.local.TripEntity
import app.fahrtenbuch.mobile.data.local.TagEntity
import app.fahrtenbuch.mobile.data.local.TrackPointEntity
import app.fahrtenbuch.mobile.location.ReverseGeocoder
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import app.fahrtenbuch.mobile.data.model.*
import app.fahrtenbuch.mobile.sync.SyncScheduler
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.time.Instant
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID

/** Verhindert parallele Ortsauflösung, auch über mehrere Repository-Instanzen. */
private val detailsMutex = Mutex()

class FahrtenbuchRepository(
    private val context: Context,
    private val api: FahrtenbuchApi,
    private val store: SessionStore
) {
    private val dao = OfflineDatabase.get(context).dao()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val vehicleAdapter = moshi.adapter<List<Vehicle>>(Types.newParameterizedType(List::class.java, Vehicle::class.java))
    private val tripAdapter = moshi.adapter<List<Trip>>(Types.newParameterizedType(List::class.java, Trip::class.java))

    suspend fun login(email: String, password: String, totpCode: String?, deviceName: String): User {
        val response = api.login(
            LoginRequest(
                email = email,
                password = password,
                totpCode = totpCode?.takeIf { it.isNotBlank() },
                deviceName = deviceName,
                deviceId = store.deviceId,
                appVersion = BuildConfig.VERSION_NAME
            )
        )
        check(store.completeLogin(response.accessToken, response.refreshToken)) {
            "Die Anmeldung konnte nicht lokal gespeichert werden."
        }
        migrateStandaloneDataIfNeeded()
        SyncScheduler.enqueue(context)
        return response.user
    }

    suspend fun pairLogin(payload: PairQrPayload, deviceName: String) {
        val response = api.pair(
            PairLoginRequest(
                pairId = payload.pairId,
                pairToken = payload.pairToken,
                deviceName = deviceName,
                deviceId = store.deviceId,
                appVersion = BuildConfig.VERSION_NAME
            )
        )

        // Persist server and both tokens in one synchronous transaction. Once
        // /auth/pair returned successfully the paired session must survive an
        // Activity/process recreation before the next screen is rendered.
        check(
            store.completePairing(
                serverUrl = payload.server,
                accessToken = response.accessToken,
                refreshToken = response.refreshToken
            )
        ) { "Die Pairing-Sitzung konnte nicht lokal gespeichert werden." }
        migrateStandaloneDataIfNeeded()
        SyncScheduler.enqueue(context)
    }

    /** Auf dem Server eingerichtete externe Anmeldungen; ältere Server ohne Endpunkt: leer. */
    suspend fun oauthProviders(): List<OAuthProvider> =
        runCatching { api.oauthProviders() }.getOrDefault(emptyList()).filter { it.enabled }

    /**
     * Startet die Anmeldung bei Google/Microsoft: PKCE-Verifier erzeugen und merken, Challenge
     * an den Server, Autorisierungs-URL zurück (wird im Browser/Custom Tab geöffnet).
     */
    suspend fun startOAuthLogin(provider: String): String {
        val verifier = Pkce.newVerifier()
        store.pendingOAuthVerifier = verifier
        return api.oauthLogin(provider, OAuthLoginRequest(OAUTH_REDIRECT_URI, Pkce.challenge(verifier))).authorizationUrl
    }

    /** Ticket aus dem Callback gegen Tokens tauschen. Wirft [OAuthMfaRequired], wenn TOTP aktiv ist. */
    suspend fun completeOAuthLogin(ticket: String, totpCode: String?, deviceName: String): User {
        val verifier = store.pendingOAuthVerifier ?: error("Die Anmeldung wurde nicht in dieser App gestartet.")
        val response = try {
            api.oauthExchange(
                OAuthExchangeRequest(
                    ticket = ticket,
                    appCodeVerifier = verifier,
                    totpCode = totpCode?.takeIf { it.isNotBlank() },
                    deviceId = store.deviceId,
                    deviceName = deviceName,
                    appVersion = BuildConfig.VERSION_NAME
                )
            )
        } catch (error: retrofit2.HttpException) {
            val body = runCatching { error.response()?.errorBody()?.string() }.getOrNull().orEmpty()
            val json = runCatching { JSONObject(body) }.getOrNull()
            val code = json?.optString("error")?.takeIf { it.isNotBlank() } ?: json?.optString("code")
            if (code == "MFA_REQUIRED") throw OAuthMfaRequired()
            throw IllegalStateException(json?.optString("message")?.takeIf { it.isNotBlank() } ?: "HTTP ${error.code()}")
        }
        store.pendingOAuthVerifier = null
        check(store.completeLogin(response.accessToken, response.refreshToken)) {
            "Die Anmeldung konnte nicht lokal gespeichert werden."
        }
        migrateStandaloneDataIfNeeded()
        SyncScheduler.enqueue(context)
        return response.user
    }

    suspend fun passkeyLoginOptions(): String = api.passkeyLoginOptions().string()

    suspend fun passkeyLogin(authenticationResponseJson: String, deviceName: String): User {
        val body = JSONObject(authenticationResponseJson).apply {
            put("deviceName", deviceName)
            put("deviceId", store.deviceId)
            put("platform", "android")
            put("appVersion", BuildConfig.VERSION_NAME)
        }.toString().toRequestBody("application/json".toMediaType())

        val response = api.passkeyLoginVerify(body)
        check(store.completeLogin(response.accessToken, response.refreshToken)) {
            "Die Passkey-Anmeldung konnte nicht lokal gespeichert werden."
        }
        migrateStandaloneDataIfNeeded()
        SyncScheduler.enqueue(context)
        return response.user
    }

    suspend fun forgotPassword(email: String) {
        api.forgotPassword(ForgotPasswordRequest(email))
    }

    suspend fun resetPassword(token: String, password: String) {
        api.resetPassword(ResetPasswordRequest(token, password))
    }

    suspend fun me(): User = api.me()

    /** Export vom Server (Steuer-PDF bzw. CSV); wirft bei Fehlern, damit der Aufrufer lokal erzeugen kann. */
    suspend fun serverExport(request: ExportRequest): ExportResult {
        val response = when (request.format) {
            ExportFormat.PDF -> api.exportPdf(request.from, request.to, request.vehicleId, request.tripType?.apiValue, request.specialCategory?.apiValue)
            ExportFormat.CSV -> api.exportCsv(request.from, request.to, request.vehicleId, request.tripType?.apiValue, request.specialCategory?.apiValue)
        }
        val body = response.body()
        if (!response.isSuccessful || body == null) {
            throw retrofit2.HttpException(response)
        }
        val fileName = response.headers()["Content-Disposition"]
            ?.let { Regex("filename=\"?([^\";]+)\"?").find(it)?.groupValues?.get(1) }
            ?: "fahrtenbuch_${request.from}_${request.to}.${request.format.extension}"
        return ExportResult(body.bytes(), fileName, request.format.mimeType, generatedOnDevice = false)
    }

    /** Serverstatus und -version (nur Server-Modus); null, wenn nicht erreichbar. */
    suspend fun serverHealth(): HealthResponse? =
        if (store.isServerMode) runCatching { api.health() }.getOrNull() else null

    /** Zuletzt bekannte Anzeige-Einstellungen (sofort verfügbar, auch offline). */
    fun cachedPreferences(): UserPreferences =
        UserPreferences.fromApi(
            store.distanceUnit, store.speedUnit, store.dateFormat, store.timeFormat, store.timeZone,
            gravatarEnabled = store.gravatarEnabled,
            // Gravatar braucht ein Server-Konto (E-Mail-Adresse)
            gravatarAvailable = store.isServerMode && store.gravatarAvailable
        )

    /** Im Server-Modus vom Server laden und lokal merken; sonst bzw. bei Fehlern der lokale Stand. */
    suspend fun preferences(): UserPreferences {
        if (store.isServerMode && store.hasAuthenticatedSession) {
            runCatching { api.mySettings().settings }.getOrNull()?.let(::cacheSettings)
            // Die Zeitzone gehört auf dem Server zum Benutzerprofil (Standard Europe/Berlin)
            runCatching { api.me().timezone }.getOrNull()?.takeIf { it.isNotBlank() }?.let { store.timeZone = it }
        }
        return cachedPreferences()
    }

    suspend fun updatePreferences(preferences: UserPreferences): UserPreferences {
        if (store.isServerMode) {
            val saved = api.updateMySettings(
                mapOf(
                    "distanceUnit" to preferences.distanceUnit.apiValue,
                    "speedUnit" to preferences.speedUnit.apiValue,
                    "dateFormat" to preferences.dateFormat.apiValue,
                    "timeFormat" to preferences.timeFormat.apiValue
                )
            )
            cacheSettings(saved)
            val zone = preferences.timeZone
            if (zone != null && zone != store.timeZone) {
                api.patchProfile(mapOf("timezone" to zone))
                store.timeZone = zone
            }
        } else {
            store.timeZone = preferences.timeZone
            store.distanceUnit = preferences.distanceUnit.apiValue
            store.speedUnit = preferences.speedUnit.apiValue
            store.dateFormat = preferences.dateFormat.apiValue
            store.timeFormat = preferences.timeFormat.apiValue
        }
        return cachedPreferences()
    }

    private fun cacheSettings(settings: UserSettingsDto) {
        settings.distanceUnit?.let { store.distanceUnit = it }
        settings.speedUnit?.let { store.speedUnit = it }
        settings.dateFormat?.let { store.dateFormat = it }
        settings.timeFormat?.let { store.timeFormat = it }
        settings.gravatarEnabled?.let { store.gravatarEnabled = it }
        settings.gravatarAvailable?.let { store.gravatarAvailable = it }
    }

    /** Gravatar als Profilbild ein- oder ausschalten (wird mit dem Konto synchronisiert). */
    suspend fun setGravatarEnabled(enabled: Boolean): UserPreferences {
        if (store.isServerMode) {
            val body = JSONObject().put("gravatarEnabled", enabled).toString()
                .toRequestBody("application/json".toMediaType())
            cacheSettings(api.updateMySettingsJson(body))
        } else {
            store.gravatarEnabled = enabled
        }
        return cachedPreferences()
    }

    suspend fun updateProfile(
        displayName: String?,
        username: String?,
        email: String?
    ): User = api.updateMe(
        mapOf(
            "displayName" to displayName?.trim()?.takeIf { it.isNotBlank() },
            "username" to username?.trim()?.takeIf { it.isNotBlank() },
            "email" to email?.trim()?.takeIf { it.isNotBlank() }
        )
    )

    suspend fun devices(): List<DeviceInfo> = api.devices()
    suspend fun deleteDevice(deviceId: Uuid) = api.deleteDevice(deviceId)

    suspend fun logout() {
        runCatching { api.logout(LogoutRequest(store.refreshToken)) }
        store.clearTokens()
    }

    suspend fun vehicles(forceNetwork: Boolean = true): List<Vehicle> {
        if (forceNetwork) {
            // Do not hide a successful /vehicles response just because the local
            // cache cannot be written. The network result must reach the UI first.
            val values = api.getVehicles()
            runCatching {
                dao.putVehicles(values.map(Vehicle::toEntity))
                dao.putCache(
                    CacheEntity(
                        "vehicles",
                        vehicleAdapter.toJson(values),
                        Instant.now().toString()
                    )
                )
            }
            return values
        }

        val stored = dao.vehicles().map(VehicleEntity::toModel)
        if (stored.isNotEmpty()) return stored

        // One-time compatibility fallback for databases created before v2.
        val legacy = dao.cache("vehicles")?.json?.let(vehicleAdapter::fromJson).orEmpty()
        if (legacy.isNotEmpty()) dao.replaceVehicles(legacy.map(Vehicle::toEntity))
        return legacy
    }

    /** Standardfahrzeug setzen (Server-Modus über die API, lokal nur in Room). */
    suspend fun setDefaultVehicle(vehicleId: Uuid): List<Vehicle> {
        if (store.isServerMode) api.setDefaultVehicle(vehicleId)
        dao.setDefaultVehicle(vehicleId)
        return dao.vehicles().map(VehicleEntity::toModel)
    }

    suspend fun createVehicle(name: String, licensePlate: String?): Vehicle {
        if (store.isLocalMode) {
            val now = Instant.now().toString()
            // Wie auf dem Server: das erste Fahrzeug wird automatisch Standardfahrzeug
            val created = Vehicle(
                UUID.randomUUID().toString(), name.trim(), licensePlate?.trim()?.uppercase()?.takeIf { it.isNotBlank() },
                createdAt = now, updatedAt = now, isDefault = dao.vehicles().isEmpty()
            )
            dao.putVehicle(created.toEntity().copy(syncState = "LOCAL"))
            return created
        }
        val created = api.createVehicle(
            CreateVehicleRequest(
                name = name.trim(),
                licensePlate = licensePlate?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
            )
        )
        dao.putVehicle(created.toEntity())
        return created
    }

    suspend fun setVehicleBluetooth(vehicleId: Uuid, bluetoothMac: String?): Vehicle {
        val normalized = bluetoothMac
            ?.trim()
            ?.uppercase()
            ?.takeIf { it.isNotBlank() }
        if (store.isLocalMode) {
            val current = dao.vehicle(vehicleId) ?: error("Lokales Fahrzeug nicht gefunden")
            val now = Instant.now().toString()
            dao.setVehicleBluetooth(vehicleId, normalized, now)
            return current.copy(bluetoothMac = normalized, updatedAt = now).toModel()
        }
        val updated = api.setBluetooth(vehicleId, BluetoothMacRequest(normalized))
        dao.putVehicle(updated.toEntity())
        return updated
    }

    suspend fun trips(forceNetwork: Boolean = true): List<Trip> {
        if (forceNetwork) runCatching { api.getTrips(limit = 500) }.onSuccess { values ->
            dao.putTripRecords(values.map(Trip::toEntity))
            dao.putCache(CacheEntity("trips", tripAdapter.toJson(values), Instant.now().toString()))
            return mergeLocalTrips(values)
        }
        val stored = dao.trips().map(TripEntity::toModel)
        if (stored.isNotEmpty()) return mergeLocalTrips(stored)

        // Alter Cache kann Tags noch als Strings enthalten: dann verwerfen statt abstürzen
        val legacy = runCatching { dao.cache("trips")?.json?.let(tripAdapter::fromJson) }.getOrNull().orEmpty()
        if (legacy.isNotEmpty()) dao.replaceTrips(legacy.map(Trip::toEntity))
        return mergeLocalTrips(legacy)
    }

    suspend fun tags(forceNetwork: Boolean = true): List<Tag> {
        if (forceNetwork && store.isServerMode) runCatching { api.tags() }.onSuccess { values ->
            dao.replaceTags(values.map { TagEntity(it.id, it.name, color = it.color) })
            return values.sortedBy { it.name.lowercase() }
        }
        return dao.tags().map { Tag(it.id, it.name, it.color) }
    }

    /** Neuen Tag anlegen; gibt es den Namen schon (ohne Groß-/Kleinschreibung), wird dieser verwendet. */
    suspend fun createTag(name: String): Tag {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty())
        tags(forceNetwork = store.isServerMode).firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let { return it }
        if (store.isLocalMode) {
            val created = Tag(UUID.randomUUID().toString(), trimmed)
            dao.putTag(TagEntity(created.id, created.name, syncState = "LOCAL"))
            return created
        }
        val created = api.createTag(TagRequest(trimmed))
        dao.putTag(TagEntity(created.id, created.name, color = created.color))
        return created
    }

    /** Tags einer Fahrt setzen (ersetzt die bisherigen). */
    suspend fun setTripTags(trip: Trip, tags: List<Tag>): Trip {
        if (store.isLocalMode) {
            dao.setLocalTags(trip.id, tags.toTagsJson())
            return trip.copy(tags = tags, syncState = "LOCAL")
        }
        val updated = api.setTripTags(trip.id, TripTagsRequest(tags.map { it.id }))
        dao.putTripRecord(updated.toEntity())
        return updated
    }

    /**
     * Pulls the complete server snapshots into Room. UI code reads Room first;
     * this method only refreshes the local source of truth.
     */
    private suspend fun migrateStandaloneDataIfNeeded() {
        if (!store.migrationPending) return
        val localVehicles = dao.vehicles()
        val serverVehicles = api.getVehicles().toMutableList()

        for (local in localVehicles.filter { it.syncState == "LOCAL" }) {
            val existing = serverVehicles.firstOrNull { remote ->
                (!local.licensePlate.isNullOrBlank() && remote.licensePlate.equals(local.licensePlate, ignoreCase = true)) ||
                    remote.name.equals(local.name, ignoreCase = true)
            }
            val remote = existing ?: api.createVehicle(CreateVehicleRequest(local.name, local.licensePlate)).also(serverVehicles::add)
            dao.remapOfflineTripsVehicle(local.id, remote.id)
            if (!local.bluetoothMac.isNullOrBlank() && remote.bluetoothMac.isNullOrBlank()) {
                runCatching { api.setBluetooth(remote.id, BluetoothMacRequest(local.bluetoothMac)) }
            }
        }

        dao.replaceVehicles(serverVehicles.map(Vehicle::toEntity))
        store.completeMigration()
    }

    suspend fun syncFromServer() {
        val remoteVehicles = api.getVehicles()
        val remoteTrips = api.getTrips(limit = 500)
        val remoteTags = api.tags()

        dao.replaceVehicles(remoteVehicles.map(Vehicle::toEntity))
        dao.replaceTrips(remoteTrips.map(Trip::toEntity))
        dao.replaceTags(remoteTags.map { TagEntity(it.id, it.name, color = it.color) })

        // Legacy cache is retained only so existing v1 installations can still
        // migrate cleanly during the transition.
        val now = Instant.now().toString()
        dao.putCache(CacheEntity("vehicles", vehicleAdapter.toJson(remoteVehicles), now))
        dao.putCache(CacheEntity("trips", tripAdapter.toJson(remoteTrips), now))
    }

    suspend fun getTripDetails(id: Uuid): Trip = if (store.isLocalMode) {
        dao.trip(id)?.toUiTrip(dao.trip(id)?.endedAt) ?: dao.storedTrip(id)?.toModel() ?: error("Lokale Fahrt nicht gefunden")
    } else api.getTrip(id)

    suspend fun tripPoints(id: Uuid): List<TrackPoint> = if (store.isLocalMode) {
        dao.points(id).map { TrackPoint(id = it.id.toString(), tripId = id, lat = it.lat, lon = it.lon, altitude = it.altitude, accuracy = it.accuracy, speed = it.speed, bearing = it.bearing, recordedAt = it.recordedAt) }
    } else api.tripPoints(id)

    suspend fun updateTripCategory(
        trip: Trip,
        type: TripType,
        specialCategory: SpecialCategory? = SpecialCategory.fromApi(trip.specialCategory)
    ): Trip {
        if (store.isLocalMode) {
            dao.setLocalTripType(trip.id, type.apiValue)
            dao.setLocalSpecialCategory(trip.id, specialCategory?.apiValue)
            return trip.copy(tripType = type, specialCategory = specialCategory?.apiValue, syncState = "LOCAL")
        }
        // Classification must not overwrite GPS coordinates, timestamps,
        // addresses or other trip metadata.
        val body = JSONObject()
            .put("category", type.apiValue)
            .put("specialCategory", specialCategory?.apiValue ?: JSONObject.NULL)
            .toString()
            .toRequestBody("application/json".toMediaType())
        val updated = api.classifyTripJson(trip.id, body)
        dao.putTripRecord(updated.toEntity())
        return updated
    }

    /** Fahrtzweck, Kontakt und Notiz ändern; leere Felder werden gelöscht. */
    suspend fun updateTripPurpose(trip: Trip, purpose: String?, contact: String?, note: String?): Trip {
        val p = purpose?.trim()?.takeIf { it.isNotEmpty() }
        val c = contact?.trim()?.takeIf { it.isNotEmpty() }
        val n = note?.trim()?.takeIf { it.isNotEmpty() }
        if (store.isLocalMode) {
            dao.setLocalPurpose(trip.id, p, c, n)
            return trip.copy(purpose = p, contact = c, note = n, syncState = "LOCAL")
        }
        // Über /classify, damit GPS-Daten, Zeiten und Adressen unangetastet bleiben
        val body = JSONObject()
            .put("category", trip.tripType.apiValue)
            .put("purpose", p ?: JSONObject.NULL)
            .put("contact", c ?: JSONObject.NULL)
            .put("notes", n ?: JSONObject.NULL)
            .toString()
            .toRequestBody("application/json".toMediaType())
        val updated = api.classifyTripJson(trip.id, body)
        dao.putTripRecord(updated.toEntity())
        return updated
    }

    suspend fun dashboard(): Dashboard = api.dashboard()

    /** Starts immediately without requiring a network connection. */
    suspend fun startTracking(vehicleId: Uuid, type: TripType, odometer: Int?): Uuid {
        val localId = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        dao.putTrip(
            OfflineTripEntity(
                localId, null, vehicleId, type.apiValue, now, null, "RECORDING",
                startTimeZone = java.time.ZoneId.systemDefault().id
            )
        )
        store.activeTripId = localId
        if (store.isServerMode) SyncScheduler.enqueue(context)
        return localId
    }

    suspend fun stopTracking(localId: Uuid, endOdometer: Int?, note: String?): Trip {
        val local = dao.trip(localId) ?: error("Lokale Fahrt nicht gefunden")
        val endedAt = Instant.now().toString()
        dao.putTrip(local.copy(endedAt = endedAt, state = "STOPPED", pendingStop = true, endTimeZone = java.time.ZoneId.systemDefault().id))
        store.activeTripId = null
        if (store.isServerMode) SyncScheduler.enqueue(context)
        return local.toUiTrip(endedAt).copy(syncState = if (store.isLocalMode) "LOCAL" else "PENDING")
    }

    fun requestSync() { if (store.isServerMode) SyncScheduler.enqueue(context) }

    /**
     * Ergänzt beendete Fahrten auf dem Gerät um Start-/Zieladresse (Android-Geocoder) und
     * die Strecke aus den GPS-Punkten. Läuft ohne Server, z. B. im lokalen Modus. Was ohne
     * Internet nicht aufgelöst werden kann, bleibt leer und wird beim nächsten Mal erneut
     * versucht. Liefert true, wenn sich etwas geändert hat.
     */
    suspend fun resolveLocalTripDetails(): Boolean = detailsMutex.withLock {
        var changed = false
        for (trip in dao.tripsMissingDetails()) {
            val points = dao.points(trip.localId)
            if (points.isEmpty()) continue
            val distance = trip.distanceKm ?: trackDistanceKm(points)
            val start = trip.startAddress
                ?: runCatching { ReverseGeocoder.lookup(context, points.first().lat, points.first().lon) }.getOrNull()
            val end = trip.endAddress
                ?: runCatching { ReverseGeocoder.lookup(context, points.last().lat, points.last().lon) }.getOrNull()
            if (start != trip.startAddress || end != trip.endAddress || distance != trip.distanceKm) {
                dao.setTripDetails(trip.localId, start, end, distance)
                changed = true
            }
        }
        changed
    }

    private suspend fun mergeLocalTrips(remote: List<Trip>): List<Trip> {
        val pending = dao.pendingTrips()
        val pendingRemoteIds = pending.mapNotNull { it.remoteId }.toSet()
        // Reconcile completed local sessions with the server by remote ID.
        // Never label a server-side recording as fully synchronized.
        val local = pending.map { session ->
            val serverTrip = session.remoteId?.let { id -> remote.firstOrNull { it.id == id } }
            if (store.isServerMode && serverTrip != null && serverTrip.status == "completed" &&
                session.pendingStop && session.endedAt != null
            ) {
                // The stop response can be lost after the server committed the trip.
                dao.markTripSynced(session.localId)
                serverTrip
            } else {
                session.toUiTrip(session.endedAt).copy(syncState = if (store.isLocalMode) "LOCAL" else if (session.syncError != null) "FAILED" else "PENDING")
            }
        }
        return (local + remote.filterNot { it.id in pendingRemoteIds })
            .distinctBy { it.id }
            .sortedByDescending { it.startedAt }
    }
}

private fun OfflineTripEntity.toUiTrip(end: String?) = Trip(
    id = localId,
    vehicleId = vehicleId,
    tripType = TripType.entries.firstOrNull { it.apiValue == type } ?: TripType.UNCLASSIFIED,
    status = if (end == null) "recording" else "completed",
    startedAt = startedAt,
    endedAt = end,
    startAddress = startAddress,
    endAddress = endAddress,
    distanceKm = distanceKm,
    specialCategory = specialCategory,
    startTimeZone = startTimeZone,
    endTimeZone = endTimeZone ?: startTimeZone,
    purpose = purpose,
    contact = contact,
    note = notes,
    tags = parseTagsJson(tagsJson),
    durationSeconds = end?.let {
        runCatching { java.time.Duration.between(Instant.parse(startedAt), Instant.parse(it)).seconds.toInt() }.getOrNull()
    },
    syncState = if (syncError != null) "FAILED" else "PENDING"
)

/** Strecke aus GPS-Punkten (Haversine); ungenaue Fixes über 50 m werden ignoriert. */
private fun trackDistanceKm(points: List<TrackPointEntity>): Double {
    val usable = points.filter { it.accuracy == null || it.accuracy <= 50.0 }
    var meters = 0.0
    for (i in 1 until usable.size) {
        val a = usable[i - 1]
        val b = usable[i]
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = Math.sin(dLat / 2).let { it * it } +
            Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) * Math.sin(dLon / 2).let { it * it }
        meters += 2 * 6_371_000.0 * Math.asin(Math.sqrt(h))
    }
    return meters / 1000.0
}


private fun Vehicle.toEntity() = VehicleEntity(
    id = id,
    name = name,
    licensePlate = licensePlate,
    bluetoothMac = bluetoothMac,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDefault = isDefault
)

private fun VehicleEntity.toModel() = Vehicle(
    id = id,
    name = name,
    licensePlate = licensePlate,
    bluetoothMac = bluetoothMac,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDefault = isDefault
)

private fun Trip.toEntity() = TripEntity(
    id = id,
    vehicleId = vehicleId,
    type = tripType.apiValue,
    status = status,
    startedAt = startedAt,
    endedAt = endedAt,
    startAddress = startAddress,
    endAddress = endAddress,
    purpose = purpose,
    contact = contact,
    notes = note,
    distanceKm = distanceKm,
    durationSeconds = durationSeconds,
    createdAt = createdAt,
    updatedAt = updatedAt,
    vehicleName = vehicleName,
    tagsJson = tags.toTagsJson(),
    specialCategory = specialCategory,
    startTimeZone = startTimeZone,
    endTimeZone = endTimeZone
)

private fun TripEntity.toModel() = Trip(
    id = id,
    vehicleId = vehicleId,
    tripType = TripType.entries.firstOrNull { it.apiValue == type } ?: TripType.UNCLASSIFIED,
    status = status,
    startedAt = startedAt,
    endedAt = endedAt,
    startAddress = startAddress,
    endAddress = endAddress,
    purpose = purpose,
    contact = contact,
    note = notes,
    distanceKm = distanceKm,
    durationSeconds = durationSeconds,
    createdAt = createdAt,
    updatedAt = updatedAt,
    vehicleName = vehicleName,
    tags = parseTagsJson(tagsJson),
    syncState = syncState,
    specialCategory = specialCategory,
    startTimeZone = startTimeZone,
    endTimeZone = endTimeZone
)
