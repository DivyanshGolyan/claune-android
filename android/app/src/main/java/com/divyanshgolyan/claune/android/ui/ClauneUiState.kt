package com.divyanshgolyan.claune.android.ui

import com.divyanshgolyan.claune.android.data.local.PersistedSessionDetail
import com.divyanshgolyan.claune.android.data.local.PersistedSessionSummary
import com.divyanshgolyan.claune.android.data.local.SettingsState
import com.divyanshgolyan.claune.android.runtime.SessionUiState

data class ClauneUiState(
    val sessionState: SessionUiState = SessionUiState(),
    val settingsState: SettingsState = SettingsState(),
    val historyEntries: List<SessionHistoryEntry> = emptyList(),
    val sessionDetail: PersistedSessionDetail? = null,
)

data class SessionHistoryEntry(val sessionId: String, val sessionPath: String, val goal: String, val summary: String)

fun sessionHistoryEntries(sessions: List<PersistedSessionSummary>): List<SessionHistoryEntry> = sessions.map { session ->
    SessionHistoryEntry(
        sessionId = session.sessionId,
        sessionPath = session.path,
        goal = session.title,
        summary = session.preview,
    )
}
