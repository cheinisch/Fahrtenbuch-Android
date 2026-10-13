package app.fahrtenbuch.mobile.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fahrtenbuch.mobile.R
import app.fahrtenbuch.mobile.ui.permissions.PermissionChecklist
import app.fahrtenbuch.mobile.ui.permissions.rememberPermissionStatus

/**
 * Einrichtungsschritt für die Berechtigungen der automatischen Fahrtenaufzeichnung.
 * Alles ist freiwillig: „Weiter“ geht immer, die App funktioniert dann mit manuellem Start.
 * Dieselbe Liste steht später in den Einstellungen unter „Berechtigungen“ zur Verfügung.
 */
@Composable
fun LocationPermissionScreen(onContinue: () -> Unit) {
    val (status, refresh) = rememberPermissionStatus()

    CenteredOnboardingLayout(
        actions = {
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    stringResource(
                        if (status.foregroundLocation && status.backgroundLocation) R.string.location_notice_continue
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
        PermissionChecklist(status = status, onChanged = refresh)
    }
}
