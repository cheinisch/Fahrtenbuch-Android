package app.fahrtenbuch.mobile.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import app.fahrtenbuch.mobile.ui.AppViewModel
import kotlinx.coroutines.launch

private enum class LoginMode { LOGIN, FORGOT, RESET }

@Composable
fun LoginScreen(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val deviceName = remember {
        listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.joinToString(" ")
    }

    var mode by rememberSaveable { mutableStateOf(LoginMode.LOGIN) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var totp by rememberSaveable { mutableStateOf("") }
    var resetToken by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var resetRequested by rememberSaveable { mutableStateOf(false) }
    var resetChanged by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (mode != LoginMode.LOGIN) {
                    IconButton(
                        onClick = {
                            mode = LoginMode.LOGIN
                            viewModel.cancelAuthLoading()
                        },
                        enabled = !state.loading
                    ) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Zurück")
                    }
                }

                when (mode) {
                    LoginMode.LOGIN -> {
                        Text("Fahrtenbuch", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("Am Server anmelden", color = MaterialTheme.colorScheme.onSurfaceVariant)

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("E-Mail oder Benutzername") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            enabled = !state.loading,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Passwort") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            enabled = !state.loading,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = totp,
                            onValueChange = { totp = it.filter(Char::isDigit).take(6) },
                            label = { Text("TOTP-Code (falls aktiviert)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            enabled = !state.loading,
                            modifier = Modifier.fillMaxWidth()
                        )

                        AuthError(state.error)

                        Button(
                            onClick = {
                                viewModel.login(
                                    email.trim(),
                                    password,
                                    totp.ifBlank { null },
                                    deviceName
                                )
                            },
                            enabled = email.isNotBlank() && password.isNotBlank() && !state.loading,
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            if (state.loading) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Anmelden")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val options = viewModel.passkeyOptions().getOrNull() ?: return@launch
                                    try {
                                        val result = credentialManager.getCredential(
                                            context = context,
                                            request = GetCredentialRequest(
                                                listOf(GetPublicKeyCredentialOption(requestJson = options))
                                            )
                                        )
                                        val credential = result.credential
                                        if (credential is PublicKeyCredential) {
                                            viewModel.passkeyLogin(
                                                credential.authenticationResponseJson,
                                                deviceName
                                            )
                                        } else {
                                            viewModel.cancelAuthLoading("Der ausgewählte Anmeldenachweis ist kein Passkey.")
                                        }
                                    } catch (error: Exception) {
                                        viewModel.cancelAuthLoading(
                                            error.message ?: "Passkey-Anmeldung wurde abgebrochen."
                                        )
                                    }
                                }
                            },
                            enabled = !state.loading,
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Icon(Icons.Outlined.Key, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Mit Passkey anmelden")
                        }

                        TextButton(
                            onClick = {
                                mode = LoginMode.FORGOT
                                resetRequested = false
                                viewModel.cancelAuthLoading()
                            },
                            enabled = !state.loading,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Passwort vergessen?")
                        }
                    }

                    LoginMode.FORGOT -> {
                        Text("Passwort zurücksetzen", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        if (!resetRequested) {
                            Text(
                                "Gib deine E-Mail-Adresse ein. Wenn ein aktives Konto existiert, erhältst du einen Reset-Link.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("E-Mail") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                singleLine = true,
                                enabled = !state.loading,
                                modifier = Modifier.fillMaxWidth()
                            )
                            AuthError(state.error)
                            Button(
                                onClick = {
                                    viewModel.forgotPassword(email.trim()) {
                                        resetRequested = true
                                    }
                                },
                                enabled = email.isNotBlank() && !state.loading,
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) {
                                if (state.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                else Text("Reset-Link anfordern")
                            }
                        } else {
                            Text(
                                "Die Anfrage wurde angenommen. Prüfe dein Postfach und öffne den Reset-Link. Du kannst den enthaltenen Token auch hier eingeben.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { mode = LoginMode.RESET },
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) { Text("Reset-Token eingeben") }
                            TextButton(
                                onClick = { mode = LoginMode.LOGIN },
                                modifier = Modifier.align(Alignment.End)
                            ) { Text("Zur Anmeldung") }
                        }
                    }

                    LoginMode.RESET -> {
                        Text("Neues Passwort", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        if (resetChanged) {
                            Text("Das Passwort wurde geändert. Alle bisherigen Sitzungen wurden vom Server widerrufen.")
                            Button(
                                onClick = {
                                    resetChanged = false
                                    mode = LoginMode.LOGIN
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) { Text("Jetzt anmelden") }
                        } else {
                            OutlinedTextField(
                                value = resetToken,
                                onValueChange = { resetToken = it.trim() },
                                label = { Text("Reset-Token") },
                                singleLine = true,
                                enabled = !state.loading,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = newPassword,
                                onValueChange = { newPassword = it },
                                label = { Text("Neues Passwort") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                enabled = !state.loading,
                                supportingText = { Text("Mindestens 8 Zeichen") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it },
                                label = { Text("Passwort wiederholen") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                enabled = !state.loading,
                                isError = confirmPassword.isNotEmpty() && confirmPassword != newPassword,
                                modifier = Modifier.fillMaxWidth()
                            )
                            AuthError(state.error)
                            Button(
                                onClick = {
                                    viewModel.resetPassword(resetToken, newPassword) {
                                        resetChanged = true
                                    }
                                },
                                enabled = resetToken.length >= 20 &&
                                    newPassword.length >= 8 &&
                                    newPassword == confirmPassword &&
                                    !state.loading,
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) {
                                if (state.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                else Text("Passwort ändern")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthError(message: String?) {
    if (!message.isNullOrBlank()) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}
