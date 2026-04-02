package com.katch

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.time.Instant

internal class CrashHandler(
    private val context: Context,
    private val logBuffer: LogBuffer,
    private val previousHandler: Thread.UncaughtExceptionHandler?,
    private val fileWriter: FileWriter = FileWriter(),
    private val timestampProvider: () -> Instant = { Instant.now() },
    private val appVersionProvider: () -> String = { resolveAppVersion(context) },
    private val deviceProvider: () -> String = { Build.MODEL ?: "Unknown" },
    private val osVersionProvider: () -> String = {
        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        runCatching {
            fileWriter.write(
                context,
                CrashReport(
                    timestamp = timestampProvider(),
                    appVersion = appVersionProvider(),
                    device = deviceProvider(),
                    osVersion = osVersionProvider(),
                    logs = logBuffer.snapshot(),
                    throwable = throwable
                )
            )
        }

        previousHandler?.uncaughtException(thread, throwable)
    }

    internal companion object {
        fun resolveAppVersion(context: Context): String {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }

            val versionName = packageInfo.versionName ?: "unknown"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            return "$versionName ($versionCode)"
        }
    }
}
