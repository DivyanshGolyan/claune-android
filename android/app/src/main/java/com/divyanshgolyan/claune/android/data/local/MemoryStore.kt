package com.divyanshgolyan.claune.android.data.local

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface MemoryStore {
    suspend fun read(): String

    suspend fun edit(oldText: String, newText: String)

    suspend fun overwrite(content: String)
}

class FileMemoryStore(private val file: File) : MemoryStore {
    init {
        file.parentFile?.mkdirs()
        if (!file.exists()) {
            file.writeText(MemoryPolicy.defaultContent())
        }
    }

    override suspend fun read(): String = withContext(Dispatchers.IO) {
        file.readText()
    }

    override suspend fun edit(oldText: String, newText: String) {
        FileMutationQueue.withFileLock(file) {
            withContext(Dispatchers.IO) {
                require(oldText.isNotEmpty()) { "oldText must not be empty." }

                val current = file.readText()
                val occurrences = current.split(oldText).size - 1
                when {
                    occurrences == 0 -> error("Could not find the exact text in memory.md.")
                    occurrences > 1 -> error("Found $occurrences occurrences in memory.md. The text must be unique.")
                }

                val updated = current.replace(oldText, newText)
                check(updated != current) { "No changes made to memory.md." }
                file.writeText(MemoryPolicy.normalize(updated))
            }
        }
    }

    override suspend fun overwrite(content: String) {
        FileMutationQueue.withFileLock(file) {
            withContext(Dispatchers.IO) {
                file.writeText(MemoryPolicy.normalize(content))
            }
        }
    }
}
