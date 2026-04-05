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

    val output = plaintext.decodeToString()

    if (parsed.outputPath != null) {
        File(parsed.outputPath).writeText(output)
        println("Decrypted report written to: ${parsed.outputPath}")
    } else {
        println(output)
    }

    return 0
}

private data class ParsedArgs(
    val keyHex: String,
    val inputPath: String,
    val outputPath: String?
)

private fun parseArgs(args: Array<String>): ParsedArgs? {
    var key: String? = null
    var input: String? = null
    var output: String? = null

    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--key" -> { key = args.getOrNull(++i) }
            "--input" -> { input = args.getOrNull(++i) }
            "--output" -> { output = args.getOrNull(++i) }
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

    return ParsedArgs(key, input, output)
}

private fun printUsage() {
    System.err.println("Usage: katch-decryptor --key <hex-key> --input <file.enc> [--output <file.txt>]")
}

private fun String.hexToByteArray(): ByteArray {
    require(length % 2 == 0) { "Hex string must have even length" }
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
