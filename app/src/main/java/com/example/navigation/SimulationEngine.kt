package com.example.navigation

import kotlin.math.*
import kotlin.random.Random

data class LatLngPoint(val latitude: Double, val longitude: Double)

data class SimulationState(
    val isRunning: Boolean = false,
    val currentPosition: LatLngPoint = LatLngPoint(-12.046374, -77.042793), // Lima default
    val targetDestination: LatLngPoint? = null,
    val currentSpeedKmh: Float = 45f,
    val isJoystickActive: Boolean = false,
    val isJitterEnabled: Boolean = true,
    val isFromMockProviderMasked: Boolean = true,
    val activeRoutePoints: List<LatLngPoint> = emptyList(),
    val currentWaypointIndex: Int = 0
)

object SimulationEngine {

    /**
     * Calculates distance in meters between two coordinates using Haversine formula.
     */
    fun calculateDistanceMeters(p1: LatLngPoint, p2: LatLngPoint): Double {
        val r = 6371000.0 // Earth radius in meters
        val lat1Rad = Math.toRadians(p1.latitude)
        val lat2Rad = Math.toRadians(p2.latitude)
        val deltaLat = Math.toRadians(p2.latitude - p1.latitude)
        val deltaLng = Math.toRadians(p2.longitude - p1.longitude)

        val a = sin(deltaLat / 2).pow(2.0) +
                cos(lat1Rad) * cos(lat2Rad) * sin(deltaLng / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return r * c
    }

    /**
     * Interpolates points between start and end to create a realistic path with traffic jitter.
     */
    fun generateInterpolatedRoute(
        start: LatLngPoint,
        end: LatLngPoint,
        steps: Int = 30
    ): List<LatLngPoint> {
        val list = mutableListOf<LatLngPoint>()
        for (i in 0..steps) {
            val fraction = i.toDouble() / steps
            val lat = start.latitude + (end.latitude - start.latitude) * fraction
            val lng = start.longitude + (end.longitude - start.longitude) * fraction
            list.add(LatLngPoint(lat, lng))
        }
        return list
    }

    /**
     * Applies subtle GPS jittering (hardware noise simulation) to prevent static GPS detection.
     */
    fun applyJitter(point: LatLngPoint, intensityMeters: Double = 1.5): LatLngPoint {
        // 1 degree latitude ~ 111,111 meters
        val latOffset = (Random.nextDouble(-intensityMeters, intensityMeters)) / 111111.0
        val lngOffset = (Random.nextDouble(-intensityMeters, intensityMeters)) / (111111.0 * cos(Math.toRadians(point.latitude)))
        return LatLngPoint(point.latitude + latOffset, point.longitude + lngOffset)
    }

    /**
     * Moves current position by a joystick offset vector (dx, dy in normalized -1..1 range).
     */
    fun moveByJoystick(
        current: LatLngPoint,
        dx: Float,
        dy: Float,
        speedKmh: Float,
        deltaTimeSeconds: Float = 0.5f
    ): LatLngPoint {
        if (dx == 0f && dy == 0f) return current

        val speedMs = (speedKmh * 1000f) / 3600f
        val distance = speedMs * deltaTimeSeconds // Meters moved in this step

        val bearing = atan2(dx.toDouble(), dy.toDouble()) // Direction in radians
        val deltaLat = (distance * cos(bearing)) / 111111.0
        val deltaLng = (distance * sin(bearing)) / (111111.0 * cos(Math.toRadians(current.latitude)))

        return LatLngPoint(current.latitude + deltaLat, current.longitude + deltaLng)
    }
}
