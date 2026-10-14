package app.fahrtenbuch.mobile.data.export

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import app.fahrtenbuch.mobile.BuildConfig
import app.fahrtenbuch.mobile.R
import app.fahrtenbuch.mobile.data.model.DateFormatPreference
import app.fahrtenbuch.mobile.data.model.DistanceUnit
import app.fahrtenbuch.mobile.data.model.ExportFormat
import app.fahrtenbuch.mobile.data.model.ExportRequest
import app.fahrtenbuch.mobile.data.model.ExportResult
import app.fahrtenbuch.mobile.data.model.SpecialCategory
import app.fahrtenbuch.mobile.data.model.TimeFormatPreference
import app.fahrtenbuch.mobile.data.model.Trip
import app.fahrtenbuch.mobile.data.model.TripType
import app.fahrtenbuch.mobile.data.model.UserPreferences
import app.fahrtenbuch.mobile.data.model.Vehicle
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Erzeugt den Export auf dem Gerät, wenn kein Server genutzt wird oder er nicht erreichbar
 * ist: CSV (Semikolon, UTF-8 mit BOM – öffnet direkt in Excel/LibreOffice) und ein
 * A4-PDF mit Zusammenfassung und Fahrtenliste (android.graphics.pdf, keine Bibliothek).
 * Texte kommen aus den String-Ressourcen, Einheiten und Formate aus den Einstellungen.
 */
object LocalExport {

    private const val KM_PER_MILE = 1.609344

    fun create(
        context: Context,
        request: ExportRequest,
        allTrips: List<Trip>,
        vehicles: List<Vehicle>,
        prefs: UserPreferences
    ): ExportResult {
        val from = LocalDate.parse(request.from)
        val to = LocalDate.parse(request.to)
        val trips = allTrips
            .filter { it.status != "recording" }
            .filter { trip ->
                val day = localDate(trip.startedAt, tripPrefs(prefs, trip.startTimeZone)) ?: return@filter false
                !day.isBefore(from) && !day.isAfter(to) &&
                    (request.vehicleId == null || trip.vehicleId == request.vehicleId) &&
                    (request.tripType == null || trip.tripType == request.tripType) &&
                    (request.specialCategory == null || trip.specialCategory == request.specialCategory.apiValue)
            }
            .sortedBy { it.startedAt }
        val formatter = Formatter(context, prefs, vehicles)
        val bytes = when (request.format) {
            ExportFormat.CSV -> csv(trips, formatter)
            ExportFormat.PDF -> pdf(context, request, trips, formatter)
        }
        return ExportResult(
            bytes = bytes,
            fileName = "fahrtenbuch_${request.from}_${request.to}.${request.format.extension}",
            mimeType = request.format.mimeType,
            generatedOnDevice = true
        )
    }

    // ---------------------------------------------------------------- CSV

    private fun csv(trips: List<Trip>, f: Formatter): ByteArray {
        val header = listOf(
            f.str(R.string.export_col_date), f.str(R.string.export_col_start_time), f.str(R.string.export_col_end_time),
            f.str(R.string.format_time_zone),
            f.str(R.string.export_col_start_address), f.str(R.string.export_col_end_address),
            f.str(R.string.vehicle), f.str(R.string.category), f.str(R.string.special_category),
            f.str(R.string.trip_detail_purpose), f.str(R.string.trip_detail_contact), f.str(R.string.trip_detail_note),
            "${f.str(R.string.trip_detail_distance)} (${f.unit})"
        )
        val rows = trips.map { trip ->
            listOf(
                f.date(trip.startedAt, trip.startTimeZone), f.time(trip.startedAt, trip.startTimeZone), f.time(trip.endedAt, trip.endTimeZone ?: trip.startTimeZone),
                trip.startTimeZone ?: f.prefs.zoneId().id,
                trip.startAddress.orEmpty(), trip.endAddress.orEmpty(),
                f.vehicle(trip), f.type(trip.tripType), f.special(trip.specialCategory),
                trip.purpose.orEmpty(), trip.contact.orEmpty(), trip.note.orEmpty(),
                f.distanceNumber(trip.distanceKm)
            )
        }
        val text = (listOf(header) + rows).joinToString("\r\n") { row -> row.joinToString(";") { csvField(it) } }
        // BOM, damit Excel Umlaute korrekt erkennt
        return byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray(Charsets.UTF_8)
    }

