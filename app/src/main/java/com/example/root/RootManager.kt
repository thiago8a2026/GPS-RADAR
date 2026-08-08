package com.example.root

import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

data class RootCommandResult(
    val exitCode: Int,
    val output: String,
    val error: String
)

object RootManager {
    private const val TAG = "RootManager"

    /**
     * Checks whether root privileges (su) are available on the device.
     */
    fun isRootAvailable(): Boolean {
        val result = executeSuCommand("id")
        return result.exitCode == 0 && (result.output.contains("uid=0") || result.output.contains("root"))
    }

    /**
     * Executes a command via su shell.
     */
    fun executeSuCommand(command: String): RootCommandResult {
        var process: Process? = null
        var os: DataOutputStream? = null
        var reader: BufferedReader? = null
        var errReader: BufferedReader? = null

        return try {
            process = Runtime.getRuntime().exec("su")
            os = DataOutputStream(process.outputStream)
            
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()

            val stdout = StringBuilder()
            val stderr = StringBuilder()

            reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                stdout.append(line).append("\n")
            }

            errReader = BufferedReader(InputStreamReader(process.errorStream))
            while (errReader.readLine().also { line = it } != null) {
                stderr.append(line).append("\n")
            }

            val exitCode = process.waitFor()
            RootCommandResult(
                exitCode = exitCode,
                output = stdout.toString().trim(),
                error = stderr.toString().trim()
            )
        } catch (e: Exception) {
            Log.w(TAG, "su command not available or denied: ${e.message}")
            RootCommandResult(-1, "", e.localizedMessage ?: "Execution failed")
        } finally {
            try { os?.close() } catch (_: Exception) {}
            try { reader?.close() } catch (_: Exception) {}
            try { errReader?.close() } catch (_: Exception) {}
            try { process?.destroy() } catch (_: Exception) {}
        }
    }

    /**
     * Attempts to convert the app to a system app (/system/priv-app/) using Root privileges.
     * Note: On modern Android (Android 10+ / APEX / SAR), remounting /system requires Magisk overlay
     * or copying to /data/adb/modules.
     */
    fun installAsSystemApp(apkPath: String, packageName: String): RootCommandResult {
        val cmds = """
            mount -o remount,rw /system 2>/dev/null || mount -o remount,rw / 2>/dev/null
            mkdir -p /system/priv-app/$packageName
            cp $apkPath /system/priv-app/$packageName/$packageName.apk
            chmod 755 /system/priv-app/$packageName
            chmod 644 /system/priv-app/$packageName/$packageName.apk
            chcon u:object_r:system_file:s0 /system/priv-app/$packageName/$packageName.apk 2>/dev/null
        """.trimIndent()
        return executeSuCommand(cmds)
    }

    /**
     * Grants specific system permissions using pm grant via root.
     */
    fun grantPrivilegedPermissions(packageName: String): Boolean {
        val permissions = listOf(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.ACCESS_MOCK_LOCATION"
        )
        var allSuccess = true
        for (perm in permissions) {
            val res = executeSuCommand("pm grant $packageName $perm")
            if (res.exitCode != 0) {
                allSuccess = false
            }
        }
        return allSuccess
    }

    /**
     * Reads system hardware ID (ANDROID_ID) directly via settings root command.
     */
    fun getHardwareId(): String {
        val result = executeSuCommand("settings get secure android_id")
        return if (result.exitCode == 0 && result.output.isNotBlank()) {
            result.output
        } else {
            "UNKNOWN_HWID"
        }
    }
}
