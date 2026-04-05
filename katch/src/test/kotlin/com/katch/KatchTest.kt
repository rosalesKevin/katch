package com.katch

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId

private val UTC = ZoneId.of("UTC")

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
            crashHandlerFactory = { _, _, _, _ ->
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
            crashHandlerFactory = { context, logBuffer, previous, writer ->
                CrashHandler(
                    context = context,
                    logBuffer = logBuffer,
                    previousHandler = previous,
                    fileWriter = writer,
                    timestampProvider = { Instant.parse("2026-04-01T14:32:05Z") },
                    appVersionProvider = { "1.2.3 (45)" },
                    deviceProvider = { "Pixel 8" },
                    osVersionProvider = { "Android 15 (API 35)" }
                )
            },
            timestampProvider = { Instant.parse("2026-04-01T14:32:05Z") },
            appVersionProvider = { "1.2.3 (45)" },
            deviceProvider = { "Pixel 8" },
            osVersionProvider = { "Android 15 (API 35)" },
            zoneIdProvider = { UTC }
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

    @Test
    fun `init with ByteArray encryption key produces encrypted crash reports`() {
        val rootDir = Files.createTempDirectory("katch-enc-init").toFile()
        val reportsDir = File(rootDir, "crash_logs")
        val appContext = mockk<Context>()
        val hostContext = mockk<Context>()
        every { hostContext.applicationContext } returns appContext
        every { appContext.getExternalFilesDir("crash_logs") } returns reportsDir

        val key = ByteArray(32) { it.toByte() }
        Katch.configureForTests(
            currentHandlerProvider = { null },
            crashHandlerFactory = { context, logBuffer, previous, writer ->
                CrashHandler(
                    context = context,
                    logBuffer = logBuffer,
                    previousHandler = previous,
                    fileWriter = writer,
                    timestampProvider = { Instant.parse("2026-04-01T14:32:05Z") },
                    appVersionProvider = { "1.2.3 (45)" },
                    deviceProvider = { "Pixel 8" },
                    osVersionProvider = { "Android 15 (API 35)" }
                )
            },
            timestampProvider = { Instant.parse("2026-04-01T14:32:05Z") },
            appVersionProvider = { "1.2.3 (45)" },
            deviceProvider = { "Pixel 8" },
            osVersionProvider = { "Android 15 (API 35)" },
            zoneIdProvider = { UTC }
        )

        Katch.init(hostContext, encryptionKey = key)
        Katch.testCrash()

        val encFile = File(reportsDir, "crash_2026-04-01_14-32-05.enc")
        assertTrue("Expected .enc file to exist", encFile.exists())

        val encryptor = Encryptor(key)
        val decrypted = encryptor.decrypt(encFile.readBytes()).decodeToString()
        assertTrue(decrypted.contains("KATCH - CRASH REPORT"))
        assertTrue(decrypted.contains("simulated crash"))
    }

    @Test
    fun `init without encryption key produces plaintext txt reports`() {
        val rootDir = Files.createTempDirectory("katch-no-enc-init").toFile()
        val reportsDir = File(rootDir, "crash_logs")
        val appContext = mockk<Context>()
        val hostContext = mockk<Context>()
        every { hostContext.applicationContext } returns appContext
        every { appContext.getExternalFilesDir("crash_logs") } returns reportsDir

        Katch.configureForTests(
            currentHandlerProvider = { null },
            crashHandlerFactory = { context, logBuffer, previous, writer ->
                CrashHandler(
                    context = context,
                    logBuffer = logBuffer,
                    previousHandler = previous,
                    fileWriter = writer,
                    timestampProvider = { Instant.parse("2026-04-01T14:32:05Z") },
                    appVersionProvider = { "1.2.3 (45)" },
                    deviceProvider = { "Pixel 8" },
                    osVersionProvider = { "Android 15 (API 35)" }
                )
            },
            timestampProvider = { Instant.parse("2026-04-01T14:32:05Z") },
            appVersionProvider = { "1.2.3 (45)" },
            deviceProvider = { "Pixel 8" },
            osVersionProvider = { "Android 15 (API 35)" },
            zoneIdProvider = { UTC }
        )

        Katch.init(hostContext)
        Katch.testCrash()

        val txtFile = File(reportsDir, "crash_2026-04-01_14-32-05.txt")
        assertTrue("Expected .txt file to exist", txtFile.exists())
        assertTrue(txtFile.readText().contains("KATCH - CRASH REPORT"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `init with wrong key length throws IllegalArgumentException`() {
        val appContext = mockk<Context>()
        val hostContext = mockk<Context>()
        every { hostContext.applicationContext } returns appContext

        Katch.configureForTests(
            currentHandlerProvider = { null },
            crashHandlerFactory = { context, logBuffer, previous, writer ->
                CrashHandler(context, logBuffer, previous, writer)
            }
        )

        Katch.init(hostContext, encryptionKey = ByteArray(16))
    }

    @Test
    fun `exportKey returns null when no encryption`() {
        val appContext = mockk<Context>()
        val hostContext = mockk<Context>()
        every { hostContext.applicationContext } returns appContext

        Katch.configureForTests(
            currentHandlerProvider = { null },
            crashHandlerFactory = { context, logBuffer, previous, writer ->
                CrashHandler(context, logBuffer, previous, writer)
            }
        )

        Katch.init(hostContext)

        assertEquals(null, Katch.exportKey())
    }

    @Test
    fun `exportKey returns null when developer-supplied key used`() {
        val appContext = mockk<Context>()
        val hostContext = mockk<Context>()
        every { hostContext.applicationContext } returns appContext

        Katch.configureForTests(
            currentHandlerProvider = { null },
            crashHandlerFactory = { context, logBuffer, previous, writer ->
                CrashHandler(context, logBuffer, previous, writer)
            }
        )

        Katch.init(hostContext, encryptionKey = ByteArray(32) { it.toByte() })

        assertEquals(null, Katch.exportKey())
    }

    @Test
    fun `exportKey returns null before init`() {
        assertEquals(null, Katch.exportKey())
    }

    @Test
    fun `init with Auto encryption key uses KeyManager and exportKey returns key`() {
        val appContext = mockk<Context>()
        val hostContext = mockk<Context>()
        every { hostContext.applicationContext } returns appContext
        every { appContext.getExternalFilesDir("crash_logs") } returns
            Files.createTempDirectory("katch-auto").toFile()

        val fakeKey = ByteArray(32) { 0x42 }
        val fakeKeyManager = object : KeyManager(
            wrappedKeyStore = object : KeyManager.WrappedKeyStore {
                override fun load(): ByteArray? = null
                override fun save(wrappedKey: ByteArray) {}
            },
            keyWrapper = object : KeyManager.KeyWrapper {
                override fun wrap(key: ByteArray): ByteArray = key
                override fun unwrap(wrappedKey: ByteArray): ByteArray = wrappedKey
            }
        ) {
            override fun getOrGenerateKey(): ByteArray = fakeKey
            override fun exportKey(): ByteArray = fakeKey
        }

        Katch.configureForTests(
            currentHandlerProvider = { null },
            crashHandlerFactory = { context, logBuffer, previous, writer ->
                CrashHandler(context, logBuffer, previous, writer)
            },
            keyManagerFactory = { _ -> fakeKeyManager }
        )

        Katch.init(hostContext, encryptionKey = Katch.EncryptionKey.Auto)

        assertArrayEquals(fakeKey, Katch.exportKey())
    }
}
