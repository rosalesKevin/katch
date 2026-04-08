package com.katch

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.Writer
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId

class FileWriterTest {

    @Test
    fun `write creates a crash report file with the expected content`() {
        val rootDir = Files.createTempDirectory("katch-write").toFile()
        val reportsDir = File(rootDir, "crash_logs")
        val context = mockk<Context>()
        every { context.getExternalFilesDir("crash_logs") } returns reportsDir

        val fileWriter = FileWriter(zoneId = UTC)
        val report = CrashReport(
            timestamp = Instant.parse("2026-04-01T14:32:05Z"),
            appVersion = "1.2.3 (45)",
            device = "Pixel 8",
            osVersion = "Android 15 (API 35)",
            logs = listOf("[14:32:01] D/Auth: Started", "[14:32:03] E/Auth: Failed"),
            throwable = RuntimeException("Boom")
        )

        val output = fileWriter.write(context, report)

        assertNotNull(output)
        assertEquals("crash_2026-04-01_14-32-05.txt", output?.name)
        assertTrue(output?.readText()?.contains("KATCH - CRASH REPORT") == true)
        assertTrue(output?.readText()?.contains("App Version : 1.2.3 (45)") == true)
        assertTrue(output?.readText()?.contains("[14:32:03] E/Auth: Failed") == true)
        assertTrue(output?.readText()?.contains("java.lang.RuntimeException: Boom") == true)
    }

    @Test
    fun `write appends a suffix when a report file for the same second already exists`() {
        val rootDir = Files.createTempDirectory("katch-suffix").toFile()
        val reportsDir = File(rootDir, "crash_logs")
        reportsDir.mkdirs()
        File(reportsDir, "crash_2026-04-01_14-32-05.txt").writeText("existing")

        val context = mockk<Context>()
        every { context.getExternalFilesDir("crash_logs") } returns reportsDir

        val output = FileWriter(zoneId = UTC).write(context, sampleReport())

        assertEquals("crash_2026-04-01_14-32-05_2.txt", output?.name)
    }

    @Test
    fun `write returns null when external storage is unavailable`() {
        val context = mockk<Context>()
        every { context.getExternalFilesDir("crash_logs") } returns null

        val output = FileWriter(zoneId = UTC).write(context, sampleReport())

        assertEquals(null, output)
    }

    @Test
    fun `write swallows io failures and returns null`() {
        val rootDir = Files.createTempDirectory("katch-failure").toFile()
        val reportsDir = File(rootDir, "crash_logs")
        val context = mockk<Context>()
        every { context.getExternalFilesDir("crash_logs") } returns reportsDir

        val fileWriter = FileWriter(
            zoneId = UTC,
            writerFactory = { throw IOException("disk full") }
        )

        val output = fileWriter.write(context, sampleReport())

        assertEquals(null, output)
    }

    @Test
    fun `write with encryptor produces an enc file`() {
        val rootDir = Files.createTempDirectory("katch-enc").toFile()
        val reportsDir = File(rootDir, "crash_logs")
        val context = mockk<Context>()
        every { context.getExternalFilesDir("crash_logs") } returns reportsDir

        val key = ByteArray(32) { it.toByte() }
        val encryptor = Encryptor(key)
        val fileWriter = FileWriter(zoneId = UTC, encryptor = encryptor)
        val report = sampleReport()

        val output = fileWriter.write(context, report)

        assertNotNull(output)
        assertEquals("crash_2026-04-01_14-32-05.enc", output?.name)
    }

    @Test
    fun `encrypted file round-trips through Encryptor decrypt`() {
        val rootDir = Files.createTempDirectory("katch-enc-rt").toFile()
        val reportsDir = File(rootDir, "crash_logs")
        val context = mockk<Context>()
        every { context.getExternalFilesDir("crash_logs") } returns reportsDir

        val key = ByteArray(32) { it.toByte() }
        val encryptor = Encryptor(key)
        val fileWriter = FileWriter(zoneId = UTC, encryptor = encryptor)

        val output = fileWriter.write(context, sampleReport())

        assertNotNull(output)
        val encryptedBytes = output!!.readBytes()
        val decrypted = encryptor.decrypt(encryptedBytes).decodeToString()
        assertTrue(decrypted.contains("KATCH - CRASH REPORT"))
        assertTrue(decrypted.contains("Boom"))
    }

    @Test
    fun `encrypted file starts with format version byte`() {
        val rootDir = Files.createTempDirectory("katch-enc-ver").toFile()
        val reportsDir = File(rootDir, "crash_logs")
        val context = mockk<Context>()
        every { context.getExternalFilesDir("crash_logs") } returns reportsDir

        val key = ByteArray(32) { it.toByte() }
        val encryptor = Encryptor(key)
        val fileWriter = FileWriter(zoneId = UTC, encryptor = encryptor)

        val output = fileWriter.write(context, sampleReport())

        assertEquals(0x01.toByte(), output!!.readBytes()[0])
    }

    @Test
    fun `write with encryptor appends suffix for collision`() {
        val rootDir = Files.createTempDirectory("katch-enc-suffix").toFile()
        val reportsDir = File(rootDir, "crash_logs")
        reportsDir.mkdirs()
        File(reportsDir, "crash_2026-04-01_14-32-05.enc").writeBytes(byteArrayOf(1, 2, 3))

        val context = mockk<Context>()
        every { context.getExternalFilesDir("crash_logs") } returns reportsDir

        val key = ByteArray(32) { it.toByte() }
        val encryptor = Encryptor(key)
        val fileWriter = FileWriter(zoneId = UTC, encryptor = encryptor)

        val output = fileWriter.write(context, sampleReport())

        assertEquals("crash_2026-04-01_14-32-05_2.enc", output?.name)
    }

