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
