package com.katch.decryptor

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import java.io.File

private const val FORMAT_VERSION: Byte = 0x01
private const val MIN_CIPHERTEXT_LENGTH = 29 // 1 version + 12 IV + 16 GCM tag

fun main(args: Array<String>) {
    val exitCode = mainReturningExitCode(args)
    if (exitCode != 0) kotlin.system.exitProcess(exitCode)
}

fun mainReturningExitCode(args: Array<String>): Int {
    val parsed = parseArgs(args) ?: return 1

    val inputFile = File(parsed.inputPath)
    if (!inputFile.exists()) {
        System.err.println("Error: Input file not found: ${parsed.inputPath}")
        return 1
    }

    val keyBytes = try {
        parsed.keyHex.hexToByteArray()
    } catch (e: Exception) {
        System.err.println("Error: Invalid hex key: ${e.message}")
        return 1
    }

    if (keyBytes.size != 32) {
        System.err.println("Error: Key must be 64 hex characters (32 bytes), got ${parsed.keyHex.length} characters")
        return 1
    }

    val encryptedBytes = inputFile.readBytes()
    if (encryptedBytes.size < MIN_CIPHERTEXT_LENGTH) {
        System.err.println("Error: File too small to be a valid encrypted report (${encryptedBytes.size} bytes)")
        return 1
    }

    if (encryptedBytes[0] != FORMAT_VERSION) {
        System.err.println(
            "Error: Unsupported format version: 0x${
                encryptedBytes[0].toInt().and(0xFF).toString(16).padStart(2, '0')
            } (expected 0x01)"
        )
        return 1
    }

    val ciphertext = encryptedBytes.copyOfRange(1, encryptedBytes.size)

    val plaintext = try {
        val provider = CryptographyProvider.Default
        val aesGcm = provider.get(AES.GCM)
        val key = aesGcm.keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, keyBytes)
        key.cipher().decryptBlocking(ciphertext)
    } catch (e: Exception) {
        System.err.println("Error: Decryption failed — wrong key or corrupted file")
        return 1
    }

    var reportText = plaintext.decodeToString()

    if (parsed.mappingPath != null) {
        val mappingFile = File(parsed.mappingPath)
        if (!mappingFile.exists()) {
            System.err.println("Error: Mapping file not found: ${parsed.mappingPath}")
            return 1
        }
        val stackSection = extractStackTrace(reportText)
        if (stackSection != null) {
            reportText = try {
                val retraced = retrace(parsed.mappingPath, stackSection)
                val idx = reportText.indexOf(stackSection)
                if (idx == -1) {
                    reportText
                } else {
                    reportText.substring(0, idx) + retraced + reportText.substring(idx + stackSection.length)
                }
            } catch (e: Exception) {
                System.err.println("Error: Retrace failed — mapping mismatch or unsupported format: ${e.message}")
                return 1
            }
        }
    }

    val output = when (parsed.format) {
        OutputFormat.TEXT -> reportText
        OutputFormat.JSON -> toJson(reportText)
    }

    if (parsed.outputPath != null) {
        File(parsed.outputPath).writeText(output)
        println("Report written to: ${parsed.outputPath}")
    } else {
        println(output)
    }

    return 0
}

private data class ParsedArgs(
    val keyHex: String,
    val inputPath: String,
    val outputPath: String?,
    val mappingPath: String?,
    val format: OutputFormat
)

internal enum class OutputFormat { TEXT, JSON }

private fun parseArgs(args: Array<String>): ParsedArgs? {
    var key: String? = null
    var input: String? = null
    var output: String? = null
    var mappingPath: String? = null
    var format = OutputFormat.TEXT

    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--key" -> { key = args.getOrNull(++i) }
            "--input" -> { input = args.getOrNull(++i) }
            "--output" -> { output = args.getOrNull(++i) }
            "--mapping" -> {
                mappingPath = args.getOrNull(++i)
                if (mappingPath == null) {
                    System.err.println("Error: --mapping requires a value")
                    printUsage()
                    return null
                }
            }
            "--format" -> {
                val raw = args.getOrNull(++i)
                format = when (raw?.lowercase()) {
                    "text" -> OutputFormat.TEXT
                    "json" -> OutputFormat.JSON
                    null -> {
                        System.err.println("Error: --format requires a value (text|json)")
                        printUsage()
                        return null
                    }
                    else -> {
                        System.err.println("Error: Unknown format '$raw' — expected text or json")
                        printUsage()
                        return null
                    }
                }
            }
            else -> {
                System.err.println("Unknown argument: ${args[i]}")
                printUsage()
                return null
            }
        }
        i++
    }

    if (key == null || input == null) {
        System.err.println("Error: --key and --input are required")
        printUsage()
        return null
    }

    return ParsedArgs(key, input, output, mappingPath, format)
}

private fun printUsage() {
    System.err.println(
        "Usage: katch-decryptor --key <hex-key> --input <file.enc> " +
        "[--output <file.txt>] [--mapping <mapping.txt>] [--format text|json]"
    )
}

private fun String.hexToByteArray(): ByteArray {
    require(length % 2 == 0) { "Hex string must have even length" }
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

/**
 * Returns the stack trace body (after the section header, before the closing banner),
 * or null if the section header is not present in the report.
 */
private fun extractStackTrace(report: String): String? {
    val marker = "--- STACK TRACE ---"
    val start = report.indexOf(marker)
    if (start == -1) return null
    return report.substring(start + marker.length)
        .trimStart('\n')
        .trimEnd()
        .removeSuffix("=====================================")
        .trimEnd()
}

/** Converts a plaintext Katch crash report to a JSON string. */
private fun toJson(report: String): String {
    val lines = report.lines()
    fun headerField(label: String): String =
        lines.firstOrNull { it.trimStart().startsWith(label) }
            ?.let { Regex("^$label\\s*:\\s*(.+)$").find(it.trim())?.groupValues?.get(1) }
            ?.escapeJson()
            ?: ""

    val logsMarker = "--- LOGS (last 100 entries) ---"
    val stackMarker = "--- STACK TRACE ---"
    val logsStart = report.indexOf(logsMarker)
    val logsEnd = report.indexOf(stackMarker)
    val logs = if (logsStart != -1 && logsEnd != -1) {
        report.substring(logsStart + logsMarker.length, logsEnd)
            .trim().lines()
            .filter { it.isNotBlank() }
            .map { it.escapeJson() }
    } else emptyList()

    val stackTrace = extractStackTrace(report)
        ?.trim()?.lines()
        ?.filter { it.isNotBlank() }
        ?.map { it.escapeJson() }
        ?: emptyList()

    val logsJson = logs.joinToString(",\n    ") { "\"$it\"" }.let {
        if (it.isEmpty()) "" else "\n    $it\n  "
    }
    val stackJson = stackTrace.joinToString(",\n    ") { "\"$it\"" }.let {
        if (it.isEmpty()) "" else "\n    $it\n  "
    }

    return """{
  "timestamp": "${headerField("Timestamp")}",
  "appVersion": "${headerField("App Version")}",
  "device": "${headerField("Device")}",
  "osVersion": "${headerField("OS Version")}",
  "logs": [$logsJson],
  "stackTrace": [$stackJson]
}"""
}

private fun String.escapeJson(): String = this
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")
    .replace("\r", "\\r")
    .replace("\t", "\\t")
