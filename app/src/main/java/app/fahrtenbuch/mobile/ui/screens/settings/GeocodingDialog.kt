package app.fahrtenbuch.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.fahrtenbuch.mobile.R
import app.fahrtenbuch.mobile.data.api.GeocoderProvider
import app.fahrtenbuch.mobile.location.PhotonGeocoder
import kotlinx.coroutines.launch

// Testpunkt für „Verbindung testen“: Frankfurter Römer – liegt in jedem Europa-/Weltindex
private const val TEST_LAT = 50.1106
private const val TEST_LON = 8.6821

private sealed interface PhotonTest {
    data object Idle : PhotonTest
    data object Running : PhotonTest
    data class Success(val address: String?) : PhotonTest
    data class Failure(val message: String) : PhotonTest
}

/**
 * Adressauflösung im lokalen Modus: Android-Geocoder oder ein Photon-Server (eigener oder
 * der öffentliche Server von komoot als Vorschlag).
 */
@Composable
fun GeocodingDialog(
    provider: GeocoderProvider,
    photonUrl: String?,
    onDismiss: () -> Unit,
    onSave: (provider: GeocoderProvider, photonUrl: String?) -> Unit
) {
    var selected by rememberSaveable { mutableStateOf(provider) }
    var url by rememberSaveable { mutableStateOf(photonUrl.orEmpty()) }
    var test by remember { mutableStateOf<PhotonTest>(PhotonTest.Idle) }
    val scope = rememberCoroutineScope()
    val normalized = PhotonGeocoder.normalize(url)
    val urlInvalid = url.isNotBlank() && normalized == null
    val canSave = selected == GeocoderProvider.SYSTEM || normalized != null
    val isPublicServer = normalized?.equals(PhotonGeocoder.PUBLIC_SERVER, ignoreCase = true) == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.geocoding_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.geocoding_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(Modifier.selectableGroup()) {
                    ProviderOption(
                        title = stringResource(R.string.geocoding_system),
                        subtitle = stringResource(R.string.geocoding_system_help),
                        selected = selected == GeocoderProvider.SYSTEM,
                        onClick = { selected = GeocoderProvider.SYSTEM }
                    )
                    ProviderOption(
                        title = stringResource(R.string.geocoding_photon),
                        subtitle = stringResource(R.string.geocoding_photon_help),
                        selected = selected == GeocoderProvider.PHOTON,
                        onClick = { selected = GeocoderProvider.PHOTON }
                    )
                }

                if (selected == GeocoderProvider.PHOTON) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = {
                            url = it
                            test = PhotonTest.Idle
                        },
                        label = { Text(stringResource(R.string.geocoding_photon_url)) },
                        supportingText = {
                            Text(
                                stringResource(if (urlInvalid) R.string.geocoding_photon_url_invalid else R.string.geocoding_photon_url_help)
                            )
                        },
                        isError = urlInvalid,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Vorschlag: öffentlicher Server, wenn noch kein eigener eingetragen ist
                    if (!isPublicServer) {
                        AssistChip(
                            onClick = {
                                url = PhotonGeocoder.PUBLIC_SERVER
                                test = PhotonTest.Idle
                            },
                            label = { Text(stringResource(R.string.geocoding_photon_use_public)) },
                            leadingIcon = {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            }
                        )
                    }
                    if (isPublicServer) {
                        Text(
                            stringResource(R.string.geocoding_photon_public_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        stringResource(R.string.geocoding_photon_privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        enabled = normalized != null && test != PhotonTest.Running,
                        onClick = {
                            val target = normalized ?: return@OutlinedButton
                            test = PhotonTest.Running
                            scope.launch {
                                test = runCatching { PhotonGeocoder.reverse(target, TEST_LAT, TEST_LON) }
                                    .fold(
                                        onSuccess = { PhotonTest.Success(it) },
                                        onFailure = { PhotonTest.Failure(it.message ?: it.javaClass.simpleName) }
                                    )
                            }
                        }
                    ) {
                        Text(stringResource(if (test == PhotonTest.Running) R.string.geocoding_testing else R.string.geocoding_test))
                    }
                    when (val result = test) {
                        is PhotonTest.Success -> Text(
                            if (result.address != null) stringResource(R.string.geocoding_test_ok, result.address)
                            else stringResource(R.string.geocoding_test_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (result.address != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        is PhotonTest.Failure -> Text(
                            stringResource(R.string.geocoding_test_failed, result.message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        else -> Unit
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    // URL bleibt auch beim Wechsel auf System gespeichert, damit man zurückwechseln kann
                    onSave(selected, normalized)
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun ProviderOption(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
