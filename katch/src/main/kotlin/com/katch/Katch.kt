package com.katch

import android.content.Context
import java.time.Instant

object Katch {
    private var appContext: Context? = null
    private var logBuffer: LogBuffer? = null
    private var crashHandler: CrashHandler? = null
    private var isInitialized = false
    private var testHooks = TestHooks()

    fun init(context: Context) {
        if (isInitialized) {
            return
        }

        val applicationContext = context.applicationContext
        val buffer = LogBuffer()
        val handler = testHooks.crashHandlerFactory(
            applicationContext,
            buffer,
            testHooks.currentHandlerProvider()
        )

        appContext = applicationContext
        logBuffer = buffer
        crashHandler = handler
        testHooks.handlerInstaller(handler)
        isInitialized = true
    }

    fun d(tag: String, message: String) = addLog("D", tag, message)

    fun i(tag: String, message: String) = addLog("I", tag, message)

    fun w(tag: String, message: String) = addLog("W", tag, message)

    fun e(tag: String, message: String) = addLog("E", tag, message)

    fun testCrash() {
        val context = appContext ?: return
        val buffer = logBuffer ?: return
        val report = CrashReport(
            timestamp = testHooks.timestampProvider(),
            appVersion = testHooks.appVersionProvider(context),
            device = testHooks.deviceProvider(),
            osVersion = testHooks.osVersionProvider(),
            logs = buffer.snapshot(),
            throwable = RuntimeException("Katch.testCrash() - simulated crash")
        )

        runCatching {
            testHooks.fileWriterFactory().write(context, report)
        }
    }

    internal fun resetForTests() {
        appContext = null
        logBuffer = null
        crashHandler = null
        isInitialized = false
        testHooks = TestHooks()
    }

    internal fun snapshotForTests(): List<String> = logBuffer?.snapshot().orEmpty()

    internal fun configureForTests(
        currentHandlerProvider: () -> Thread.UncaughtExceptionHandler? = testHooks.currentHandlerProvider,
        handlerInstaller: (Thread.UncaughtExceptionHandler) -> Unit = testHooks.handlerInstaller,
        crashHandlerFactory: (
            Context,
            LogBuffer,
            Thread.UncaughtExceptionHandler?
        ) -> CrashHandler = testHooks.crashHandlerFactory,
        fileWriterFactory: () -> FileWriter = testHooks.fileWriterFactory,
        timestampProvider: () -> Instant = testHooks.timestampProvider,
        appVersionProvider: (Context) -> String = testHooks.appVersionProvider,
        deviceProvider: () -> String = testHooks.deviceProvider,
        osVersionProvider: () -> String = testHooks.osVersionProvider
    ) {
        testHooks = TestHooks(
            currentHandlerProvider = currentHandlerProvider,
            handlerInstaller = handlerInstaller,
            crashHandlerFactory = crashHandlerFactory,
            fileWriterFactory = fileWriterFactory,
            timestampProvider = timestampProvider,
            appVersionProvider = appVersionProvider,
            deviceProvider = deviceProvider,
            osVersionProvider = osVersionProvider
        )
    }

    private fun addLog(level: String, tag: String, message: String) {
        val entry = "[${testHooks.logTimeProvider()}] $level/$tag: $message"
        logBuffer?.add(entry)
    }

    private data class TestHooks(
        val currentHandlerProvider: () -> Thread.UncaughtExceptionHandler? = {
            Thread.getDefaultUncaughtExceptionHandler()
        },
        val handlerInstaller: (Thread.UncaughtExceptionHandler) -> Unit = { handler ->
            Thread.setDefaultUncaughtExceptionHandler(handler)
        },
        val crashHandlerFactory: (
            Context,
            LogBuffer,
            Thread.UncaughtExceptionHandler?
        ) -> CrashHandler = { context, buffer, previous ->
            CrashHandler(
                context = context,
                logBuffer = buffer,
                previousHandler = previous
            )
        },
        val fileWriterFactory: () -> FileWriter = { FileWriter() },
        val timestampProvider: () -> Instant = { Instant.now() },
        val appVersionProvider: (Context) -> String = { context ->
            CrashHandler.resolveAppVersion(context)
        },
        val deviceProvider: () -> String = { android.os.Build.MODEL ?: "Unknown" },
        val osVersionProvider: () -> String = {
            "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
        },
        val logTimeProvider: () -> String = {
            java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
        }
    )
}
