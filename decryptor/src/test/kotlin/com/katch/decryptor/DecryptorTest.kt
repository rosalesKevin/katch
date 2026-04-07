package com.katch.decryptor

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `parses --mapping flag without error`() {
        val mapping = tempFile(".txt").also { it.writeText("mapping") }
        val encrypted = encrypt(key, "data".toByteArray())
        val inputFile = tempFile(".enc").also { it.writeBytes(encrypted) }

        val exitCode = run(
            "--key", key.toHex(),
            "--input", inputFile.absolutePath,
            "--mapping", mapping.absolutePath
        )

        assertEquals(0, exitCode)
    }

    @Test
    fun `parses --format json without error`() {
        val encrypted = encrypt(key, sampleReport())
        val inputFile = tempFile(".enc").also { it.writeBytes(encrypted) }
        val outputFile = tempFile(".json")

        val exitCode = run(
            "--key", key.toHex(),
            "--input", inputFile.absolutePath,
            "--output", outputFile.absolutePath,
            "--format", "json"
        )

        assertEquals(0, exitCode)
    }

    @Test
    fun `rejects unknown --format value`() {
        val encrypted = encrypt(key, "data".toByteArray())
        val inputFile = tempFile(".enc").also { it.writeBytes(encrypted) }

        val exitCode = run(
            "--key", key.toHex(),
            "--input", inputFile.absolutePath,
            "--format", "xml"
        )

        assertEquals(1, exitCode)
    }

    @Test
    fun `rejects --mapping without value`() {
        val encrypted = encrypt(key, "data".toByteArray())
        val inputFile = tempFile(".enc").also { it.writeBytes(encrypted) }

        val exitCode = run(
            "--key", key.toHex(),
            "--input", inputFile.absolutePath,
            "--mapping"
        )

        assertEquals(1, exitCode)
    }

    @Test
    fun `applies retrace when --mapping is provided`() {
        val mapping = tempFile(".txt").also {
            it.writeText("com.example.RealClass -> p1.k:\n    void realMethod() -> a\n")
        }
        val report = """
            --- STACK TRACE ---
            java.lang.RuntimeException: crash
            	at p1.k.a(Unknown Source:1)
            =====================================
        """.trimIndent()
        val encrypted = encrypt(key, report.toByteArray())
        val inputFile = tempFile(".enc").also { it.writeBytes(encrypted) }
        val outputFile = tempFile(".txt")

        val exitCode = run(
            "--key", key.toHex(),
            "--input", inputFile.absolutePath,
            "--output", outputFile.absolutePath,
            "--mapping", mapping.absolutePath
        )

        assertEquals(0, exitCode)
        val result = outputFile.readText()
        assertTrue(
            "Expected deobfuscated class name in output",
            result.contains("com.example.RealClass")
        )
        assertFalse("Obfuscated class name must not appear", result.contains("p1.k"))
    }

    @Test
    fun `exits with error when --mapping file does not exist`() {
        val encrypted = encrypt(key, "plaintext".toByteArray())
        val inputFile = tempFile(".enc").also { it.writeBytes(encrypted) }

        val exitCode = run(
            "--key", key.toHex(),
            "--input", inputFile.absolutePath,
            "--mapping", "/nonexistent/mapping.txt"
        )

        assertEquals(1, exitCode)
    }

    @Test
    fun `--format text produces plain text output`() {
        val encrypted = encrypt(key, sampleReport())
        val inputFile = tempFile(".enc").also { it.writeBytes(encrypted) }
        val outputFile = tempFile(".txt")

        val exitCode = run(
            "--key", key.toHex(),
            "--input", inputFile.absolutePath,
            "--output", outputFile.absolutePath,
            "--format", "text"
        )

        assertEquals(0, exitCode)
        val output = outputFile.readText()
        assertFalse("Plain text output must not start with {", output.trimStart().startsWith("{"))
        assertTrue("Plain text must contain report header", output.contains("KATCH - CRASH REPORT"))
    }

    @Test
    fun `outputs json when report has no logs section`() {
        val noLogsReport = """
=====================================
 KATCH - CRASH REPORT
=====================================
Timestamp   : 2026-04-07 10:00:00
App Version : 1.0.0
Device      : Pixel 7
OS Version  : 14
=====================================

--- STACK TRACE ---
java.lang.RuntimeException: crash
=====================================
""".trimIndent()
        val encrypted = encrypt(key, noLogsReport.toByteArray())
        val inputFile = tempFile(".enc").also { it.writeBytes(encrypted) }
        val outputFile = tempFile(".json")

        val exitCode = run(
            "--key", key.toHex(),
            "--input", inputFile.absolutePath,
            "--output", outputFile.absolutePath,
            "--format", "json"
        )

        assertEquals(0, exitCode)
        val json = outputFile.readText()
        assertTrue("logs field must be present", json.contains("\"logs\""))
        assertTrue("stackTrace field must be present", json.contains("\"stackTrace\""))
    }

    @Test
    fun `outputs valid json structure when --format json is specified`() {
        val encrypted = encrypt(key, sampleReport())
        val inputFile = tempFile(".enc").also { it.writeBytes(encrypted) }
        val outputFile = tempFile(".json")

        val exitCode = run(
            "--key", key.toHex(),
            "--input", inputFile.absolutePath,
            "--output", outputFile.absolutePath,
            "--format", "json"
        )

        assertEquals(0, exitCode)
        val json = outputFile.readText()
        assertTrue("Missing timestamp field", json.contains("\"timestamp\""))
        assertTrue("Missing appVersion field", json.contains("\"appVersion\""))
        assertTrue("Missing stackTrace field", json.contains("\"stackTrace\""))
        assertTrue("Missing logs field", json.contains("\"logs\""))
    }

    // --- Helpers ---

    private fun sampleReport(): ByteArray = """
=====================================
 KATCH - CRASH REPORT
=====================================
Timestamp   : 2026-04-07 10:00:00
App Version : 1.0.0
Device      : Pixel 7
OS Version  : 14
=====================================

--- LOGS (last 100 entries) ---
D some log entry

--- STACK TRACE ---
java.lang.RuntimeException: crash
	at p1.k.a(Unknown Source:1)
=====================================
""".trimIndent().toByteArray()

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
