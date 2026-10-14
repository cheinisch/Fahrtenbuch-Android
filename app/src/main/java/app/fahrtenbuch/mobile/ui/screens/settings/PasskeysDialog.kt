package app.fahrtenbuch.mobile.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.CreateCredentialCancellationException
import app.fahrtenbuch.mobile.R
import app.fahrtenbuch.mobile.data.model.PasskeyInfo
import app.fahrtenbuch.mobile.ui.AppViewModel
import app.fahrtenbuch.mobile.ui.common.LocalUserPreferences
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Passkeys des Kontos: zeigt alle – im Web-Dashboard und in der App angelegte –,
 * legt neue über den Android Credential Manager an, benennt um und löscht.
 */
@Composable
fun PasskeysDialog(viewModel: AppViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val zone = LocalUserPreferences.current.zoneId()
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }

    var passkeys by remember { mutableStateOf<List<PasskeyInfo>?>(null) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<PasskeyInfo?>(null) }
    var deleting by remember { mutableStateOf<PasskeyInfo?>(null) }
    val defaultName = remember { listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Android" } }
    val createdText = stringResource(R.string.passkeys_created)
    val genericError = stringResource(R.string.passkeys_error)

    suspend fun reload() {
        viewModel.listPasskeys()
            .onSuccess { passkeys = it }
            .onFailure { error = viewModel.readableError(it) }
    }
    LaunchedEffect(Unit) { reload() }

    fun formatDate(value: String?): String? =
        value?.let { runCatching { dateFormat.format(Instant.parse(it).atZone(zone)) }.getOrNull() }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(stringResource(R.string.passkeys_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.passkeys_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                val list = passkeys
                when {
                    list == null && error == null -> CircularProgressIndicator(Modifier.size(24.dp))
                    list != null && list.isEmpty() -> Text(stringResource(R.string.passkeys_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    list != null -> list.forEachIndexed { index, passkey ->
                        if (index > 0) HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    passkey.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.passkeys_unnamed),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val details = listOfNotNull(
                                    formatDate(passkey.createdAt)?.let { stringResource(R.string.passkeys_created_at, it) },
                                    formatDate(passkey.lastUsedAt)?.let { stringResource(R.string.passkeys_last_used, it) },
                                    if (passkey.backedUp == true) stringResource(R.string.passkeys_synced) else null
                                )
                                if (details.isNotEmpty()) {
                                    Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconButton(onClick = { renaming = passkey }, enabled = !working) {
                                Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.passkeys_rename))
                            }
                            IconButton(onClick = { deleting = passkey }, enabled = !working) {
                                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.passkeys_delete))
                            }
                        }
                    }
                }

                FilledTonalButton(
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    onClick = {
                        working = true
                        error = null
                        message = null
                        scope.launch {
                            val options = viewModel.passkeyRegisterOptions().getOrElse {
                                error = viewModel.readableError(it)
                                working = false
                                return@launch
                            }
                            try {
                                val result = credentialManager.createCredential(context, CreatePublicKeyCredentialRequest(options))
                                val json = (result as? CreatePublicKeyCredentialResponse)?.registrationResponseJson
                                if (json == null) {
                                    error = genericError
                                } else {
                                    viewModel.registerPasskey(json, defaultName)
                                        .onSuccess { message = createdText; reload() }
                                        .onFailure { error = viewModel.readableError(it) }
                                }
                            } catch (_: CreateCredentialCancellationException) {
                                // vom Nutzer abgebrochen
                            } catch (e: Exception) {
                                error = e.message ?: genericError
                            }
                            working = false
                        }
                    }
                ) {
                    if (working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.passkeys_add), modifier = Modifier.padding(start = 8.dp))
                }
                Text(stringResource(R.string.passkeys_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(enabled = !working, onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )

    renaming?.let { passkey ->
        var name by remember(passkey.id) { mutableStateOf(passkey.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.passkeys_rename)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 120) name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.passkeys_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    renaming = null
                    scope.launch {
                        viewModel.renamePasskey(passkey.id, name.trim())
                            .onSuccess { reload() }
                            .onFailure { error = viewModel.readableError(it) }
                    }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    deleting?.let { passkey ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.passkeys_delete_title)) },
            text = { Text(stringResource(R.string.passkeys_delete_text, passkey.name ?: "")) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        viewModel.deletePasskey(passkey.id)
                            .onSuccess { reload() }
                            .onFailure { error = viewModel.readableError(it) }
                    }
                }) { Text(stringResource(R.string.passkeys_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
