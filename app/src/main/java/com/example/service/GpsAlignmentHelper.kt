package com.example.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.example.root.RootManager
import com.example.root.RootStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SystemGpsAlignmentStatus(
    val isMasterLocationEnabled: Boolean,
    val hasFineLocationPermission: Boolean,
    val isRootGranted: Boolean,
    val isAppOpsMockAllowed: Boolean,
    val locationMode: Int?,
    val summary: String
) {
    val isFullyAligned: Boolean
        get() = isMasterLocationEnabled && hasFineLocationPermission && isRootGranted && isAppOpsMockAllowed
}

/**
 * Diagnóstico y preparación del sistema de ubicación del teléfono para evitar conflictos
 * entre el GPS real (GNSS / Red / Fused) y la ubicación simulada.
 */
object GpsAlignmentHelper {

    suspend fun inspectAlignment(context: Context): SystemGpsAlignmentStatus = withContext(Dispatchers.IO) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val isLocationEnabled = locationManager?.let {
            runCatching { it.isLocationEnabled }.getOrDefault(false)
        } ?: false

        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val rootGranted = RootManager.rootState.value.status == RootStatus.GRANTED
        var appOpsAllowed = false
        var locMode: Int? = null

        if (rootGranted) {
            val appOpsOut = RootManager.executeCommand(
                "appops get ${context.packageName} android:mock_location",
                timeoutMs = 2_000L
            )
            appOpsAllowed = appOpsOut.isSuccess && appOpsOut.output.contains("allow", ignoreCase = true)

            val modeOut = RootManager.executeCommand(
                "settings get secure location_mode",
                timeoutMs = 2_000L
            )
            locMode = modeOut.output.trim().toIntOrNull()
        }

        val summary = when {
            !isLocationEnabled -> "Activa la Ubicación del teléfono para que las apps lean el GPS simulado"
            !hasFine -> "Concede permiso de Ubicación precisa a GPS ANTHI"
            !rootGranted -> "Autoriza permiso SU en Magisk para alinear AppOps y proveedores"
            !appOpsAllowed -> "AppOps mock_location se habilitará automáticamente al pulsar Play"
            else -> "GPS del sistema alineado (GPS + Network + Fused listos)"
        }

        SystemGpsAlignmentStatus(
            isMasterLocationEnabled = isLocationEnabled,
            hasFineLocationPermission = hasFine,
            isRootGranted = rootGranted,
            isAppOpsMockAllowed = appOpsAllowed,
            locationMode = locMode,
            summary = summary
        )
    }

    suspend fun prepareRootLocationEnvironment(packageName: String): Boolean {
        val appOps = RootManager.enableAppOpsMockLocation(packageName)
        return appOps.isSuccess
    }

    suspend fun restoreRootLocationEnvironment(packageName: String) {
        RootManager.executeCommand(
            "appops set $packageName android:mock_location default 2>/dev/null || true",
            timeoutMs = 2_000L
        )
    }
}
