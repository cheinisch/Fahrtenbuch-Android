package app.fahrtenbuch.mobile.ui.permissions

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.os.PowerManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.fahrtenbuch.mobile.R

/** Aktueller Stand der Berechtigungen für die automatische Fahrtenaufzeichnung. */
@Immutable
data class PermissionStatus(
    val foregroundLocation: Boolean,
    val backgroundLocation: Boolean,
    /** Bluetooth (Android 12+) und Benachrichtigungen (Android 13+); true, wenn nicht nötig. */
    val extras: Boolean,
    val extrasRequired: Boolean,
    /** Keine Akku-Optimierung und keine Hintergrund-Einschränkung („Energie sparen“). */
    val batteryUnrestricted: Boolean = true
) {
    val missingCount: Int
        get() = listOf(foregroundLocation, backgroundLocation, extras || !extrasRequired, batteryUnrestricted).count { !it }
    val allGranted: Boolean get() = missingCount == 0
}

/**
 * Liest den Berechtigungsstatus und aktualisiert ihn automatisch, wenn die App wieder in
 * den Vordergrund kommt (z. B. nach den Systemeinstellungen). [onChanged] erlaubt
 * zusätzliches Neueinlesen nach einer Anfrage.
 */
@Composable
fun rememberPermissionStatus(): Pair<PermissionStatus, () -> Unit> {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var refresh by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val status = remember(refresh) {
        PermissionStatus(
            foregroundLocation = hasForegroundLocation(context),
            backgroundLocation = context.isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            extras = extraPermissions().all { context.isGranted(it) },
            extrasRequired = extraPermissions().isNotEmpty(),
            batteryUnrestricted = isBatteryUnrestricted(context)
        )
    }
    return status to { refresh++ }
}

/**
 * Liste der drei Berechtigungsschritte mit Status und Button zum Anfordern. Wird im
 * Einrichtungsassistenten und im Berechtigungen-Dialog der Einstellungen verwendet.
 *
 * Android erlaubt die Hintergrund-Berechtigung erst nach dem Standort während der Nutzung;
 * ab Android 11 öffnet die Anfrage die Systemseite mit „Immer zulassen“. Zeigt Android nach
 * wiederholter Ablehnung keinen Dialog mehr, führen die Buttons in die App-Einstellungen.
 */
@Composable
fun PermissionChecklist(
    status: PermissionStatus,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier,
    /** Zusätzlich Akku-Optimierung bzw. Energiesparen prüfen (Einstellungen). */
    showBattery: Boolean = false
) {
    val context = LocalContext.current
    var foregroundDenied by rememberSaveable { mutableStateOf(false) }
    var backgroundDenied by rememberSaveable { mutableStateOf(false) }
    var extrasDenied by rememberSaveable { mutableStateOf(false) }

    val foregroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onChanged()
        foregroundDenied = !hasForegroundLocation(context)
    }
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onChanged()
        backgroundDenied = !granted
    }
    val extrasLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        onChanged()
        extrasDenied = result.values.any { !it }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PermissionStep(
            icon = Icons.Outlined.LocationOn,
            title = stringResource(R.string.permissions_location_title),
            text = stringResource(R.string.permissions_location_text),
            granted = status.foregroundLocation,
            enabled = true,
            actionLabel = stringResource(if (foregroundDenied) R.string.permissions_open_settings else R.string.permissions_allow),
            onAction = {
                if (foregroundDenied && !context.canAskAgain(Manifest.permission.ACCESS_FINE_LOCATION)) openAppSettings(context)
                else foregroundLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            }
        )
        PermissionStep(
            icon = Icons.Outlined.MyLocation,
            title = stringResource(R.string.permissions_background_title),
            text = stringResource(
                if (status.foregroundLocation) R.string.permissions_background_text
                else R.string.permissions_background_needs_location
            ),
            granted = status.backgroundLocation,
            enabled = status.foregroundLocation,
            actionLabel = stringResource(if (backgroundDenied) R.string.permissions_open_settings else R.string.permissions_allow_always),
            onAction = {
                if (backgroundDenied && !context.canAskAgain(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) openAppSettings(context)
                else backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        )
        if (status.extrasRequired) {
            PermissionStep(
                icon = Icons.Outlined.Bluetooth,
                title = stringResource(R.string.permissions_extras_title),
                text = stringResource(R.string.permissions_extras_text),
                granted = status.extras,
                enabled = true,
                actionLabel = stringResource(if (extrasDenied) R.string.permissions_open_settings else R.string.permissions_allow),
                onAction = {
                    if (extrasDenied && extraPermissions().none { context.canAskAgain(it) }) openAppSettings(context)
                    else extrasLauncher.launch(extraPermissions().toTypedArray())
                }
            )
        }
        if (showBattery) {
            PermissionStep(
                icon = Icons.Outlined.BatteryAlert,
                title = stringResource(R.string.permissions_battery_title),
                text = stringResource(R.string.permissions_battery_text),
                granted = status.batteryUnrestricted,
                enabled = true,
                actionLabel = stringResource(R.string.permissions_battery_action),
                // Ohne Sonderberechtigung: die App-Seite der Systemeinstellungen, dort „Akku“ → „Nicht eingeschränkt“
                onAction = { openAppSettings(context) }
            )
            // Hersteller-Energiesparfunktionen (z. B. Samsung „Schlafende Apps“) lassen sich nicht abfragen
            Text(
                stringResource(
                    if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) R.string.permissions_battery_samsung
                    else R.string.permissions_battery_vendor
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionStep(
    icon: ImageVector,
    title: String,
    text: String,
    granted: Boolean,
    enabled: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, if (granted) colors.primary.copy(alpha = 0.5f) else colors.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(if (granted) colors.primary else colors.primaryContainer, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (granted) Icons.Default.Check else icon,
                        contentDescription = null,
                        tint = if (granted) colors.onPrimary else colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }
            if (granted) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(colors.primary, CircleShape))
                    Text(
                        stringResource(R.string.permissions_granted),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                }
            } else if (enabled) {
                FilledTonalButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                    Text(actionLabel, fontWeight = FontWeight.SemiBold)
                }
            } else {
                OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Text(actionLabel)
                }
            }
        }
    }
}

/** Bluetooth (Android 12+, für den Auto-Start) und Benachrichtigungen (Android 13+). */
internal fun extraPermissions(): List<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}

/**
 * True, wenn Android die App nicht im Hintergrund ausbremst: weder Akku-Optimierung
 * (Doze/„Optimiert“) noch „Eingeschränkt“ in der Akkunutzung der App.
 */
internal fun isBatteryUnrestricted(context: Context): Boolean {
    val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val ignoringOptimizations = power?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    val backgroundRestricted = activityManager?.isBackgroundRestricted ?: false
    return ignoringOptimizations && !backgroundRestricted
}

internal fun hasForegroundLocation(context: Context): Boolean =
    context.isGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
        context.isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

private fun Context.isGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** False, wenn Android den Dialog nicht mehr zeigt (zweimal abgelehnt oder „Nicht mehr fragen“). */
private fun Context.canAskAgain(permission: String): Boolean {
    val activity = findActivity() ?: return true
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** Öffnet die App-Seite in den Android-Einstellungen (Berechtigungen ändern oder entziehen). */
fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
