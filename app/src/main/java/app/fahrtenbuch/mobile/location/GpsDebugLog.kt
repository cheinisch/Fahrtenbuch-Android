package app.fahrtenbuch.mobile.location

import android.content.Context
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

/**
 * Logdatei pro Fahrt für den GPS-Debug-Modus. Dateien liegen privat im App-Speicher
 * (filesDir/gps_debug_logs), bleiben also auf dem Gerät, bis der Nutzer sie herunterlädt.
 * Schreibzugriffe laufen seriell auf einem Hintergrund-Thread, damit der Service nicht blockiert.
 */
object GpsDebugLog {
    private const val DIR_NAME = "gps_debug_logs"
    private const val MAX_FILES = 50
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    private val writer = Executors.newSingleThreadExecutor { r ->
        Thread(r, "gps-debug-log").apply { isDaemon = true }
    }

    @Volatile
    private var current: File? = null

    @Volatile
    private var currentTripId: String? = null

    data class LogFile(val name: String, val file: File, val sizeBytes: Long, val modifiedMs: Long)

    private fun dir(context: Context): File = File(context.applicationContext.filesDir, DIR_NAME).apply { mkdirs() }

    /**
     * Öffnet die Logdatei für die Fahrt [tripId]. Ist sie schon offen, passiert nichts; gibt es schon
     * eine Datei dieser Fahrt (z. B. Fortsetzung nach Pause), wird dort weitergeschrieben.
     * @return true, wenn eine neue Datei angelegt wurde.
     */
    @Synchronized
    fun open(context: Context, tripId: String): Boolean {
        if (currentTripId == tripId && current != null) return false
        val dir = dir(context)
        val suffix = "_${tripId.take(8)}.log"
        val existing = dir.listFiles { f -> f.isFile && f.name.endsWith(suffix) }?.maxByOrNull { it.name }
        val file = existing ?: File(dir, "trip_${stamp.format(LocalDateTime.now())}$suffix")
        current = file
        currentTripId = tripId
        writer.execute {
            if (existing == null) prune(dir, keep = MAX_FILES - 1)
            file.appendText(
                if (existing == null) "# Fahrt $tripId gestartet ${Instant.now()}\n"
                else "# Fahrt $tripId fortgesetzt ${Instant.now()}\n"
            )
        }
        return existing == null
    }

    /** Schreibt mehrere Zeilen am Stück (z. B. die Diagnose beim Fahrtstart). */
    fun logBlock(lines: List<String>) {
        val file = current ?: return
        val text = lines.joinToString(separator = "\n", postfix = "\n")
        writer.execute { runCatching { file.appendText(text) } }
    }

    /** Hängt eine Zeile an die aktuelle Logdatei an. Ohne offene Datei passiert nichts. */
    fun log(message: String) {
        val file = current ?: return
        val line = "${Instant.now()} $message\n"
        writer.execute { runCatching { file.appendText(line) } }
    }

    /** Schließt die aktuelle Logdatei mit einer Endmarke. */
    @Synchronized
    fun close() {
        val file = current ?: return
        current = null
        currentTripId = null
        writer.execute { runCatching { file.appendText("# Ende ${Instant.now()}\n") } }
    }

    fun list(context: Context): List<LogFile> =
        dir(context).listFiles { f -> f.isFile && f.name.endsWith(".log") }
            ?.map { LogFile(it.name, it, it.length(), it.lastModified()) }
            ?.sortedByDescending { it.name }
            ?: emptyList()

    fun delete(file: File) {
        writer.execute { runCatching { file.delete() } }
    }

    private fun prune(dir: File, keep: Int) {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".log") }?.sortedBy { it.name } ?: return
        if (files.size > keep) files.take(files.size - keep).forEach { it.delete() }
    }
}
