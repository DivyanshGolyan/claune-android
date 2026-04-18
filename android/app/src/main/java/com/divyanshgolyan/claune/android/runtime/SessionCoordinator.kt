package com.divyanshgolyan.claune.android.runtime

import com.divyanshgolyan.claune.android.data.local.SessionLogStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionCoordinator(private val logStore: SessionLogStore) {
    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    fun startSession(goal: String) {
        val sessionId = "session-${System.currentTimeMillis()}"
        val line = "Session started for goal: $goal"
        _uiState.value =
            _uiState.value.copy(
                sessionId = sessionId,
                status = SessionStatus.Running,
                summaryLine = line,
                timeline = listOf(line),
            )
        logStore.record(_uiState.value)
    }

    fun stopSession(reason: String) {
        val line = "Session stopped. $reason"
        _uiState.value =
            _uiState.value.copy(
                status = SessionStatus.Cancelled,
                summaryLine = line,
                foregroundServiceRunning = false,
                timeline = (_uiState.value.timeline + line).takeLast(20),
            )
        logStore.record(_uiState.value)
    }

    fun recoverOrphanedSession(reason: String) {
        if (_uiState.value.status == SessionStatus.Running) {
            stopSession(reason)
        }
    }

    fun finishSession(summary: String) {
        _uiState.value =
            _uiState.value.copy(
                status = SessionStatus.Completed,
                summaryLine = summary,
                foregroundServiceRunning = false,
                timeline = (_uiState.value.timeline + summary).takeLast(20),
            )
        logStore.record(_uiState.value)
    }

    fun blockSession(reason: String) {
        _uiState.value =
            _uiState.value.copy(
                status = SessionStatus.Blocked,
                summaryLine = reason,
                foregroundServiceRunning = false,
                timeline = (_uiState.value.timeline + reason).takeLast(20),
            )
        logStore.record(_uiState.value)
    }

    fun logEvent(event: String) {
        val currentState = _uiState.value
        _uiState.value =
            currentState.copy(
                timeline = (currentState.timeline + event).takeLast(20),
                summaryLine =
                if (currentState.status == SessionStatus.Running) {
                    event
                } else {
                    currentState.summaryLine
                },
            )
        logStore.record(_uiState.value)
    }

    fun setAccessibilityConnected(connected: Boolean) {
        _uiState.value = _uiState.value.copy(accessibilityConnected = connected)
    }

    fun setForegroundServiceRunning(running: Boolean) {
        _uiState.value = _uiState.value.copy(foregroundServiceRunning = running)
    }

    fun setLastKnownApp(packageName: String) {
        _uiState.value = _uiState.value.copy(lastKnownApp = packageName)
    }
}
