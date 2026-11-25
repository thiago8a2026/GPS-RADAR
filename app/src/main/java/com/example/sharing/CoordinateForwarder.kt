package com.example.sharing

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.example.navigation.LatLngPoint
import java.util.Locale

enum class ForwardChannel {
    WHATSAPP,
    TELEGRAM,
    SYSTEM_SHARE_SHEET,
    CLIPBOARD
}

data class ForwardPayload(
    val latitude: Double,
    val longitude: Double,
    val label: String? = null,
    val isSimulated: Boolean = true,
    val timestampMs: Long = System.currentTimeMillis()
) {
    val osmUrl: String
        get() = String.format(
            Locale.US,
            "https://www.openstreetmap.org/?mlat=%.6f&mlon=%.6f#map=17/%.6f/%.6f",
            latitude,
            longitude,
            latitude,
            longitude
        )

    val formattedMessage: String
        get() {
            val header = label?.takeIf { it.isNotBlank() } ?: "Ubicación GPS ANTHI"
            val mode = if (isSimulated) "Simulada" else "Actual"
            val coords = String.format(Locale.US, "%.6f, %.6f", latitude, longitude)
            return "$header ($mode)\nCoordenadas: $coords\nMapa OSM: $osmUrl"
        }

    companion object {
        fun fromPoint(point: LatLngPoint, label: String? = null, isSimulated: Boolean = true): ForwardPayload {
            return ForwardPayload(
                latitude = point.latitude,
                longitude = point.longitude,
                label = label,
                isSimulated = isSimulated
            )
        }
    }
}

sealed interface ForwardResult {
    data class LaunchIntent(val intent: Intent) : ForwardResult
    data object CopiedToClipboard : ForwardResult
    data class Unavailable(val reason: String) : ForwardResult
}

/**
 * Construye intents seguros y desacoplados de la UI para reenviar coordenadas
 * hacia WhatsApp, Telegram, hoja de compartir del sistema o portapapeles.
 */
object CoordinateForwarder {

    private const val WHATSAPP_PACKAGE = "com.whatsapp"
    private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
    private const val TELEGRAM_PACKAGE = "org.telegram.messenger"

    fun prepareForward(
        context: Context,
        payload: ForwardPayload,
        channel: ForwardChannel,
        targetPhoneDigits: String? = null
    ): ForwardResult {
        if (payload.latitude !in -90.0..90.0 || payload.longitude !in -180.0..180.0) {
            return ForwardResult.Unavailable("Coordenadas fuera de rango válido")
        }

        return when (channel) {
            ForwardChannel.CLIPBOARD -> {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    ?: return ForwardResult.Unavailable("Portapapeles no disponible")
                val clip = ClipData.newPlainText("Coordenadas GPS ANTHI", payload.formattedMessage)
                clipboard.setPrimaryClip(clip)
                ForwardResult.CopiedToClipboard
            }

            ForwardChannel.WHATSAPP -> {
                val sanitizedPhone = targetPhoneDigits?.filter(Char::isDigit).orEmpty()
                val intent = if (sanitizedPhone.length >= 7) {
                    val encodedText = Uri.encode(payload.formattedMessage)
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$sanitizedPhone?text=$encodedText"))
                } else {
                    val waPkg = resolveInstalledPackage(context, listOf(WHATSAPP_PACKAGE, WHATSAPP_BUSINESS_PACKAGE))
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, payload.formattedMessage)
                        if (waPkg != null) {
                            setPackage(waPkg)
                        }
                    }
                }
                ForwardResult.LaunchIntent(intent)
            }

            ForwardChannel.TELEGRAM -> {
                val tgPkg = resolveInstalledPackage(context, listOf(TELEGRAM_PACKAGE))
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, payload.formattedMessage)
                    if (tgPkg != null) {
                        setPackage(tgPkg)
                    }
                }
                ForwardResult.LaunchIntent(intent)
            }

            ForwardChannel.SYSTEM_SHARE_SHEET -> {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, payload.formattedMessage)
                }
                val chooser = Intent.createChooser(sendIntent, "Compartir ubicación GPS ANTHI")
                ForwardResult.LaunchIntent(chooser)
            }
        }
    }

    private fun resolveInstalledPackage(context: Context, candidates: List<String>): String? {
        val pm = context.packageManager
        return candidates.firstOrNull { pkg ->
            runCatching {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                true
            }.recoverCatching {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
                true
            }.getOrDefault(false)
        }
    }
}
