package com.example.geofence

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.navigation.LatLngPoint
import com.example.navigation.SimulationEngine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

enum class GeofenceTransition {
    ENTER,
    EXIT
}

data class GeofenceZone(
    val id: String,
    val name: String,
    val center: LatLngPoint,
    val radiusMeters: Float,
    val isEnabled: Boolean = true,
    val notifyOnEnter: Boolean = true,
    val notifyOnExit: Boolean = true
) {
    init {
        require(radiusMeters > 0f) { "El radio de la geocerca debe ser mayor a 0 metros" }
    }
}

data class GeofenceEvent(
    val zone: GeofenceZone,
    val transition: GeofenceTransition,
    val position: LatLngPoint,
    val distanceMeters: Double,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Motor puro en Kotlin para evaluación de zonas geográficas sin dependencia de Google Play Services.
 * Ideal para dispositivos rooteados, AOSP o sin GMS.
 */
class GeofenceMonitor {

    private val mutex = Mutex()
    private val _zones = MutableStateFlow<List<GeofenceZone>>(emptyList())
    val zones: StateFlow<List<GeofenceZone>> = _zones.asStateFlow()

    private val _events = MutableSharedFlow<GeofenceEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<GeofenceEvent> = _events.asSharedFlow()

    private var insideZoneIds: Set<String> = emptySet()

    suspend fun setZones(newZones: List<GeofenceZone>) {
        mutex.withLock {
            _zones.value = newZones
            val validIds = newZones.filter { it.isEnabled }.map { it.id }.toSet()
            insideZoneIds = insideZoneIds.intersect(validIds)
        }
    }

    suspend fun upsertZone(zone: GeofenceZone) {
        mutex.withLock {
            val mutable = _zones.value.toMutableList()
            val idx = mutable.indexOfFirst { it.id == zone.id }
            if (idx >= 0) {
                mutable[idx] = zone
            } else {
                mutable.add(zone)
            }
            _zones.value = mutable
        }
    }

    suspend fun removeZone(zoneId: String) {
        mutex.withLock {
            _zones.value = _zones.value.filterNot { it.id == zoneId }
            insideZoneIds = insideZoneIds - zoneId
        }
    }

    /**
     * Evalúa una posición actual o simulada contra todas las geocercas activas
     * y emite eventos ENTER / EXIT sin bloquear el hilo llamador.
     */
    suspend fun evaluatePosition(position: LatLngPoint): List<GeofenceEvent> {
        val emittedEvents = mutableListOf<GeofenceEvent>()

        mutex.withLock {
            val updatedInside = insideZoneIds.toMutableSet()

            for (zone in _zones.value) {
                if (!zone.isEnabled) {
                    updatedInside.remove(zone.id)
                    continue
                }

                val distance = SimulationEngine.calculateDistanceMeters(position, zone.center)
                val isCurrentlyInside = distance <= zone.radiusMeters.toDouble()
                val wasInside = zone.id in insideZoneIds

                when {
                    isCurrentlyInside && !wasInside -> {
                        updatedInside.add(zone.id)
                        if (zone.notifyOnEnter) {
                            emittedEvents += GeofenceEvent(
                                zone = zone,
                                transition = GeofenceTransition.ENTER,
                                position = position,
                                distanceMeters = distance
                            )
                        }
                    }

                    !isCurrentlyInside && wasInside -> {
                        updatedInside.remove(zone.id)
                        if (zone.notifyOnExit) {
                            emittedEvents += GeofenceEvent(
                                zone = zone,
                                transition = GeofenceTransition.EXIT,
                                position = position,
                                distanceMeters = distance
                            )
                        }
                    }
                }
            }

            insideZoneIds = updatedInside
        }

        emittedEvents.forEach { _events.emit(it) }
        return emittedEvents
    }

    suspend fun resetState() {
        mutex.withLock {
            insideZoneIds = emptySet()
        }
    }
}

/**
 * Despachador de notificaciones locales para eventos de geocerca, desacoplado de GeofenceMonitor.
 */
class GeofenceNotificationDispatcher(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "gps_anthi_geofence_channel"
        private const val BASE_NOTIFICATION_ID = 2100
    }

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alertas de Geocercas",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifica al entrar o salir de zonas geográficas configuradas"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    fun notifyEvent(event: GeofenceEvent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ensureChannel()
        val actionLabel = when (event.transition) {
            GeofenceTransition.ENTER -> "Entrada a zona"
            GeofenceTransition.EXIT -> "Salida de zona"
        }
        val coords = String.format(
            Locale.US,
            "%.5f, %.5f (%.0f m)",
            event.position.latitude,
            event.position.longitude,
            event.distanceMeters
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$actionLabel: ${event.zone.name}")
            .setContentText("Posición: $coords")
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.notify(BASE_NOTIFICATION_ID + event.zone.id.hashCode(), notification)
    }
}
