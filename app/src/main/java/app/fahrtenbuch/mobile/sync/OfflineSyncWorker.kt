package app.fahrtenbuch.mobile.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.fahrtenbuch.mobile.data.api.ApiFactory
import app.fahrtenbuch.mobile.data.api.SessionStore
import app.fahrtenbuch.mobile.data.local.OfflineDatabase
import app.fahrtenbuch.mobile.data.model.*

class OfflineSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = SessionStore(applicationContext)
        if (!store.serverConfigured || store.refreshToken == null) return Result.success()
        val api = ApiFactory.create(store)
        val dao = OfflineDatabase.get(applicationContext).dao()
        return try {
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
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }
}

private fun app.fahrtenbuch.mobile.data.local.TrackPointEntity.toApi() = TrackPoint(
    lat = lat, lon = lon, altitude = altitude, accuracy = accuracy, speed = speed,
    bearing = bearing, recordedAt = recordedAt
)