    @Test
    fun `write without encryptor still produces txt file`() {
        val rootDir = Files.createTempDirectory("katch-no-enc").toFile()
        val reportsDir = File(rootDir, "crash_logs")
        val context = mockk<Context>()
        every { context.getExternalFilesDir("crash_logs") } returns reportsDir

        val fileWriter = FileWriter(zoneId = UTC)
        val output = fileWriter.write(context, sampleReport())

        assertNotNull(output)
        assertEquals("crash_2026-04-01_14-32-05.txt", output?.name)
        assertTrue(output!!.readText().contains("KATCH - CRASH REPORT"))
    }

    @Test
    fun `write uses custom dir when it already exists`() {
        val rootDir = Files.createTempDirectory("katch-custom-exists").toFile()
        val customDir = File(rootDir, "my_custom_crashes").also { it.mkdirs() }
        val context = mockk<Context>()
        every { context.getExternalFilesDir(any()) } returns File(rootDir, "crash_logs")

        val fileWriter = FileWriter(zoneId = UTC, customOutputDirProvider = { customDir })
        val output = fileWriter.write(context, sampleReport())

        assertNotNull(output)
        assertEquals(customDir.absolutePath, output!!.parentFile.absolutePath)
    }

    @Test
    fun `write creates custom dir when it does not exist yet`() {
        val rootDir = Files.createTempDirectory("katch-custom-create").toFile()
        val customDir = File(rootDir, "new_dir_not_yet_created")
        val context = mockk<Context>()
        every { context.getExternalFilesDir(any()) } returns File(rootDir, "crash_logs")

        val fileWriter = FileWriter(zoneId = UTC, customOutputDirProvider = { customDir })
        val output = fileWriter.write(context, sampleReport())

        assertNotNull(output)
        assertTrue(customDir.exists())
        assertEquals(customDir.absolutePath, output!!.parentFile.absolutePath)
    }

    @Test
    fun `write falls back to default when custom dir mkdirs fails`() {
        val rootDir = Files.createTempDirectory("katch-custom-fallback").toFile()
        // Place a file at the intended directory path so mkdirs() returns false
        val blockedPath = File(rootDir, "blocked").also { it.writeText("i am a file") }
        val defaultDir = File(rootDir, "crash_logs")
        val context = mockk<Context>()
        every { context.getExternalFilesDir(any()) } returns defaultDir

        val warnings = mutableListOf<String>()
        val fileWriter = FileWriter(
            zoneId = UTC,
            customOutputDirProvider = { blockedPath },
            logWarning = { msg, _ -> warnings.add(msg) }
        )
        val output = fileWriter.write(context, sampleReport())

        assertNotNull(output)
        assertEquals(defaultDir.absolutePath, output!!.parentFile.absolutePath)
        assertTrue("Expected a fallback warning", warnings.any { it.contains("Custom output directory unavailable") })
    }

    @Test
    fun `write uses default dir when no custom dir is set`() {
        val rootDir = Files.createTempDirectory("katch-no-custom").toFile()
        val defaultDir = File(rootDir, "crash_logs")
        val context = mockk<Context>()
        every { context.getExternalFilesDir(any()) } returns defaultDir

        val fileWriter = FileWriter(zoneId = UTC) // no customOutputDirProvider
        val output = fileWriter.write(context, sampleReport())

        assertNotNull(output)
        assertEquals(defaultDir.absolutePath, output!!.parentFile.absolutePath)
    }

    @Test
    fun `write returns null when default dir cannot be created`() {
        val rootDir = Files.createTempDirectory("katch-default-mkdirs-fail").toFile()
        // Place a file at the default dir path so mkdirs() on it fails
        val blockedDefaultDir = File(rootDir, "crash_logs").also { it.writeText("i am a file") }
        val context = mockk<Context>()
        every { context.getExternalFilesDir(any()) } returns blockedDefaultDir

        val warnings = mutableListOf<String>()
        val fileWriter = FileWriter(
            zoneId = UTC,
            logWarning = { msg, _ -> warnings.add(msg) }
        )
        val output = fileWriter.write(context, sampleReport())

        assertNull(output)
        assertTrue("Expected a warning about default dir creation failure",
            warnings.any { it.contains("Failed to create default output directory") })
    }

    @Test
    fun `write returns null when custom dir fails and external storage is also unavailable`() {
        val rootDir = Files.createTempDirectory("katch-double-fail").toFile()
        val blockedPath = File(rootDir, "blocked").also { it.writeText("i am a file") }
        val context = mockk<Context>()
        every { context.getExternalFilesDir(any()) } returns null

        val fileWriter = FileWriter(
            zoneId = UTC,
            customOutputDirProvider = { blockedPath }
        )
        val output = fileWriter.write(context, sampleReport())

        assertNull(output)
    }

    private fun sampleReport(): CrashReport = CrashReport(
        timestamp = Instant.parse("2026-04-01T14:32:05Z"),
        appVersion = "1.2.3 (45)",
        device = "Pixel 8",
        osVersion = "Android 15 (API 35)",
        logs = listOf("[14:32:01] D/Auth: Started"),
        throwable = RuntimeException("Boom")
    )

    private companion object {
        val UTC: ZoneId = ZoneId.of("UTC")
    }
}
