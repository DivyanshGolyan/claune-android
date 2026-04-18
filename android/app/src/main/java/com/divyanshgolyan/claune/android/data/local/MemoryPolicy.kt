package com.divyanshgolyan.claune.android.data.local

internal object MemoryPolicy {
    private const val HEADER = "# Claune Memory"

    fun defaultContent(): String = "$HEADER\n\n"

    fun normalize(content: String): String {
        val trimmed = content.trim()
        if (trimmed.isBlank()) {
            return defaultContent()
        }
        val withHeader =
            if (trimmed.startsWith(HEADER)) {
                trimmed
            } else {
                "$HEADER\n\n$trimmed"
            }
        return withHeader.trimEnd() + "\n"
    }
}
