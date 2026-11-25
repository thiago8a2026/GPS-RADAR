package com.example.ui.components

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.BuildConfig
import com.example.navigation.LatLngPoint
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

private fun ensureOsmdroidConfigured(context: Context) {
    val config = Configuration.getInstance()
    config.userAgentValue = BuildConfig.APPLICATION_ID
    val baseDir = File(context.cacheDir, "osmdroid")
    if (!baseDir.exists()) baseDir.mkdirs()
    val tileDir = File(baseDir, "tiles")
    if (!tileDir.exists()) tileDir.mkdirs()
    config.osmdroidBasePath = baseDir
    config.osmdroidTileCache = tileDir
}

@Composable
fun OsmMapView(
    targetPoint: LatLngPoint,
    routePoints: List<LatLngPoint>,
    pointA: LatLngPoint?,
    pointB: LatLngPoint?,
    zoomLevel: Double,
    recenterNonce: Int,
    isSimulating: Boolean,
    onPinMoved: (LatLngPoint, Boolean) -> Unit,
    onZoomChanged: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentOnPinMoved by rememberUpdatedState(onPinMoved)
    val currentOnZoomChanged by rememberUpdatedState(onZoomChanged)

    var lastHandledRecenterNonce by remember { mutableIntStateOf(recenterNonce) }
    var lastHandledZoom by remember { mutableIntStateOf((zoomLevel * 10).toInt()) }

    val mapView = remember {
        ensureOsmdroidConfigured(context.applicationContext)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = 3.0
            maxZoomLevel = 20.0
            controller.setZoom(zoomLevel)
            controller.setCenter(GeoPoint(targetPoint.latitude, targetPoint.longitude))
        }
    }

    val routePolyline = remember(mapView) {
        Polyline(mapView, true).apply {
            outlinePaint.color = Color.parseColor("#00F0FF")
            outlinePaint.strokeWidth = 10f
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
        }
    }

    val markerA = remember(mapView) {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Punto A (Inicio)"
            alpha = 0.85f
        }
    }

    val markerB = remember(mapView) {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Punto B (Destino)"
            alpha = 0.85f
        }
    }

    val marker = remember(mapView) {
        Marker(mapView).apply {
            position = GeoPoint(targetPoint.latitude, targetPoint.longitude)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            isDraggable = true
            title = "Pin GPS ANTHI"
            setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                override fun onMarkerDrag(marker: Marker) = Unit

                override fun onMarkerDragEnd(marker: Marker) {
                    val pos = marker.position
                    currentOnPinMoved(LatLngPoint(pos.latitude, pos.longitude), false)
                }

                override fun onMarkerDragStart(marker: Marker) = Unit
            })
        }
    }

    DisposableEffect(mapView) {
        val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                p?.let {
                    currentOnPinMoved(LatLngPoint(it.latitude, it.longitude), false)
                }
                return true
            }

            override fun longPressHelper(p: GeoPoint?): Boolean {
                p?.let {
                    currentOnPinMoved(LatLngPoint(it.latitude, it.longitude), true)
                }
                return true
            }
        })

        val mapListener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean = false

            override fun onZoom(event: ZoomEvent?): Boolean {
                val z = event?.zoomLevel ?: mapView.zoomLevelDouble
                lastHandledZoom = (z * 10).toInt()
                currentOnZoomChanged(z)
                return false
            }
        }

        if (!mapView.overlays.contains(eventsOverlay)) {
            mapView.overlays.add(0, eventsOverlay)
        }
        if (!mapView.overlays.contains(routePolyline)) {
            mapView.overlays.add(routePolyline)
        }
        if (!mapView.overlays.contains(markerA)) {
            mapView.overlays.add(markerA)
        }
        if (!mapView.overlays.contains(markerB)) {
            mapView.overlays.add(markerB)
        }
        if (!mapView.overlays.contains(marker)) {
            mapView.overlays.add(marker)
        }
        mapView.addMapListener(mapListener)

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.removeMapListener(mapListener)
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.testTag("osm_map_view"),
        update = { view ->
            // 1. Polilínea de ruta vial
            if (routePoints.size >= 2) {
                routePolyline.isVisible = true
                routePolyline.setPoints(routePoints.map { GeoPoint(it.latitude, it.longitude) })
            } else {
                routePolyline.isVisible = false
                routePolyline.setPoints(emptyList())
            }

            // 2. Marcadores A / B
            if (pointA != null) {
                markerA.isEnabled = true
                markerA.position = GeoPoint(pointA.latitude, pointA.longitude)
            } else {
                markerA.isEnabled = false
            }

            if (pointB != null) {
                markerB.isEnabled = true
                markerB.position = GeoPoint(pointB.latitude, pointB.longitude)
            } else {
                markerB.isEnabled = false
            }

            // 3. Pin principal activo
            val geoPoint = GeoPoint(targetPoint.latitude, targetPoint.longitude)
            if (marker.position.latitude != geoPoint.latitude ||
                marker.position.longitude != geoPoint.longitude
            ) {
                marker.position = geoPoint
            }
            marker.snippet = if (isSimulating) {
                "Simulando ubicación activa"
            } else {
                "Toca el mapa o arrastra el pin"
            }

            val targetZoomInt = (zoomLevel * 10).toInt()
            if (targetZoomInt != lastHandledZoom) {
                lastHandledZoom = targetZoomInt
                view.controller.setZoom(zoomLevel)
            }

            if (recenterNonce != lastHandledRecenterNonce) {
                lastHandledRecenterNonce = recenterNonce
                view.controller.animateTo(geoPoint)
            }

            view.invalidate()
        }
    )
}
