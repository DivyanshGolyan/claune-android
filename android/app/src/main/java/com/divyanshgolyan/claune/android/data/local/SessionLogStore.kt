package com.divyanshgolyan.claune.android.data.local

import com.divyanshgolyan.claune.android.runtime.ScreenState
import com.divyanshgolyan.claune.android.runtime.SessionUiState
import com.divyanshgolyan.claune.android.scripting.HostCallRecord
import com.divyanshgolyan.claune.android.scripting.ScriptExecutionRecord

interface SessionLogStore {
    fun record(state: SessionUiState)

    fun recordScreenState(screenState: ScreenState)

    fun recordScriptExecution(execution: ScriptExecutionRecord)

    fun recordHostCall(hostCall: HostCallRecord)

    fun recentScreenStates(): List<ScreenState>

    fun recentHostCalls(): List<HostCallRecord>
}

class InMemorySessionLogStore : SessionLogStore {
    private val screenStates = ArrayDeque<ScreenState>()
    private val hostCalls = ArrayDeque<HostCallRecord>()

    override fun record(state: SessionUiState) = Unit

    override fun recordScreenState(screenState: ScreenState) {
        screenStates += screenState
        trim(screenStates)
    }

    override fun recordScriptExecution(execution: ScriptExecutionRecord) = Unit

    override fun recordHostCall(hostCall: HostCallRecord) {
        hostCalls += hostCall
        trim(hostCalls)
    }

    override fun recentScreenStates(): List<ScreenState> = screenStates.toList()

    override fun recentHostCalls(): List<HostCallRecord> = hostCalls.toList()

    private fun <T> trim(deque: ArrayDeque<T>) {
        while (deque.size > 50) {
            deque.removeFirst()
        }
    }
}
