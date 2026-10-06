package app.fahrtenbuch.mobile.data.repository

import android.content.Context
import app.fahrtenbuch.mobile.BuildConfig
import app.fahrtenbuch.mobile.data.api.FahrtenbuchApi
import app.fahrtenbuch.mobile.data.api.SessionStore
import app.fahrtenbuch.mobile.data.local.CacheEntity
import app.fahrtenbuch.mobile.data.local.OfflineDatabase
import app.fahrtenbuch.mobile.data.local.OfflineTripEntity
import app.fahrtenbuch.mobile.data.model.*
import app.fahrtenbuch.mobile.sync.SyncScheduler
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.time.Instant
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
        val response = api.login(LoginRequest(email, password, totpCode?.takeIf { it.isNotBlank() }, deviceName))
        store.accessToken = response.accessToken
        store.refreshToken = response.refreshToken
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

        // /auth/pair returns tokens only. Persist them immediately: from this
        // point on the device is paired and must survive app restarts.
        store.accessToken = response.accessToken
        store.refreshToken = response.refreshToken
        SyncScheduler.enqueue(context)
    }

    suspend fun devices(): List<DeviceInfo> = api.devices()
    suspend fun deleteDevice(deviceId: Uuid) = api.deleteDevice(deviceId)

    suspend fun logout() {
        runCatching { api.logout(LogoutRequest(store.refreshToken)) }
        store.clearTokens()
    }

    suspend fun vehicles(forceNetwork: Boolean = true): List<Vehicle> {
        if (forceNetwork) runCatching { api.getVehicles() }.onSuccess { values ->
            dao.putCache(CacheEntity("vehicles", vehicleAdapter.toJson(values), Instant.now().toString()))
            return values
        }
        return dao.cache("vehicles")?.json?.let(vehicleAdapter::fromJson).orEmpty()
    }

    suspend fun trips(forceNetwork: Boolean = true): List<Trip> {
        if (forceNetwork) runCatching { api.getTrips(limit = 500) }.onSuccess { values ->
            dao.putCache(CacheEntity("trips", tripAdapter.toJson(values), Instant.now().toString()))
            return mergeLocalTrips(values)
        }
        val cached = dao.cache("trips")?.json?.let(tripAdapter::fromJson).orEmpty()
        return mergeLocalTrips(cached)
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
