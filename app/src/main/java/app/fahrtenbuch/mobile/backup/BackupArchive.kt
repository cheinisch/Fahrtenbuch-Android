package app.fahrtenbuch.mobile.backup

import android.content.Context
import android.net.Uri
import app.fahrtenbuch.mobile.BuildConfig
import app.fahrtenbuch.mobile.data.api.SessionStore
import app.fahrtenbuch.mobile.data.local.OfflineDatabase
import app.fahrtenbuch.mobile.data.local.OfflineTripEntity
import app.fahrtenbuch.mobile.data.local.TagEntity
import app.fahrtenbuch.mobile.data.local.TrackPointEntity
import app.fahrtenbuch.mobile.data.local.TripEntity
import app.fahrtenbuch.mobile.data.local.VehicleEntity
import app.fahrtenbuch.mobile.ui.theme.ThemeMode
import app.fahrtenbuch.mobile.ui.theme.ThemePreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Lokales Backup als ZIP: alle Fahrzeuge, Fahrten (inkl. GPS-Punkte, offline erfasster
 * Fahrten und Tags) sowie die Einstellungen. Nicht enthalten sind Anmeldedaten (Tokens,
 * Geräte-ID) – nach einer Wiederherstellung meldet man sich im Server-Modus neu an.
 *
 * Aufbau: manifest.json, settings.json und je Tabelle eine JSON-Datei unter data/.
 */
object BackupArchive {
    const val FORMAT = 1
    const val MIME_TYPE = "application/zip"

    data class Summary(val vehicles: Int, val trips: Int, val points: Int, val createdAt: String?)

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private inline fun <reified T> listAdapter() =
        moshi.adapter<List<T>>(Types.newParameterizedType(List::class.java, T::class.java))

    fun fileName(): String = "fahrtenbuch-backup-${java.time.LocalDate.now()}.zip"

    suspend fun export(context: Context, target: Uri): Summary = withContext(Dispatchers.IO) {
        val dao = OfflineDatabase.get(context).dao()
        val store = SessionStore(context)
        val vehicles = dao.vehicles()
        val trips = dao.trips()
        val tags = dao.tags()
        val offlineTrips = dao.allOfflineTrips()
        val points = dao.allPoints()
        val createdAt = Instant.now().toString()

        val manifest = JSONObject()
            .put("format", FORMAT)
            .put("app", "app.fahrtenbuch.mobile")
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("createdAt", createdAt)
            .put("appMode", store.appMode.name)
            .put("counts", JSONObject()
                .put("vehicles", vehicles.size)
                .put("trips", trips.size + offlineTrips.size)
                .put("trackPoints", points.size))
        val settings = JSONObject()
            .put("session", JSONObject(store.exportSettings()))
            .put("themeMode", ThemePreferences(context).themeMode.name)

        val stream = context.contentResolver.openOutputStream(target) ?: error("Datei konnte nicht geöffnet werden")
        ZipOutputStream(stream.buffered()).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("manifest.json", manifest.toString(2))
            put("settings.json", settings.toString(2))
            put("data/vehicles.json", listAdapter<VehicleEntity>().toJson(vehicles))
            put("data/trips.json", listAdapter<TripEntity>().toJson(trips))
            put("data/tags.json", listAdapter<TagEntity>().toJson(tags))
            put("data/offline_trips.json", listAdapter<OfflineTripEntity>().toJson(offlineTrips))
            put("data/track_points.json", listAdapter<TrackPointEntity>().toJson(points))
        }
        Summary(vehicles.size, trips.size + offlineTrips.size, points.size, createdAt)
    }

    /** Liest ein Backup und ersetzt damit alle lokalen Daten und Einstellungen. */
    suspend fun restore(context: Context, source: Uri): Summary = withContext(Dispatchers.IO) {
        val files = mutableMapOf<String, String>()
        val stream = context.contentResolver.openInputStream(source) ?: error("Datei konnte nicht geöffnet werden")
        ZipInputStream(stream.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                // Nur bekannte Dateien lesen (Schutz vor beliebigen Archiven)
                if (!entry.isDirectory && entry.name in KNOWN_FILES) {
                    files[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                }
                zip.closeEntry()
            }
        }
        val manifest = files["manifest.json"]?.let(::JSONObject) ?: throw InvalidBackup()
        if (manifest.optString("app") != "app.fahrtenbuch.mobile") throw InvalidBackup()
        if (manifest.optInt("format", 0) > FORMAT) throw UnsupportedBackup()

        val vehicles = files["data/vehicles.json"]?.let { listAdapter<VehicleEntity>().fromJson(it) }.orEmpty()
        val trips = files["data/trips.json"]?.let { listAdapter<TripEntity>().fromJson(it) }.orEmpty()
        val tags = files["data/tags.json"]?.let { listAdapter<TagEntity>().fromJson(it) }.orEmpty()
        val offlineTrips = files["data/offline_trips.json"]?.let { listAdapter<OfflineTripEntity>().fromJson(it) }.orEmpty()
        val points = files["data/track_points.json"]?.let { listAdapter<TrackPointEntity>().fromJson(it) }.orEmpty()

        OfflineDatabase.get(context).dao().restoreAll(vehicles, trips, tags, offlineTrips, points)

        files["settings.json"]?.let(::JSONObject)?.let { settings ->
            settings.optJSONObject("session")?.let { session ->
                SessionStore(context).importSettings(session.keys().asSequence().associateWith { key -> session.opt(key).takeUnless { it == JSONObject.NULL } })
            }
            settings.optString("themeMode").takeIf { it.isNotBlank() }?.let { mode ->
                runCatching { ThemeMode.valueOf(mode) }.getOrNull()?.let { ThemePreferences(context).themeMode = it }
            }
        }
        Summary(vehicles.size, trips.size + offlineTrips.size, points.size, manifest.optString("createdAt").ifBlank { null })
    }

    /** Startet die App neu, damit alle Teile den wiederhergestellten Stand laden. */
    fun restartApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    class InvalidBackup : Exception("Keine Fahrtenbuch-Sicherung")
    class UnsupportedBackup : Exception("Sicherung aus einer neueren App-Version")

    private val KNOWN_FILES = setOf(
        "manifest.json", "settings.json", "data/vehicles.json", "data/trips.json",
        "data/tags.json", "data/offline_trips.json", "data/track_points.json"
    )
}
