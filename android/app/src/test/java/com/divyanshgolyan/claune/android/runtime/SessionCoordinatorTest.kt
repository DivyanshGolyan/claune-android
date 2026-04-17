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
}
