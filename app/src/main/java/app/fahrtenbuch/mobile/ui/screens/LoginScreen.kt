package app.fahrtenbuch.mobile.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.fahrtenbuch.mobile.ui.AppViewModel

@Composable
fun LoginScreen(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var totp by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Fahrtenbuch", style = MaterialTheme.typography.headlineMedium)
                Text("Am Server anmelden", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(email, { email = it }, label = { Text("E-Mail") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text("Passwort") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(totp, { totp = it.filter(Char::isDigit).take(6) }, label = { Text("TOTP-Code (optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true, modifier = Modifier.fillMaxWidth())
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = { viewModel.login(email.trim(), password, totp.ifBlank { null }, "${Build.MANUFACTURER} ${Build.MODEL}") },
                    enabled = email.isNotBlank() && password.isNotBlank() && !state.loading,
                    modifier = Modifier.fillMaxWidth()
                ) { if (state.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Anmelden") }
                Text("Passkey und Passwort-Reset sind in der API-Schicht bereits definiert und werden als eigene Oberfläche ergänzt.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
