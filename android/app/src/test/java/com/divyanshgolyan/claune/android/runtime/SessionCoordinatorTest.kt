package com.divyanshgolyan.claune.android.runtime

import com.divyanshgolyan.claune.android.data.local.InMemorySessionLogStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SessionCoordinatorTest {
    @Test
    fun `finish session clears foreground service flag`() {
        val coordinator = SessionCoordinator(InMemorySessionLogStore())

        coordinator.startSession("Open Settings")
        coordinator.setForegroundServiceRunning(true)

        coordinator.finishSession("Completed successfully.")

        assertEquals(SessionStatus.Completed, coordinator.uiState.value.status)
        assertFalse(coordinator.uiState.value.foregroundServiceRunning)
    }

    @Test
    fun `block session clears foreground service flag`() {
        val coordinator = SessionCoordinator(InMemorySessionLogStore())

        coordinator.startSession("Open Settings")
        coordinator.setForegroundServiceRunning(true)

        coordinator.blockSession("Blocked on missing accessibility.")

        assertEquals(SessionStatus.Blocked, coordinator.uiState.value.status)
        assertFalse(coordinator.uiState.value.foregroundServiceRunning)
    }

    @Test
    fun `recover orphaned session cancels stale running state`() {
        val coordinator = SessionCoordinator(InMemorySessionLogStore())

        coordinator.startSession("Open Settings")
        coordinator.setForegroundServiceRunning(true)

        coordinator.recoverOrphanedSession("Previous session ended unexpectedly.")

        assertEquals(SessionStatus.Cancelled, coordinator.uiState.value.status)
        assertFalse(coordinator.uiState.value.foregroundServiceRunning)
    }

    @Test
    fun `log events do not overwrite terminal session summaries`() {
        val coordinator = SessionCoordinator(InMemorySessionLogStore())

        coordinator.startSession("Open Settings")
        coordinator.finishSession("Completed successfully.")
        coordinator.logEvent("Memory reflection made no durable update.")

        assertEquals(SessionStatus.Completed, coordinator.uiState.value.status)
        assertEquals("Completed successfully.", coordinator.uiState.value.summaryLine)
    }
}
