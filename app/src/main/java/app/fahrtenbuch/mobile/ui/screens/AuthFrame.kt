package app.fahrtenbuch.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fahrtenbuch.mobile.R
import app.fahrtenbuch.mobile.ui.common.LocalWindowLayout

/**
 * Rahmen für Einrichtung und Anmeldung. Auf Tablets quer steht links eine farbige Fläche mit
 * App-Name und Kurzbeschreibung, rechts der eigentliche Bildschirm in lesbarer Breite.
 * Auf Smartphones und hochkant wird der Inhalt unverändert angezeigt.
 */
@Composable
internal fun AuthFrame(content: @Composable () -> Unit) {
    if (!LocalWindowLayout.current.twoPane) {
        content()
        return
    }
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier
                .weight(0.42f)
                .fillMaxHeight()
                .background(colors.primary)
                .safeDrawingPadding()
                .padding(48.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Surface(shape = RoundedCornerShape(16.dp), color = colors.onPrimary, modifier = Modifier.size(56.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = colors.primary)
                }
            }
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = colors.onPrimary
            )
            Text(
                stringResource(R.string.auth_tagline),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onPrimary.copy(alpha = 0.9f)
            )
            Spacer(Modifier.weight(1f))
            listOf(
                stringResource(R.string.auth_feature_bluetooth),
                stringResource(R.string.auth_feature_offline),
                stringResource(R.string.auth_feature_export)
            ).forEach { feature ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(colors.onPrimary.copy(alpha = 0.8f), CircleShape))
                    Text(feature, style = MaterialTheme.typography.bodyLarge, color = colors.onPrimary.copy(alpha = 0.9f))
                }
            }
        }
        Box(Modifier.weight(0.58f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Box(Modifier.widthIn(max = 560.dp)) {
                content()
            }
        }
    }
}
