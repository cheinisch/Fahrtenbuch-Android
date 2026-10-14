package app.fahrtenbuch.mobile

import app.fahrtenbuch.mobile.sync.SyncScheduler
import android.content.pm.ActivityInfo
import android.os.Bundle
import org.maplibre.android.MapLibre
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.fahrtenbuch.mobile.ui.FahrtenbuchApp
import app.fahrtenbuch.mobile.ui.theme.FahrtenbuchTheme
import app.fahrtenbuch.mobile.ui.theme.ThemePreferences

class MainActivity : ComponentActivity() {
    private lateinit var themePreferences: ThemePreferences
    private var selectedThemeMode by mutableStateOf(app.fahrtenbuch.mobile.ui.theme.ThemeMode.SYSTEM)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Auf Smartphones nur Hochformat. Tablets und aufgeklappte Foldables (≥ 600 dp)
        // dürfen drehen; Android 16 ignoriert die Sperre dort ohnehin.
        if (resources.configuration.smallestScreenWidthDp < 600) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        SyncScheduler.schedulePeriodic(this)
        // MapLibre einmalig initialisieren, bevor die erste Karte angezeigt wird.
        MapLibre.getInstance(applicationContext)
        enableEdgeToEdge()
        themePreferences = ThemePreferences(this)
        selectedThemeMode = themePreferences.themeMode

        setContent {
            FahrtenbuchTheme(themeMode = selectedThemeMode) {
                FahrtenbuchApp(
                    themeMode = selectedThemeMode,
                    onThemeModeChange = { mode ->
                        selectedThemeMode = mode
                        themePreferences.themeMode = mode
                    }
                )
            }
        }
    }
}
