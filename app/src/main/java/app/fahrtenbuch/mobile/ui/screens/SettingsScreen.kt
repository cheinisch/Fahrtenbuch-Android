package app.fahrtenbuch.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.fahrtenbuch.mobile.BuildConfig
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.widthIn
import app.fahrtenbuch.mobile.ui.common.LocalWindowLayout
import app.fahrtenbuch.mobile.ui.common.UserAvatar
import app.fahrtenbuch.mobile.R
import app.fahrtenbuch.mobile.data.api.AppMode
import app.fahrtenbuch.mobile.data.api.SessionStore
import app.fahrtenbuch.mobile.data.model.DateFormatPreference
import app.fahrtenbuch.mobile.data.model.DistanceUnit
import app.fahrtenbuch.mobile.data.model.SpeedUnit
import app.fahrtenbuch.mobile.data.model.TimeFormatPreference
import app.fahrtenbuch.mobile.data.model.User
import app.fahrtenbuch.mobile.data.model.UserPreferences
import app.fahrtenbuch.mobile.ui.AppViewModel
import app.fahrtenbuch.mobile.ui.permissions.rememberPermissionStatus
import app.fahrtenbuch.mobile.ui.screens.settings.AboutDialog
import app.fahrtenbuch.mobile.ui.screens.settings.BackupDialog
import app.fahrtenbuch.mobile.ui.screens.settings.PasskeysDialog
import app.fahrtenbuch.mobile.ui.screens.settings.FormatPreferencesDialog
import app.fahrtenbuch.mobile.ui.screens.settings.LogoutConfirmDialog
import app.fahrtenbuch.mobile.ui.screens.settings.OpenSourceLicensesDialog
import app.fahrtenbuch.mobile.ui.screens.settings.PermissionsDialog
import app.fahrtenbuch.mobile.ui.screens.settings.ProfileEditDialog
import app.fahrtenbuch.mobile.ui.screens.settings.SegmentedSelector
import app.fahrtenbuch.mobile.ui.screens.settings.ServerSettingsDialog
import app.fahrtenbuch.mobile.ui.screens.settings.SettingsCard
import app.fahrtenbuch.mobile.ui.screens.settings.SettingsDivider
import app.fahrtenbuch.mobile.ui.screens.settings.SettingsRow
import app.fahrtenbuch.mobile.ui.screens.settings.SettingsSectionLabel
import app.fahrtenbuch.mobile.ui.theme.ThemeMode

private enum class SettingsDialog { NONE, PROFILE, SERVER, LOGOUT, PERMISSIONS, FORMAT, ABOUT, LIBRARIES, BACKUP, PASSKEYS }

private val trackingIntervals = listOf(5, 10, 15, 30, 60)

