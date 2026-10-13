package app.fahrtenbuch.mobile.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Löst Koordinaten über den Android-Geocoder in eine Adresse auf – ohne eigenen Server
 * und ohne API-Key. Der Geocoder des Systems braucht eine Internetverbindung; ohne Netz
 * (oder auf Geräten ohne Geocoder-Dienst) liefert er null, und der Aufrufer versucht es
 * später erneut.
 *
 * Das Ergebnis ist wie die Server-Adressen aufgebaut („Name, Straße Nr, PLZ Ort“), damit
 * die Kurzform in Liste und Details (nur Ortsname) gleich funktioniert.
 */
internal object ReverseGeocoder {

    suspend fun lookup(context: Context, lat: Double, lon: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (continuation.isActive) continuation.resume(addresses.firstOrNull())
                    }

                    override fun onError(errorMessage: String?) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                })
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(lat, lon, 1)?.firstOrNull() }.getOrNull()
            }
        }
        return address?.let(::format)
    }

    private fun format(address: Address): String? {
        val street = listOfNotNull(address.thoroughfare, address.subThoroughfare)
            .joinToString(" ")
            .ifBlank { null }
        val place = address.locality ?: address.subLocality ?: address.subAdminArea
        val city = listOfNotNull(address.postalCode, place).joinToString(" ").ifBlank { null }
        // featureName ist oft nur die Hausnummer oder die Straße – dann nicht doppelt anzeigen.
        val name = address.featureName?.takeIf { feature ->
            feature.isNotBlank() &&
                feature != address.subThoroughfare &&
                feature != address.thoroughfare &&
                feature != street &&
                feature != address.postalCode &&
                feature != place &&
                !feature.first().isDigit()
        }
        val parts = listOfNotNull(name, street, city)
        return if (parts.isNotEmpty()) parts.joinToString(", ") else address.getAddressLine(0)
    }
}
