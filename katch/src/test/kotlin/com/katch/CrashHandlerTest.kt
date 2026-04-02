package com.katch

import android.content.Context
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class CrashHandlerTest {

    @Test
    fun `uncaughtException writes a crash report with app and device metadata`() {
        val context = mockk<Context>()
        val fileWriter = mockk<FileWriter>()
        val previousHandler = mockk<Thread.UncaughtExceptionHandler>()
        val logBuffer = LogBuffer(maxEntries = 5).apply {
            add("[14:32:01] D/Auth: Started")
            add("[14:32:04] E/Auth: Failed")
        }
        val capturedReport = slot<CrashReport>()
        every { fileWriter.write(context, any()) } answers {
            capturedReport.captured = secondArg()
            null
        }
        every { previousHandler.uncaughtException(any(), any()) } just runs

        val crashHandler = CrashHandler(
            context = context,
            logBuffer = logBuffer,
            previousHandler = previousHandler,
            fileWriter = fileWriter,
            timestampProvider = { Instant.parse("2026-04-01T14:32:05Z") },
            appVersionProvider = { "1.2.3 (45)" },
            deviceProvider = { "Pixel 8" },
            osVersionProvider = { "Android 15 (API 35)" }
        )
        val thread = Thread.currentThread()
        val throwable = IllegalStateException("Broken")

        crashHandler.uncaughtException(thread, throwable)

        assertEquals("1.2.3 (45)", capturedReport.captured.appVersion)
        assertEquals("Pixel 8", capturedReport.captured.device)
        assertEquals("Android 15 (API 35)", capturedReport.captured.osVersion)
        assertEquals(
            listOf("[14:32:01] D/Auth: Started", "[14:32:04] E/Auth: Failed"),
            capturedReport.captured.logs
        )
        assertEquals(throwable, capturedReport.captured.throwable)
        verify(exactly = 1) { fileWriter.write(context, any()) }
        verify(exactly = 1) { previousHandler.uncaughtException(thread, throwable) }
    }

    @Test
    fun `uncaughtException still chains when no previous handler is installed`() {
        val context = mockk<Context>()
        val fileWriter = mockk<FileWriter>()
        every { fileWriter.write(any(), any()) } returns null

        val crashHandler = CrashHandler(
            context = context,
            logBuffer = LogBuffer(),
            previousHandler = null,
            fileWriter = fileWriter,
            timestampProvider = { Instant.parse("2026-04-01T14:32:05Z") },
            appVersionProvider = { "1.2.3 (45)" },
            deviceProvider = { "Pixel 8" },
            osVersionProvider = { "Android 15 (API 35)" }
        )

        crashHandler.uncaughtException(Thread.currentThread(), RuntimeException("Boom"))

        verify(exactly = 1) { fileWriter.write(context, any()) }
    }
}
