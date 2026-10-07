package app.fahrtenbuch.mobile.data.repository

import android.content.Context
import app.fahrtenbuch.mobile.BuildConfig
import app.fahrtenbuch.mobile.data.api.FahrtenbuchApi
import app.fahrtenbuch.mobile.data.api.SessionStore
import app.fahrtenbuch.mobile.data.local.CacheEntity
import app.fahrtenbuch.mobile.data.local.OfflineDatabase
import app.fahrtenbuch.mobile.data.local.OfflineTripEntity
import app.fahrtenbuch.mobile.data.local.VehicleEntity
import app.fahrtenbuch.mobile.data.local.TripEntity
import app.fahrtenbuch.mobile.data.local.TagEntity
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
        SyncScheduler.enqueue(context)
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
        SyncScheduler.enqueue(context)
        return response.user
    }

    suspend fun forgotPassword(email: String) {
        api.forgotPassword(ForgotPasswordRequest(email))
    }

    suspend fun resetPassword(token: String, password: String) {
        api.resetPassword(ResetPasswordRequest(token, password))
    }

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

    suspend fun createVehicle(name: String, licensePlate: String?): Vehicle {
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

        val legacy = dao.cache("trips")?.json?.let(tripAdapter::fromJson).orEmpty()
        if (legacy.isNotEmpty()) dao.replaceTrips(legacy.map(Trip::toEntity))
        return mergeLocalTrips(legacy)
    }

    suspend fun tags(forceNetwork: Boolean = true): List<Tag> {
        if (forceNetwork) runCatching { api.tags() }.onSuccess { values ->
            dao.replaceTags(values.map { TagEntity(it.id, it.name) })
            return values
        }
        return dao.tags().map { Tag(it.id, it.name) }
    }

    /**
     * Pulls the complete server snapshots into Room. UI code reads Room first;
     * this method only refreshes the local source of truth.
     */
    suspend fun syncFromServer() {
        val remoteVehicles = api.getVehicles()
        val remoteTrips = api.getTrips(limit = 500)
        val remoteTags = api.tags()

        dao.replaceVehicles(remoteVehicles.map(Vehicle::toEntity))
        dao.replaceTrips(remoteTrips.map(Trip::toEntity))
        dao.replaceTags(remoteTags.map { TagEntity(it.id, it.name) })

        // Legacy cache is retained only so existing v1 installations can still
        // migrate cleanly during the transition.
        val now = Instant.now().toString()
        dao.putCache(CacheEntity("vehicles", vehicleAdapter.toJson(remoteVehicles), now))
        dao.putCache(CacheEntity("trips", tripAdapter.toJson(remoteTrips), now))
    }

    suspend fun dashboard(): Dashboard = api.dashboard()

    /** Starts immediately without requiring a network connection. */
    suspend fun startTracking(vehicleId: Uuid, type: TripType, odometer: Int?): Uuid {
        val localId = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        dao.putTrip(OfflineTripEntity(localId, null, vehicleId, type.apiValue, now, null, "RECORDING"))
        store.activeTripId = localId
        SyncScheduler.enqueue(context)
        return localId
    }

    suspend fun stopTracking(localId: Uuid, endOdometer: Int?, note: String?): Trip {
        val local = dao.trip(localId) ?: error("Lokale Fahrt nicht gefunden")
        val endedAt = Instant.now().toString()
        dao.putTrip(local.copy(endedAt = endedAt, state = "STOPPED", pendingStop = true))
        store.activeTripId = null
        SyncScheduler.enqueue(context)
        return local.toUiTrip(endedAt)
    }

    fun requestSync() = SyncScheduler.enqueue(context)

    private suspend fun mergeLocalTrips(remote: List<Trip>): List<Trip> {
        val local = dao.pendingTrips().map { it.toUiTrip(it.endedAt) }
        return (local + remote).distinctBy { it.id }.sortedByDescending { it.startedAt }
    }
}

private fun OfflineTripEntity.toUiTrip(end: String?) = Trip(
    id = localId,
    vehicleId = vehicleId,
    tripType = TripType.entries.firstOrNull { it.apiValue == type } ?: TripType.UNCLASSIFIED,
    status = if (end == null) "recording" else "completed",
    startedAt = startedAt,
    endedAt = end
)


private fun Vehicle.toEntity() = VehicleEntity(
    id = id,
    name = name,
    licensePlate = licensePlate,
    bluetoothMac = bluetoothMac,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun VehicleEntity.toModel() = Vehicle(
    id = id,
    name = name,
    licensePlate = licensePlate,
    bluetoothMac = bluetoothMac,
    createdAt = createdAt,
    updatedAt = updatedAt
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
    tagsJson = org.json.JSONArray(tags).toString()
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
    tags = runCatching {
        val array = org.json.JSONArray(tagsJson)
        List(array.length()) { array.getString(it) }
    }.getOrDefault(emptyList())
)
