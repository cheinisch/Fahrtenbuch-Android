package app.fahrtenbuch.mobile.ui.common

import androidx.compose.runtime.compositionLocalOf
import app.fahrtenbuch.mobile.data.model.UserPreferences

/**
 * Anzeige-Einstellungen (Einheiten, Datumsformat) für alle Screens. Wird in
 * FahrtenbuchApp aus dem App-Zustand bereitgestellt; Formatierer in TripFormat.kt
 * bekommen den Wert als Parameter.
 */
val LocalUserPreferences = compositionLocalOf { UserPreferences() }
