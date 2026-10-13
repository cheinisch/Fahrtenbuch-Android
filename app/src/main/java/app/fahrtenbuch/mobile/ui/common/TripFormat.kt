package app.fahrtenbuch.mobile.ui.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Zerlegt eine Adresse wie „TÜV Hessen, Am Römerhof 15, 60486 Frankfurt am Main, Hessen, Deutschland“. */
internal data class ParsedAddress(val name: String, val detail: String?, val city: String?)

private val postalCodePattern = Regex("^\\d{4,5}\\s+(.+)$")

internal fun parseAddress(raw: String?): ParsedAddress? {
    val parts = raw?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    if (parts.isEmpty()) return null
    val postalIndex = parts.indexOfFirst { postalCodePattern.matches(it) }
    if (postalIndex < 0) {
        return ParsedAddress(parts.first(), parts.drop(1).take(2).joinToString(", ").ifBlank { null }, null)
    }
    val postalPart = parts[postalIndex]
    val city = postalCodePattern.find(postalPart)?.groupValues?.get(1)
    return when (postalIndex) {
        0 -> ParsedAddress(postalPart, null, city)
        1 -> ParsedAddress(parts[0], postalPart, city)
        else -> ParsedAddress(
            name = parts.subList(0, postalIndex - 1).joinToString(", "),
            detail = "${parts[postalIndex - 1]}, $postalPart",
            city = city
        )
    }
}

internal fun formatDistance(distanceKm: Double?): String =
    distanceKm?.let { String.format(java.util.Locale.getDefault(), "%.1f", it) } ?: "–"

/** Liefert Wert und Einheit, z. B. „2:12“ + „h“ oder „45“ + „min“. */
internal fun splitDuration(seconds: Int?): Pair<String, String> {
    if (seconds == null) return "–" to ""
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}" to "h" else "$minutes" to "min"
}

/** Formatiert einen ISO-Zeitstempel im Format der aktuellen Sprache und Zeitzone. */
private fun formatTripInstant(value: String?, formatter: DateTimeFormatter): String? {
    if (value.isNullOrBlank()) return null
    return runCatching {
        formatter
            .withLocale(java.util.Locale.getDefault())
            .withZone(java.time.ZoneId.systemDefault())
            .format(java.time.Instant.parse(value))
    }.getOrNull()
}

// de: „Dienstag, 13. Oktober 2026“ · en: „Tuesday, October 13, 2026“
internal fun formatTripDate(value: String?): String? =
    formatTripInstant(value, DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))

// de: „16:12“ · en: „4:12 PM“
internal fun formatTripTime(value: String?): String =
    formatTripInstant(value, DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)) ?: "–"

// de: „13.10.2026, 16:12“ · en: „Oct 13, 2026, 4:12 PM“
internal fun formatTripDateTime(value: String?): String? =
    formatTripInstant(value, DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))

/** Lokales Datum (Gerätezeitzone) eines ISO-Zeitstempels. */
internal fun tripLocalDate(value: String?): LocalDate? =
    value?.let { runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull() }

/** Zeitspanne einer Fahrt, z. B. „16:12 – 18:25“. */
internal fun formatTripTimeRange(startedAt: String?, endedAt: String?): String =
    listOfNotNull(
        formatTripTime(startedAt).takeIf { it != "–" },
        endedAt?.let { formatTripTime(it) }?.takeIf { it != "–" }
    ).joinToString(" – ").ifBlank { "–" }

/** Kurze Route aus Ortsnamen, z. B. „Frankfurt am Main → Stephanshausen“. */
internal fun shortRoute(startAddress: String?, endAddress: String?, startFallback: String, endFallback: String): String {
    fun label(raw: String?, fallback: String): String {
        val parsed = parseAddress(raw) ?: return fallback
        return parsed.city ?: parsed.name
    }
    return "${label(startAddress, startFallback)} → ${label(endAddress, endFallback)}"
}

/** Datum nach Vorlage der aktuellen Sprache, z. B. Skeleton „EEEEdMMMM“ → „Dienstag, 13. Oktober“. */
internal fun formatLocalDate(date: LocalDate, skeleton: String): String {
    val locale = Locale.getDefault()
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
    return DateTimeFormatter.ofPattern(pattern, locale).format(date)
}
