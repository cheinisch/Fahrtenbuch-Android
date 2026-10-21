package app.fahrtenbuch.mobile.data.model

/** Schalter für App-Funktionen, die vorübergehend ausgeblendet sind. */
object AppFeatures {
    /**
     * Auf die Straße gelegte Route (Map-Matching) in Karten und Fahrtdetails anzeigen.
     * Vorerst aus: Karten zeigen die aufgezeichnete GPS-Route, der Profilschalter ist
     * ausgeblendet. Das Matching auf dem Server läuft unabhängig davon weiter.
     */
    const val SHOW_MAP_MATCHING = false
}