    private fun csvField(value: String): String =
        if (value.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

    // ---------------------------------------------------------------- PDF

    private const val PAGE_WIDTH = 595  // A4 in PostScript-Punkten
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f

    private fun pdf(context: Context, request: ExportRequest, trips: List<Trip>, f: Formatter): ByteArray {
        val document = PdfDocument()
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.BLACK }
        val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = Color.DKGRAY }
        val line = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.6f }

        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        fun finishPage() {
            page?.let { current ->
                current.canvas.drawText(
                    f.str(R.string.export_pdf_footer, BuildConfig.VERSION_NAME, f.dateOf(LocalDate.now(f.prefs.zoneId())), pageNumber),
                    MARGIN, PAGE_HEIGHT - 20f, muted
                )
                document.finishPage(current)
            }
        }

        fun newPage() {
            finishPage()
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            y = MARGIN
        }

        fun ensureSpace(height: Float) {
            if (page == null || y + height > PAGE_HEIGHT - 40f) newPage()
        }

        // Kopf
        newPage()
        val canvas = { page!!.canvas }
        canvas().drawText(f.str(R.string.export_pdf_title), MARGIN, y + 18f, title)
        y += 34f
        canvas().drawText(
            f.str(R.string.export_pdf_period, f.dateOf(LocalDate.parse(request.from)), f.dateOf(LocalDate.parse(request.to))),
            MARGIN, y, body
        )
        y += 13f
        val filters = listOfNotNull(
            request.vehicleId?.let { id -> f.vehicles.firstOrNull { it.id == id }?.name },
            request.tripType?.let { f.type(it) },
            request.specialCategory?.let { f.special(it.apiValue) }
        )
        if (filters.isNotEmpty()) {
            canvas().drawText(f.str(R.string.export_pdf_filters, filters.joinToString(", ")), MARGIN, y, body)
            y += 13f
        }
        canvas().drawText(f.str(R.string.export_pdf_generated_on_device), MARGIN, y, muted)
        y += 22f

        // Zusammenfassung
        val totalKm = trips.sumOf { it.distanceKm ?: 0.0 }
        canvas().drawText(f.str(R.string.export_pdf_summary), MARGIN, y, heading)
        y += 16f
        val summaryRows = buildList {
            add(f.str(R.string.statistics_total_distance) to f.distance(totalKm))
            add(f.str(R.string.trips) to trips.size.toString())
            TripType.entries.forEach { type ->
                val km = trips.filter { it.tripType == type }.sumOf { it.distanceKm ?: 0.0 }
                if (km > 0) add(f.type(type) to f.distance(km))
            }
            SpecialCategory.entries.forEach { category ->
                val km = trips.filter { it.specialCategory == category.apiValue }.sumOf { it.distanceKm ?: 0.0 }
                if (km > 0) add("${f.str(R.string.special_category)}: ${f.special(category.apiValue)}" to f.distance(km))
            }
        }
        summaryRows.forEach { (label, value) ->
            canvas().drawText(label, MARGIN, y, body)
            canvas().drawText(value, MARGIN + 260f, y, body)
            y += 13f
        }
        y += 14f

        // Fahrtenliste
        val columns = listOf(
            f.str(R.string.export_col_date) to 0f,
            f.str(R.string.export_col_time) to 62f,
            f.str(R.string.export_col_route) to 130f,
            f.str(R.string.category) to 360f,
            f.unit to 470f
        )
        val tableWidth = PAGE_WIDTH - 2 * MARGIN

        fun header() {
            columns.forEach { (label, x) -> canvas().drawText(label, MARGIN + x, y, heading) }
            y += 5f
            canvas().drawLine(MARGIN, y, MARGIN + tableWidth, y, line)
            y += 12f
        }

        ensureSpace(40f)
        canvas().drawText(f.str(R.string.export_pdf_trips), MARGIN, y, heading)
        y += 16f
        header()

        if (trips.isEmpty()) {
            canvas().drawText(f.str(R.string.statistics_empty), MARGIN, y, body)
            y += 13f
        }
        trips.forEach { trip ->
            val purpose = listOfNotNull(trip.purpose?.takeIf { it.isNotBlank() }, trip.contact?.takeIf { it.isNotBlank() }).joinToString(" · ")
            val rowHeight = if (purpose.isNotEmpty()) 24f else 14f
            if (page == null || y + rowHeight > PAGE_HEIGHT - 40f) {
                newPage()
                header()
            }
            val route = "${f.place(trip.startAddress)} → ${f.place(trip.endAddress)}"
            val category = listOfNotNull(f.type(trip.tripType), trip.specialCategory?.let { f.special(it) }).joinToString(" / ")
            canvas().drawText(f.date(trip.startedAt, trip.startTimeZone), MARGIN + columns[0].second, y, body)
            canvas().drawText("${f.time(trip.startedAt, trip.startTimeZone)}–${f.time(trip.endedAt, trip.endTimeZone ?: trip.startTimeZone)}", MARGIN + columns[1].second, y, body)
            canvas().drawText(ellipsize(route, body, 225f), MARGIN + columns[2].second, y, body)
            canvas().drawText(ellipsize(category, body, 105f), MARGIN + columns[3].second, y, body)
            canvas().drawText(f.distanceNumber(trip.distanceKm), MARGIN + columns[4].second, y, body)
            if (purpose.isNotEmpty()) {
                canvas().drawText(ellipsize(purpose, muted, 335f), MARGIN + columns[2].second, y + 10f, muted)
            }
            y += rowHeight - 4f
            canvas().drawLine(MARGIN, y, MARGIN + tableWidth, y, line)
            y += 11f
        }

        finishPage()
        return ByteArrayOutputStream().use { out ->
            document.writeTo(out)
            document.close()
            out.toByteArray()
        }
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text, 0, end) + paint.measureText("…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }

    private fun tripPrefs(prefs: UserPreferences, zone: String?): UserPreferences =
        if (zone.isNullOrBlank()) prefs else prefs.copy(timeZone = zone)

    private fun localDate(value: String?, prefs: UserPreferences): LocalDate? =
        parseInstant(value)?.atZone(prefs.zoneId())?.toLocalDate()

    private fun parseInstant(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value) }.getOrNull()
            ?: runCatching { java.time.OffsetDateTime.parse(value).toInstant() }.getOrNull()
            ?: runCatching { java.time.LocalDateTime.parse(value.replace(' ', 'T')).toInstant(java.time.ZoneOffset.UTC) }.getOrNull()
    }

    /** Formatierung nach Benutzereinstellungen, unabhängig von Compose. */
    private class Formatter(private val context: Context, val prefs: UserPreferences, val vehicles: List<Vehicle>) {
        val unit = if (prefs.distanceUnit == DistanceUnit.MI) "mi" else "km"
        private val dateFormatter = DateTimeFormatter.ofPattern(if (prefs.dateFormat == DateFormatPreference.US) "MM/dd/yyyy" else "dd.MM.yyyy", Locale.US)
        private val timeFormatter = DateTimeFormatter.ofPattern(if (prefs.timeFormat == TimeFormatPreference.H12) "h:mm a" else "HH:mm", Locale.US)
        private val defaultZone = prefs.zoneId()

        private fun zoneOf(id: String?): ZoneId =
            id?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: defaultZone

        fun str(id: Int, vararg args: Any): String = context.getString(id, *args)
        fun dateOf(date: LocalDate): String = dateFormatter.format(date)
        /** Datum/Uhrzeit in der Ortszeit der Fahrt (zone = startTimeZone bzw. endTimeZone). */
        fun date(value: String?, zone: String? = null): String = parseInstant(value)?.let { dateFormatter.format(it.atZone(zoneOf(zone))) }.orEmpty()
        fun time(value: String?, zone: String? = null): String = parseInstant(value)?.let { timeFormatter.format(it.atZone(zoneOf(zone))) }.orEmpty()
        fun vehicle(trip: Trip): String = vehicles.firstOrNull { it.id == trip.vehicleId }?.name ?: trip.vehicleName.orEmpty()

        fun distanceNumber(km: Double?): String {
            val value = (km ?: 0.0).let { if (prefs.distanceUnit == DistanceUnit.MI) it / KM_PER_MILE else it }
            return String.format(Locale.getDefault(), "%.1f", value)
        }

        fun distance(km: Double): String = "${distanceNumber(km)} $unit"

        /** Kurzform für das PDF: Ortsname (PLZ Ort → Ort), sonst der erste Adressteil. */
        fun place(address: String?): String {
            val parts = address?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
            if (parts.isEmpty()) return "–"
            return parts.firstNotNullOfOrNull { Regex("^\\d{4,5}\\s+(.+)$").find(it)?.groupValues?.get(1) } ?: parts.first()
        }

        fun type(type: TripType): String = str(
            when (type) {
                TripType.BUSINESS -> R.string.trip_business
                TripType.PRIVATE -> R.string.trip_private
                TripType.COMMUTE -> R.string.trip_commute
                TripType.UNCLASSIFIED -> R.string.trip_unclassified
            }
        )

        fun special(value: String?): String = when (SpecialCategory.fromApi(value)) {
            SpecialCategory.MEDICAL -> str(R.string.special_medical)
            SpecialCategory.VOLUNTEER -> str(R.string.special_volunteer)
            SpecialCategory.EDUCATION -> str(R.string.special_education)
            SpecialCategory.APPLICATION -> str(R.string.special_application)
            SpecialCategory.MOVING -> str(R.string.special_moving)
            SpecialCategory.OTHER -> str(R.string.special_other)
            null -> ""
        }
    }
}
