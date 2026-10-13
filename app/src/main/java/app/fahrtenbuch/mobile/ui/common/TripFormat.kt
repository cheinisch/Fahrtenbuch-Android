package app.fahrtenbuch.mobile.ui.common

import app.fahrtenbuch.mobile.data.model.DateFormatPreference
import app.fahrtenbuch.mobile.data.model.DistanceUnit
import app.fahrtenbuch.mobile.data.model.SpeedUnit
import app.fahrtenbuch.mobile.data.model.TimeFormatPreference
import app.fahrtenbuch.mobile.data.model.UserPreferences
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

private const val KM_PER_MILE = 1.609344

/** Strecke als Zahl in der gewählten Einheit (km oder Meilen), eine Nachkommastelle. */
internal fun formatDistance(distanceKm: Double?, prefs: UserPreferences): String =
    distanceKm?.let {
        val value = if (prefs.distanceUnit == DistanceUnit.MI) it / KM_PER_MILE else it
        String.format(java.util.Locale.getDefault(), "%.1f", value)
    } ?: "–"

internal fun distanceUnitLabel(prefs: UserPreferences): String =
    if (prefs.distanceUnit == DistanceUnit.MI) "mi" else "km"

/** Strecke mit Einheit, z. B. „110,8 km“ oder „68,9 mi“. */
internal fun formatDistanceWithUnit(distanceKm: Double?, prefs: UserPreferences): String =
    "${formatDistance(distanceKm, prefs)} ${distanceUnitLabel(prefs)}"

/** Geschwindigkeit als ganze Zahl in km/h oder mph. */
internal fun formatSpeed(speedKmh: Double?, prefs: UserPreferences): String =
    speedKmh?.let {
        val value = if (prefs.speedUnit == SpeedUnit.MPH) it / KM_PER_MILE else it
        Math.round(value).toString()
    } ?: "–"

internal fun speedUnitLabel(prefs: UserPreferences): String =
    if (prefs.speedUnit == SpeedUnit.MPH) "mph" else "km/h"

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

private fun timePattern(prefs: UserPreferences): String =
    if (prefs.timeFormat == TimeFormatPreference.H12) "h:mm a" else "HH:mm"

private fun datePattern(prefs: UserPreferences): String =
    if (prefs.dateFormat == DateFormatPreference.US) "MM/dd/yyyy" else "dd.MM.yyyy"

// Uhrzeit nach Uhrzeitformat: 24h „16:12“ · 12h „4:12 PM“
internal fun formatTripTime(value: String?, prefs: UserPreferences): String =
    formatTripPattern(value, timePattern(prefs)) ?: "–"

// Datum und Uhrzeit, z. B. EU + 24h „13.10.2026, 16:12“ · US + 12h „10/13/2026, 4:12 PM“
internal fun formatTripDateTime(value: String?, prefs: UserPreferences): String? =
    formatTripPattern(value, "${datePattern(prefs)}, ${timePattern(prefs)}")

/** Festes Zahlenformat (Locale.US, damit AM/PM und Ziffern unabhängig von der Sprache stabil sind). */
private fun formatTripPattern(value: String?, pattern: String): String? {
    if (value.isNullOrBlank()) return null
    return runCatching {
        DateTimeFormatter.ofPattern(pattern, java.util.Locale.US)
            .withZone(ZoneId.systemDefault())
            .format(Instant.parse(value))
    }.getOrNull()
}

/** Lokales Datum (Gerätezeitzone) eines ISO-Zeitstempels. */
internal fun tripLocalDate(value: String?): LocalDate? =
    value?.let { runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull() }

/** Zeitspanne einer Fahrt, z. B. „16:12 – 18:25“. */
internal fun formatTripTimeRange(startedAt: String?, endedAt: String?, prefs: UserPreferences): String =
    listOfNotNull(
        formatTripTime(startedAt, prefs).takeIf { it != "–" },
        endedAt?.let { formatTripTime(it, prefs) }?.takeIf { it != "–" }
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
