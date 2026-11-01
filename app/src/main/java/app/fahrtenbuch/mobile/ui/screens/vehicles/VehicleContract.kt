package app.fahrtenbuch.mobile.ui.screens.vehicles

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.fahrtenbuch.mobile.R
import app.fahrtenbuch.mobile.data.model.AcquisitionType
import app.fahrtenbuch.mobile.data.model.Vehicle
import app.fahrtenbuch.mobile.ui.common.LocalUserPreferences
import app.fahrtenbuch.mobile.ui.common.distanceUnitLabel
import app.fahrtenbuch.mobile.ui.common.formatCalendarDate
import app.fahrtenbuch.mobile.ui.common.wholeDistanceInPreferredUnit
import java.time.LocalDate
import java.util.Locale

/**
 * Vertragsangaben in einer Zeile, z. B. „Leasing · 01.04.2024 – 31.03.2027 · 45.000 km“.
 * Null, wenn das Fahrzeug keinen Vertrag hat oder nur freigegeben ist – Mitbenutzer
 * bekommen die Finanzierungsart vom Server ohnehin nicht.
 */
@Composable
internal fun vehicleContractSummary(vehicle: Vehicle): String? {
    if (!vehicle.isOwner || !vehicle.acquisitionType.hasContract) return null
    val prefs = LocalUserPreferences.current
    val start = parseContractDate(vehicle.leaseStartDate)
    val end = parseContractDate(vehicle.leaseEndDate)
    return listOfNotNull(
        stringResource(acquisitionLabel(vehicle.acquisitionType)),
        if (start != null && end != null) {
            stringResource(
                R.string.vehicles_contract_period_value,
                formatCalendarDate(start, prefs),
                formatCalendarDate(end, prefs)
            )
        } else null,
        vehicle.leaseIncludedKm?.let {
            String.format(
                Locale.getDefault(),
                "%,d %s",
                wholeDistanceInPreferredUnit(it, prefs),
                distanceUnitLabel(prefs)
            )
        }
    ).joinToString(" · ")
}

internal fun acquisitionLabel(type: AcquisitionType): Int = when (type) {
    AcquisitionType.OWNED -> R.string.vehicles_acquisition_owned
    AcquisitionType.LEASING -> R.string.vehicles_acquisition_leasing
    AcquisitionType.FINANCING -> R.string.vehicles_acquisition_financing
}

internal fun parseContractDate(value: String?): LocalDate? =
    value?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
