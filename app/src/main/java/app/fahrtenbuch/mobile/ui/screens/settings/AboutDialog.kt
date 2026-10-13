package app.fahrtenbuch.mobile.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fahrtenbuch.mobile.BuildConfig
import app.fahrtenbuch.mobile.R
import app.fahrtenbuch.mobile.data.model.HealthResponse

private const val PRIVACY_URL = "https://fahrtenbuch.app/privacy.html"
private const val SOURCE_URL = "https://github.com/cheinisch/Fahrtenbuch-Android"
private const val LICENSE_URL = "$SOURCE_URL/blob/master/LICENSE"
private const val ISSUES_URL = "$SOURCE_URL/issues"

/**
 * „Über die App“: Version und Diagnose (kopierbar für Fehlerberichte), Server-Version
 * über GET /health, Lizenz der App (GNU AGPL v3), alle Open-Source-Bibliotheken (AboutLibraries), Kartenquellen
 * sowie Links zu Datenschutz, Quellcode und Fehlermeldung.
 */
@Composable
fun AboutDialog(
    serverMode: Boolean,
    serverUrl: String,
    loadServerHealth: suspend () -> HealthResponse?,
    onShowLibraries: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var health by remember { mutableStateOf<HealthResponse?>(null) }
    var healthLoaded by remember { mutableStateOf(!serverMode) }
    LaunchedEffect(serverMode) {
        if (serverMode) {
            health = runCatching { loadServerHealth() }.getOrNull()
            healthLoaded = true
        }
    }

    val serverLine = when {
        !serverMode -> stringResource(R.string.about_mode_local)
        !healthLoaded -> stringResource(R.string.about_server_checking)
        health?.version != null -> stringResource(R.string.about_server_version, health?.version.orEmpty(), health?.status ?: "–")
        else -> stringResource(R.string.about_server_unreachable)
    }
    val diagnostics = buildString {
        appendLine("Fahrtenbuch ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("${Build.MANUFACTURER} ${Build.MODEL}")
        if (serverMode) {
            appendLine("Server: $serverUrl")
            appendLine("Server version: ${health?.version ?: "–"} · database: ${health?.database ?: "–"}")
        } else {
            appendLine("Mode: local")
        }
    }.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_section_about)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Version und Diagnose
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.app_name) + " " + BuildConfig.VERSION_NAME,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            stringResource(R.string.settings_build, BuildConfig.VERSION_CODE, BuildConfig.APPLICATION_ID),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(serverLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(
                            onClick = { copyToClipboard(context, diagnostics) },
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.about_copy_diagnostics), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }

                // Links
                Column {
                    AboutLink(stringResource(R.string.about_privacy)) { uriHandler.openUri(PRIVACY_URL) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AboutLink(stringResource(R.string.about_source_code)) { uriHandler.openUri(SOURCE_URL) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AboutLink(stringResource(R.string.about_report_issue)) { uriHandler.openUri(ISSUES_URL) }
                }

                // Lizenz der App
                AboutSection(stringResource(R.string.about_license_title)) {
                    Text(stringResource(R.string.about_license_text), style = MaterialTheme.typography.bodyMedium)
                    AboutLink(stringResource(R.string.about_license_show)) { uriHandler.openUri(LICENSE_URL) }
                }

                // Bibliotheken
                AboutSection(stringResource(R.string.about_libraries_title)) {
                    Text(stringResource(R.string.about_libraries_text), style = MaterialTheme.typography.bodySmall)
                    AboutLink(stringResource(R.string.about_libraries_show), onShowLibraries)
                }

                // Kartenquellen
                AboutSection(stringResource(R.string.about_map_data_title)) {
                    Text(stringResource(R.string.about_map_data_text), style = MaterialTheme.typography.bodySmall)
                    AboutLink("openstreetmap.org/copyright") { uriHandler.openUri("https://www.openstreetmap.org/copyright") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}

@Composable
private fun AboutSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}

@Composable
private fun AboutLink(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Fahrtenbuch diagnostics", text))
    // Ab Android 13 zeigt das System selbst einen Hinweis
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, context.getString(R.string.about_copied), Toast.LENGTH_SHORT).show()
    }
}
