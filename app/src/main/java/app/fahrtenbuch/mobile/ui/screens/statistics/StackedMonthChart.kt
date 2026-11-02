package app.fahrtenbuch.mobile.ui.screens.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fahrtenbuch.mobile.R
import app.fahrtenbuch.mobile.data.model.TripType
import app.fahrtenbuch.mobile.data.model.UserPreferences
import app.fahrtenbuch.mobile.ui.common.categoryColors
import app.fahrtenbuch.mobile.ui.common.formatDistanceWithUnit
import app.fahrtenbuch.mobile.ui.common.localizedTripType
import app.fahrtenbuch.mobile.ui.common.wholeDistanceInPreferredUnit
import app.fahrtenbuch.mobile.ui.screens.StatCard
import java.util.Locale

/** Ein Monat im gestapelten Diagramm; alle Werte in km (gespeichert), `untrackedKm` = Tacho minus erfasste Fahrten. */
internal data class StackedMonth(
    val label: String,
    val description: String,
    val businessKm: Double = 0.0,
    val privateKm: Double = 0.0,
    val commuteKm: Double = 0.0,
    val unclassifiedKm: Double = 0.0,
    val otherDriversKm: Double = 0.0,
    val untrackedKm: Double = 0.0
) {
    val totalKm: Double get() = businessKm + privateKm + commuteKm + unclassifiedKm + otherDriversKm + untrackedKm
}

/**
 * Monatsdiagramm wie in der Web-App: Balkenhöhe = Tacho-Strecke, darin die erfassten Fahrten nach
 * Kategorie und obenauf schraffiert der Rest ohne Fahrten. Fehlt die Ablesung (z. B. im laufenden
 * Monat), besteht der Balken nur aus den erfassten Fahrten.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StackedMonthChartCard(
    title: String,
    months: List<StackedMonth>,
    highlightIndex: Int?,
    prefs: UserPreferences,
    onBarClick: ((Int) -> Unit)?,
    /** Monatsgrenze aus dem Leasing-/Finanzierungsvertrag, in km. */
    limitKm: Double? = null,
    limitEnabled: Boolean = true,
    onLimitEnabledChange: ((Boolean) -> Unit)? = null
) {
    val limitActive = limitKm != null && limitKm > 0 && limitEnabled
    val maxTotal = months.maxOfOrNull { it.totalKm }?.takeIf { it > 0 }
    // Die eingeblendete Grenze zählt für die Skalierung mit, sonst läge sie über dem Diagramm.
    val scaleMax = listOfNotNull(maxTotal, limitKm?.takeIf { limitActive }).maxOrNull()

    val business = categoryColors(TripType.BUSINESS).content
    val private = categoryColors(TripType.PRIVATE).content
    val commute = categoryColors(TripType.COMMUTE).content
    val unclassified = categoryColors(TripType.UNCLASSIFIED).content
    val otherDrivers = MaterialTheme.colorScheme.tertiary
    val hatchBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val hatchLine = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val limitColor = categoryColors(TripType.UNCLASSIFIED).content
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val baselineColor = MaterialTheme.colorScheme.outlineVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = labelColor, fontSize = 10.sp)
    val gap = if (months.size > 12) 2.dp else 6.dp

    StatCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = labelColor)
            maxTotal?.let {
                Text(
                    stringResource(R.string.statistics_max, formatDistanceWithUnit(it, prefs)),
                    style = MaterialTheme.typography.labelMedium,
                    color = labelColor
                )
            }
        }
        Text(
            stringResource(R.string.statistics_by_odometer),
            style = MaterialTheme.typography.labelSmall,
            color = labelColor
        )
        Spacer(Modifier.height(8.dp))

        Box(Modifier.fillMaxWidth().height(170.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val topPad = 18.dp.toPx() // Platz für die Summe über dem höchsten Balken
                val innerHeight = size.height - topPad
                val gapPx = gap.toPx()
                val n = months.size
                val barWidth = (size.width - gapPx * (n - 1)) / n
                val showValues = barWidth + gapPx >= 34.dp.toPx()
                val segmentGap = 1.5.dp.toPx()
                val corner = 4.dp.toPx()

                drawLine(baselineColor, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())

                months.forEachIndexed { index, month ->
                    val left = index * (barWidth + gapPx)
                    var bottom = size.height
                    val parts = listOf(
                        month.businessKm to business,
                        month.privateKm to private,
                        month.commuteKm to commute,
                        month.unclassifiedKm to unclassified,
                        month.otherDriversKm to otherDrivers,
                        month.untrackedKm to null // schraffiert
                    ).filter { it.first > 0 }

                    if (scaleMax == null || parts.isEmpty()) {
                        // Leere Monate als dünne Grundlinie
                        drawRect(hatchBackground, Offset(left, size.height - 2.dp.toPx()), Size(barWidth, 2.dp.toPx()))
                        return@forEachIndexed
                    }

                    parts.forEachIndexed { partIndex, (value, color) ->
                        val height = (value / scaleMax).toFloat() * innerHeight
                        val top = bottom - height
                        val drawTop = if (partIndex == parts.lastIndex) top else top + segmentGap
                        val drawHeight = (bottom - drawTop).coerceAtLeast(1f)
                        val topLeft = Offset(left, bottom - drawHeight)
                        val segmentSize = Size(barWidth, drawHeight)
                        val round = if (partIndex == parts.lastIndex) corner else 1.dp.toPx()
                        if (color != null) {
                            drawPath(segmentPath(topLeft, segmentSize, round), color)
                        } else {
                            drawHatched(topLeft, segmentSize, round, hatchBackground, hatchLine)
                        }
                        bottom = top
                    }

                    if (showValues) {
                        val text = String.format(Locale.getDefault(), "%,d", wholeDistanceInPreferredUnit(month.totalKm, prefs))
                        val layout = measurer.measure(text, labelStyle)
                        drawText(
                            layout,
                            topLeft = Offset(
                                left + barWidth / 2 - layout.size.width / 2f,
                                (bottom - layout.size.height - 2.dp.toPx()).coerceAtLeast(0f)
                            )
                        )
                    }
                }

                if (limitActive && limitKm != null && scaleMax != null) {
                    val y = size.height - (limitKm / scaleMax).toFloat().coerceIn(0f, 1f) * innerHeight
                    drawLine(
                        color = limitColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))
                    )
                }
            }
            // Klickflächen und Beschreibungen für Bedienungshilfen über der Zeichnung
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                months.forEachIndexed { index, month ->
                    val description = "${month.description}: ${formatDistanceWithUnit(month.totalKm, prefs)}"
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(if (onBarClick != null) Modifier.clickable { onBarClick(index) } else Modifier)
                            .semantics { contentDescription = description }
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            months.forEachIndexed { index, month ->
                val current = index == highlightIndex
                Text(
                    month.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (current) MaterialTheme.colorScheme.primary else labelColor,
                    fontWeight = if (current) FontWeight.Bold else null,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }

        // Legende: nur Einträge, die im Diagramm vorkommen
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LegendItem(business, localizedTripType(TripType.BUSINESS), months.any { it.businessKm > 0 })
            LegendItem(private, localizedTripType(TripType.PRIVATE), months.any { it.privateKm > 0 })
            LegendItem(commute, localizedTripType(TripType.COMMUTE), months.any { it.commuteKm > 0 })
            LegendItem(unclassified, localizedTripType(TripType.UNCLASSIFIED), months.any { it.unclassifiedKm > 0 })
            LegendItem(otherDrivers, stringResource(R.string.statistics_other_drivers), months.any { it.otherDriversKm > 0 })
            if (months.any { it.untrackedKm > 0 }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Canvas(Modifier.size(12.dp)) { drawHatched(Offset.Zero, size, 2.dp.toPx(), hatchBackground, hatchLine) }
                    Text(stringResource(R.string.statistics_not_tracked), style = MaterialTheme.typography.labelSmall, color = labelColor)
                }
            }
        }

        if (limitKm != null && limitKm > 0) {
            ContractLimitToggle(limitKm, limitEnabled, onLimitEnabledChange, limitColor, prefs)
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, visible: Boolean) {
    if (!visible) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(12.dp)) { drawPath(segmentPath(Offset.Zero, size, 2.dp.toPx()), color) }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Checkbox mit Strichmuster, um die gestrichelte Vertragsgrenze ein- und auszublenden. */
