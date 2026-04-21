package com.divyanshgolyan.claune.android.data.local

import com.divyanshgolyan.claune.android.runtime.SessionUiState
import com.divyanshgolyan.claune.android.runtime.UiSnapshot
import com.divyanshgolyan.claune.android.scripting.HostCallRecord
import com.divyanshgolyan.claune.android.scripting.ScriptExecutionRecord

interface SessionLogStore {
    fun record(state: SessionUiState)

    fun recordSnapshot(snapshot: UiSnapshot)

    fun recordScriptExecution(execution: ScriptExecutionRecord)

    fun recordHostCall(hostCall: HostCallRecord)

    fun recentSnapshots(): List<UiSnapshot>

    fun recentHostCalls(): List<HostCallRecord>
}

class InMemorySessionLogStore : SessionLogStore {
    private val snapshots = ArrayDeque<UiSnapshot>()
    private val hostCalls = ArrayDeque<HostCallRecord>()

    override fun record(state: SessionUiState) = Unit

    override fun recordSnapshot(snapshot: UiSnapshot) {
        snapshots += snapshot
        trim(snapshots)
    }

    override fun recordScriptExecution(execution: ScriptExecutionRecord) = Unit

    override fun recordHostCall(hostCall: HostCallRecord) {
        hostCalls += hostCall
        trim(hostCalls)
    }

    override fun recentSnapshots(): List<UiSnapshot> = snapshots.toList()

    override fun recentHostCalls(): List<HostCallRecord> = hostCalls.toList()

    private fun <T> trim(deque: ArrayDeque<T>) {
        while (deque.size > 50) {
            deque.removeFirst()
        }
    }
}
