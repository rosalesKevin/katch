package com.katch.sample

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyExportFormatterTest {

    @Test
    fun `format returns lowercase hex string for available key`() {
        val key = byteArrayOf(0x00, 0x0F, 0x10, 0x2A, 0x7F, 0x80.toByte(), 0xFF.toByte())

        val formatted = KeyExportFormatter.format(key)

        assertEquals("000f102a7f80ff", formatted)
    }

    @Test
    fun `format returns null when key is unavailable`() {
        assertNull(KeyExportFormatter.format(null))
    }
}