@Composable
internal fun ContractLimitToggle(
    limitKm: Double,
    enabled: Boolean,
    onEnabledChange: ((Boolean) -> Unit)?,
    color: Color,
    prefs: UserPreferences
) {
    val label = stringResource(R.string.statistics_contract_limit, formatDistanceWithUnit(limitKm, prefs))
    Row(
        modifier = Modifier
            .then(
                if (onEnabledChange != null) {
                    Modifier.toggleable(value = enabled, role = Role.Checkbox, onValueChange = onEnabledChange)
                } else Modifier
            )
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Checkbox(checked = enabled, onCheckedChange = null, enabled = onEnabledChange != null)
        Canvas(Modifier.size(width = 16.dp, height = 2.dp)) {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = size.height,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx()))
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Rechteck mit abgerundeten oberen Ecken. */
private fun segmentPath(topLeft: Offset, size: Size, radius: Float): Path = Path().apply {
    val r = CornerRadius(radius, radius)
    addRoundRect(
        RoundRect(
            left = topLeft.x, top = topLeft.y, right = topLeft.x + size.width, bottom = topLeft.y + size.height,
            topLeftCornerRadius = r, topRightCornerRadius = r,
            bottomRightCornerRadius = CornerRadius.Zero, bottomLeftCornerRadius = CornerRadius.Zero
        )
    )
}

/** Diagonal schraffierte Fläche (Strecke ohne erfasste Fahrten). */
private fun DrawScope.drawHatched(topLeft: Offset, size: Size, radius: Float, background: Color, line: Color) {
    clipPath(segmentPath(topLeft, size, radius)) {
        drawRect(background, topLeft, size)
        val spacing = 6.dp.toPx()
        val stroke = 1.5.dp.toPx()
        var x = topLeft.x - size.height
        while (x < topLeft.x + size.width) {
            drawLine(line, Offset(x, topLeft.y + size.height), Offset(x + size.height, topLeft.y), stroke)
            x += spacing
        }
    }
}
