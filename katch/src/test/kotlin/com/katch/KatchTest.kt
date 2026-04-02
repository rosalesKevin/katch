package com.katch

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId

class KatchTest {

    @Before
    fun setUp() {
        Katch.resetForTests()
    }

    @Test
    fun `logs before init are dropped`() {
        Katch.d("Auth", "before init")

        assertEquals(emptyList<String>(), Katch.snapshotForTests())
    }

    @Test
    fun `double init is a no-op`() {
        val appContext = mockk<Context>()
        val hostContext = mockk<Context>()
        every { hostContext.applicationContext } returns appContext

        var installCount = 0
        Katch.configureForTests(
            crashHandlerFactory = { _, _, _ ->
                mockk(relaxed = true)
            },
            handlerInstaller = {
                installCount++
            }
        )

        Katch.init(hostContext)
        Katch.init(hostContext)

        assertEquals(1, installCount)
    }

    @Test
    fun `testCrash writes a report without throwing`() {
        val rootDir = Files.createTempDirectory("katch-test-crash").toFile()
        val reportsDir = File(rootDir, "crash_logs")
        val appContext = mockk<Context>()
        val hostContext = mockk<Context>()
        every { hostContext.applicationContext } returns appContext
        every { appContext.getExternalFilesDir("crash_logs") } returns reportsDir

        Katch.configureForTests(
            currentHandlerProvider = { null },
            crashHandlerFactory = { context, logBuffer, previous ->
                CrashHandler(
                    context = context,
                    logBuffer = logBuffer,
                    previousHandler = previous,
                    fileWriter = FileWriter(zoneId = ZoneId.of("UTC")),
                    timestampProvider = { Instant.parse("2026-04-01T14:32:05Z") },
                    appVersionProvider = { "1.2.3 (45)" },
                    deviceProvider = { "Pixel 8" },
                    osVersionProvider = { "Android 15 (API 35)" }
                )
            },
            fileWriterFactory = {
                FileWriter(zoneId = ZoneId.of("UTC"))
            },
            timestampProvider = { Instant.parse("2026-04-01T14:32:05Z") },
            appVersionProvider = { "1.2.3 (45)" },
            deviceProvider = { "Pixel 8" },
            osVersionProvider = { "Android 15 (API 35)" }
        )

        Katch.init(hostContext)
        Katch.i("Auth", "initialized")

        Katch.testCrash()

        val reportFile = File(reportsDir, "crash_2026-04-01_14-32-05.txt")
        assertTrue(reportFile.exists())
        assertNotNull(reportFile.readText())
        assertTrue(reportFile.readText().contains("simulated crash"))
        assertTrue(reportFile.readText().contains("["))
    }
}
