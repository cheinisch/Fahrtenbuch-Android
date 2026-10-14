package app.fahrtenbuch.mobile.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import app.fahrtenbuch.mobile.data.api.ApiFactory
import app.fahrtenbuch.mobile.data.api.SessionStore
import app.fahrtenbuch.mobile.data.local.OfflineDatabase
import app.fahrtenbuch.mobile.data.local.VehicleEntity
import app.fahrtenbuch.mobile.data.local.TripEntity
import app.fahrtenbuch.mobile.data.local.TagEntity
import app.fahrtenbuch.mobile.data.model.*

class OfflineSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        const val TAG = "FahrtenbuchSync"
    }
    override suspend fun doWork(): Result {
        val store = SessionStore(applicationContext)
        if (!store.serverConfigured || store.refreshToken == null) {
            Log.i(TAG, "Sync skipped: serverConfigured=${store.serverConfigured} authenticated=${store.refreshToken != null}")
            return Result.success()
        }
        Log.i(TAG, "Sync started: runAttemptCount=$runAttemptCount")
        val api = ApiFactory.create(store)
        val dao = OfflineDatabase.get(applicationContext).dao()
        return try {
            // Push locally recorded tracking sessions first.
            val pendingTrips = dao.pendingTrips()
            Log.i(TAG, "Pending local trips: ${pendingTrips.size}")
            val serverVehicles = api.getVehicles()
            Log.i(TAG, "Server vehicle IDs: ${serverVehicles.map { it.id }}")
            var failedTrips = 0
            pendingTrips.forEach { local ->
                try {
                    if (serverVehicles.none { it.id == local.vehicleId }) {
                        Log.e(TAG, "Vehicle missing from server list: localId=${local.localId} vehicleId=${local.vehicleId}; retaining data")
                        dao.setTripSyncError(local.localId, "Fahrzeug nicht auf dem Server vorhanden")
                        failedTrips++
                        return@forEach
                    }
                dao.setTripSyncError(local.localId, null)
                var remoteId = local.remoteId
                Log.i(
                    TAG,
                    "Sync trip: localId=${local.localId} remoteId=${local.remoteId ?: "-"} " +
                        "vehicleId=${local.vehicleId} startedAt=${local.startedAt} endedAt=${local.endedAt ?: "-"} " +
                        "state=${local.state} pendingStop=${local.pendingStop}"
                )
                if (remoteId == null) {
                    Log.i(TAG, "POST tracking/start: localId=${local.localId} vehicleId=${local.vehicleId} startedAt=${local.startedAt}")
                    val remote = api.startTracking(StartTrackingRequest(local.vehicleId, local.startedAt, local.startTimeZone))
                    Log.i(TAG, "tracking/start OK: localId=${local.localId} remoteId=${remote.id}")
                    remoteId = remote.id
                    dao.setRemoteId(local.localId, remoteId)
                }
                while (true) {
                    val points = dao.pendingPoints(local.localId)
                    if (points.isEmpty()) {
                        Log.d(TAG, "No pending points: localId=${local.localId} remoteId=$remoteId")
                        break
                    }
                    Log.i(
                        TAG,
                        "POST tracking/points: localId=${local.localId} remoteId=$remoteId count=${points.size} " +
                            "first=${points.first().recordedAt} last=${points.last().recordedAt}"
                    )
                    api.uploadPoints(TrackingPointsRequest(remoteId, points.map { it.toApi() }))
                    dao.markPointsUploaded(points.map { it.id })
                    Log.i(TAG, "tracking/points OK: localId=${local.localId} uploaded=${points.size}")
                }
                if (local.pendingStop && local.endedAt != null) {
                    // Lokal erfassten Fahrtzweck/Kontakt/Notiz vor dem Stop senden: schlägt es fehl,
                    // wird beim nächsten Lauf beides wiederholt
                    if (local.purpose != null || local.contact != null || local.notes != null) {
                        val body = org.json.JSONObject()
                            .put("category", local.type)
                            .put("specialCategory", local.specialCategory ?: org.json.JSONObject.NULL)
                            .put("purpose", local.purpose ?: org.json.JSONObject.NULL)
                            .put("contact", local.contact ?: org.json.JSONObject.NULL)
                            .put("notes", local.notes ?: org.json.JSONObject.NULL)
                            .toString()
                            .toRequestBody("application/json".toMediaType())
                        api.classifyTripJson(remoteId, body)
                    }
                    Log.i(TAG, "POST tracking/stop: localId=${local.localId} remoteId=$remoteId endedAt=${local.endedAt}")
                    val completed = api.stopTracking(StopTrackingRequest(remoteId, local.endedAt, local.endTimeZone ?: local.startTimeZone))
                    dao.markTripSynced(local.localId)
                    Log.i(
                        TAG,
                        "tracking/stop OK: localId=${local.localId} remoteId=$remoteId " +
                            "distanceKm=${completed.distanceKm} durationSeconds=${completed.durationSeconds}"
                    )
                }
                } catch (error: HttpException) {
                    val body = runCatching { error.response()?.errorBody()?.string() }.getOrNull()
                    Log.e(TAG, "Trip failed: localId=${local.localId} HTTP ${error.code()} body=${body ?: "<empty>"}; retaining data", error)
                    val detail = runCatching { org.json.JSONObject(body ?: "{}").optString("message") }.getOrNull().orEmpty()
                    dao.setTripSyncError(local.localId, "HTTP ${error.code()}: ${detail.ifBlank { "Synchronisierung fehlgeschlagen" }}")
                    failedTrips++
                } catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    Log.e(TAG, "Trip failed: localId=${local.localId}; retaining data", error)
                    dao.setTripSyncError(local.localId, error.message ?: "Synchronisierung fehlgeschlagen")
                    failedTrips++
                }
            }

            // Pull fresh server state only after all local writes have been pushed.
            // The backend currently exposes full collection endpoints rather than
            // a delta /sync route, so these snapshots are the reconciliation point.
            val vehicles = serverVehicles
            val trips = api.getTrips(limit = 500)
            val tags = api.tags()
            dao.replaceVehicles(vehicles.map { it.toEntity() })
            dao.replaceTrips(trips.map { it.toEntity() })
            dao.replaceTags(tags.map { TagEntity(it.id, it.name) })

            Log.i(TAG, "Sync completed: failedTrips=$failedTrips")
            if (failedTrips > 0) Result.failure() else Result.success()
        } catch (error: HttpException) {
            val responseBody = runCatching { error.response()?.errorBody()?.string() }.getOrNull()
            Log.e(
                TAG,
                "HTTP sync failure: status=${error.code()} message=${error.message()} " +
                    "body=${responseBody ?: "<empty>"} attempt=$runAttemptCount",
                error
            )
            if (error.code() in 400..499 && error.code() !in listOf(408, 429)) {
                Log.e(TAG, "Permanent client error ${error.code()}; not retrying unchanged request")
                Result.failure()
            } else {
                Result.retry()
            }
        } catch (error: Exception) {
            Log.e(
                TAG,
                "Sync failure: type=${error::class.java.simpleName} message=${error.message} attempt=$runAttemptCount",
                error
            )
            Result.retry()
        }
    }
}

private fun app.fahrtenbuch.mobile.data.local.TrackPointEntity.toApi() = TrackPoint(
    lat = lat, lon = lon, altitude = altitude, accuracy = accuracy, speed = speed,
    bearing = bearing, recordedAt = recordedAt
)


private fun Vehicle.toEntity() = VehicleEntity(
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
    tagsJson = org.json.JSONArray(tags).toString(),
    specialCategory = specialCategory,
    startTimeZone = startTimeZone,
    endTimeZone = endTimeZone
)