@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenVehicles: () -> Unit = {}
) {
    val appState by viewModel.state.collectAsState()
    val context = LocalContext.current
    val sessionStore = remember { SessionStore(context) }
    var trackingInterval by remember { mutableStateOf(sessionStore.trackingIntervalSeconds) }
    var dialog by rememberSaveable { mutableStateOf(SettingsDialog.NONE) }
    val isServerMode = appState.appMode == AppMode.SERVER
    val (permissionStatus, refreshPermissions) = rememberPermissionStatus()

    LaunchedEffect(Unit) {
        viewModel.loadAccount()
        // Passkey-Verwaltung nur zeigen, wenn Server und Gerät Passkeys unterstützen
        viewModel.loadPasskeySupport()
    }

    val windowLayout = LocalWindowLayout.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (windowLayout.wide) 32.dp else 16.dp, vertical = 24.dp)
    ) {
        Text(
            stringResource(R.string.settings),
            modifier = Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold
        )

        // Tablet quer: Konto/Verbindung/Darstellung links, App-Einstellungen rechts
        val accountSection: @Composable ColumnScope.() -> Unit = {
        if (isServerMode) {
            ProfileCard(
                user = appState.user,
                useGravatar = appState.preferences.useGravatar,
                modifier = Modifier.padding(top = 20.dp),
                onClick = { dialog = SettingsDialog.PROFILE }
            )
            // Einheiten und Datumsformat gehören im Server-Modus zum Benutzerkonto
            FormatPreferencesRow(
                preferences = appState.preferences,
                modifier = Modifier.padding(top = 10.dp),
                onClick = { dialog = SettingsDialog.FORMAT }
            )

            SettingsSectionLabel(stringResource(R.string.settings_section_connection))
            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.Dns,
                    title = stringResource(R.string.settings_server),
                    subtitle = serverHost(appState.serverUrl),
                    onClick = { dialog = SettingsDialog.SERVER }
                )
                SettingsDivider()
                val https = appState.serverUrl.startsWith("https://", ignoreCase = true)
                val certChecked = https && !sessionStore.allowInvalidCertificates
                if (appState.passkeysAvailable) {
                    SettingsRow(
                        icon = Icons.Default.Key,
                        title = stringResource(R.string.passkeys_title),
                        subtitle = stringResource(R.string.passkeys_row_subtitle),
                        onClick = { dialog = SettingsDialog.PASSKEYS }
                    )
                    SettingsDivider()
                }
                SettingsRow(
                    icon = if (certChecked) Icons.Default.Lock else Icons.Default.LockOpen,
                    title = stringResource(R.string.settings_security),
                    subtitle = stringResource(
                        when {
                            !https -> R.string.settings_security_http
                            certChecked -> R.string.settings_security_verified
                            else -> R.string.settings_security_unverified
                        }
                    )
                )
            }
        } else if (appState.appMode == AppMode.LOCAL) {
            SettingsSectionLabel(stringResource(R.string.storage_mode))
            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.CloudUpload,
                    title = stringResource(R.string.local_mode_title),
                    subtitle = stringResource(R.string.local_mode_migration_help)
                )
                OutlinedButton(
                    onClick = { viewModel.startServerMigrationSetup() },
                    modifier = Modifier.padding(start = 70.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    Text(stringResource(R.string.migrate_to_server))
                }
            }
        }

        if (!isServerMode) {
            SettingsSectionLabel(stringResource(R.string.format_title))
            FormatPreferencesRow(preferences = appState.preferences, onClick = { dialog = SettingsDialog.FORMAT })
        }

        SettingsSectionLabel(stringResource(R.string.appearance))
        SegmentedSelector(
            options = ThemeMode.entries,
            selected = themeMode,
            label = { mode ->
                stringResource(
                    when (mode) {
                        ThemeMode.SYSTEM -> R.string.settings_theme_system
                        ThemeMode.LIGHT -> R.string.settings_theme_light
                        ThemeMode.DARK -> R.string.settings_theme_dark
                    }
                )
            },
            onSelect = onThemeModeChange
        )
        }

        val appSection: @Composable ColumnScope.() -> Unit = {
        SettingsSectionLabel(stringResource(R.string.vehicles))
        SettingsCard {
            SettingsRow(
                icon = Icons.Default.DirectionsCar,
                title = stringResource(R.string.vehicles_bluetooth),
                subtitle = if (appState.vehicles.isEmpty()) {
                    stringResource(R.string.settings_no_vehicles)
                } else {
                    pluralStringResource(R.plurals.settings_vehicle_count, appState.vehicles.size, appState.vehicles.size) +
                        " · " + appState.vehicles.joinToString(", ") { it.name }
                },
                onClick = onOpenVehicles
            )
        }

        SettingsSectionLabel(stringResource(R.string.tracking_title))
        SettingsCard {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.gps_interval), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        pluralStringResource(R.plurals.settings_gps_every, trackingInterval, trackingInterval),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                SegmentedSelector(
                    options = trackingIntervals,
                    selected = trackingInterval,
                    label = { stringResource(R.string.settings_seconds_short, it) },
                    onSelect = { seconds ->
                        trackingInterval = seconds
                        sessionStore.trackingIntervalSeconds = seconds
                    }
                )
                Text(
                    stringResource(R.string.settings_gps_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SettingsSectionLabel(stringResource(R.string.permissions_settings_title))
        SettingsCard {
            SettingsRow(
                icon = Icons.Default.Shield,
                title = stringResource(R.string.permissions_settings_row),
                subtitle = if (permissionStatus.allGranted) stringResource(R.string.permissions_all_granted)
                    else pluralStringResource(R.plurals.permissions_missing, permissionStatus.missingCount, permissionStatus.missingCount),
                trailing = {
                    if (!permissionStatus.allGranted) {
                        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    }
                },
                onClick = {
                    refreshPermissions()
                    dialog = SettingsDialog.PERMISSIONS
                }
            )
        }

        SettingsSectionLabel(stringResource(R.string.backup_title))
        SettingsCard {
            SettingsRow(
                icon = Icons.Default.Backup,
                title = stringResource(R.string.backup_row_title),
                subtitle = stringResource(if (sessionStore.googleBackupEnabled) R.string.backup_row_google_on else R.string.backup_row_google_off),
                onClick = { dialog = SettingsDialog.BACKUP }
            )
        }

        SettingsSectionLabel(stringResource(R.string.settings_section_about))
        SettingsCard {
            SettingsRow(
                icon = Icons.Default.Info,
                title = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                subtitle = stringResource(R.string.about_row_subtitle),
                onClick = { dialog = SettingsDialog.ABOUT }
            )
        }

        if (isServerMode) {
            OutlinedButton(
                onClick = { dialog = SettingsDialog.LOGOUT },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(R.string.settings_logout),
                    modifier = Modifier.padding(start = 8.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }
        }

        if (windowLayout.twoPane) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f), content = accountSection)
                // Der Abschnittstitel links beginnt bei Server-Betrieb mit der Profilkarte (20 dp Abstand)
                Column(Modifier.weight(1f).padding(top = if (isServerMode) 4.dp else 0.dp), content = appSection)
            }
        } else {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().align(Alignment.CenterHorizontally)) {
                accountSection()
                appSection()
            }
        }
    }

    when (dialog) {
        SettingsDialog.PASSKEYS -> PasskeysDialog(
            viewModel = viewModel,
            onDismiss = { dialog = SettingsDialog.NONE }
        )
        SettingsDialog.BACKUP -> BackupDialog(
            store = sessionStore,
            serverMode = isServerMode,
            onDismiss = { dialog = SettingsDialog.NONE }
        )
        SettingsDialog.PROFILE -> ProfileEditDialog(
            user = appState.user,
            loading = appState.loading,
            error = appState.error,
            onDismiss = { dialog = SettingsDialog.NONE },
            onSave = { displayName, username, email ->
                viewModel.updateAccount(
                    displayName,
                    username,
                    email,
                    appState.serverUrl,
                    sessionStore.allowInvalidCertificates
                ) { dialog = SettingsDialog.NONE }
            },
            gravatarEnabled = appState.preferences.gravatarEnabled,
            gravatarAvailable = appState.preferences.gravatarAvailable,
            onGravatarChange = viewModel::setGravatarEnabled,
            mapMatchingEnabled = appState.preferences.mapMatchingEnabled,
            mapMatchingAvailable = appState.preferences.mapMatchingAvailable,
            onMapMatchingChange = viewModel::setMapMatchingEnabled,
            tripMergeGapSeconds = appState.preferences.tripMergeGapSeconds,
            onTripMergeGapChange = viewModel::setTripMergeGapSeconds
        )
        SettingsDialog.SERVER -> ServerSettingsDialog(
            serverUrl = appState.serverUrl,
            allowInvalidCertificates = sessionStore.allowInvalidCertificates,
            loading = appState.loading,
            error = appState.error,
            onDismiss = { dialog = SettingsDialog.NONE },
            onSave = { serverUrl, allowInvalid ->
                val user = appState.user
                viewModel.updateAccount(
                    user?.displayName,
                    user?.loginName ?: user?.username,
                    user?.email,
                    serverUrl,
                    allowInvalid
                ) { dialog = SettingsDialog.NONE }
            }
        )
        SettingsDialog.LOGOUT -> LogoutConfirmDialog(
            onDismiss = { dialog = SettingsDialog.NONE },
            onConfirm = {
                dialog = SettingsDialog.NONE
                viewModel.logout()
            }
        )
        SettingsDialog.PERMISSIONS -> PermissionsDialog(
            status = permissionStatus,
            onChanged = refreshPermissions,
            onDismiss = { dialog = SettingsDialog.NONE }
        )
        SettingsDialog.FORMAT -> FormatPreferencesDialog(
            current = appState.preferences,
            serverMode = isServerMode,
            loading = appState.loading,
            error = appState.error,
            onDismiss = { dialog = SettingsDialog.NONE },
            onSave = { preferences -> viewModel.updatePreferences(preferences) { dialog = SettingsDialog.NONE } }
        )
        SettingsDialog.ABOUT -> AboutDialog(
            serverMode = isServerMode,
            serverUrl = appState.serverUrl,
            loadServerHealth = { viewModel.serverHealth() },
            onShowLibraries = { dialog = SettingsDialog.LIBRARIES },
            onDismiss = { dialog = SettingsDialog.NONE }
        )
        // Zurück aus der Lizenzliste führt wieder zu „Über die App“
        SettingsDialog.LIBRARIES -> OpenSourceLicensesDialog(onDismiss = { dialog = SettingsDialog.ABOUT })
        SettingsDialog.NONE -> Unit
    }
}

