package app.fahrtenbuch.mobile.ui.navigation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fahrtenbuch.mobile.R

data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    /** Zeigt einen pulsierenden roten Punkt, z. B. am Start-Tab während einer Aufzeichnung. */
    val showRecordingDot: Boolean = false
)

/**
 * Untere Navigation (Variante B aus dem Redesign): alle Tabs beschriftet, klare Pille
 * am aktiven Tab, fettes aktives Label und eine feine Trennlinie zum Inhalt.
 */
@Composable
fun AppBottomBar(
    destinations: List<BottomDestination>,
    isSelected: (BottomDestination) -> Boolean,
    onSelect: (BottomDestination) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column {
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
        NavigationBar(
            containerColor = colors.surface,
            tonalElevation = 0.dp
        ) {
            destinations.forEach { destination ->
                val selected = isSelected(destination)
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(destination) },
                    icon = {
                        if (destination.showRecordingDot) {
                            val recordingLabel = stringResource(R.string.nav_recording_active)
                            BadgedBox(
                                badge = { RecordingDot() },
                                modifier = Modifier.semantics { contentDescription = recordingLabel }
                            ) {
                                Icon(destination.icon, contentDescription = null)
                            }
                        } else {
                            Icon(destination.icon, contentDescription = null)
                        }
                    },
                    label = {
                        Text(
                            destination.label,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            maxLines = 1
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.primary,
                        selectedTextColor = colors.onSurface,
                        indicatorColor = colors.primaryContainer,
                        unselectedIconColor = colors.onSurfaceVariant,
                        unselectedTextColor = colors.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun RecordingDot() {
    val pulse by rememberInfiniteTransition(label = "recording-dot").animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulse"
    )
    Badge(
        modifier = Modifier.alpha(pulse),
        containerColor = MaterialTheme.colorScheme.error
    )
}
