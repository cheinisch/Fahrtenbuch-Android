package app.fahrtenbuch.mobile.ui.theme

import android.content.Context

class ThemePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("fahrtenbuch_theme", Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = runCatching {
            ThemeMode.valueOf(preferences.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)!!)
        }.getOrDefault(ThemeMode.SYSTEM)
        set(value) {
            preferences.edit().putString(KEY_THEME_MODE, value.name).apply()
        }

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
    }
}
