package com.katch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogBufferTest {

    @Test
    fun `snapshot returns entries in insertion order`() {
        val buffer = LogBuffer(maxEntries = 3)

        buffer.add("first")
        buffer.add("second")
        buffer.add("third")

        assertEquals(listOf("first", "second", "third"), buffer.snapshot())
    }

    @Test
    fun `buffer drops the oldest entry when capacity is reached`() {
        val buffer = LogBuffer(maxEntries = 3)

        buffer.add("first")
        buffer.add("second")
        buffer.add("third")
        buffer.add("fourth")

        assertEquals(listOf("second", "third", "fourth"), buffer.snapshot())
    }

    @Test
    fun `snapshot returns a defensive copy that does not reflect subsequent adds`() {
        val buffer = LogBuffer(maxEntries = 3)
        buffer.add("first")

        val snapshot = buffer.snapshot()
        buffer.add("second")

        assertEquals(listOf("first"), snapshot)
        assertEquals(listOf("first", "second"), buffer.snapshot())
    }

    @Test
    fun `concurrent adds do not corrupt the buffer`() {
        val buffer = LogBuffer(maxEntries = 100)
        val threads = (1..10).map { threadId ->
            Thread { repeat(100) { i -> buffer.add("thread-$threadId-entry-$i") } }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        val snapshot = buffer.snapshot()
        assertTrue(snapshot.size <= 100)
        assertTrue(snapshot.isNotEmpty())
    }
}
