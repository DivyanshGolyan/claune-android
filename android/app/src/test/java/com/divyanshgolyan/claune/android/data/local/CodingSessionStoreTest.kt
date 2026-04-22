package com.divyanshgolyan.claune.android.data.local

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodingSessionStoreTest {
    @Test
    fun `blank session creation returns a persisted loadable session`() {
        val root = Files.createTempDirectory("claune-store-sessions").toFile()
        val store = CodingSessionStore(cwd = root.absolutePath, agentDir = root.resolve("agent"))

        val created = store.createSession("")

        assertTrue(File(created.path).isFile)
        assertEquals("Untitled session", created.title)
        assertEquals("Untitled session", store.listSessions().single().title)
        assertEquals(created.path, store.loadSession(created.path)?.path)
        assertNotNull(store.loadSessionDetail(created.path))
    }
}
