package com.katch

import android.content.Context
import android.util.Log
import java.io.File
import java.io.Writer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal class FileWriter(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val writerFactory: (File) -> Writer = { file -> file.bufferedWriter() },
    private val logWarning: (String, Throwable?) -> Unit = { message, error ->
        runCatching { Log.w(LOG_TAG, message, error) }
    },
    // Log.e is intentional — the spec requires E/Katch level for the saved-path message
    private val logSaved: (String) -> Unit = { message ->
        runCatching { Log.e(LOG_TAG, message) }
    }
) {
    fun write(context: Context, report: CrashReport): File? {
        val outputDirectory = context.getExternalFilesDir(DIRECTORY_NAME) ?: run {
            logWarning("External storage unavailable for crash report", null)
            return null
        }

        return try {
            if (!outputDirectory.exists()) {
                outputDirectory.mkdirs()
            }

            val outputFile = nextAvailableFile(outputDirectory, report.timestamp)
            writerFactory(outputFile).use { writer ->
                writer.write(buildReportContent(report))
            }

            logSaved("Crash report saved -> ${outputFile.absolutePath}")
            outputFile
        } catch (exception: Exception) {
            logWarning("Failed to write crash report", exception)
            null
        }
    }

    private fun nextAvailableFile(directory: File, timestamp: Instant): File {
        val baseName = "crash_${FILE_NAME_FORMATTER.format(timestamp.atZone(zoneId))}"
        var candidate = File(directory, "$baseName.txt")
        var counter = 2

        while (candidate.exists()) {
            candidate = File(directory, "${baseName}_$counter.txt")
            counter++
        }

        return candidate
    }

    private fun buildReportContent(report: CrashReport): String {
        val builder = StringBuilder()
        builder.appendLine("=====================================")
        builder.appendLine(" KATCH - CRASH REPORT")
        builder.appendLine("=====================================")
        builder.appendLine("Timestamp   : ${HEADER_FORMATTER.format(report.timestamp.atZone(zoneId))}")
        builder.appendLine("App Version : ${report.appVersion}")
        builder.appendLine("Device      : ${report.device}")
        builder.appendLine("OS Version  : ${report.osVersion}")
        builder.appendLine("=====================================")
        builder.appendLine()
        builder.appendLine("--- LOGS (last 100 entries) ---")

        report.logs.forEach { entry ->
            builder.appendLine(entry)
        }

        builder.appendLine()
        builder.appendLine("--- STACK TRACE ---")
        builder.appendLine(report.throwable.stackTraceToString().trimEnd())
        builder.appendLine("=====================================")
        return builder.toString()
    }

    private companion object {
        const val DIRECTORY_NAME = "crash_logs"
        const val LOG_TAG = "Katch"

        val FILE_NAME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
        val HEADER_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
