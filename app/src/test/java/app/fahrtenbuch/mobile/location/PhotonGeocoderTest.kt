package app.fahrtenbuch.mobile.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotonGeocoderTest {
    @Test
    fun normalize_stripsEndpointsAndSlashes() {
        assertEquals("https://photon.komoot.io", PhotonGeocoder.normalize("https://photon.komoot.io/"))
        assertEquals("https://photon.komoot.io", PhotonGeocoder.normalize(" https://photon.komoot.io/api/ "))
        assertEquals("https://photon.komoot.io", PhotonGeocoder.normalize("https://photon.komoot.io/reverse"))
        assertEquals("http://192.168.1.10:2322", PhotonGeocoder.normalize("http://192.168.1.10:2322"))
        assertEquals("https://geo.example.com/photon", PhotonGeocoder.normalize("https://geo.example.com/photon/api"))
    }

    @Test
    fun normalize_addsHttpsWhenSchemeMissing() {
        assertEquals("https://photon.example.com", PhotonGeocoder.normalize("photon.example.com"))
    }

    @Test
    fun normalize_rejectsInvalidInput() {
        assertNull(PhotonGeocoder.normalize(""))
        assertNull(PhotonGeocoder.normalize("   "))
    }
}
