package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.navigation.LatLngPoint
import com.example.navigation.OsrmRoutingService
import com.example.navigation.RouteTraversalMode
import com.example.root.RootDiagnostics
import com.example.root.RootManager
import com.example.root.RootStatus
import com.example.service.GpsAlignmentHelper
import com.example.service.LocationSpooferService
import com.example.service.SystemGpsAlignmentStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

enum class ControlTabMode {
    FIXED_POINT,
    ROAD_ROUTE
}

data class GpsAnthiUiState(
    val controlMode: ControlTabMode = ControlTabMode.FIXED_POINT,
    val targetPoint: LatLngPoint = LatLngPoint(7.0622, -73.0864),
    val latInput: String = "7.0622",
    val lngInput: String = "-73.0864",
    val coordinateError: String? = null,
    val pointA: LatLngPoint? = LatLngPoint(7.0622, -73.0864),
    val pointB: LatLngPoint? = null,
    val routePoints: List<LatLngPoint> = emptyList(),
    val routeDistanceMeters: Double = 0.0,
    val routeSourceLabel: String? = null,
    val isCalculatingRoute: Boolean = false,
    val traversalMode: RouteTraversalMode = RouteTraversalMode.ROUND_TRIP,
    val speedKmh: Float = 35f,
    val isSimulating: Boolean = false,
    val zoomLevel: Double = 16.0,
    val recenterNonce: Int = 0,
    val rootDiagnostics: RootDiagnostics = RootDiagnostics(),
    val gpsAlignment: SystemGpsAlignmentStatus? = null,
    val feedbackMessage: String? = null
) {
    val spoofStatusText: String
        get() = if (isSimulating) {
            val prefix = if (routePoints.size >= 2 && controlMode == ControlTabMode.ROAD_ROUTE) {
                "Ruta (${speedKmh.toInt()} km/h)"
            } else {
                "Simulando"
            }
            "Estado: $prefix • %.6f, %.6f".format(
                Locale.US,
                targetPoint.latitude,
                targetPoint.longitude
            )
        } else {
            "Estado: Inactivo"
        }

    val rootStatusLabel: String
        get() = when (rootDiagnostics.status) {
            RootStatus.CHECKING -> "Root: Verificando..."
            RootStatus.GRANTED -> "Root: Concedido"
            RootStatus.DENIED -> "Root: Denegado"
            RootStatus.NOT_ROOTED -> "Sin Root"
            RootStatus.TIMEOUT -> "Root: Timeout"
        }
}

class GpsAnthiViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GpsAnthiUiState())
    val uiState: StateFlow<GpsAnthiUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            RootManager.rootState.collect { diagnostics ->
                _uiState.update { it.copy(rootDiagnostics = diagnostics) }
            }
        }
        viewModelScope.launch {
            LocationSpooferService.serviceState.collect { serviceState ->
                _uiState.update { current ->
                    val syncedTarget = if (serviceState.isRunning && serviceState.isRouteActive) {
                        serviceState.activePoint
                    } else {
                        current.targetPoint
                    }
                    current.copy(
                        isSimulating = serviceState.isRunning,
                        targetPoint = syncedTarget,
                        latInput = if (serviceState.isRunning && serviceState.isRouteActive) {
                            "%.6f".format(Locale.US, syncedTarget.latitude)
                        } else {
                            current.latInput
                        },
                        lngInput = if (serviceState.isRunning && serviceState.isRouteActive) {
                            "%.6f".format(Locale.US, syncedTarget.longitude)
                        } else {
                            current.lngInput
                        },
                        feedbackMessage = serviceState.lastError ?: current.feedbackMessage
                    )
                }
            }
        }
        viewModelScope.launch {
            RootManager.verifyRootAccess(forceRefresh = false)
            refreshGpsAlignment()
        }
    }

    fun refreshGpsAlignment() {
        viewModelScope.launch {
            val status = GpsAlignmentHelper.inspectAlignment(getApplication())
            _uiState.update { it.copy(gpsAlignment = status) }
        }
    }

    fun onVerifyRootClicked() {
        viewModelScope.launch {
            RootManager.verifyRootAccess(forceRefresh = true)
            refreshGpsAlignment()
        }
    }

    fun onControlModeChanged(mode: ControlTabMode) {
        _uiState.update { it.copy(controlMode = mode, feedbackMessage = null) }
    }

    fun onLatInputChanged(value: String) {
        _uiState.update {
            it.copy(
                latInput = value,
                coordinateError = null,
                feedbackMessage = null
            )
        }
    }

    fun onLngInputChanged(value: String) {
        _uiState.update {
            it.copy(
                lngInput = value,
                coordinateError = null,
                feedbackMessage = null
            )
        }
    }

    fun onMapPinSelected(point: LatLngPoint, centerMap: Boolean = false) {
        val clampedLat = point.latitude.coerceIn(-90.0, 90.0)
        val clampedLng = point.longitude.coerceIn(-180.0, 180.0)
        val newPoint = LatLngPoint(clampedLat, clampedLng)
        val state = _uiState.value

        _uiState.update {
            it.copy(
                targetPoint = newPoint,
                latInput = "%.6f".format(Locale.US, clampedLat),
                lngInput = "%.6f".format(Locale.US, clampedLng),
                coordinateError = null,
                recenterNonce = if (centerMap) it.recenterNonce + 1 else it.recenterNonce,
                feedbackMessage = null
            )
        }

        if (state.isSimulating && state.controlMode == ControlTabMode.FIXED_POINT) {
            val context = getApplication<Application>()
            context.startService(
                LocationSpooferService.createUpdateIntent(context, clampedLat, clampedLng)
            )
        }
    }

    fun onApplyCoordinatesClicked(): Boolean {
        val state = _uiState.value
        val parsedLat = state.latInput.trim().replace(',', '.').toDoubleOrNull()
        val parsedLng = state.lngInput.trim().replace(',', '.').toDoubleOrNull()

        if (parsedLat == null || parsedLat !in -90.0..90.0) {
            _uiState.update {
                it.copy(coordinateError = "Latitud inválida (rango permitido: -90 a 90)")
            }
            return false
        }
        if (parsedLng == null || parsedLng !in -180.0..180.0) {
            _uiState.update {
                it.copy(coordinateError = "Longitud inválida (rango permitido: -180 a 180)")
            }
            return false
        }

        val updatedPoint = LatLngPoint(parsedLat, parsedLng)
        val wasSimulating = _uiState.value.isSimulating

        _uiState.update {
            it.copy(
                targetPoint = updatedPoint,
                latInput = "%.6f".format(Locale.US, parsedLat),
                lngInput = "%.6f".format(Locale.US, parsedLng),
                coordinateError = null,
                recenterNonce = it.recenterNonce + 1,
                feedbackMessage = null
            )
        }

        if (wasSimulating && state.controlMode == ControlTabMode.FIXED_POINT) {
            val context = getApplication<Application>()
            context.startService(
                LocationSpooferService.createUpdateIntent(context, parsedLat, parsedLng)
            )
        }
        return true
    }

    fun onSetPointAFromPin() {
        val current = _uiState.value.targetPoint
        _uiState.update {
            it.copy(
                pointA = current,
                routePoints = emptyList(),
                routeSourceLabel = null,
                feedbackMessage = "Punto A fijado. Mueve el pin para elegir el Punto B."
            )
        }
    }

    fun onSetPointBFromPin() {
        val current = _uiState.value.targetPoint
        _uiState.update {
            it.copy(
                pointB = current,
                feedbackMessage = null
            )
        }
        onCalculateRoadRoute()
    }

    fun onCalculateRoadRoute() {
        val pointA = _uiState.value.pointA
        val pointB = _uiState.value.pointB
        if (pointA == null || pointB == null) {
            _uiState.update {
                it.copy(feedbackMessage = "Fija Punto A y Punto B para calcular la ruta vial.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCalculatingRoute = true, feedbackMessage = null) }
            val result = OsrmRoutingService.calculateRoadRoute(
                waypoints = listOf(pointA, pointB),
                speedKmh = _uiState.value.speedKmh
            )
            _uiState.update {
                it.copy(
                    isCalculatingRoute = false,
                    routePoints = result.points,
                    routeDistanceMeters = result.distanceMeters,
                    routeSourceLabel = result.sourceLabel,
                    feedbackMessage = if (!result.isRoadSnapped) result.sourceLabel else null
                )
            }
        }
    }

    fun onClearRoute() {
        _uiState.update {
            it.copy(
                pointB = null,
                routePoints = emptyList(),
                routeDistanceMeters = 0.0,
                routeSourceLabel = null,
                feedbackMessage = null
            )
        }
    }

    fun onTraversalModeChanged(mode: RouteTraversalMode) {
        _uiState.update { it.copy(traversalMode = mode) }
    }

    fun onSpeedChanged(speedKmh: Float) {
        val clamped = speedKmh.coerceIn(5f, 80f)
        _uiState.update { it.copy(speedKmh = clamped) }
        if (_uiState.value.isSimulating && _uiState.value.controlMode == ControlTabMode.ROAD_ROUTE) {
            LocationSpooferService.updateSpeed(getApplication(), clamped)
        }
    }

    fun onRecenterClicked() {
        _uiState.update { it.copy(recenterNonce = it.recenterNonce + 1) }
    }

    fun onZoomInClicked() {
        _uiState.update { it.copy(zoomLevel = (it.zoomLevel + 1.0).coerceAtMost(20.0)) }
    }

    fun onZoomOutClicked() {
        _uiState.update { it.copy(zoomLevel = (it.zoomLevel - 1.0).coerceAtLeast(3.0)) }
    }

    fun onZoomChangedFromMap(newZoom: Double) {
        _uiState.update { it.copy(zoomLevel = newZoom.coerceIn(3.0, 20.0)) }
    }

    fun onPlayClicked() {
        val state = _uiState.value
        if (state.controlMode == ControlTabMode.FIXED_POINT && !onApplyCoordinatesClicked()) {
            return
        }
        if (state.controlMode == ControlTabMode.ROAD_ROUTE && state.routePoints.size < 2) {
            _uiState.update {
                it.copy(feedbackMessage = "Define Punto A, Punto B y traza la ruta vial antes de iniciar.")
            }
            return
        }

        viewModelScope.launch {
            val diagnostics = RootManager.verifyRootAccess(forceRefresh = false)
            refreshGpsAlignment()
            if (diagnostics.status != RootStatus.GRANTED) {
                _uiState.update {
                    it.copy(
                        isSimulating = false,
                        feedbackMessage = "Se requiere acceso root (su / Magisk) activo para iniciar."
                    )
                }
                return@launch
            }

            val latest = _uiState.value
            val context = getApplication<Application>()
            if (latest.controlMode == ControlTabMode.ROAD_ROUTE && latest.routePoints.size >= 2) {
                LocationSpooferService.startRoute(
                    context = context,
                    routePoints = latest.routePoints,
                    speedKmh = latest.speedKmh,
                    traversalMode = latest.traversalMode
                )
            } else {
                LocationSpooferService.start(
                    context = context,
                    latitude = latest.targetPoint.latitude,
                    longitude = latest.targetPoint.longitude
                )
            }
            _uiState.update {
                it.copy(
                    isSimulating = true,
                    feedbackMessage = null
                )
            }
        }
    }

    fun onStopClicked() {
        LocationSpooferService.stop(getApplication())
        _uiState.update {
            it.copy(
                isSimulating = false,
                feedbackMessage = null
            )
        }
        refreshGpsAlignment()
    }
}
