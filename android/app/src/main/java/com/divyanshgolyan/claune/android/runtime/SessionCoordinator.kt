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
        selectSessionSummary(created, "Started new session: ${created.title}")
        return created
    }

    fun beginRun(userMessage: String): PersistedSessionSummary {
        val selectedSession =
            _uiState.value.selectedSessionPath
                ?.let(codingSessionStore::loadSession)
                ?: codingSessionStore.createSession("")
        val runId = "run-${System.currentTimeMillis()}"
        val line = "Run started: $userMessage"
        _uiState.value =
            _uiState.value.copy(
                activeRunId = runId,
                selectedSessionPath = selectedSession.path,
                selectedPersistentSessionId = selectedSession.sessionId,
                selectedSessionTitle = selectedSession.title,
                activeSessionPath = selectedSession.path,
                activePersistentSessionId = selectedSession.sessionId,
                activeSessionTitle = selectedSession.title,
                recentSessions = refreshSessionList(selectedSession.path),
                status = SessionStatus.Running,
                summaryLine = line,
                foregroundServiceRunning = true,
                isStreaming = true,
                isCompacting = false,
                pendingSteeringCount = 0,
                pendingQuestion = null,
                lastAssistantText = "",
                timeline = listOf(line),
            )
        logStore.record(_uiState.value)
        return selectedSession
    }

    fun selectSession(path: String): PersistedSessionSummary? {
        val selected = codingSessionStore.loadSession(path) ?: return null
        selectSessionSummary(selected, "Selected session: ${selected.title}")
        return selected
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
        val current = _uiState.value
        val terminalStatus = current.status.takeIf { it.isTerminalOutcome() }
        _uiState.value =
            current.copy(
                status = terminalStatus ?: SessionStatus.Cancelled,
                summaryLine = terminalStatus?.let { current.summaryLine } ?: line,
                foregroundServiceRunning = false,
                isStreaming = false,
                pendingSteeringCount = 0,
                pendingQuestion = null,
                isCompacting = false,
                timeline = (current.timeline + line).takeLast(20),
                recentSessions = refreshSessionList(current.selectedSessionPath),
            )
        logStore.record(_uiState.value)
    }

    fun recoverOrphanedSession(reason: String) {
        if (_uiState.value.foregroundServiceRunning || _uiState.value.status == SessionStatus.Running) {
            stopSession(reason)
        }
    }

    fun completeTurn(summary: String) {
        _uiState.value =
            _uiState.value.copy(
                status = SessionStatus.Completed,
                summaryLine = summary,
                foregroundServiceRunning = true,
                isStreaming = false,
                pendingSteeringCount = 0,
                pendingQuestion = null,
                isCompacting = false,
                timeline = (_uiState.value.timeline + summary).takeLast(20),
                recentSessions = refreshSessionList(_uiState.value.selectedSessionPath),
            )
        logStore.record(_uiState.value)
    }

    fun blockTurn(reason: String) {
        _uiState.value =
            _uiState.value.copy(
                status = SessionStatus.Blocked,
                summaryLine = reason,
                foregroundServiceRunning = true,
                isStreaming = false,
                pendingSteeringCount = 0,
                pendingQuestion = null,
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
                pendingQuestion = null,
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
            val effectiveRunning = running && current.status != SessionStatus.Cancelled
            if (current.foregroundServiceRunning == effectiveRunning) {
                current
            } else {
                current.copy(foregroundServiceRunning = effectiveRunning)
            }
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

    fun setPendingQuestion(question: PendingQuestionUiState) {
        updateUiState { current ->
            if (current.pendingQuestion == question && current.foregroundServiceRunning) {
                current
            } else {
                current.copy(pendingQuestion = question, foregroundServiceRunning = true)
            }
        }
    }

    fun clearPendingQuestion(questionId: String? = null) {
        updateUiState { current ->
            val pending = current.pendingQuestion
            if (pending == null || (questionId != null && pending.id != questionId)) {
                current
            } else {
                current.copy(pendingQuestion = null)
            }
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

    private fun selectSessionSummary(selected: PersistedSessionSummary, summaryLine: String) {
        _uiState.value =
            _uiState.value.copy(
                selectedSessionPath = selected.path,
                selectedPersistentSessionId = selected.sessionId,
                selectedSessionTitle = selected.title,
                recentSessions = refreshSessionList(selected.path),
                summaryLine = summaryLine,
            )
    }

    private fun refreshSessionList(selectedPath: String?): List<PersistedSessionSummary> {
        val sessions = codingSessionStore.listSessions(limit = 12)
        if (selectedPath == null) {
            return sessions
        }
        val selected = sessions.firstOrNull { it.path == selectedPath } ?: return sessions
        return listOf(selected) + sessions.filterNot { it.path == selectedPath }
    }

    private fun SessionStatus.isTerminalOutcome(): Boolean = this == SessionStatus.Completed || this == SessionStatus.Blocked
}
