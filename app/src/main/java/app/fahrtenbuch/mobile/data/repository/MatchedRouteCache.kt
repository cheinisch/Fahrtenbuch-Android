package app.fahrtenbuch.mobile.data.repository

import android.util.Log
import app.fahrtenbuch.mobile.data.api.FahrtenbuchApi
import app.fahrtenbuch.mobile.data.api.SessionStore
import app.fahrtenbuch.mobile.data.local.MatchedRouteEntity
import app.fahrtenbuch.mobile.data.local.OfflineDao
import app.fahrtenbuch.mobile.data.local.TripEntity
import app.fahrtenbuch.mobile.data.model.TrackPoint
import app.fahrtenbuch.mobile.data.model.TripMapMatching
import app.fahrtenbuch.mobile.data.model.UserSettingsDto
import kotlin.math.roundToLong

/** Map-Matching-Felder einer Server-Fahrt in die lokale Zeile übernehmen. */
internal fun TripEntity.withMapMatching(matching: TripMapMatching?): TripEntity = copy(
    mapMatchingStatus = matching?.status,
    mapMatchingProvider = matching?.provider,
    mapMatchingMatchedAt = matching?.matchedAt,
    mapMatchingRawDistanceMeters = matching?.rawDistanceMeters,
    mapMatchingMatchedDistanceMeters = matching?.matchedDistanceMeters,
    mapMatchingMessage = matching?.message
)

/** Map-Matching aus der lokalen Zeile (null = nicht gematcht). */
internal fun TripEntity.mapMatchingModel(): TripMapMatching? = mapMatchingStatus?.let {
    TripMapMatching(
        status = it,
        provider = mapMatchingProvider,
        matchedAt = mapMatchingMatchedAt,
        rawDistanceMeters = mapMatchingRawDistanceMeters,
        matchedDistanceMeters = mapMatchingMatchedDistanceMeters,
        message = mapMatchingMessage
    )
}

/** Map-Matching-Einstellungen vom Server merken (auch aus dem Sync heraus). */
internal fun SessionStore.cacheMapMatchingSettings(settings: UserSettingsDto) {
    (settings.mapMatchingEnabled ?: settings.autoMapMatchingEnabled)?.let { mapMatchingEnabled = it }
    settings.mapMatchingAvailable?.let { mapMatchingAvailable = it }
}

/**
 * Lokaler Speicher für Straßenrouten gematchter Fahrten. Die Route wird beim Sync
 * und beim Öffnen einer Fahrt geladen und steht danach auch offline zur Verfügung.
 */
internal object MatchedRouteCache {
    private const val TAG = "FahrtenbuchSync"

    /** Mehr Punkte braucht die Karte nicht; hält die Datenbankzeile klein (CursorWindow ≈ 2 MB). */
    private const val MAX_POINTS = 20_000

    /** Höchstens so viele Routen pro Sync nachladen, damit ein Lauf kurz bleibt. */
    private const val MAX_PER_SYNC = 30

    /** Gespeicherte Route, sofern sie zum Stand [matchedAt] des Servers passt (null = unbekannt, jede passt). */
    suspend fun read(dao: OfflineDao, tripId: String, matchedAt: String?, acceptStale: Boolean = false): List<TrackPoint>? {
        val stored = dao.matchedRoute(tripId) ?: return null
        if (!acceptStale && matchedAt != null && stored.matchedAt != matchedAt) return null
        return decode(stored.polyline).map { (lat, lon) -> TrackPoint(tripId = tripId, lat = lat, lon = lon, recordedAt = "") }
            .takeIf { it.size >= 2 }
    }

    /**
     * Route vom Server holen und speichern. Leer, wenn die Fahrt nicht (mehr) gematcht
     * ist oder der Server den Endpunkt nicht kennt (404); andere Fehler werden geworfen.
     */
    suspend fun fetch(api: FahrtenbuchApi, dao: OfflineDao, tripId: String): List<TrackPoint> {
        val route = try {
            api.tripMatchedRoute(tripId)
        } catch (error: retrofit2.HttpException) {
            if (error.code() == 404) return emptyList() else throw error
        }
        if (route.status != "applied") return emptyList()
        val coordinates = route.coordinates.filter { it.size >= 2 }.map { it[0] to it[1] }
        if (coordinates.size < 2) return emptyList()
        val sampled = sample(coordinates, MAX_POINTS)
        dao.putMatchedRoute(MatchedRouteEntity(tripId, route.matchedAt, encode(sampled), sampled.size))
        return sampled.map { (lat, lon) -> TrackPoint(tripId = tripId, lat = lat, lon = lon, recordedAt = "") }
    }

    /**
     * Beim Sync: Routen gematchter Fahrten laden, die lokal fehlen oder veraltet sind
     * (neuere zuerst), und Routen nicht mehr gematchter Fahrten entfernen.
     */
    suspend fun syncRoutes(api: FahrtenbuchApi, dao: OfflineDao, store: SessionStore) {
        dao.pruneMatchedRoutes()
        if (!store.mapMatchingEnabled || !store.mapMatchingAvailable) return
        val known = dao.matchedRouteVersions().associate { it.tripId to it.matchedAt }
        val missing = dao.trips()
            .filter { it.mapMatchingStatus == "applied" }
            .filter { trip -> !known.containsKey(trip.id) || known[trip.id] != trip.mapMatchingMatchedAt }
            .take(MAX_PER_SYNC)
        var loaded = 0
        for (trip in missing) {
            try {
                if (fetch(api, dao, trip.id).isNotEmpty()) loaded++
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                // Netz weg o. Ä.: Rest beim nächsten Sync
                Log.w(TAG, "Matched route not loaded: tripId=${trip.id}", error)
                break
            }
        }
        Log.i(TAG, "Matched routes: loaded=$loaded pending=${missing.size - loaded}")
    }

    private fun sample(points: List<Pair<Double, Double>>, maximum: Int): List<Pair<Double, Double>> {
        if (points.size <= maximum) return points
        val step = (points.size - 1).toDouble() / (maximum - 1)
        return List(maximum) { points[(it * step).roundToLong().toInt().coerceAtMost(points.size - 1)] }
    }

    /** Google-Polyline mit Präzision 1e6 (wie Valhalla). */
    fun encode(points: List<Pair<Double, Double>>): String {
        val out = StringBuilder()
        var previousLat = 0L
        var previousLon = 0L
        fun write(value: Long) {
            var v = if (value < 0) (value shl 1).inv() else value shl 1
            while (v >= 0x20) {
                out.append(((0x20L or (v and 0x1f)) + 63).toInt().toChar())
                v = v shr 5
            }
            out.append((v + 63).toInt().toChar())
        }
        for ((lat, lon) in points) {
            val la = (lat * 1e6).roundToLong()
            val lo = (lon * 1e6).roundToLong()
            write(la - previousLat)
            write(lo - previousLon)
            previousLat = la
            previousLon = lo
        }
        return out.toString()
    }

    fun decode(encoded: String): List<Pair<Double, Double>> {
        val result = ArrayList<Pair<Double, Double>>()
        var index = 0
        var lat = 0L
        var lon = 0L
        fun read(): Long {
            var shift = 0
            var value = 0L
            var byte: Int
            do {
                byte = encoded[index++].code - 63
                value = value or ((byte and 0x1f).toLong() shl shift)
                shift += 5
            } while (byte >= 0x20)
            return if ((value and 1L) != 0L) (value shr 1).inv() else value shr 1
        }
        while (index < encoded.length) {
            lat += read()
            lon += read()
            result.add(lat / 1e6 to lon / 1e6)
        }
        return result
    }
}
