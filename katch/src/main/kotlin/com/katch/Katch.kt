package com.katch

import android.content.Context
import java.time.Instant
import java.time.ZoneId

/**
 * Katch is the single entry point for the library.
 *
 * Call [init] once in your [android.app.Application.onCreate]. Everything else — logging,
 * crash capture, and report writing — happens automatically after that.
 */
object Katch {

    /**
     * Represents the encryption strategy for crash reports.
     *
     * Pass an [EncryptionKey] to [init] to enable AES-256-GCM encryption.
     * Omit it entirely for plaintext reports.
     */
    sealed interface EncryptionKey {
        /**
         * Katch generates and manages the AES-256 key for you via Android Keystore.
         * The key persists across app restarts. Retrieve it at any time with [exportKey].
         */
        object Auto : EncryptionKey
    }

    private var appContext: Context? = null
    private var logBuffer: LogBuffer? = null
    private var crashHandler: CrashHandler? = null
    private var fileWriter: FileWriter? = null
    private var keyManager: KeyManager? = null
    private var isInitialized = false
    private var testHooks = TestHooks()

    /**
     * Initializes Katch without encryption.
     * Crash reports are written as plaintext `.txt` files.
     *
     * Subsequent calls after the first are ignored.
     */
    fun init(context: Context) {
        initInternal(context, null)
    }

    /**
     * Initializes Katch with a caller-supplied AES-256 key.
     * Crash reports are written as encrypted `.enc` files.
     *
     * @param encryptionKey Must be exactly 32 bytes. Throws [IllegalArgumentException] otherwise.
     *
     * Subsequent calls after the first are ignored.
     */
    fun init(context: Context, encryptionKey: ByteArray) {
        if (isInitialized) return
        require(encryptionKey.size == 32) {
            "Encryption key must be exactly 32 bytes (AES-256), got ${encryptionKey.size}"
        }
        initInternal(context, Encryptor(encryptionKey))
    }

    /**
     * Initializes Katch with a managed key strategy.
     * Crash reports are written as encrypted `.enc` files.
     *
     * @param encryptionKey Use [EncryptionKey.Auto] to let Katch generate and persist the key.
     *
     * Subsequent calls after the first are ignored.
     */
    fun init(context: Context, encryptionKey: EncryptionKey) {
        if (isInitialized) return
        when (encryptionKey) {
            is EncryptionKey.Auto -> {
                val km = testHooks.keyManagerFactory(context.applicationContext)
                val key = km.getOrGenerateKey()
                keyManager = km
                initInternal(context, Encryptor(key))
            }
        }
    }

    private fun initInternal(context: Context, encryptor: Encryptor?) {
        if (isInitialized) return

        val applicationContext = context.applicationContext
        val buffer = LogBuffer()
        val writer = FileWriter(zoneId = testHooks.zoneIdProvider(), encryptor = encryptor)
        val handler = testHooks.crashHandlerFactory(
            applicationContext,
            buffer,
            testHooks.currentHandlerProvider(),
            writer
        )

        appContext = applicationContext
        logBuffer = buffer
        fileWriter = writer
        crashHandler = handler
        testHooks.handlerInstaller(handler)
        isInitialized = true
    }

    /**
     * Returns the raw 32-byte AES-256 key currently in use, or `null` if encryption is disabled.
     *
     * Only useful when initialized with [EncryptionKey.Auto]. Pass the result to the CLI
     * decryptor to read `.enc` crash reports on your development machine.
     */
    fun exportKey(): ByteArray? = keyManager?.exportKey()

    /** Logs a debug message. Dropped silently if called before [init]. */
    fun d(tag: String, message: String) = addLog("D", tag, message)

    /** Logs an info message. Dropped silently if called before [init]. */
    fun i(tag: String, message: String) = addLog("I", tag, message)

    /** Logs a warning message. Dropped silently if called before [init]. */
    fun w(tag: String, message: String) = addLog("W", tag, message)

    /** Logs an error message. Dropped silently if called before [init]. */
    fun e(tag: String, message: String) = addLog("E", tag, message)

    /**
     * Writes a crash report using the current log buffer and a synthetic stack trace,
     * without terminating the app.
     *
     * Useful for verifying the report format and file path during development.
     * Has no effect if called before [init].
     */
    fun testCrash() {
        val context = appContext ?: return
        val buffer = logBuffer ?: return
        val writer = fileWriter ?: return
        val report = CrashReport(
            timestamp = testHooks.timestampProvider(),
            appVersion = testHooks.appVersionProvider(context),
            device = testHooks.deviceProvider(),
            osVersion = testHooks.osVersionProvider(),
            logs = buffer.snapshot(),
            throwable = RuntimeException("Katch.testCrash() - simulated crash")
        )

        runCatching {
            writer.write(context, report)
        }
    }

    internal fun resetForTests() {
        appContext = null
        logBuffer = null
        crashHandler = null
        fileWriter = null
        keyManager = null
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
            Thread.UncaughtExceptionHandler?,
            FileWriter
        ) -> CrashHandler = testHooks.crashHandlerFactory,
        timestampProvider: () -> Instant = testHooks.timestampProvider,
        appVersionProvider: (Context) -> String = testHooks.appVersionProvider,
        deviceProvider: () -> String = testHooks.deviceProvider,
        osVersionProvider: () -> String = testHooks.osVersionProvider,
        keyManagerFactory: (Context) -> KeyManager = testHooks.keyManagerFactory,
        zoneIdProvider: () -> ZoneId = testHooks.zoneIdProvider
    ) {
        testHooks = TestHooks(
            currentHandlerProvider = currentHandlerProvider,
            handlerInstaller = handlerInstaller,
            crashHandlerFactory = crashHandlerFactory,
            timestampProvider = timestampProvider,
            appVersionProvider = appVersionProvider,
            deviceProvider = deviceProvider,
            osVersionProvider = osVersionProvider,
            keyManagerFactory = keyManagerFactory,
            zoneIdProvider = zoneIdProvider
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
            Thread.UncaughtExceptionHandler?,
            FileWriter
        ) -> CrashHandler = { context, buffer, previous, writer ->
            CrashHandler(
                context = context,
                logBuffer = buffer,
                previousHandler = previous,
                fileWriter = writer
            )
        },
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
        },
        val keyManagerFactory: (Context) -> KeyManager = { context -> KeyManager(context) },
        val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() }
    )
}
