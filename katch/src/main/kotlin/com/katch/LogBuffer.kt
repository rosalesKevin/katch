package com.katch

internal class LogBuffer(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES
) {
    private val entries = ArrayDeque<String>(maxEntries)

    @Synchronized
    fun add(entry: String) {
        if (entries.size == maxEntries) {
            entries.removeFirst()
        }
        entries.addLast(entry)
    }

    @Synchronized
    fun snapshot(): List<String> = entries.toList()

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 100
    }
}
