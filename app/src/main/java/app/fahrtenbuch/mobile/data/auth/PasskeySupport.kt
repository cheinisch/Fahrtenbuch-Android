package app.fahrtenbuch.mobile.data.auth

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Kann dieses Gerät Passkeys anlegen und nutzen? Ab Android 14 bringt das System den
 * Credential Manager mit, davor braucht er die Google-Play-Dienste (Geräte ohne GMS
 * scheitern sonst erst beim Anlegen mit einer unverständlichen Fehlermeldung).
 */
object PasskeySupport {
    private const val PLAY_SERVICES = "com.google.android.gms"

    fun isDeviceSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        return try {
            context.packageManager.getPackageInfo(PLAY_SERVICES, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
