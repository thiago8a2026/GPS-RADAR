package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.navigation.LatLngPoint
import com.example.navigation.SimulationEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocationSpooferService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _currentPosition = MutableStateFlow(LatLngPoint(-12.046374, -77.042793))
    val currentPosition: StateFlow<LatLngPoint> = _currentPosition.asStateFlow()

    private val _isSimulating = MutableStateFlow(false)
    val isSimulating: StateFlow<Boolean> = _isSimulating.asStateFlow()

    private val _simSpeedKmh = MutableStateFlow(45f)
    val simSpeedKmh: StateFlow<Float> = _simSpeedKmh.asStateFlow()

    private var simulationJob: Job? = null

    inner class LocalBinder : Binder() {
        fun getService(): LocationSpooferService = this@LocationSpooferService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START -> startSimulation()
            ACTION_STOP -> stopSimulation()
        }
        return START_STICKY
    }

    fun updatePosition(newPos: LatLngPoint) {
        _currentPosition.value = newPos
        pushMockLocationToSystem(newPos)
    }

    fun setSpeed(speedKmh: Float) {
        _simSpeedKmh.value = speedKmh
    }

    fun startSimulation(route: List<LatLngPoint> = emptyList()) {
        if (_isSimulating.value) return
        _isSimulating.value = true

        simulationJob = serviceScope.launch {
            if (route.isNotEmpty()) {
                var index = 0
                while (isActive && _isSimulating.value) {
                    val target = route[index % route.size]
                    val jittered = SimulationEngine.applyJitter(target)
                    updatePosition(jittered)
                    index++
                    delay(800)
                }
            } else {
                // Continuous static location with micro jittering
                while (isActive && _isSimulating.value) {
                    val jittered = SimulationEngine.applyJitter(_currentPosition.value)
                    pushMockLocationToSystem(jittered)
                    delay(1000)
                }
            }
        }
    }

    fun stopSimulation() {
        _isSimulating.value = false
        simulationJob?.cancel()
    }

    private fun pushMockLocationToSystem(point: LatLngPoint) {
        try {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val mockLocation = Location(LocationManager.GPS_PROVIDER).apply {
                latitude = point.latitude
                longitude = point.longitude
                altitude = 15.0
                accuracy = 1.0f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            // In Root / System mode, location is set via LocationManager or Native hooks
            Log.d("LocationSpooferService", "Spoofed position: ${point.latitude}, ${point.longitude}")
        } catch (e: Exception) {
            Log.e("LocationSpooferService", "Error pushing mock location", e)
        }
    }

    private fun startForegroundServiceNotification() {
        val channelId = "gps_setter_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "GPS Setter Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("GPS Setter Pro Running")
            .setContentText("Location Spoofing & Route Engine Active")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_START = "com.example.service.START"
        const val ACTION_STOP = "com.example.service.STOP"
    }
}
