package com.katch

import android.content.Context
import java.security.SecureRandom

internal open class KeyManager(
    private val wrappedKeyStore: WrappedKeyStore,
    private val keyWrapper: KeyWrapper
) {

    constructor(context: Context) : this(
        wrappedKeyStore = SharedPrefsWrappedKeyStore(context),
        keyWrapper = KeystoreKeyWrapper()
    )

    open fun getOrGenerateKey(): ByteArray {
        val existing = wrappedKeyStore.load()
        if (existing != null) {
            return keyWrapper.unwrap(existing)
        }

        val newKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val wrapped = keyWrapper.wrap(newKey)

        try {
            wrappedKeyStore.save(wrapped)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to persist encryption key", e)
        }

        return newKey
    }

    open fun exportKey(): ByteArray? = runCatching {
        val wrapped = wrappedKeyStore.load() ?: return null
        keyWrapper.unwrap(wrapped)
    }.getOrNull()

    interface WrappedKeyStore {
        fun load(): ByteArray?
        fun save(wrappedKey: ByteArray)
    }

    interface KeyWrapper {
        fun wrap(key: ByteArray): ByteArray
        fun unwrap(wrappedKey: ByteArray): ByteArray
    }

    private class SharedPrefsWrappedKeyStore(context: Context) : WrappedKeyStore {
        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        override fun load(): ByteArray? {
            val encoded = prefs.getString(KEY_WRAPPED_AES, null) ?: return null
            return android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
        }

        override fun save(wrappedKey: ByteArray) {
            val encoded = android.util.Base64.encodeToString(wrappedKey, android.util.Base64.NO_WRAP)
            prefs.edit().putString(KEY_WRAPPED_AES, encoded).apply()
        }
    }

    private class KeystoreKeyWrapper : KeyWrapper {
        override fun wrap(key: ByteArray): ByteArray {
            val masterKey = getOrCreateMasterKey()
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(javax.crypto.Cipher.WRAP_MODE, masterKey)
            val iv = cipher.iv
            val wrappedBytes = cipher.wrap(javax.crypto.spec.SecretKeySpec(key, "AES"))
            return iv + wrappedBytes
        }

        override fun unwrap(wrappedKey: ByteArray): ByteArray {
            val masterKey = getOrCreateMasterKey()
            val iv = wrappedKey.copyOfRange(0, 12)
            val wrapped = wrappedKey.copyOfRange(12, wrappedKey.size)
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                javax.crypto.Cipher.UNWRAP_MODE,
                masterKey,
                javax.crypto.spec.GCMParameterSpec(128, iv)
            )
            val unwrapped = cipher.unwrap(wrapped, "AES", javax.crypto.Cipher.SECRET_KEY)
            return unwrapped.encoded
        }

        private fun getOrCreateMasterKey(): java.security.Key {
            val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)

            keyStore.getKey(KEYSTORE_ALIAS, null)?.let { return it }

            val keyGen = javax.crypto.KeyGenerator.getInstance(
                android.security.keystore.KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )
            keyGen.init(
                android.security.keystore.KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                        android.security.keystore.KeyProperties.PURPOSE_DECRYPT or
                        android.security.keystore.KeyProperties.PURPOSE_WRAP_KEY
                )
                    .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            return keyGen.generateKey()
        }
    }

    private companion object {
        const val PREFS_NAME = "katch_crypto"
        const val KEY_WRAPPED_AES = "wrapped_aes_key"
        const val KEYSTORE_ALIAS = "katch_master_key"
    }
}
