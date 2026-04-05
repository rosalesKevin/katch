package com.katch

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES

internal class Encryptor(key: ByteArray) {

    private val aesGcmKey: AES.GCM.Key = CryptographyProvider.Default
        .get(AES.GCM)
        .keyDecoder()
        .decodeFromByteArrayBlocking(AES.Key.Format.RAW, key)

    fun encrypt(plaintext: ByteArray): ByteArray {
        val ciphertext = aesGcmKey.cipher().encryptBlocking(plaintext)
        return byteArrayOf(FORMAT_VERSION) + ciphertext
    }

    fun decrypt(data: ByteArray): ByteArray {
        require(data.size >= MIN_CIPHERTEXT_LENGTH) {
            "Ciphertext too short: ${data.size} bytes, minimum $MIN_CIPHERTEXT_LENGTH"
        }
        require(data[0] == FORMAT_VERSION) {
            "Unsupported format version: 0x${data[0].toInt().and(0xFF).toString(16).padStart(2, '0')}"
        }
        val ciphertext = data.copyOfRange(1, data.size)
        return aesGcmKey.cipher().decryptBlocking(ciphertext)
    }

    companion object {
        const val FORMAT_VERSION: Byte = 0x01
        // 1 version + 12 IV + 16 GCM tag = 29 bytes minimum overhead
        const val MIN_CIPHERTEXT_LENGTH = 29
    }
}
