package com.example.root

import android.util.Log
import com.example.BuildConfig
import com.topjohnwu.superuser.NoShellException
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

enum class RootStatus {
    CHECKING,
    GRANTED,
    DENIED,
    NOT_ROOTED,
    TIMEOUT
}

data class RootDiagnostics(
    val status: RootStatus = RootStatus.CHECKING,
    val suBinaryPath: String? = null,
    val isMagiskDetected: Boolean = false,
    val magiskVersion: String? = null,
    val uid: Int? = null,
    val lastCheckedAtMs: Long = 0L,
    val message: String = "Pendiente de verificación root"
) {
    val isGranted: Boolean
        get() = status == RootStatus.GRANTED
}

data class RootCommandResult(
    val exitCode: Int,
    val output: String,
    val error: String,
    val timedOut: Boolean = false
) {
    val isSuccess: Boolean
        get() = exitCode == 0 && !timedOut
}

object RootManager {

    private const val TAG = "RootManager"
    const val DEFAULT_ROOT_TIMEOUT_MS = 10_000L
    const val DEFAULT_COMMAND_TIMEOUT_MS = 8_000L

    private val SU_CANDIDATE_PATHS = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/vendor/bin/su",
        "/debug_ramdisk/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/data/adb/ksu/bin/su",
        "/data/adb/ap/bin/su",
        "/data/adb/magisk/magisk"
    )

    private val MAGISK_ARTIFACTS = listOf(
        "/data/adb/magisk",
        "/data/adb/magisk.db",
        "/sbin/.magisk",
        "/debug_ramdisk/.magisk"
    )

    private val builderConfigured = AtomicBoolean(false)
    private val shellMutex = Mutex()

    private val _rootState = MutableStateFlow(RootDiagnostics())
    val rootState: StateFlow<RootDiagnostics> = _rootState.asStateFlow()

    private fun ensureShellConfigured(timeoutSeconds: Long = 10L) {
        if (builderConfigured.compareAndSet(false, true)) {
            Shell.enableVerboseLogging = BuildConfig.DEBUG
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_MOUNT_MASTER)
                    .setTimeout(timeoutSeconds)
            )
        }
    }

    /**
     * Verifica la presencia de binarios su/Magisk y valida acceso real de superusuario (uid=0)
     * usando libsu exclusivamente en Dispatchers.IO.
     *
     * @param forceRefresh Cierra la shell cacheada para re-evaluar permisos si el usuario cambió
     *                     la autorización en Magisk / KernelSU.
     */
    suspend fun verifyRootAccess(
        forceRefresh: Boolean = false,
        timeoutMs: Long = DEFAULT_ROOT_TIMEOUT_MS
    ): RootDiagnostics = withContext(Dispatchers.IO) {
        shellMutex.withLock {
            _rootState.value = _rootState.value.copy(
                status = RootStatus.CHECKING,
                message = "Verificando acceso root (Magisk / su)..."
            )

            val suBinary = findSuBinaryPath()
            val magiskArtifactPresent = hasMagiskArtifacts()
            val timeoutSeconds = TimeUnit.MILLISECONDS.toSeconds(timeoutMs).coerceAtLeast(1L)
            ensureShellConfigured(timeoutSeconds)

            if (forceRefresh || Shell.getCachedShell()?.isRoot != true) {
                runCatching { Shell.getCachedShell()?.close() }
            }

            val diagnostics = try {
                val shellResult = withTimeoutOrNull(timeoutMs) {
                    Shell.getShell()
                }

                when {
                    shellResult == null -> {
                        runCatching { Shell.getCachedShell()?.close() }
                        RootDiagnostics(
                            status = RootStatus.TIMEOUT,
                            suBinaryPath = suBinary,
                            isMagiskDetected = magiskArtifactPresent,
                            lastCheckedAtMs = System.currentTimeMillis(),
                            message = "Tiempo de espera agotado al solicitar permiso root (${timeoutMs} ms)"
                        )
                    }

                    shellResult.isRoot -> {
                        val idOutput = runShellJob(shellResult, "id")
                        val isUidZero = idOutput.output.contains("uid=0")
                        val magiskVer = detectMagiskVersion(shellResult)
                        val isMagisk = magiskArtifactPresent || magiskVer != null

                        if (isUidZero) {
                            RootDiagnostics(
                                status = RootStatus.GRANTED,
                                suBinaryPath = suBinary ?: detectSuViaShell(shellResult),
                                isMagiskDetected = isMagisk,
                                magiskVersion = magiskVer,
                                uid = 0,
                                lastCheckedAtMs = System.currentTimeMillis(),
                                message = if (magiskVer != null) {
                                    "Root concedido (uid=0 • $magiskVer)"
                                } else {
                                    "Root concedido (uid=0)"
                                }
                            )
                        } else {
                            runCatching { shellResult.close() }
                            RootDiagnostics(
                                status = RootStatus.DENIED,
                                suBinaryPath = suBinary,
                                isMagiskDetected = isMagisk,
                                lastCheckedAtMs = System.currentTimeMillis(),
                                message = "Shell abierta sin privilegios uid=0"
                            )
                        }
                    }

                    else -> {
                        runCatching { shellResult.close() }
                        if (suBinary != null || magiskArtifactPresent) {
                            RootDiagnostics(
                                status = RootStatus.DENIED,
                                suBinaryPath = suBinary,
                                isMagiskDetected = magiskArtifactPresent,
                                lastCheckedAtMs = System.currentTimeMillis(),
                                message = "Permiso root denegado o revocado en Magisk"
                            )
                        } else {
                            RootDiagnostics(
                                status = RootStatus.NOT_ROOTED,
                                suBinaryPath = null,
                                isMagiskDetected = false,
                                lastCheckedAtMs = System.currentTimeMillis(),
                                message = "Dispositivo sin binario su ni Magisk detectado"
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: NoShellException) {
                Log.w(TAG, "NoShellException verificando root: ${e.message}")
                RootDiagnostics(
                    status = if (suBinary != null || magiskArtifactPresent) RootStatus.DENIED else RootStatus.NOT_ROOTED,
                    suBinaryPath = suBinary,
                    isMagiskDetected = magiskArtifactPresent,
                    lastCheckedAtMs = System.currentTimeMillis(),
                    message = e.localizedMessage ?: "No se pudo inicializar shell su"
                )
            } catch (e: Throwable) {
                Log.e(TAG, "Error inesperado verificando root", e)
                RootDiagnostics(
                    status = if (suBinary != null) RootStatus.DENIED else RootStatus.NOT_ROOTED,
                    suBinaryPath = suBinary,
                    isMagiskDetected = magiskArtifactPresent,
                    lastCheckedAtMs = System.currentTimeMillis(),
                    message = e.localizedMessage ?: "Error desconocido al verificar root"
                )
            }

            _rootState.value = diagnostics
            diagnostics
        }
    }

    /**
     * Ejecuta un comando privilegiado con timeout controlado en Dispatchers.IO.
     * Nunca lanza excepciones no controladas al llamador.
     */
    suspend fun executeCommand(
        command: String,
        timeoutMs: Long = DEFAULT_COMMAND_TIMEOUT_MS
    ): RootCommandResult = withContext(Dispatchers.IO) {
        if (command.isBlank()) {
            return@withContext RootCommandResult(
                exitCode = -1,
                output = "",
                error = "Comando vacío"
            )
        }

        val currentShell = Shell.getCachedShell()
        val activeDiagnostics = if (currentShell == null || !currentShell.isRoot) {
            verifyRootAccess(forceRefresh = false, timeoutMs = timeoutMs)
        } else {
            _rootState.value
        }

        if (!activeDiagnostics.isGranted) {
            return@withContext RootCommandResult(
                exitCode = -1,
                output = "",
                error = activeDiagnostics.message,
                timedOut = activeDiagnostics.status == RootStatus.TIMEOUT
            )
        }

        val rootShell = Shell.getCachedShell()
        if (rootShell == null || !rootShell.isRoot) {
            _rootState.value = _rootState.value.copy(
                status = RootStatus.DENIED,
                message = "La sesión root ya no está activa"
            )
            return@withContext RootCommandResult(
                exitCode = -1,
                output = "",
                error = "La sesión root fue cerrada o revocada"
            )
        }

        try {
            val result = withTimeoutOrNull(timeoutMs) {
                runShellJob(rootShell, command)
            }

            if (result == null) {
                Log.w(TAG, "Timeout (${timeoutMs} ms) ejecutando comando root")
                runCatching { rootShell.close() }
                _rootState.value = _rootState.value.copy(
                    status = RootStatus.TIMEOUT,
                    lastCheckedAtMs = System.currentTimeMillis(),
                    message = "Timeout ejecutando comando privilegiado (${timeoutMs} ms)"
                )
                RootCommandResult(
                    exitCode = -2,
                    output = "",
                    error = "Timeout al ejecutar comando tras ${timeoutMs} ms",
                    timedOut = true
                )
            } else {
                result
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e(TAG, "Fallo ejecutando comando root: $command", e)
            RootCommandResult(
                exitCode = -1,
                output = "",
                error = e.localizedMessage ?: "Error ejecutando comando su"
            )
        }
    }

    /**
     * Habilita la app actual como proveedor de ubicación simulada a nivel de sistema (appops).
     */
    suspend fun enableAppOpsMockLocation(packageName: String): RootCommandResult {
        return executeCommand("appops set $packageName android:mock_location allow")
    }

    /**
     * Concede permisos en tiempo de ejecución vía pm grant usando privilegios root.
     */
    suspend fun grantPrivilegedPermissions(packageName: String): Boolean {
        val permissions = listOf(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.SYSTEM_ALERT_WINDOW"
        )
        var allGranted = true
        for (permission in permissions) {
            val res = executeCommand("pm grant $packageName $permission")
            if (!res.isSuccess) {
                allGranted = false
            }
        }
        return allGranted
    }

    /**
     * Obtiene el ANDROID_ID del sistema en segundo plano.
     */
    suspend fun getHardwareId(): String {
        val result = executeCommand("settings get secure android_id", timeoutMs = 4_000L)
        return if (result.isSuccess && result.output.isNotBlank() && result.output != "null") {
            result.output
        } else {
            "NO_ROOT_HWID"
        }
    }

    /**
     * Cierra la sesión de shell root cacheada (útil para limpieza o re-autenticación).
     */
    suspend fun closeSession() = withContext(Dispatchers.IO) {
        shellMutex.withLock {
            runCatching { Shell.getCachedShell()?.close() }
        }
    }

    private fun runShellJob(shell: Shell, command: String): RootCommandResult {
        val stdout = mutableListOf<String>()
        val stderr = mutableListOf<String>()
        val result = shell.newJob()
            .add(command)
            .to(stdout, stderr)
            .exec()

        return RootCommandResult(
            exitCode = result.code,
            output = stdout.joinToString("\n").trim(),
            error = stderr.joinToString("\n").trim(),
            timedOut = false
        )
    }

    private fun findSuBinaryPath(): String? {
        SU_CANDIDATE_PATHS.firstOrNull { path ->
            runCatching { File(path).exists() }.getOrDefault(false)
        }?.let { return it }

        val pathEnv = System.getenv("PATH") ?: return null
        return pathEnv.split(File.pathSeparatorChar)
            .asSequence()
            .map { dir -> File(dir, "su") }
            .firstOrNull { file -> runCatching { file.exists() && file.canExecute() }.getOrDefault(false) }
            ?.absolutePath
    }

    private fun hasMagiskArtifacts(): Boolean {
        return MAGISK_ARTIFACTS.any { path ->
            runCatching { File(path).exists() }.getOrDefault(false)
        }
    }

    private fun detectSuViaShell(shell: Shell): String? {
        val res = runShellJob(shell, "which su 2>/dev/null")
        return res.output.lineSequence().firstOrNull { it.isNotBlank() }
    }

    private fun detectMagiskVersion(shell: Shell): String? {
        val magiskRes = runShellJob(shell, "magisk -v 2>/dev/null || su -v 2>/dev/null")
        val raw = magiskRes.output.lineSequence().firstOrNull { it.isNotBlank() } ?: return null
        return raw.takeIf {
            it.contains("magisk", ignoreCase = true) ||
                it.contains("kernelsu", ignoreCase = true) ||
                it.contains("apatch", ignoreCase = true) ||
                it.any(Char::isDigit)
        }
    }
}
