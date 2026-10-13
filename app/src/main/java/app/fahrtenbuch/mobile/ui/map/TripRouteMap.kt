package app.fahrtenbuch.mobile.ui.map

import android.view.Gravity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.fahrtenbuch.mobile.data.model.TrackPoint
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Vektorkarten von OpenFreeMap (kostenlos, ohne API-Key). Für eigene Kacheln, z. B. aus
 * dem Homelab, reicht es, diese beiden URLs auf einen eigenen Style zu ändern.
 */
private const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/positron"
private const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"

private const val ROUTE_SOURCE = "trip-route"
private const val START_SOURCE = "trip-start"
private const val END_SOURCE = "trip-end"

/**
 * Zeigt die GPS-Route einer Fahrt auf einer MapLibre-Karte.
 *
 * [contentPadding] hält die Route von Bereichen frei, die über der Karte liegen
 * (z. B. Titel und Verlauf unten in der Detailansicht). [attributionTopOffset] schiebt
 * den Quellenhinweis (OSM/OpenMapTiles, Pflicht) unter schwebende Buttons oben rechts.
 */
@Composable
internal fun TripRouteMap(
    points: List<TrackPoint>,
    modifier: Modifier = Modifier,
    contentPadding: MapPadding = MapPadding(),
    attributionTopOffset: Dp = 0.dp
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val styleUrl = if (dark) STYLE_DARK else STYLE_LIGHT
    val routeColor = colors.primary.toArgb()
    val ringColor = colors.background.toArgb()

    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply { onCreate(null) }
    }

    // MapView braucht die Lebenszyklus-Aufrufe der Activity.
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView, styleUrl, points, routeColor, ringColor) {
        val padding = with(density) {
            intArrayOf(
                contentPadding.start.roundToPx(),
                contentPadding.top.roundToPx(),
                contentPadding.end.roundToPx(),
                contentPadding.bottom.roundToPx()
            )
        }
        val attributionTop = with(density) { attributionTopOffset.roundToPx() }
        val margin = with(density) { 12.dp.roundToPx() }
        mapView.getMapAsync { map ->
            map.uiSettings.apply {
                isLogoEnabled = false
                isCompassEnabled = false
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                attributionGravity = Gravity.TOP or Gravity.END
                setAttributionMargins(0, attributionTop + margin, margin, 0)
            }
            map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
                drawRoute(style, points, routeColor, ringColor)
                fitCamera(map, points, padding)
            }
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

data class MapPadding(
    val start: Dp = 48.dp,
    val top: Dp = 48.dp,
    val end: Dp = 48.dp,
    val bottom: Dp = 48.dp
)

private fun drawRoute(style: Style, points: List<TrackPoint>, routeColor: Int, ringColor: Int) {
    if (points.isEmpty()) return
    val geoPoints = points.map { Point.fromLngLat(it.lon, it.lat) }

    if (geoPoints.size > 1) {
        style.addSource(GeoJsonSource(ROUTE_SOURCE, Feature.fromGeometry(LineString.fromLngLats(geoPoints))))
        // Breiter, halbtransparenter Saum unter der Linie für besseren Kontrast auf der Karte
        style.addLayer(
            LineLayer("trip-route-casing", ROUTE_SOURCE).withProperties(
                lineColor(routeColor),
                lineWidth(12f),
                lineOpacity(0.25f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
        style.addLayer(
            LineLayer("trip-route-line", ROUTE_SOURCE).withProperties(
                lineColor(routeColor),
                lineWidth(4.5f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
    }

    style.addSource(GeoJsonSource(START_SOURCE, Feature.fromGeometry(geoPoints.first())))
    style.addLayer(
        CircleLayer("trip-start", START_SOURCE).withProperties(
            circleRadius(7f),
            circleColor(ringColor),
            circleStrokeColor(routeColor),
            circleStrokeWidth(4f)
        )
    )
    if (geoPoints.size > 1) {
        style.addSource(GeoJsonSource(END_SOURCE, Feature.fromGeometry(geoPoints.last())))
        style.addLayer(
            CircleLayer("trip-end", END_SOURCE).withProperties(
                circleRadius(9f),
                circleColor(routeColor),
                circleStrokeColor(ringColor),
                circleStrokeWidth(3f)
            )
        )
    }
}

private fun fitCamera(map: MapLibreMap, points: List<TrackPoint>, padding: IntArray) {
    if (points.isEmpty()) return
    val latLngs = points.map { LatLng(it.lat, it.lon) }
    // LatLngBounds braucht mindestens zwei verschiedene Punkte.
    if (latLngs.distinct().size < 2) {
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLngs.first(), 15.0))
        return
    }
    val bounds = LatLngBounds.Builder().includes(latLngs).build()
    map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding[0], padding[1], padding[2], padding[3]))
}
