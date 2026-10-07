package app.fahrtenbuch.mobile.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.fahrtenbuch.mobile.data.api.ApiFactory
import app.fahrtenbuch.mobile.data.api.SessionStore
import app.fahrtenbuch.mobile.data.local.OfflineDatabase
import app.fahrtenbuch.mobile.data.local.VehicleEntity
import app.fahrtenbuch.mobile.data.local.TripEntity
import app.fahrtenbuch.mobile.data.local.TagEntity
import app.fahrtenbuch.mobile.data.model.*

class OfflineSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = SessionStore(applicationContext)
        if (!store.serverConfigured || store.refreshToken == null) return Result.success()
        val api = ApiFactory.create(store)
        val dao = OfflineDatabase.get(applicationContext).dao()
        return try {
            // Push locally recorded tracking sessions first.
            dao.pendingTrips().forEach { local ->
                var remoteId = local.remoteId
                if (remoteId == null) {
                    val remote = api.startTracking(StartTrackingRequest(local.vehicleId, local.startedAt))
                    remoteId = remote.id
                    dao.setRemoteId(local.localId, remoteId)
                }
                while (true) {
                    val points = dao.pendingPoints(local.localId)
                    if (points.isEmpty()) break
                    api.uploadPoints(TrackingPointsRequest(remoteId, points.map { it.toApi() }))
                    dao.markPointsUploaded(points.map { it.id })
                }
                if (local.pendingStop && local.endedAt != null) {
                    api.stopTracking(StopTrackingRequest(remoteId, local.endedAt))
                    dao.markTripSynced(local.localId)
                }
            }

            // Pull fresh server state only after all local writes have been pushed.
            // The backend currently exposes full collection endpoints rather than
            // a delta /sync route, so these snapshots are the reconciliation point.
            val vehicles = api.getVehicles()
            val trips = api.getTrips(limit = 500)
            val tags = api.tags()
            dao.replaceVehicles(vehicles.map { it.toEntity() })
            dao.replaceTrips(trips.map { it.toEntity() })
            dao.replaceTags(tags.map { TagEntity(it.id, it.name) })

            Result.success()
        } catch (_: Exception) { Result.retry() }
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
