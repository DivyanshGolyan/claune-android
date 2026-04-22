package com.divyanshgolyan.claune.android.runtime

import com.divyanshgolyan.claune.android.data.local.CodingSessionStore
import com.divyanshgolyan.claune.android.data.local.InMemorySessionLogStore
import com.divyanshgolyan.claune.android.data.local.PersistedSessionSummary
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import pi.ai.core.AssistantMessage
import pi.ai.core.StopReason
import pi.ai.core.TextContent
import pi.ai.core.Usage

class SessionCoordinatorTest {
    @Test
    fun `starting a session selects a persisted coding session`() {
        val coordinator = coordinator()

        coordinator.beginRun("Open Settings")

        assertNotNull(coordinator.uiState.value.selectedPersistentSessionId)
    }

    @Test
    fun `finish session clears foreground service flag`() {
        val coordinator = coordinator()

        coordinator.beginRun("Open Settings")
        coordinator.setForegroundServiceRunning(true)

        coordinator.finishSession("Completed successfully.")

        assertEquals(SessionStatus.Completed, coordinator.uiState.value.status)
        assertFalse(coordinator.uiState.value.foregroundServiceRunning)
    }

    @Test
    fun `block session clears foreground service flag`() {
        val coordinator = coordinator()

        coordinator.beginRun("Open Settings")
        coordinator.setForegroundServiceRunning(true)

        coordinator.blockSession("Blocked on missing accessibility.")

        assertEquals(SessionStatus.Blocked, coordinator.uiState.value.status)
        assertFalse(coordinator.uiState.value.foregroundServiceRunning)
    }

    @Test
    fun `recover orphaned session cancels stale running state`() {
        val coordinator = coordinator()

        coordinator.beginRun("Open Settings")
        coordinator.setForegroundServiceRunning(true)

        coordinator.recoverOrphanedSession("Previous session ended unexpectedly.")

        assertEquals(SessionStatus.Cancelled, coordinator.uiState.value.status)
        assertFalse(coordinator.uiState.value.foregroundServiceRunning)
    }

    @Test
    fun `log events do not overwrite terminal session summaries`() {
        val coordinator = coordinator()

        coordinator.beginRun("Open Settings")
        coordinator.finishSession("Completed successfully.")
        coordinator.logEvent("Memory reflection made no durable update.")

        assertEquals(SessionStatus.Completed, coordinator.uiState.value.status)
        assertEquals("Completed successfully.", coordinator.uiState.value.summaryLine)
    }

    @Test
    fun `refresh sessions clears deleted selected coding session`() {
        val (coordinator, store) = coordinatorWithStore()
        val selected = persistedSession(store)
        coordinator.selectSession(selected.path)
        require(File(selected.path).delete())

        coordinator.refreshSessions()

        assertNull(coordinator.uiState.value.selectedSessionPath)
        assertNull(coordinator.uiState.value.selectedPersistentSessionId)
        assertFalse(File(selected.path).exists())
    }

    @Test
    fun `begin run creates fresh session when selected coding session was deleted`() {
        val (coordinator, store) = coordinatorWithStore()
        val stale = persistedSession(store)
        coordinator.selectSession(stale.path)
        require(File(stale.path).delete())

        val selected = coordinator.beginRun("Open Settings")

        assertNotEquals(stale.path, selected.path)
        assertEquals(selected.path, coordinator.uiState.value.selectedSessionPath)
        assertFalse(File(stale.path).exists())
    }

    @Test
    fun `create session replaces previously selected coding session`() {
        val (coordinator, store) = coordinatorWithStore()
        val previous = persistedSession(store)
        coordinator.selectSession(previous.path)

        val created = coordinator.createSession()

        assertNotEquals(previous.path, created.path)
        assertTrue(File(created.path).isFile)
        assertEquals(created.path, store.loadSession(created.path)?.path)
        assertEquals(created.path, coordinator.uiState.value.selectedSessionPath)
        assertEquals(created.sessionId, coordinator.uiState.value.selectedPersistentSessionId)
    }

    @Test
    fun `hot path setters keep state reference when value is unchanged`() {
        val coordinator = coordinator()

        coordinator.setLastKnownApp("com.android.settings")
        val current = coordinator.uiState.value

        coordinator.setLastKnownApp("com.android.settings")

        assertSame(current, coordinator.uiState.value)
    }

    private fun coordinator(): SessionCoordinator = coordinatorWithStore().first

    private fun coordinatorWithStore(): Pair<SessionCoordinator, CodingSessionStore> {
        val root = Files.createTempDirectory("claune-sessions").toFile()
        val store = CodingSessionStore(cwd = root.absolutePath, agentDir = root.resolve("agent"))
        return SessionCoordinator(InMemorySessionLogStore(), store) to store
    }

    private fun persistedSession(store: CodingSessionStore): PersistedSessionSummary {
        val manager = store.sessionManager(null)
        manager.appendMessage(
            AssistantMessage(
                content = mutableListOf(TextContent("Ready")),
                api = "anthropic-messages",
                provider = "anthropic",
                model = "claude-sonnet-4-5",
                usage = Usage(),
                stopReason = StopReason.STOP,
                timestamp = System.currentTimeMillis(),
            ),
        )
        return requireNotNull(store.loadSession(manager.getSessionFile()))
    }
}
