package app.fahrtenbuch.mobile.location

import app.fahrtenbuch.mobile.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Reverse-Geocoding über einen Photon-Server (https://github.com/komoot/photon) – Alternative
 * zum Android-Geocoder für den lokalen Modus, z. B. mit eigenem Server oder ohne Google-Dienste.
 *
 * Die Adresse wird wie beim [ReverseGeocoder] als „Name, Straße Nr, PLZ Ort“ aufgebaut.
 */
internal object PhotonGeocoder {

    /** Öffentlicher Server von komoot – nur für geringe Last gedacht (Fair Use). */
    const val PUBLIC_SERVER = "https://photon.komoot.io"

    // Photon kennt nur wenige Sprachen; alles andere liefert die Standardsprache (Landessprache)
    private val supportedLanguages = setOf("de", "en", "fr", "it")

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Akzeptiert die Basisadresse auch mit angehängtem „/api“ oder „/reverse“ und
     * liefert die Wurzel ohne abschließenden Schrägstrich – oder null bei ungültiger Eingabe.
     */
    fun normalize(input: String): String? {
        var base = input.trim().trimEnd('/')
        if (base.isEmpty()) return null
        if (!base.contains("://")) base = "https://$base"
        base = base.removeSuffix("/reverse").removeSuffix("/api").trimEnd('/')
        val url = base.toHttpUrlOrNull() ?: return null
        return if (url.host.isBlank()) null else base
    }

    /** Liefert die Adresse oder null, wenn der Ort nicht aufgelöst werden konnte; wirft bei Netz-/Serverfehlern. */
    suspend fun reverse(baseUrl: String, lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        val base = normalize(baseUrl) ?: throw IOException("Invalid Photon URL")
        val builder = "$base/reverse".toHttpUrlOrNull()?.newBuilder() ?: throw IOException("Invalid Photon URL")
        builder.addQueryParameter("lat", lat.toString())
        builder.addQueryParameter("lon", lon.toString())
        builder.addQueryParameter("limit", "1")
        Locale.getDefault().language.takeIf { it in supportedLanguages }?.let { builder.addQueryParameter("lang", it) }

        val request = Request.Builder()
            .url(builder.build())
            // Der öffentliche Server verlangt einen aussagekräftigen User-Agent
            .header("User-Agent", "Fahrtenbuch-Android/${BuildConfig.VERSION_NAME} (+https://fahrtenbuch.app)")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body?.string() ?: return@withContext null
            val properties = JSONObject(body).optJSONArray("features")
                ?.optJSONObject(0)
                ?.optJSONObject("properties")
                ?: return@withContext null
            format(properties)
        }
    }

    private fun format(p: JSONObject): String? {
        fun field(key: String) = p.optString(key, "").trim().ifBlank { null }
        val streetName = field("street")
        val houseNumber = field("housenumber")
        val street = listOfNotNull(streetName, houseNumber).joinToString(" ").ifBlank { null }
        val place = field("city") ?: field("locality") ?: field("district") ?: field("county")
        val city = listOfNotNull(field("postcode"), place).joinToString(" ").ifBlank { null }
        // „name“ ist bei Adressen oft die Straße oder der Ort selbst – dann nicht doppelt zeigen
        val name = field("name")?.takeIf { it != streetName && it != street && it != place }
        val parts = listOfNotNull(name, street, city)
        return if (parts.isNotEmpty()) parts.joinToString(", ") else field("state") ?: field("country")
    }
}
