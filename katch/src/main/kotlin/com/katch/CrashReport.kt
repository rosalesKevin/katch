package com.katch

import java.time.Instant

internal data class CrashReport(
    val timestamp: Instant,
    val appVersion: String,
    val device: String,
    val osVersion: String,
    val logs: List<String>,
    val throwable: Throwable
)
