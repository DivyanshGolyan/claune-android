package com.divyanshgolyan.claune.android.runtime

import com.divyanshgolyan.claune.android.data.local.CodingSessionStore
import com.divyanshgolyan.claune.android.data.local.PersistedSessionSummary
import com.divyanshgolyan.claune.android.data.local.SessionLogStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionCoordinator(private val logStore: SessionLogStore, private val codingSessionStore: CodingSessionStore) {
    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    init {
        refreshSessions()
    }

    fun createSession(name: String = ""): PersistedSessionSummary {
        val created = codingSessionStore.createSession(name)
        selectSession(created.path)
        return created
    }

    fun beginRun(goal: String): PersistedSessionSummary {
        val selectedSession =
            _uiState.value.selectedSessionPath
                ?.let(codingSessionStore::loadSession)
                ?: codingSessionStore.createSession("")
        val runId = "session-${System.currentTimeMillis()}"
        val line = "Session started for goal: $goal"
        _uiState.value =
            _uiState.value.copy(
                sessionId = runId,
                selectedSessionPath = selectedSession.path,
                selectedPersistentSessionId = selectedSession.sessionId,
                selectedSessionTitle = selectedSession.title,
                activeSessionPath = selectedSession.path,
                activePersistentSessionId = selectedSession.sessionId,
                activeSessionTitle = selectedSession.title,
                recentSessions = refreshSessionList(selectedSession.path),
                status = SessionStatus.Running,
                summaryLine = line,
                isStreaming = true,
                isCompacting = false,
                pendingSteeringCount = 0,
                lastAssistantText = "",
                timeline = listOf(line),
            )
        logStore.record(_uiState.value)
        return selectedSession
    }

    fun selectSession(path: String) {
        val selected = codingSessionStore.loadSession(path) ?: return
        _uiState.value =
            _uiState.value.copy(
                selectedSessionPath = selected.path,
                selectedPersistentSessionId = selected.sessionId,
                selectedSessionTitle = selected.title,
                recentSessions = refreshSessionList(selected.path),
                summaryLine = "Selected session: ${selected.title}",
            )
    }

    fun refreshSessions() {
        val fallbackSelection = _uiState.value.selectedSessionPath
        val selected = fallbackSelection?.let(codingSessionStore::loadSession)
        _uiState.value =
            _uiState.value.copy(
                selectedSessionPath = selected?.path,
                selectedPersistentSessionId = selected?.sessionId,
                selectedSessionTitle = selected?.title,
                recentSessions = refreshSessionList(selected?.path),
            )
    }

    fun stopSession(reason: String) {
        val line = "Session stopped. $reason"
        _uiState.value =
            _uiState.value.copy(
                status = SessionStatus.Cancelled,
                summaryLine = line,
                foregroundServiceRunning = false,
                isStreaming = false,
                pendingSteeringCount = 0,
                isCompacting = false,
                timeline = (_uiState.value.timeline + line).takeLast(20),
                recentSessions = refreshSessionList(_uiState.value.selectedSessionPath),
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
                isStreaming = false,
                pendingSteeringCount = 0,
                isCompacting = false,
                timeline = (_uiState.value.timeline + summary).takeLast(20),
                recentSessions = refreshSessionList(_uiState.value.selectedSessionPath),
            )
        logStore.record(_uiState.value)
    }

    fun blockSession(reason: String) {
        _uiState.value =
            _uiState.value.copy(
                status = SessionStatus.Blocked,
                summaryLine = reason,
                foregroundServiceRunning = false,
                isStreaming = false,
                pendingSteeringCount = 0,
                isCompacting = false,
                timeline = (_uiState.value.timeline + reason).takeLast(20),
                recentSessions = refreshSessionList(_uiState.value.selectedSessionPath),
            )
        logStore.record(_uiState.value)
    }

    fun pauseSession(reason: String) {
        _uiState.value =
            _uiState.value.copy(
                status = SessionStatus.Paused,
                summaryLine = reason,
                foregroundServiceRunning = true,
                isStreaming = false,
                pendingSteeringCount = 0,
                isCompacting = false,
                timeline = (_uiState.value.timeline + reason).takeLast(20),
                recentSessions = refreshSessionList(_uiState.value.selectedSessionPath),
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
        updateUiState { current ->
            if (current.accessibilityConnected == connected) current else current.copy(accessibilityConnected = connected)
        }
    }

    fun setForegroundServiceRunning(running: Boolean) {
        updateUiState { current ->
            if (current.foregroundServiceRunning == running) current else current.copy(foregroundServiceRunning = running)
        }
    }

    fun setLastKnownApp(packageName: String) {
        updateUiState { current ->
            if (current.lastKnownApp == packageName) current else current.copy(lastKnownApp = packageName)
        }
    }

    fun setAppInForeground(inForeground: Boolean) {
        updateUiState { current ->
            if (current.appInForeground == inForeground) current else current.copy(appInForeground = inForeground)
        }
    }

    fun setStreaming(streaming: Boolean) {
        updateUiState { current ->
            if (current.isStreaming == streaming) current else current.copy(isStreaming = streaming)
        }
    }

    fun setCompacting(compacting: Boolean) {
        updateUiState { current ->
            if (current.isCompacting == compacting) current else current.copy(isCompacting = compacting)
        }
    }

    fun setPendingSteeringCount(count: Int) {
        updateUiState { current ->
            if (current.pendingSteeringCount == count) current else current.copy(pendingSteeringCount = count)
        }
    }

    fun setLastAssistantText(text: String) {
        updateUiState { current ->
            if (current.lastAssistantText == text) current else current.copy(lastAssistantText = text)
        }
    }

    private fun updateUiState(update: (SessionUiState) -> SessionUiState) {
        val current = _uiState.value
        val next = update(current)
        if (next != current) {
            _uiState.value = next
        }
    }

    private fun refreshSessionList(selectedPath: String?): List<PersistedSessionSummary> {
        val sessions = codingSessionStore.listSessions(limit = 12)
        if (selectedPath == null) {
            return sessions
        }
        val selected = sessions.firstOrNull { it.path == selectedPath } ?: return sessions
        return listOf(selected) + sessions.filterNot { it.path == selectedPath }
    }
}
