package com.divyanshgolyan.claune.android.ui

import com.divyanshgolyan.claune.android.data.local.PersistedSessionDetail
import com.divyanshgolyan.claune.android.data.local.PersistedSessionSummary
import com.divyanshgolyan.claune.android.data.local.SettingsState
import com.divyanshgolyan.claune.android.llm.CodexAuthState
import com.divyanshgolyan.claune.android.runtime.SessionUiState

data class ClauneUiState(
    val sessionState: SessionUiState = SessionUiState(),
    val settingsState: SettingsState = SettingsState(),
    val codexAuthState: CodexAuthState = CodexAuthState(),
    val historyEntries: List<SessionHistoryEntry> = emptyList(),
    val sessionDetail: PersistedSessionDetail? = null,
)

data class SessionHistoryEntry(val sessionId: String, val sessionPath: String, val title: String, val summary: String)

fun sessionHistoryEntries(sessions: List<PersistedSessionSummary>): List<SessionHistoryEntry> = sessions.map { session ->
    SessionHistoryEntry(
        sessionId = session.sessionId,
        sessionPath = session.path,
        title = session.title,
        summary = session.preview,
    )
}
