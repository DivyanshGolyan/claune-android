package com.divyanshgolyan.claune.android.data.local

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object FileMutationQueue {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> withFileLock(file: File, block: suspend () -> T): T {
        val key = file.canonicalPath
        val mutex = locks.computeIfAbsent(key) { Mutex() }
        val result = mutex.withLock { block() }
        if (!mutex.isLocked) {
            locks.remove(key, mutex)
        }
        return result
    }
}
