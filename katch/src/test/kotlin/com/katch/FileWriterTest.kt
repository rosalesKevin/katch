package com.katch

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
