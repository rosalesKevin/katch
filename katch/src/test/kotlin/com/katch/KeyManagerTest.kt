package com.katch

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class KeyManagerTest {

    @Test
    fun `getOrGenerateKey generates a 32-byte key on first call`() {
        val store = FakeWrappedKeyStore()
        val keyManager = KeyManager(
            wrappedKeyStore = store,
            keyWrapper = FakeKeyWrapper()
        )

        val key = keyManager.getOrGenerateKey()

        assertEquals(32, key.size)
    }

    @Test
    fun `getOrGenerateKey returns the same key on subsequent calls`() {
        val store = FakeWrappedKeyStore()
        val wrapper = FakeKeyWrapper()
        val keyManager = KeyManager(wrappedKeyStore = store, keyWrapper = wrapper)

        val key1 = keyManager.getOrGenerateKey()
        val key2 = keyManager.getOrGenerateKey()

        assertArrayEquals(key1, key2)
    }

    @Test
    fun `key persists across KeyManager instances`() {
        val store = FakeWrappedKeyStore()
        val wrapper = FakeKeyWrapper()

        val key1 = KeyManager(wrappedKeyStore = store, keyWrapper = wrapper).getOrGenerateKey()
        val key2 = KeyManager(wrappedKeyStore = store, keyWrapper = wrapper).getOrGenerateKey()

        assertArrayEquals(key1, key2)
    }

    @Test
    fun `exportKey returns the same key as getOrGenerateKey`() {
        val store = FakeWrappedKeyStore()
        val keyManager = KeyManager(wrappedKeyStore = store, keyWrapper = FakeKeyWrapper())

        val generated = keyManager.getOrGenerateKey()
        val exported = keyManager.exportKey()

        assertNotNull(exported)
        assertArrayEquals(generated, exported)
    }

    @Test
    fun `exportKey returns null when store throws`() {
        val store = object : KeyManager.WrappedKeyStore {
            override fun load(): ByteArray? = throw RuntimeException("storage broken")
            override fun save(wrappedKey: ByteArray) = throw RuntimeException("storage broken")
        }
        val keyManager = KeyManager(wrappedKeyStore = store, keyWrapper = FakeKeyWrapper())

        val result = keyManager.exportKey()

        assertNull(result)
    }

    @Test
    fun `getOrGenerateKey throws when store fails on first generate`() {
        val store = object : KeyManager.WrappedKeyStore {
            override fun load(): ByteArray? = null
            override fun save(wrappedKey: ByteArray) = throw RuntimeException("cannot save")
        }
        val keyManager = KeyManager(wrappedKeyStore = store, keyWrapper = FakeKeyWrapper())

        try {
            keyManager.getOrGenerateKey()
            org.junit.Assert.fail("Expected exception")
        } catch (e: IllegalStateException) {
            assert(e.message!!.contains("Failed"))
        }
    }

    // --- Fakes ---

    private class FakeWrappedKeyStore : KeyManager.WrappedKeyStore {
        private var stored: ByteArray? = null
        override fun load(): ByteArray? = stored
        override fun save(wrappedKey: ByteArray) { stored = wrappedKey }
    }

    private class FakeKeyWrapper : KeyManager.KeyWrapper {
        override fun wrap(key: ByteArray): ByteArray =
            key.map { (it.toInt() xor 0xAA).toByte() }.toByteArray()
        override fun unwrap(wrappedKey: ByteArray): ByteArray =
            wrappedKey.map { (it.toInt() xor 0xAA).toByte() }.toByteArray()
    }
}
