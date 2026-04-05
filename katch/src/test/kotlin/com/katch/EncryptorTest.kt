package com.katch

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Test

class EncryptorTest {

    private val validKey = ByteArray(32) { it.toByte() }

    @Test
    fun `encrypt then decrypt round-trips successfully`() {
        val encryptor = Encryptor(validKey)
        val plaintext = "Hello, crash report!".toByteArray()

        val ciphertext = encryptor.encrypt(plaintext)
        val decrypted = encryptor.decrypt(ciphertext)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `encrypted output starts with format version byte 0x01`() {
        val encryptor = Encryptor(validKey)
        val ciphertext = encryptor.encrypt("data".toByteArray())

        assertEquals(0x01.toByte(), ciphertext[0])
    }

    @Test
    fun `encrypted output is longer than plaintext by at least 29 bytes`() {
        // 1 version + 12 IV + 16 GCM tag = 29 bytes overhead minimum
        val encryptor = Encryptor(validKey)
        val plaintext = "short".toByteArray()
        val ciphertext = encryptor.encrypt(plaintext)

        assert(ciphertext.size >= plaintext.size + 29) {
            "Ciphertext (${ciphertext.size}) should be at least ${plaintext.size + 29} bytes"
        }
    }

    @Test
    fun `two encryptions of same plaintext produce different ciphertext`() {
        val encryptor = Encryptor(validKey)
        val plaintext = "same input".toByteArray()

        val ciphertext1 = encryptor.encrypt(plaintext)
        val ciphertext2 = encryptor.encrypt(plaintext)

        assertNotEquals(ciphertext1.toList(), ciphertext2.toList())
    }

    @Test
    fun `decrypt with wrong key throws`() {
        val encryptor = Encryptor(validKey)
        val wrongKey = ByteArray(32) { (it + 1).toByte() }
        val wrongEncryptor = Encryptor(wrongKey)

        val ciphertext = encryptor.encrypt("secret".toByteArray())

        try {
            wrongEncryptor.decrypt(ciphertext)
            fail("Expected decryption with wrong key to throw")
        } catch (_: Exception) {
            // Expected — GCM authentication should fail
        }
    }

    @Test
    fun `decrypt detects tampered ciphertext`() {
        val encryptor = Encryptor(validKey)
        val ciphertext = encryptor.encrypt("original".toByteArray())

        // Tamper with a byte in the ciphertext body (after version + IV)
        val tampered = ciphertext.copyOf()
        tampered[14] = (tampered[14].toInt() xor 0xFF).toByte()

        try {
            encryptor.decrypt(tampered)
            fail("Expected tampered ciphertext to throw")
        } catch (_: Exception) {
            // Expected — GCM authentication should fail
        }
    }

    @Test
    fun `decrypt rejects ciphertext with unknown version byte`() {
        val encryptor = Encryptor(validKey)
        val ciphertext = encryptor.encrypt("data".toByteArray())

        val badVersion = ciphertext.copyOf()
        badVersion[0] = 0x99.toByte()

        try {
            encryptor.decrypt(badVersion)
            fail("Expected unknown version to throw")
        } catch (e: IllegalArgumentException) {
            assert(e.message!!.contains("version")) {
                "Error message should mention version, got: ${e.message}"
            }
        }
    }

    @Test
    fun `decrypt rejects ciphertext shorter than minimum length`() {
        val encryptor = Encryptor(validKey)

        try {
            encryptor.decrypt(ByteArray(10))
            fail("Expected short ciphertext to throw")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }
}
