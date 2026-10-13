package app.fahrtenbuch.mobile.ui.screens

import android.Manifest
import android.app.Activity
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.fahrtenbuch.mobile.R

/**
 * Einrichtungsschritt für die Berechtigungen der automatischen Fahrtenaufzeichnung:
 * 1. Standort während der Nutzung (Pflicht für das Tracking)
 * 2. Standort im Hintergrund („Immer zulassen“) – Android erlaubt diese Anfrage erst,
 *    wenn 1 erteilt ist; ab Android 11 öffnet das System dafür die Einstellungsseite
 * 3. Bluetooth und Benachrichtigungen (Auto-Start beim Verbinden mit dem Auto,
 *    Hinweis während der Aufzeichnung)
 *
 * Alles ist freiwillig: „Weiter“ geht immer, die App funktioniert dann mit manuellem Start.
 * Der Status wird beim Zurückkehren aus den Einstellungen neu geprüft.
 */
@Composable
fun LocationPermissionScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Zähler, der beim Zurückkehren in die App den Berechtigungsstatus neu einliest
    var refresh by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val foregroundGranted = remember(refresh) { hasForegroundLocation(context) }
    val backgroundGranted = remember(refresh) { context.isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
    val extrasGranted = remember(refresh) { extraPermissions().all { context.isGranted(it) } }
    // Nach einer Ablehnung zeigt Android den Dialog oft nicht mehr: dann direkt in die App-Einstellungen
    var foregroundDenied by rememberSaveable { mutableStateOf(false) }
    var backgroundDenied by rememberSaveable { mutableStateOf(false) }
    var extrasDenied by rememberSaveable { mutableStateOf(false) }

    val foregroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh++
        foregroundDenied = !hasForegroundLocation(context)
    }
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refresh++
        backgroundDenied = !granted
    }
    val extrasLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        refresh++
        extrasDenied = result.values.any { !it }
    }

    CenteredOnboardingLayout(
        actions = {
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    stringResource(
                        if (foregroundGranted && backgroundGranted) R.string.location_notice_continue
                        else R.string.permissions_continue_later
                    ),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    ) {
        Icon(
            Icons.Outlined.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.permissions_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.permissions_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PermissionStep(
                icon = Icons.Outlined.LocationOn,
                title = stringResource(R.string.permissions_location_title),
                text = stringResource(R.string.permissions_location_text),
                granted = foregroundGranted,
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
                    if (foregroundGranted) R.string.permissions_background_text
                    else R.string.permissions_background_needs_location
                ),
                granted = backgroundGranted,
                enabled = foregroundGranted,
                actionLabel = stringResource(if (backgroundDenied) R.string.permissions_open_settings else R.string.permissions_allow_always),
                onAction = {
                    if (backgroundDenied && !context.canAskAgain(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) openAppSettings(context)
                    else backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            )
            if (extraPermissions().isNotEmpty()) {
                PermissionStep(
                    icon = Icons.Outlined.Bluetooth,
                    title = stringResource(R.string.permissions_extras_title),
                    text = stringResource(R.string.permissions_extras_text),
                    granted = extrasGranted,
                    enabled = true,
                    actionLabel = stringResource(if (extrasDenied) R.string.permissions_open_settings else R.string.permissions_allow),
                    onAction = {
                        if (extrasDenied && extraPermissions().none { context.canAskAgain(it) }) openAppSettings(context)
                        else extrasLauncher.launch(extraPermissions().toTypedArray())
                    }
                )
            }
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
private fun extraPermissions(): List<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}

private fun hasForegroundLocation(context: Context): Boolean =
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

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