@Composable
private fun FormatPreferencesRow(preferences: UserPreferences, modifier: Modifier = Modifier, onClick: () -> Unit) {
    SettingsCard(modifier = modifier) {
        SettingsRow(
            icon = Icons.Default.Straighten,
            title = stringResource(R.string.format_title),
            subtitle = listOf(
                stringResource(if (preferences.distanceUnit == DistanceUnit.KM) R.string.format_km else R.string.format_mi),
                if (preferences.speedUnit == SpeedUnit.KMH) "km/h" else "mph",
                stringResource(if (preferences.dateFormat == DateFormatPreference.EU) R.string.format_date_eu else R.string.format_date_us),
                stringResource(if (preferences.timeFormat == TimeFormatPreference.H24) R.string.format_time_24 else R.string.format_time_12),
                preferences.timeZone ?: stringResource(R.string.format_time_zone_device_short)
            ).joinToString(" · "),
            onClick = onClick
        )
    }
}

@Composable
private fun ProfileCard(user: User?, useGravatar: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val name = user?.displayName?.takeIf { it.isNotBlank() } ?: user?.loginName ?: user?.username ?: "–"
    SettingsCard(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gravatar zur E-Mail, falls vorhanden; sonst Initialen
            UserAvatar(email = user?.email?.takeIf { useGravatar }, initials = initials(name), size = 56.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        name,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    user?.role?.takeIf { it.isNotBlank() }?.let { role ->
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(
                                role.replaceFirstChar { it.titlecase() },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                user?.email?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    stringResource(R.string.edit_profile),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun initials(name: String): String =
    name.split(" ", "-", ".", "_")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "?" }

private fun serverHost(url: String): String =
    runCatching { java.net.URI(url.trim()).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: url
