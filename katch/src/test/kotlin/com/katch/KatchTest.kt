package com.katch

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `logKey logs hex key when initialized with Auto encryption`() {
        val appContext = mockk<Context>()
        val hostContext = mockk<Context>()
        every { hostContext.applicationContext } returns appContext

        val fakeKey = ByteArray(32) { it.toByte() }
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

        var logged: String? = null
        Katch.configureForTests(
            currentHandlerProvider = { null },
            crashHandlerFactory = { context, logBuffer, previous, writer ->
                CrashHandler(context, logBuffer, previous, writer)
            },
            keyManagerFactory = { _ -> fakeKeyManager },
            logKeyPrinter = { logged = it }
        )

        Katch.init(hostContext, encryptionKey = Katch.EncryptionKey.Auto)
        Katch.logKey()

        val expectedHex = fakeKey.joinToString("") { "%02x".format(it) }
        assertEquals(64, expectedHex.length)
        assertNotNull(logged)
        assertTrue("logged message should contain the exact 64-char hex key", logged!!.contains(expectedHex))
    }

    @Test
    fun `logKey does not log when encryption is disabled`() {
        val appContext = mockk<Context>()
        val hostContext = mockk<Context>()
        every { hostContext.applicationContext } returns appContext

        var logged: String? = null
        Katch.configureForTests(
            currentHandlerProvider = { null },
            crashHandlerFactory = { context, logBuffer, previous, writer ->
                CrashHandler(context, logBuffer, previous, writer)
            },
            logKeyPrinter = { logged = it }
        )

        Katch.init(hostContext)
        Katch.logKey()

        assertEquals(null, logged)
    }

    @Test
    fun `logKey does not log when called before init`() {
        var logged: String? = null
        Katch.configureForTests(
            logKeyPrinter = { logged = it }
        )

        Katch.logKey()

        assertEquals(null, logged)
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

    @Test
    fun `init with string key produces encrypted crash reports`() {
        val rootDir = Files.createTempDirectory("katch-string-key").toFile()
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
                    timestampProvider = { Instant.parse("2026-04-07T10:00:00Z") },
                    appVersionProvider = { "1.0.0 (1)" },
                    deviceProvider = { "Pixel 8" },
                    osVersionProvider = { "Android 15 (API 35)" }
                )
            },
            timestampProvider = { Instant.parse("2026-04-07T10:00:00Z") },
            appVersionProvider = { "1.0.0 (1)" },
            deviceProvider = { "Pixel 8" },
            osVersionProvider = { "Android 15 (API 35)" },
            zoneIdProvider = { UTC }
        )

        Katch.init(hostContext, encryptionKey = "my-secret-passphrase")
        Katch.testCrash()

        val encFile = File(reportsDir, "crash_2026-04-07_10-00-00.enc")
        assertTrue("Expected .enc file to exist", encFile.exists())

        val derivedKey = java.security.MessageDigest
            .getInstance("SHA-256")
            .digest("my-secret-passphrase".toByteArray(Charsets.UTF_8))
        val decrypted = Encryptor(derivedKey).decrypt(encFile.readBytes()).decodeToString()
        assertTrue(decrypted.contains("KATCH - CRASH REPORT"))
    }

    @Test
    fun `exportKey returns SHA-256 derived key when string key used`() {
        val appContext = mockk<Context>()
        val hostContext = mockk<Context>()
        every { hostContext.applicationContext } returns appContext

        Katch.configureForTests(
            currentHandlerProvider = { null },
            crashHandlerFactory = { context, logBuffer, previous, writer ->
                CrashHandler(context, logBuffer, previous, writer)
            }
        )

        Katch.init(hostContext, encryptionKey = "my-secret-passphrase")

        val expected = java.security.MessageDigest
            .getInstance("SHA-256")
            .digest("my-secret-passphrase".toByteArray(Charsets.UTF_8))
        assertArrayEquals(expected, Katch.exportKey())
    }

    @Test
    fun `logKey logs derived hex key when string key used`() {
        val appContext = mockk<Context>()
        val hostContext = mockk<Context>()
        every { hostContext.applicationContext } returns appContext

        var logged: String? = null
        Katch.configureForTests(
            currentHandlerProvider = { null },
            crashHandlerFactory = { context, logBuffer, previous, writer ->
                CrashHandler(context, logBuffer, previous, writer)
            },
            logKeyPrinter = { logged = it }
        )

        Katch.init(hostContext, encryptionKey = "my-secret-passphrase")
        Katch.logKey()

        val derivedKey = java.security.MessageDigest
            .getInstance("SHA-256")
            .digest("my-secret-passphrase".toByteArray(Charsets.UTF_8))
        val expectedHex = derivedKey.joinToString("") { "%02x".format(it) }
        assertEquals(64, expectedHex.length)
        assertNotNull(logged)
        assertTrue("logged message should contain the exact hex key", logged!!.contains(expectedHex))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `init with blank string throws IllegalArgumentException`() {
        val appContext = mockk<Context>()
        val hostContext = mockk<Context>()
        every { hostContext.applicationContext } returns appContext

        Katch.configureForTests(
            currentHandlerProvider = { null },
            crashHandlerFactory = { context, logBuffer, previous, writer ->
                CrashHandler(context, logBuffer, previous, writer)
            }
        )

        Katch.init(hostContext, encryptionKey = "   ")
    }

    @Test
    fun `init with string key is idempotent`() {
        val appContext = mockk<Context>()
        val hostContext = mockk<Context>()
        every { hostContext.applicationContext } returns appContext

        var installCount = 0
        Katch.configureForTests(
            crashHandlerFactory = { _, _, _, _ -> mockk(relaxed = true) },
            handlerInstaller = { installCount++ }
        )

        Katch.init(hostContext, encryptionKey = "my-secret-passphrase")
        Katch.init(hostContext, encryptionKey = "my-secret-passphrase")

        assertEquals(1, installCount)
    }

    @Test
    fun `init with string key ignored when already initialized with different overload`() {
        val rootDir = Files.createTempDirectory("katch-string-after-plain").toFile()
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
                    timestampProvider = { Instant.parse("2026-04-07T10:00:00Z") },
                    appVersionProvider = { "1.0.0 (1)" },
                    deviceProvider = { "Pixel 8" },
                    osVersionProvider = { "Android 15 (API 35)" }
                )
            },
            timestampProvider = { Instant.parse("2026-04-07T10:00:00Z") },
            appVersionProvider = { "1.0.0 (1)" },
            deviceProvider = { "Pixel 8" },
            osVersionProvider = { "Android 15 (API 35)" },
            zoneIdProvider = { UTC }
        )

        Katch.init(hostContext)
        Katch.init(hostContext, encryptionKey = "secret")

        assertEquals(null, Katch.exportKey())
        Katch.testCrash()
        val txtFile = File(reportsDir, "crash_2026-04-07_10-00-00.txt")
        val encFile = File(reportsDir, "crash_2026-04-07_10-00-00.enc")
        assertTrue("Expected plaintext .txt report", txtFile.exists())
        assertFalse("Expected no .enc report", encFile.exists())
    }

    @Test
    fun `same passphrase produces same derived key across separate inits`() {
        val appContext = mockk<Context>()
        val hostContext = mockk<Context>()
        every { hostContext.applicationContext } returns appContext

        Katch.configureForTests(
            currentHandlerProvider = { null },
            crashHandlerFactory = { context, logBuffer, previous, writer ->
                CrashHandler(context, logBuffer, previous, writer)
            }
        )

        Katch.init(hostContext, encryptionKey = "my-secret-passphrase")
        val firstKey = Katch.exportKey()

        Katch.resetForTests()

        Katch.configureForTests(
            currentHandlerProvider = { null },
            crashHandlerFactory = { context, logBuffer, previous, writer ->
                CrashHandler(context, logBuffer, previous, writer)
            }
        )
        Katch.init(hostContext, encryptionKey = "my-secret-passphrase")
        val secondKey = Katch.exportKey()

        assertArrayEquals(firstKey, secondKey)
    }

    @Test
    fun `outputDir set before init directs testCrash to custom directory`() {
        val rootDir = Files.createTempDirectory("katch-outputdir-before").toFile()
        val customDir = File(rootDir, "custom_crashes")
        val defaultDir = File(rootDir, "crash_logs")
        val appContext = mockk<Context>()
        val hostContext = mockk<Context>()
        every { hostContext.applicationContext } returns appContext
        every { appContext.getExternalFilesDir(any()) } returns defaultDir

        Katch.configureForTests(
            currentHandlerProvider = { null },
            crashHandlerFactory = { context, logBuffer, previous, writer ->
                CrashHandler(
                    context = context,
                    logBuffer = logBuffer,
                    previousHandler = previous,
                    fileWriter = writer,
                    timestampProvider = { Instant.parse("2026-04-08T10:00:00Z") },
                    appVersionProvider = { "1.0.0 (1)" },
                    deviceProvider = { "Pixel 8" },
                    osVersionProvider = { "Android 15 (API 35)" }
                )
            },
            timestampProvider = { Instant.parse("2026-04-08T10:00:00Z") },
            appVersionProvider = { "1.0.0 (1)" },
            deviceProvider = { "Pixel 8" },
            osVersionProvider = { "Android 15 (API 35)" },
            zoneIdProvider = { UTC }
        )

        Katch.outputDir(customDir)
        Katch.init(hostContext)
        Katch.testCrash()

        val report = File(customDir, "crash_2026-04-08_10-00-00.txt")
        assertTrue("Expected report in custom dir", report.exists())
        assertFalse("Expected no report in default dir", File(defaultDir, "crash_2026-04-08_10-00-00.txt").exists())
    }

    @Test
    fun `outputDir set after init directs testCrash to custom directory`() {
        val rootDir = Files.createTempDirectory("katch-outputdir-after").toFile()
        val customDir = File(rootDir, "custom_crashes")
        val defaultDir = File(rootDir, "crash_logs")
        val appContext = mockk<Context>()
        val hostContext = mockk<Context>()
        every { hostContext.applicationContext } returns appContext
        every { appContext.getExternalFilesDir(any()) } returns defaultDir

        Katch.configureForTests(
            currentHandlerProvider = { null },
            crashHandlerFactory = { context, logBuffer, previous, writer ->
                CrashHandler(
                    context = context,
                    logBuffer = logBuffer,
                    previousHandler = previous,
                    fileWriter = writer,
                    timestampProvider = { Instant.parse("2026-04-08T10:00:00Z") },
                    appVersionProvider = { "1.0.0 (1)" },
                    deviceProvider = { "Pixel 8" },
                    osVersionProvider = { "Android 15 (API 35)" }
                )
            },
            timestampProvider = { Instant.parse("2026-04-08T10:00:00Z") },
            appVersionProvider = { "1.0.0 (1)" },
            deviceProvider = { "Pixel 8" },
            osVersionProvider = { "Android 15 (API 35)" },
            zoneIdProvider = { UTC }
        )

        Katch.init(hostContext)
        Katch.outputDir(customDir)
        Katch.testCrash()

        val report = File(customDir, "crash_2026-04-08_10-00-00.txt")
        assertTrue("Expected report in custom dir", report.exists())
    }

    @Test
    fun `resetForTests clears customOutputDir`() {
        val rootDir = Files.createTempDirectory("katch-reset-outputdir").toFile()
        val customDir = File(rootDir, "custom_crashes")
        val defaultDir = File(rootDir, "crash_logs")
        val appContext = mockk<Context>()
        val hostContext = mockk<Context>()
        every { hostContext.applicationContext } returns appContext
        every { appContext.getExternalFilesDir(any()) } returns defaultDir

        Katch.outputDir(customDir)
        Katch.resetForTests()

        Katch.configureForTests(
            currentHandlerProvider = { null },
            crashHandlerFactory = { context, logBuffer, previous, writer ->
                CrashHandler(
                    context = context,
                    logBuffer = logBuffer,
                    previousHandler = previous,
                    fileWriter = writer,
                    timestampProvider = { Instant.parse("2026-04-08T10:00:00Z") },
                    appVersionProvider = { "1.0.0 (1)" },
                    deviceProvider = { "Pixel 8" },
                    osVersionProvider = { "Android 15 (API 35)" }
                )
            },
            timestampProvider = { Instant.parse("2026-04-08T10:00:00Z") },
            appVersionProvider = { "1.0.0 (1)" },
            deviceProvider = { "Pixel 8" },
            osVersionProvider = { "Android 15 (API 35)" },
            zoneIdProvider = { UTC }
        )

        Katch.init(hostContext)
        Katch.testCrash()

        assertFalse("Custom dir should not be used after reset", File(customDir, "crash_2026-04-08_10-00-00.txt").exists())
        assertTrue("Default dir should be used after reset", File(defaultDir, "crash_2026-04-08_10-00-00.txt").exists())
    }
}
