package app.fahrtenbuch.mobile.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import app.fahrtenbuch.mobile.data.api.ApiFactory
import app.fahrtenbuch.mobile.data.api.FahrtenbuchApi
import app.fahrtenbuch.mobile.data.local.OfflineTripEntity
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
                        dao.setTripSyncError(local.localId, applicationContext.getString(app.fahrtenbuch.mobile.R.string.sync_error_vehicle_missing))
                        failedTrips++
                        return@forEach
                    }
                dao.setTripSyncError(local.localId, null)
                syncTrip(api, dao, local)
                } catch (error: HttpException) {
                    val body = errorBody(error)
                    Log.e(TAG, "Trip failed: localId=${local.localId} HTTP ${error.code()} body=${body ?: "<empty>"}; retaining data", error)
                    val json = runCatching { org.json.JSONObject(body ?: "{}") }.getOrNull()
                    val detail = when (json?.optString("error")) {
                        // Eine andere Fahrt läuft auf dem Server noch (z. B. im Web gestartet und nicht beendet)
                        "TRACKING_ALREADY_ACTIVE" -> applicationContext.getString(app.fahrtenbuch.mobile.R.string.sync_error_other_recording)
                        else -> json?.optString("message").orEmpty()
                    }
                    dao.setTripSyncError(local.localId, "HTTP ${error.code()}: ${detail.ifBlank { applicationContext.getString(app.fahrtenbuch.mobile.R.string.sync_error_generic) }}")
                    failedTrips++
                } catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    Log.e(TAG, "Trip failed: localId=${local.localId}; retaining data", error)
                    dao.setTripSyncError(local.localId, error.message ?: applicationContext.getString(app.fahrtenbuch.mobile.R.string.sync_error_generic))
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
            dao.replaceTags(tags.map { TagEntity(it.id, it.name, color = it.color) })

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

    /** Überträgt eine lokale Fahrt: Start (falls nötig), offene GPS-Punkte und – wenn beendet – Stop. */
    private suspend fun syncTrip(
        api: FahrtenbuchApi,
        dao: app.fahrtenbuch.mobile.data.local.OfflineDao,
        local: OfflineTripEntity,
        resolveConflicts: Boolean = true
    ) {
        var remoteId = local.remoteId
        Log.i(
            TAG,
            "Sync trip: localId=${local.localId} remoteId=${local.remoteId ?: "-"} " +
                "vehicleId=${local.vehicleId} startedAt=${local.startedAt} endedAt=${local.endedAt ?: "-"} " +
                "state=${local.state} pendingStop=${local.pendingStop}"
        )
        if (remoteId == null) {
            Log.i(TAG, "POST tracking/start: localId=${local.localId} vehicleId=${local.vehicleId} startedAt=${local.startedAt}")
            remoteId = startOnServer(api, dao, local, resolveConflicts = resolveConflicts)
            Log.i(TAG, "tracking/start OK: localId=${local.localId} remoteId=$remoteId")
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
            // Lokal vergebene Tags per Name den Server-Tags zuordnen (fehlende anlegen)
            val localTags = parseTagsJson(local.tagsJson)
            if (localTags.isNotEmpty()) {
                val serverTags = api.tags().toMutableList()
                val ids = localTags.map { tag ->
                    (serverTags.firstOrNull { it.name.equals(tag.name, ignoreCase = true) }
                        ?: api.createTag(TagRequest(tag.name, tag.color)).also(serverTags::add)).id
                }
                api.setTripTags(remoteId, TripTagsRequest(ids.distinct()))
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
    }

    /**
     * POST tracking/start mit der Geräte-Fahrt-ID (localId). Läuft auf dem Server noch eine andere
     * Aufzeichnung (409), wird sie – wenn es eine eigene ist – aufgelöst und der Start einmal wiederholt.
     */
    private suspend fun startOnServer(
        api: FahrtenbuchApi,
        dao: app.fahrtenbuch.mobile.data.local.OfflineDao,
        local: OfflineTripEntity,
        resolveConflicts: Boolean
    ): String {
        val request = StartTrackingRequest(local.vehicleId, local.startedAt, local.startTimeZone, clientTripId = local.localId)
        return try {
            api.startTracking(request).id
        } catch (error: HttpException) {
            val conflict = activeTripConflict(error) ?: throw error
            // Ältere Server ohne clientTripId: dieselbe Fahrt an Fahrzeug + Startzeit erkennen
            if (conflict.clientTripId == local.localId || isSameTrip(api, conflict, local)) {
                Log.i(TAG, "Adopting active server trip: localId=${local.localId} remoteId=${conflict.tripId}")
                return conflict.tripId
            }
            if (!resolveConflicts || !resolveOwnActiveTrip(api, dao, conflict)) throw error
            api.startTracking(request).id
        }
    }

    /**
     * Die laufende Server-Aufzeichnung stammt von diesem Gerät, ist hier aber nicht mehr aktiv:
     * - lokal beendet → zu Ende übertragen (Punkte, Stop) bzw. nur noch beenden, falls schon synchron,
     * - lokal nicht mehr vorhanden (verworfen) → auf dem Server löschen (landet im Archiv).
     * Fremde Aufzeichnungen (Web, anderes Gerät) bleiben unangetastet.
     */
    private suspend fun resolveOwnActiveTrip(
        api: FahrtenbuchApi,
        dao: app.fahrtenbuch.mobile.data.local.OfflineDao,
        conflict: ActiveTripConflict
    ): Boolean {
        val clientTripId = conflict.clientTripId ?: return false
        val other = dao.trip(clientTripId)
        if (other == null) {
            if (!conflict.sameDevice) return false
            Log.w(TAG, "Discarding server trip of this device that no longer exists locally: remoteId=${conflict.tripId} clientTripId=$clientTripId")
            val response = api.archiveTrip(conflict.tripId)
            return response.isSuccessful || response.code() == 404
        }
        if (other.endedAt == null) {
            // Läuft auf dem Gerät noch – nichts zu reparieren
            Log.w(TAG, "Server trip is still recording locally: localId=${other.localId}")
            return false
        }
        if (other.remoteId == null) dao.setRemoteId(other.localId, conflict.tripId)
        if (other.state == "SYNCED") {
            Log.w(TAG, "Stopping server trip that was already finished locally: localId=${other.localId} remoteId=${conflict.tripId}")
            api.stopTracking(StopTrackingRequest(conflict.tripId, other.endedAt, other.endTimeZone ?: other.startTimeZone))
        } else {
            Log.i(TAG, "Finishing own server trip first: localId=${other.localId} remoteId=${conflict.tripId}")
            val fresh = dao.trip(other.localId) ?: return false
            syncTrip(api, dao, fresh.copy(pendingStop = true), resolveConflicts = false)
        }
        return true
    }
}

// Der Fehler-Body lässt sich nur einmal lesen; hier zwischengespeichert für Auswertung und Log
private val errorBodies = java.util.WeakHashMap<HttpException, String?>()

private fun errorBody(error: HttpException): String? = synchronized(errorBodies) {
    errorBodies.getOrPut(error) { runCatching { error.response()?.errorBody()?.string() }.getOrNull() }
}

/** 409 TRACKING_ALREADY_ACTIVE: Angaben zur laufenden Server-Aufzeichnung (neuere Server liefern mehr). */
private data class ActiveTripConflict(
    val tripId: String,
    val clientTripId: String?,
    val sameDevice: Boolean,
    val vehicleId: String?,
    val startedAt: String?
)

private fun activeTripConflict(error: HttpException): ActiveTripConflict? {
    if (error.code() != 409) return null
    val json = runCatching { org.json.JSONObject(errorBody(error) ?: "{}") }.getOrNull() ?: return null
    if (json.optString("error") != "TRACKING_ALREADY_ACTIVE") return null
    val details = json.optJSONObject("details") ?: return null
    fun text(name: String) = details.optString(name).takeIf { it.isNotBlank() && it != "null" }
    return ActiveTripConflict(
        tripId = text("tripId") ?: return null,
        clientTripId = text("clientTripId"),
        sameDevice = details.optBoolean("sameDevice", false),
        vehicleId = text("vehicleId"),
        startedAt = text("startedAt")
    )
}

/**
 * Gleiche Fahrt (Fahrzeug + Startzeit)? Für Server-Fahrten ohne clientTripId, die von dieser
 * Fahrt stammen, deren Start-Antwort aber verloren ging.
 */
private suspend fun isSameTrip(
    api: app.fahrtenbuch.mobile.data.api.FahrtenbuchApi,
    conflict: ActiveTripConflict,
    local: app.fahrtenbuch.mobile.data.local.OfflineTripEntity
): Boolean {
    if (conflict.clientTripId != null) return false
    val (vehicleId, startedAt) = if (conflict.vehicleId != null && conflict.startedAt != null) {
        conflict.vehicleId to conflict.startedAt
    } else {
        val active = runCatching { api.getTrip(conflict.tripId) }.getOrNull() ?: return false
        active.vehicleId to active.startedAt
    }
    val sameStart = runCatching {
        kotlin.math.abs(java.time.Duration.between(java.time.Instant.parse(startedAt), java.time.Instant.parse(local.startedAt)).toMillis()) < 1000
    }.getOrDefault(false)
    if (vehicleId != local.vehicleId || !sameStart) {
        Log.w(
            OfflineSyncWorker.TAG,
            "Other trip recording on server: remoteId=${conflict.tripId} vehicleId=$vehicleId startedAt=$startedAt " +
                "sameDevice=${conflict.sameDevice}; local vehicleId=${local.vehicleId} startedAt=${local.startedAt}"
        )
        return false
    }
    return true
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
    tagsJson = tags.toTagsJson(),
    specialCategory = specialCategory,
    startTimeZone = startTimeZone,
    endTimeZone = endTimeZone
)
