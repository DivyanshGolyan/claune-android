package com.divyanshgolyan.claune.android.data.local

import com.divyanshgolyan.claune.android.runtime.SessionUiState
import com.divyanshgolyan.claune.android.runtime.UiSnapshot

interface SessionLogStore {
    fun record(state: SessionUiState)

    fun recordSnapshot(snapshot: UiSnapshot)

    fun recentStates(): List<SessionUiState>

    fun recentSnapshots(): List<UiSnapshot>
}

class InMemorySessionLogStore : SessionLogStore {
    private val states = ArrayDeque<SessionUiState>()
    private val snapshots = ArrayDeque<UiSnapshot>()

    override fun record(state: SessionUiState) {
        states += state
        trim(states)
    }

    override fun recordSnapshot(snapshot: UiSnapshot) {
        snapshots += snapshot
        trim(snapshots)
    }

    override fun recentStates(): List<SessionUiState> = states.toList()

    override fun recentSnapshots(): List<UiSnapshot> = snapshots.toList()

    private fun <T> trim(deque: ArrayDeque<T>) {
        while (deque.size > 50) {
            deque.removeFirst()
        }
    }
}

data class PrototypeSessionRecord(val sessionId: String, val goal: String, val status: String)

data class PrototypeTurnRecord(val turnIndex: Int, val summary: String)

data class PrototypeApprovalRecord(val approvalId: String, val reason: String, val granted: Boolean)
