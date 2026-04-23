package com.katch

import android.util.Log
import timber.log.Timber

/**
 * Routes Timber log entries into Katch's in-memory buffer.
 *
 * Plant once in `Application.onCreate()` after calling `Katch.init()`:
 * ```kotlin
 * Katch.init(this, "passphrase")
 * Timber.plant(KatchTimberTree())
 * ```
 *
 * All log levels are captured. VERBOSE is forwarded as DEBUG; ASSERT is forwarded as ERROR.
 * Throwables are appended to the message as a condensed stack trace.
 * Log entries produced before `Katch.init()` are silently dropped.
 */
class KatchTimberTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val safeTag = tag ?: "App"
        val fullMessage = if (t != null) "$message\n${t.stackTraceToString()}" else message
        when (priority) {
            Log.VERBOSE, Log.DEBUG -> Katch.d(safeTag, fullMessage)
            Log.INFO               -> Katch.i(safeTag, fullMessage)
            Log.WARN               -> Katch.w(safeTag, fullMessage)
            Log.ERROR, Log.ASSERT  -> Katch.e(safeTag, fullMessage)
        }
    }
}
