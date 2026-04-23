package com.katch

import android.content.Context
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber

class KatchTimberTreeTest {

    private val appContext = mockk<Context>()
    private val hostContext = mockk<Context>()
    private val tree = KatchTimberTree()

    @Before
    fun setUp() {
        Katch.resetForTests()
        every { hostContext.applicationContext } returns appContext
        Katch.configureForTests(
            crashHandlerFactory = { _, _, _, _ -> mockk(relaxed = true) },
            handlerInstaller = {}
        )
        Katch.init(hostContext)
        Timber.plant(tree)
    }

    @After
    fun tearDown() {
        Timber.uproot(tree)
        Katch.resetForTests()
    }

    @Test
    fun `debug log reaches buffer`() {
        Timber.tag("Auth").d("user logged in")

        val snapshot = Katch.snapshotForTests()
        assertEquals(1, snapshot.size)
        assertTrue(snapshot[0].contains("D/Auth: user logged in"))
    }

    @Test
    fun `info log reaches buffer`() {
        Timber.tag("Network").i("request sent")

        val snapshot = Katch.snapshotForTests()
        assertTrue(snapshot[0].contains("I/Network: request sent"))
    }

    @Test
    fun `warn log reaches buffer`() {
        Timber.tag("Cache").w("cache miss")

        val snapshot = Katch.snapshotForTests()
        assertTrue(snapshot[0].contains("W/Cache: cache miss"))
    }

    @Test
    fun `error log reaches buffer`() {
        Timber.tag("DB").e("query failed")

        val snapshot = Katch.snapshotForTests()
        assertTrue(snapshot[0].contains("E/DB: query failed"))
    }

    @Test
    fun `verbose is forwarded as debug`() {
        Timber.tag("UI").v("view drawn")

        val snapshot = Katch.snapshotForTests()
        assertTrue(snapshot[0].contains("D/UI: view drawn"))
    }

    @Test
    fun `throwable stack trace is appended to message`() {
        val ex = RuntimeException("boom")
        Timber.tag("Crash").e(ex, "unhandled exception")

        val snapshot = Katch.snapshotForTests()
        val entry = snapshot[0]
        assertTrue(entry.contains("E/Crash: unhandled exception"))
        assertTrue(entry.contains("RuntimeException: boom"))
    }

    @Test
    fun `multiple logs accumulate in buffer`() {
        Timber.tag("A").d("one")
        Timber.tag("B").i("two")
        Timber.tag("C").e("three")

        assertEquals(3, Katch.snapshotForTests().size)
    }

    @Test
    fun `no explicit tag uses class name from Timber`() {
        // Timber infers the tag from the calling class when none is set explicitly
        Timber.d("auto-tagged message")

        val snapshot = Katch.snapshotForTests()
        assertEquals(1, snapshot.size)
        assertTrue(snapshot[0].contains("D/"))
    }
}
