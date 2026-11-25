package com.example.navigation

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

data class LatLngPoint(val latitude: Double, val longitude: Double)

enum class RouteTraversalMode(val label: String) {
    ONE_WAY("Solo ida"),
    ROUND_TRIP("Ida y vuelta"),
    LOOP("Bucle")
}

data class RouteStepState(
    val position: LatLngPoint,
    val bearingDegrees: Float,
    val segmentIndex: Int,
    val distanceIntoSegmentMeters: Double,
    val isReversing: Boolean,
    val finishedOneWay: Boolean
)

data class SimulationState(
    val isRunning: Boolean = false,
    val currentPosition: LatLngPoint = LatLngPoint(7.0622, -73.0864),
    val targetDestination: LatLngPoint? = null,
    val currentSpeedKmh: Float = 35f,
    val isJoystickActive: Boolean = false,
    val isJitterEnabled: Boolean = true,
    val isFromMockProviderMasked: Boolean = true,
    val activeRoutePoints: List<LatLngPoint> = emptyList(),
    val currentWaypointIndex: Int = 0
)

object SimulationEngine {

    /**
     * Distancia en metros entre dos coordenadas usando la fórmula de Haversine.
     */
    fun calculateDistanceMeters(p1: LatLngPoint, p2: LatLngPoint): Double {
        val r = 6371000.0
        val lat1Rad = Math.toRadians(p1.latitude)
        val lat2Rad = Math.toRadians(p2.latitude)
        val deltaLat = Math.toRadians(p2.latitude - p1.latitude)
        val deltaLng = Math.toRadians(p2.longitude - p1.longitude)

        val a = sin(deltaLat / 2).pow(2.0) +
            cos(lat1Rad) * cos(lat2Rad) * sin(deltaLng / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun calculateTotalDistanceMeters(points: List<LatLngPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until points.lastIndex) {
            total += calculateDistanceMeters(points[i], points[i + 1])
        }
        return total
    }

    /**
     * Calcula el rumbo (bearing) en grados [0..360) desde un punto hacia otro.
     */
    fun calculateBearingDegrees(from: LatLngPoint, to: LatLngPoint): Float {
        if (from == to) return 0f
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val rawDeg = Math.toDegrees(atan2(y, x))
        return ((rawDeg + 360.0) % 360.0).toFloat()
    }

    /**
     * Interpola un punto entre A y B con fracción t en [0.0, 1.0].
     */
    fun interpolate(start: LatLngPoint, end: LatLngPoint, fraction: Double): LatLngPoint {
        val t = fraction.coerceIn(0.0, 1.0)
        return LatLngPoint(
            latitude = start.latitude + (end.latitude - start.latitude) * t,
            longitude = start.longitude + (end.longitude - start.longitude) * t
        )
    }

    /**
     * Respaldo seguro cuando no hay conectividad con OSRM.
     */
    fun generateDensifiedFallbackRoute(
        waypoints: List<LatLngPoint>,
        segmentStepMeters: Double = 25.0
    ): List<LatLngPoint> {
        if (waypoints.size < 2) return waypoints
        val result = mutableListOf<LatLngPoint>()
        for (i in 0 until waypoints.lastIndex) {
            val start = waypoints[i]
            val end = waypoints[i + 1]
            val dist = calculateDistanceMeters(start, end)
            val steps = (dist / segmentStepMeters).toInt().coerceIn(1, 200)
            for (step in 0 until steps) {
                result += interpolate(start, end, step.toDouble() / steps)
            }
        }
        result += waypoints.last()
        return result
    }

    /**
     * Avanza a lo largo de la polilínea vial a velocidad constante (stepMeters por tick),
     * respetando los giros de cada calle y el modo de recorrido (ida, ida/vuelta o bucle).
     */
    fun advanceAlongPolyline(
        points: List<LatLngPoint>,
        currentSegmentIndex: Int,
        distanceIntoSegmentMeters: Double,
        stepMeters: Double,
        mode: RouteTraversalMode,
        isReversing: Boolean
    ): RouteStepState {
        if (points.isEmpty()) {
            val fallback = LatLngPoint(7.0622, -73.0864)
            return RouteStepState(fallback, 0f, 0, 0.0, isReversing = false, finishedOneWay = true)
        }
        if (points.size == 1) {
            return RouteStepState(points.first(), 0f, 0, 0.0, isReversing = false, finishedOneWay = true)
        }

        val effectivePoints = if (isReversing) points.asReversed() else points
        var segIdx = currentSegmentIndex.coerceIn(0, effectivePoints.lastIndex - 1)
        var remainingAdvance = distanceIntoSegmentMeters.coerceAtLeast(0.0) + stepMeters.coerceAtLeast(0.0)
        var reversing = isReversing

        while (segIdx < effectivePoints.lastIndex) {
            val segStart = effectivePoints[segIdx]
            val segEnd = effectivePoints[segIdx + 1]
            val segLength = calculateDistanceMeters(segStart, segEnd).coerceAtLeast(0.5)

            if (remainingAdvance <= segLength) {
                val pos = interpolate(segStart, segEnd, remainingAdvance / segLength)
                val bearing = calculateBearingDegrees(segStart, segEnd)
                return RouteStepState(
                    position = pos,
                    bearingDegrees = bearing,
                    segmentIndex = segIdx,
                    distanceIntoSegmentMeters = remainingAdvance,
                    isReversing = reversing,
                    finishedOneWay = false
                )
            }

            remainingAdvance -= segLength
            segIdx++
        }

        // Alcanzó el final del sentido actual
        return when (mode) {
            RouteTraversalMode.ONE_WAY -> {
                val prev = effectivePoints[effectivePoints.lastIndex - 1]
                val end = effectivePoints.last()
                RouteStepState(
                    position = end,
                    bearingDegrees = calculateBearingDegrees(prev, end),
                    segmentIndex = effectivePoints.lastIndex - 1,
                    distanceIntoSegmentMeters = calculateDistanceMeters(prev, end),
                    isReversing = reversing,
                    finishedOneWay = true
                )
            }

            RouteTraversalMode.ROUND_TRIP -> {
                val nextReversing = !reversing
                val nextList = if (nextReversing) points.asReversed() else points
                val bearing = calculateBearingDegrees(nextList[0], nextList[1])
                RouteStepState(
                    position = nextList[0],
                    bearingDegrees = bearing,
                    segmentIndex = 0,
                    distanceIntoSegmentMeters = 0.0,
                    isReversing = nextReversing,
                    finishedOneWay = false
                )
            }

            RouteTraversalMode.LOOP -> {
                val bearing = calculateBearingDegrees(points[0], points[1])
                RouteStepState(
                    position = points[0],
                    bearingDegrees = bearing,
                    segmentIndex = 0,
                    distanceIntoSegmentMeters = 0.0,
                    isReversing = false,
                    finishedOneWay = false
                )
            }
        }
    }

    /**
     * Aplica micro-variación realista de GNSS para evitar lecturas artificialmente estáticas.
     */
    fun applyJitter(point: LatLngPoint, intensityMeters: Double = 1.2): LatLngPoint {
        val latOffset = Random.nextDouble(-intensityMeters, intensityMeters) / 111111.0
        val cosLat = cos(Math.toRadians(point.latitude)).coerceAtLeast(0.01)
        val lngOffset = Random.nextDouble(-intensityMeters, intensityMeters) / (111111.0 * cosLat)
        return LatLngPoint(point.latitude + latOffset, point.longitude + lngOffset)
    }

    fun moveByJoystick(
        current: LatLngPoint,
        dx: Float,
        dy: Float,
        speedKmh: Float,
        deltaTimeSeconds: Float = 0.5f
    ): LatLngPoint {
        if (dx == 0f && dy == 0f) return current
        val speedMs = (speedKmh * 1000f) / 3600f
        val distance = speedMs * deltaTimeSeconds
        val bearing = atan2(dx.toDouble(), dy.toDouble())
        val deltaLat = (distance * cos(bearing)) / 111111.0
        val cosLat = cos(Math.toRadians(current.latitude)).coerceAtLeast(0.01)
        val deltaLng = (distance * sin(bearing)) / (111111.0 * cosLat)
        return LatLngPoint(current.latitude + deltaLat, current.longitude + deltaLng)
    }
}
