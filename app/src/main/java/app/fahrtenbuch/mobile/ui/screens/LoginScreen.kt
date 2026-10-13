package app.fahrtenbuch.mobile.ui.screens

import androidx.compose.ui.res.stringResource
import app.fahrtenbuch.mobile.R
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var totp by rememberSaveable { mutableStateOf("") }
    var resetToken by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var newPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var confirmPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var resetRequested by rememberSaveable { mutableStateOf(false) }
    var resetChanged by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val minHeight = maxHeight
        Box(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = minHeight)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
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
                        Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }

                when (mode) {
                    LoginMode.LOGIN -> {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.server_login), color = MaterialTheme.colorScheme.onSurfaceVariant)

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text(stringResource(R.string.email_or_username)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            enabled = !state.loading,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(stringResource(R.string.password)) },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = stringResource(if (passwordVisible) R.string.password_hide else R.string.password_show)
                                    )
                                }
                            },
                            singleLine = true,
                            enabled = !state.loading,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = totp,
                            onValueChange = { totp = it.filter(Char::isDigit).take(6) },
                            label = { Text(stringResource(R.string.totp_code)) },
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
                                Text(stringResource(R.string.sign_in))
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
                                            viewModel.cancelAuthLoading(context.getString(R.string.error_not_passkey))
                                        }
                                    } catch (error: Exception) {
                                        viewModel.cancelAuthLoading(
                                            error.message ?: context.getString(R.string.error_passkey_cancelled)
                                        )
                                    }
                                }
                            },
                            enabled = !state.loading,
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Icon(Icons.Outlined.Key, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.sign_in_passkey))
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
                            Text(stringResource(R.string.forgot_password))
                        }
                    }

                    LoginMode.FORGOT -> {
                        Text(stringResource(R.string.reset_password), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        if (!resetRequested) {
                            Text(
                                stringResource(R.string.reset_intro),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text(stringResource(R.string.email)) },
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
                                else Text(stringResource(R.string.request_reset))
                            }
                        } else {
                            Text(
                                stringResource(R.string.reset_accepted),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { mode = LoginMode.RESET },
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) { Text(stringResource(R.string.enter_reset_token)) }
                            TextButton(
                                onClick = { mode = LoginMode.LOGIN },
                                modifier = Modifier.align(Alignment.End)
                            ) { Text(stringResource(R.string.back_to_login)) }
                        }
                    }

                    LoginMode.RESET -> {
                        Text(stringResource(R.string.new_password), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        if (resetChanged) {
                            Text(stringResource(R.string.password_changed))
                            Button(
                                onClick = {
                                    resetChanged = false
                                    mode = LoginMode.LOGIN
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) { Text(stringResource(R.string.sign_in_now)) }
                        } else {
                            OutlinedTextField(
                                value = resetToken,
                                onValueChange = { resetToken = it.trim() },
                                label = { Text(stringResource(R.string.reset_token)) },
                                singleLine = true,
                                enabled = !state.loading,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = newPassword,
                                onValueChange = { newPassword = it },
                                label = { Text(stringResource(R.string.new_password)) },
                                visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                trailingIcon = {
                                    IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                                        Icon(
                                            imageVector = if (newPasswordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                            contentDescription = stringResource(if (newPasswordVisible) R.string.password_hide else R.string.password_show)
                                        )
                                    }
                                },
                                singleLine = true,
                                enabled = !state.loading,
                                supportingText = { Text(stringResource(R.string.min_eight_chars)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it },
                                label = { Text(stringResource(R.string.repeat_password)) },
                                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                trailingIcon = {
                                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                        Icon(
                                            imageVector = if (confirmPasswordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                            contentDescription = stringResource(if (confirmPasswordVisible) R.string.password_hide else R.string.password_show)
                                        )
                                    }
                                },
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
                                else Text(stringResource(R.string.change_password))
                            }
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
