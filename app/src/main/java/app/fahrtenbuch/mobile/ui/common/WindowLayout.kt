package app.fahrtenbuch.mobile.ui.common

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fenstergröße nach den Material-Breakpoints: Smartphone (< 600 dp), Tablet hochkant bzw.
 * aufgeklapptes Foldable (600–839 dp) und Tablet quer (≥ 840 dp).
 */
enum class WindowWidth { COMPACT, MEDIUM, EXPANDED }

data class WindowLayout(val width: WindowWidth, val widthDp: Dp) {
    /** Navigationsleiste links statt unten. */
    val useNavigationRail: Boolean get() = width != WindowWidth.COMPACT
    /** Platz für zwei Spalten nebeneinander (Liste + Details, zweispaltige Karten). */
    val twoPane: Boolean get() = width == WindowWidth.EXPANDED
    /** Mittelgroß oder größer: Inhalte dürfen breiter gruppiert werden. */
    val wide: Boolean get() = width != WindowWidth.COMPACT

    companion object {
        fun from(widthDp: Dp) = WindowLayout(
            width = when {
                widthDp >= 840.dp -> WindowWidth.EXPANDED
                widthDp >= 600.dp -> WindowWidth.MEDIUM
                else -> WindowWidth.COMPACT
            },
            widthDp = widthDp
        )
    }
}

val LocalWindowLayout = staticCompositionLocalOf { WindowLayout(WindowWidth.COMPACT, 360.dp) }

/** Misst die verfügbare Breite und stellt sie über [LocalWindowLayout] bereit. */
@Composable
fun ProvideWindowLayout(content: @Composable () -> Unit) {
    // Volle Größe weitergeben, damit sich der Inhalt wie direkt unter setContent verhält
    BoxWithConstraints(Modifier.fillMaxSize(), propagateMinConstraints = true) {
        CompositionLocalProvider(LocalWindowLayout provides WindowLayout.from(maxWidth)) {
            content()
        }
    }
}
