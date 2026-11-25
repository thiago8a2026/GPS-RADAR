package com.example.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.GpsAnthiApp
import com.example.MainActivity
import com.example.R
import com.example.navigation.LatLngPoint
import com.example.navigation.RouteTraversalMode
import com.example.navigation.SimulationEngine
import com.example.root.RootManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

data class SpoofServiceState(
    val isRunning: Boolean = false,
    val isRouteActive: Boolean = false,
    val activePoint: LatLngPoint = LatLngPoint(7.0622, -73.0864),
    val currentBearingDegrees: Float = 0f,
    val currentSpeedKmh: Float = 0f,
    val lastError: String? = null
)

class LocationSpooferService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var simulationJob: Job? = null
    private var providersRegistered = false

    @Volatile
    private var currentTarget = LatLngPoint(7.0622, -73.0864)

    @Volatile
    private var currentBearingDegrees = 0f

    @Volatile
    private var currentSpeedKmh = 0f

    @Volatile
    private var useMicroJitter = true

    @Volatile
    private var activeRoutePoints: List<LatLngPoint> = emptyList()

    @Volatile
    private var traversalMode: RouteTraversalMode = RouteTraversalMode.ROUND_TRIP

    inner class LocalBinder : Binder() {
        fun getService(): LocationSpooferService = this@LocationSpooferService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val lat = intent.getDoubleExtra(EXTRA_LATITUDE, Double.NaN)
                val lng = intent.getDoubleExtra(EXTRA_LONGITUDE, Double.NaN)
                if (lat.isNaN() || lng.isNaN()) {
                    stopForegroundCleanly()
                    return START_NOT_STICKY
                }
                val jitter = intent.getBooleanExtra(EXTRA_JITTER_ENABLED, true)
                val speed = intent.getFloatExtra(EXTRA_SPEED_KMH, 35f)
                val modeName = intent.getStringExtra(EXTRA_TRAVERSAL_MODE)
                val mode = RouteTraversalMode.entries.firstOrNull { it.name == modeName }
                    ?: RouteTraversalMode.ROUND_TRIP
                val route = pendingRouteSnapshot.ifEmpty { emptyList() }
                startSpoofing(
                    target = LatLngPoint(lat, lng),
                    routePoints = route,
                    speedKmh = speed,
                    mode = mode,
                    jitterEnabled = jitter
                )
            }

            ACTION_UPDATE_COORDS -> {
                val lat = intent.getDoubleExtra(EXTRA_LATITUDE, Double.NaN)
                val lng = intent.getDoubleExtra(EXTRA_LONGITUDE, Double.NaN)
                if (!lat.isNaN() && !lng.isNaN()) {
                    updateCoordinates(LatLngPoint(lat, lng))
                }
            }

            ACTION_UPDATE_SPEED -> {
                val speed = intent.getFloatExtra(EXTRA_SPEED_KMH, currentSpeedKmh)
                currentSpeedKmh = speed.coerceIn(1f, 120f)
                _serviceState.value = _serviceState.value.copy(currentSpeedKmh = currentSpeedKmh)
            }

            ACTION_STOP -> {
                stopSpoofingAndRestore()
            }
        }
        return START_NOT_STICKY
    }

    private fun startSpoofing(
        target: LatLngPoint,
        routePoints: List<LatLngPoint>,
        speedKmh: Float,
        mode: RouteTraversalMode,
        jitterEnabled: Boolean
    ) {
        val isRoute = routePoints.size >= 2
        currentTarget = if (isRoute) routePoints.first() else target
        activeRoutePoints = if (isRoute) routePoints else emptyList()
        traversalMode = mode
        currentSpeedKmh = if (isRoute) speedKmh.coerceIn(1f, 120f) else 0f
        currentBearingDegrees = if (isRoute) {
            SimulationEngine.calculateBearingDegrees(routePoints[0], routePoints[1])
        } else {
            0f
        }
        useMicroJitter = jitterEnabled

        promoteToForeground(currentTarget, isRoute)
        _serviceState.value = SpoofServiceState(
            isRunning = true,
            isRouteActive = isRoute,
            activePoint = currentTarget,
            currentBearingDegrees = currentBearingDegrees,
            currentSpeedKmh = currentSpeedKmh,
            lastError = null
        )

        simulationJob?.cancel()
        simulationJob = serviceScope.launch {
            val aligned = GpsAlignmentHelper.prepareRootLocationEnvironment(packageName)
            if (!aligned) {
                Log.w(TAG, "AppOps mock_location no confirmó por root; intentando proveedores")
            }

            val registered = setupTestProviders()
            if (!registered) {
                _serviceState.value = SpoofServiceState(
                    isRunning = false,
                    isRouteActive = false,
                    activePoint = currentTarget,
                    lastError = "No se pudo inicializar ningún proveedor de ubicación simulada. Verifica root o ubicación del sistema."
                )
                stopForegroundCleanly()
                return@launch
            }

            if (isRoute) {
                runRouteLoop()
            } else {
                runStaticLoop()
            }
        }
    }

    private suspend fun runStaticLoop() {
        while (serviceScope.isActive && simulationJob?.isActive == true) {
            val pointToInject = if (useMicroJitter) {
                SimulationEngine.applyJitter(currentTarget, intensityMeters = 0.8)
            } else {
                currentTarget
            }
            applyMockLocationCommand(
                point = pointToInject,
                bearingDegrees = 0f,
                speedMetersPerSec = 0f
            )
            delay(STATIC_INTERVAL_MS)
        }
    }

    private suspend fun runRouteLoop() {
        var segIndex = 0
        var distIntoSeg = 0.0
        var reversing = false
        var tickCount = 0

        while (serviceScope.isActive && simulationJob?.isActive == true) {
            val route = activeRoutePoints
            if (route.size < 2) break

            val speedMs = (currentSpeedKmh * 1000.0) / 3600.0
            val stepMeters = speedMs * (ROUTE_INTERVAL_MS / 1000.0)

            val step = SimulationEngine.advanceAlongPolyline(
                points = route,
                currentSegmentIndex = segIndex,
                distanceIntoSegmentMeters = distIntoSeg,
                stepMeters = stepMeters,
                mode = traversalMode,
                isReversing = reversing
            )

            segIndex = step.segmentIndex
            distIntoSeg = step.distanceIntoSegmentMeters
            reversing = step.isReversing
            currentTarget = step.position
            currentBearingDegrees = step.bearingDegrees

            val effectiveSpeedMs = if (step.finishedOneWay) 0f else speedMs.toFloat()
            val pointToInject = if (useMicroJitter && !step.finishedOneWay) {
                SimulationEngine.applyJitter(step.position, intensityMeters = 0.6)
            } else {
                step.position
            }

            applyMockLocationCommand(
                point = pointToInject,
                bearingDegrees = step.bearingDegrees,
                speedMetersPerSec = effectiveSpeedMs
            )

            _serviceState.value = _serviceState.value.copy(
                isRunning = true,
                isRouteActive = true,
                activePoint = step.position,
                currentBearingDegrees = step.bearingDegrees,
                currentSpeedKmh = if (step.finishedOneWay) 0f else currentSpeedKmh
            )

            tickCount++
            if (tickCount % 3 == 0) {
                updateNotification(step.position, isRoute = true)
            }

            delay(ROUTE_INTERVAL_MS)
        }
    }

    fun updateCoordinates(newPoint: LatLngPoint) {
        activeRoutePoints = emptyList()
        currentTarget = newPoint
        currentSpeedKmh = 0f
        _serviceState.value = _serviceState.value.copy(
            isRouteActive = false,
            activePoint = newPoint,
            currentSpeedKmh = 0f
        )
        updateNotification(newPoint, isRoute = false)
        if (providersRegistered) {
            serviceScope.launch {
                applyMockLocationCommand(newPoint, 0f, 0f)
            }
        }
    }

    private fun stopSpoofingAndRestore() {
        simulationJob?.cancel()
        simulationJob = null
        activeRoutePoints = emptyList()
        pendingRouteSnapshot = emptyList()

        serviceScope.launch {
            clearMockLocationCommand()
            _serviceState.value = SpoofServiceState(
                isRunning = false,
                isRouteActive = false,
                activePoint = currentTarget,
                currentBearingDegrees = 0f,
                currentSpeedKmh = 0f,
                lastError = null
            )
            stopForegroundCleanly()
        }
    }

    /**
     * Inyecta la ubicación coherente en GPS, Network y Fused (Android 12+) para evitar
     * rebotes con lecturas residuales del hardware real.
     */
    private suspend fun applyMockLocationCommand(
        point: LatLngPoint,
        bearingDegrees: Float,
        speedMetersPerSec: Float
    ) {
        pushLocationToProviders(point, bearingDegrees, speedMetersPerSec)
        if (!providersRegistered) {
            val latStr = String.format(Locale.US, "%.6f", point.latitude)
            val lngStr = String.format(Locale.US, "%.6f", point.longitude)
            RootManager.executeCommand(
                "cmd location providers set-test-provider-location gps --location $latStr,$lngStr 2>/dev/null || true",
                timeoutMs = 2_000L
            )
        }
    }

    private suspend fun clearMockLocationCommand() {
        restoreRealGpsProviders()
        GpsAlignmentHelper.restoreRootLocationEnvironment(packageName)
    }

    private fun targetProviderNames(): List<String> = buildList {
        add(LocationManager.GPS_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(LocationManager.FUSED_PROVIDER)
        }
    }

    private fun setupTestProviders(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        var atLeastOneReady = false

        for (provider in targetProviderNames()) {
            runCatching { locationManager.removeTestProvider(provider) }

            val added = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val properties = ProviderProperties.Builder()
                        .setHasNetworkRequirement(false)
                        .setHasSatelliteRequirement(false)
                        .setHasCellRequirement(false)
                        .setHasMonetaryCost(false)
                        .setHasAltitudeSupport(true)
                        .setHasSpeedSupport(true)
                        .setHasBearingSupport(true)
                        .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
                        .setAccuracy(ProviderProperties.ACCURACY_FINE)
                        .build()
                    locationManager.addTestProvider(provider, properties)
                } else {
                    @Suppress("DEPRECATION")
                    locationManager.addTestProvider(
                        provider,
                        false,
                        false,
                        false,
                        false,
                        true,
                        true,
                        true,
                        Criteria.POWER_LOW,
                        Criteria.ACCURACY_FINE
                    )
                }
                locationManager.setTestProviderEnabled(provider, true)
                true
            }.onFailure { error ->
                Log.w(TAG, "No se pudo registrar proveedor $provider: ${error.message}")
            }.getOrDefault(false)

            if (added) {
                atLeastOneReady = true
            }
        }

        providersRegistered = atLeastOneReady
        return atLeastOneReady
    }

    private fun pushLocationToProviders(
        point: LatLngPoint,
        bearingDegrees: Float,
        speedMetersPerSec: Float
    ) {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        val nowMillis = System.currentTimeMillis()
        val nowElapsedNanos = SystemClock.elapsedRealtimeNanos()

        for (provider in targetProviderNames()) {
            runCatching {
                val mockLocation = Location(provider).apply {
                    latitude = point.latitude
                    longitude = point.longitude
                    altitude = 960.0
                    accuracy = 3.0f
                    bearing = bearingDegrees
                    speed = speedMetersPerSec
                    time = nowMillis
                    elapsedRealtimeNanos = nowElapsedNanos
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        verticalAccuracyMeters = 3.0f
                        speedAccuracyMetersPerSecond = 0.5f
                        bearingAccuracyDegrees = 5.0f
                    }
                }
                locationManager.setTestProviderLocation(provider, mockLocation)
            }.onFailure { error ->
                Log.w(TAG, "Error inyectando ubicación en $provider: ${error.message}")
            }
        }
    }

    private fun restoreRealGpsProviders() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        for (provider in targetProviderNames()) {
            runCatching { locationManager.setTestProviderEnabled(provider, false) }
            runCatching { locationManager.removeTestProvider(provider) }
        }
        providersRegistered = false
        Log.d(TAG, "Proveedores de prueba removidos (GPS/Network/Fused restaurados).")
    }

    private fun promoteToForeground(point: LatLngPoint, isRoute: Boolean) {
        val notification = buildNotification(point, isRoute)
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)
    }

    private fun updateNotification(point: LatLngPoint, isRoute: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(point, isRoute))
    }

    private fun buildNotification(point: LatLngPoint, isRoute: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            createStopIntent(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val coordsText = String.format(
            Locale.US,
            "%.6f, %.6f",
            point.latitude,
            point.longitude
        )
        val modeInfo = if (isRoute) {
            "Ruta (${currentSpeedKmh.toInt()} km/h): $coordsText"
        } else {
            "Fijo: $coordsText"
        }

        return NotificationCompat.Builder(this, GpsAnthiApp.SPOOF_CHANNEL_ID)
            .setContentTitle("GPS ANTHI activo")
            .setContentText(modeInfo)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Detener",
                stopPendingIntent
            )
            .build()
    }

    private fun stopForegroundCleanly() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        simulationJob?.cancel()
        if (providersRegistered) {
            restoreRealGpsProviders()
        }
        _serviceState.value = _serviceState.value.copy(isRunning = false, isRouteActive = false)
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LocationSpooferService"
        private const val NOTIFICATION_ID = 1001
        private const val STATIC_INTERVAL_MS = 1_000L
        private const val ROUTE_INTERVAL_MS = 600L

        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_UPDATE_COORDS = "com.example.service.ACTION_UPDATE_COORDS"
        const val ACTION_UPDATE_SPEED = "com.example.service.ACTION_UPDATE_SPEED"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"

        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
        const val EXTRA_JITTER_ENABLED = "extra_jitter_enabled"
        const val EXTRA_SPEED_KMH = "extra_speed_kmh"
        const val EXTRA_TRAVERSAL_MODE = "extra_traversal_mode"

        @Volatile
        private var pendingRouteSnapshot: List<LatLngPoint> = emptyList()

        private val _serviceState = MutableStateFlow(SpoofServiceState())
        val serviceState: StateFlow<SpoofServiceState> = _serviceState.asStateFlow()

        fun createStartIntent(
            context: Context,
            latitude: Double,
            longitude: Double,
            jitterEnabled: Boolean = true,
            speedKmh: Float = 35f,
            traversalMode: RouteTraversalMode = RouteTraversalMode.ROUND_TRIP
        ): Intent = Intent(context, LocationSpooferService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_LATITUDE, latitude)
            putExtra(EXTRA_LONGITUDE, longitude)
            putExtra(EXTRA_JITTER_ENABLED, jitterEnabled)
            putExtra(EXTRA_SPEED_KMH, speedKmh)
            putExtra(EXTRA_TRAVERSAL_MODE, traversalMode.name)
        }

        fun createUpdateIntent(
            context: Context,
            latitude: Double,
            longitude: Double
        ): Intent = Intent(context, LocationSpooferService::class.java).apply {
            action = ACTION_UPDATE_COORDS
            putExtra(EXTRA_LATITUDE, latitude)
            putExtra(EXTRA_LONGITUDE, longitude)
        }

        fun createStopIntent(context: Context): Intent =
            Intent(context, LocationSpooferService::class.java).apply {
                action = ACTION_STOP
            }

        fun start(
            context: Context,
            latitude: Double,
            longitude: Double,
            jitterEnabled: Boolean = true
        ) {
            pendingRouteSnapshot = emptyList()
            ContextCompat.startForegroundService(
                context,
                createStartIntent(context, latitude, longitude, jitterEnabled)
            )
        }

        fun startRoute(
            context: Context,
            routePoints: List<LatLngPoint>,
            speedKmh: Float,
            traversalMode: RouteTraversalMode,
            jitterEnabled: Boolean = true
        ) {
            if (routePoints.isEmpty()) return
            pendingRouteSnapshot = routePoints.toList()
            val first = routePoints.first()
            ContextCompat.startForegroundService(
                context,
                createStartIntent(
                    context = context,
                    latitude = first.latitude,
                    longitude = first.longitude,
                    jitterEnabled = jitterEnabled,
                    speedKmh = speedKmh,
                    traversalMode = traversalMode
                )
            )
        }

        fun updateSpeed(context: Context, speedKmh: Float) {
            context.startService(
                Intent(context, LocationSpooferService::class.java).apply {
                    action = ACTION_UPDATE_SPEED
                    putExtra(EXTRA_SPEED_KMH, speedKmh)
                }
            )
        }

        fun stop(context: Context) {
            context.startService(createStopIntent(context))
        }
    }
}
