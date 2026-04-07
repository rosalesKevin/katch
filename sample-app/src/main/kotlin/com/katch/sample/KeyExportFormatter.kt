package com.katch.sample

internal object KeyExportFormatter {

    fun format(key: ByteArray?): String? {
        if (key == null) return null
        return key.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    }
}
