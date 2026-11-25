package com.example.navigation

import android.util.Log
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

data class RoutedPathResult(
    val points: List<LatLngPoint>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val isRoadSnapped: Boolean,
    val sourceLabel: String
)

@JsonClass(generateAdapter = true)
data class OsrmRouteResponse(
    val code: String? = null,
    val routes: List<OsrmRoute>? = null
)

@JsonClass(generateAdapter = true)
data class OsrmRoute(
    val distance: Double = 0.0,
    val duration: Double = 0.0,
    val geometry: OsrmGeometry? = null
)

@JsonClass(generateAdapter = true)
data class OsrmGeometry(
    val type: String? = null,
    val coordinates: List<List<Double>> = emptyList()
)

/**
 * Servicio de enrutamiento sobre vías reales de OpenStreetMap usando OSRM.
 * Incluye respaldo automático seguro si el dispositivo está sin red o el servidor falla.
 */
object OsrmRoutingService {

    private const val TAG = "OsrmRoutingService"
    private const val BASE_URL = "https://router.project-osrm.org/route/v1"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val responseAdapter = moshi.adapter(OsrmRouteResponse::class.java)

    suspend fun calculateRoadRoute(
        waypoints: List<LatLngPoint>,
        speedKmh: Float
    ): RoutedPathResult = withContext(Dispatchers.IO) {
        if (waypoints.size < 2) {
            return@withContext RoutedPathResult(
                points = waypoints,
                distanceMeters = 0.0,
                durationSeconds = 0.0,
                isRoadSnapped = false,
                sourceLabel = "Se requieren al menos Punto A y Punto B"
            )
        }

        val profile = if (speedKmh <= 10f) "foot" else "driving"
        val coordsParam = waypoints.joinToString(";") { point ->
            String.format(Locale.US, "%.6f,%.6f", point.longitude, point.latitude)
        }
        val url = "$BASE_URL/$profile/$coordsParam?overview=full&geometries=geojson&steps=false"

        val osrmResult = runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "GPS-ANTHI-Android/1.0")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                val parsed = responseAdapter.fromJson(body)
                val firstRoute = parsed?.routes?.firstOrNull()
                    ?: error("Sin rutas disponibles en respuesta OSRM")

                val geoPoints = firstRoute.geometry?.coordinates.orEmpty()
                    .mapNotNull { pair ->
                        if (pair.size >= 2) {
                            LatLngPoint(latitude = pair[1], longitude = pair[0])
                        } else {
                            null
                        }
                    }

                if (geoPoints.size < 2) {
                    error("Geometría OSRM insuficiente")
                }

                RoutedPathResult(
                    points = geoPoints,
                    distanceMeters = firstRoute.distance,
                    durationSeconds = firstRoute.duration,
                    isRoadSnapped = true,
                    sourceLabel = if (profile == "foot") {
                        "Ruta peatonal OSM (OSRM)"
                    } else {
                        "Ruta vial OSM (OSRM)"
                    }
                )
            }
        }

        osrmResult.getOrElse { error ->
            Log.w(TAG, "Fallo OSRM (${error.message}); usando respaldo interpolado")
            val fallbackPoints = SimulationEngine.generateDensifiedFallbackRoute(waypoints)
            val dist = SimulationEngine.calculateTotalDistanceMeters(fallbackPoints)
            val speedMs = (speedKmh.coerceAtLeast(5f) * 1000.0) / 3600.0
            RoutedPathResult(
                points = fallbackPoints,
                distanceMeters = dist,
                durationSeconds = dist / speedMs,
                isRoadSnapped = false,
                sourceLabel = "Respaldo directo sin red OSRM"
            )
        }
    }
}
