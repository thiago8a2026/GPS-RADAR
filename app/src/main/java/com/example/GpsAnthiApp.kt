package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import org.osmdroid.config.Configuration
import java.io.File

class GpsAnthiApp : Application() {

    companion object {
        const val SPOOF_CHANNEL_ID = "gps_anthi_spoof_channel"
    }

    override fun onCreate() {
        super.onCreate()
        configureOsmdroid()
        createNotificationChannel()
    }

    private fun configureOsmdroid() {
        val config = Configuration.getInstance()
        config.userAgentValue = packageName
        val baseDir = File(cacheDir, "osmdroid").apply { if (!exists()) mkdirs() }
        val tileDir = File(baseDir, "tiles").apply { if (!exists()) mkdirs() }
        config.osmdroidBasePath = baseDir
        config.osmdroidTileCache = tileDir
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SPOOF_CHANNEL_ID,
                "Simulación GPS Activa",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene la ubicación simulada activa en primer plano"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
