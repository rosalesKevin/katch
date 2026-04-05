package com.katch.decryptor

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DecryptorTest {

    private val key = ByteArray(32) { it.toByte() }

    @Test
    fun `decrypts a file encrypted with the same key`() {
        val plaintext = "KATCH - CRASH REPORT\nTimestamp: 2026-04-01"
        val encrypted = encrypt(key, plaintext.toByteArray())

        val inputFile = tempFile(".enc").also { it.writeBytes(encrypted) }
        val outputFile = tempFile(".txt")

        val exitCode = run(
            "--key", key.toHex(),
            "--input", inputFile.absolutePath,
            "--output", outputFile.absolutePath
        )

        assertEquals(0, exitCode)
        assertEquals(plaintext, outputFile.readText())
    }

    @Test
    fun `exits with error for wrong key`() {
        val encrypted = encrypt(key, "secret".toByteArray())
        val inputFile = tempFile(".enc").also { it.writeBytes(encrypted) }
        val outputFile = tempFile(".txt")

        val wrongKey = ByteArray(32) { (it + 1).toByte() }
        val exitCode = run(
            "--key", wrongKey.toHex(),
            "--input", inputFile.absolutePath,
            "--output", outputFile.absolutePath
        )

        assertEquals(1, exitCode)
    }

    @Test
    fun `exits with error for missing input file`() {
        val exitCode = run(
            "--key", key.toHex(),
            "--input", "/nonexistent/file.enc"
        )

        assertEquals(1, exitCode)
    }

    @Test
    fun `exits with error for file too small`() {
        val inputFile = tempFile(".enc").also { it.writeBytes(byteArrayOf(0x01, 1, 2, 3)) }
        val outputFile = tempFile(".txt")

        val exitCode = run(
            "--key", key.toHex(),
            "--input", inputFile.absolutePath,
            "--output", outputFile.absolutePath
        )

        assertEquals(1, exitCode)
    }

    @Test
    fun `exits with error for corrupted ciphertext`() {
        val encrypted = encrypt(key, "some crash report data".toByteArray())
        encrypted[20] = (encrypted[20].toInt() xor 0xFF).toByte()
        val inputFile = tempFile(".enc").also { it.writeBytes(encrypted) }
        val outputFile = tempFile(".txt")

        val exitCode = run(
            "--key", key.toHex(),
            "--input", inputFile.absolutePath,
            "--output", outputFile.absolutePath
        )

        assertEquals(1, exitCode)
    }

    @Test
    fun `exits with error for unknown version byte`() {
        val encrypted = encrypt(key, "data".toByteArray())
        encrypted[0] = 0x99.toByte()
        val inputFile = tempFile(".enc").also { it.writeBytes(encrypted) }

        val exitCode = run(
            "--key", key.toHex(),
            "--input", inputFile.absolutePath
        )

        assertEquals(1, exitCode)
    }

    // --- Helpers ---

    private fun encrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        val provider = CryptographyProvider.Default
        val aesGcm = provider.get(AES.GCM)
        val aesKey = aesGcm.keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)
        val ciphertext = aesKey.cipher().encryptBlocking(plaintext)
        return byteArrayOf(0x01) + ciphertext
    }

    private fun tempFile(suffix: String): File =
        Files.createTempFile("katch-decrypt-test", suffix).toFile().also { it.deleteOnExit() }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun run(vararg args: String): Int = mainReturningExitCode(args.toList().toTypedArray())
}
