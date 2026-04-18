package com.divyanshgolyan.claune.android.data.local

import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MemoryStoreTest {
    @Test
    fun `file memory store initializes default markdown`() = runTest {
        val dir = Files.createTempDirectory("claune-memory").toFile()
        try {
            val store = FileMemoryStore(dir.resolve("memory.md"))

            assertEquals("# Claune Memory\n\n", store.read())
            assertTrue(dir.resolve("memory.md").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `file memory store applies exact unique edits`() = runTest {
        val dir = Files.createTempDirectory("claune-memory").toFile()
        try {
            val store = FileMemoryStore(dir.resolve("memory.md"))

            store.edit("# Claune Memory\n\n", "# Claune Memory\n\n- Durable fact.\n")

            assertEquals("# Claune Memory\n\n- Durable fact.\n", store.read())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `file memory store rejects non unique edits`() = runTest {
        val dir = Files.createTempDirectory("claune-memory").toFile()
        try {
            val store = FileMemoryStore(dir.resolve("memory.md"))
            store.edit("# Claune Memory\n\n", "# Claune Memory\n\n- Fact one\n- Fact two\n")

            try {
                store.edit("- Fact", "- Updated fact")
                fail("Expected non-unique edit to fail.")
            } catch (_: IllegalStateException) {
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `file memory store can overwrite and normalize content`() = runTest {
        val dir = Files.createTempDirectory("claune-memory").toFile()
        try {
            val store = FileMemoryStore(dir.resolve("memory.md"))

            store.overwrite("- Durable fact.\n")

            assertEquals("# Claune Memory\n\n- Durable fact.\n", store.read())
        } finally {
            dir.deleteRecursively()
        }
    }
}
