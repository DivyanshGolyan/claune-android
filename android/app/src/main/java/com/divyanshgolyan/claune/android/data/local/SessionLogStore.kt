package com.divyanshgolyan.claune.android.data.local

import com.divyanshgolyan.claune.android.runtime.ScreenState
import com.divyanshgolyan.claune.android.runtime.SessionUiState
import com.divyanshgolyan.claune.android.scripting.HostCallRecord
import com.divyanshgolyan.claune.android.scripting.ScriptExecutionRecord
import kotlinx.serialization.Serializable

@Serializable
data class PerfEventRecord(
    val recordedAt: String,
    val runId: String? = null,
    val scriptExecutionId: String? = null,
    val scope: String,
    val name: String,
    val durationMs: Long,
    val attrs: Map<String, String> = emptyMap(),
    val phases: List<PerfPhaseRecord> = emptyList(),
)

@Serializable
data class PerfPhaseRecord(val name: String, val durationMs: Long)

object PerfTelemetry {
    const val SCOPE_SCREEN_STATE = "screen_state"
    const val SCOPE_PROJECTION = "projection"
    const val SCOPE_QUICKJS_BRIDGE = "quickjs_bridge"

    const val RECORD_SCREEN_STATE = "recordScreenState"
    const val OBSERVE_SCREEN_PROJECT = "observeScreen.project"
    const val DIFF_SCREEN_PROJECT = "diffScreen.project"
    const val OBSERVE_SCREEN_ENCODE = "observeScreen.encode"
    const val DIFF_SCREEN_ENCODE = "diffScreen.encode"
}

interface SessionLogStore {
    fun record(state: SessionUiState)

    fun recordScreenState(screenState: ScreenState)

    fun recordPerfEvent(event: PerfEventRecord)

    fun recordScriptExecution(execution: ScriptExecutionRecord)

    fun recordHostCall(hostCall: HostCallRecord)

    fun recentScreenStates(): List<ScreenState>

    fun recentHostCalls(): List<HostCallRecord>

    fun recentHostCallCount(): Int = recentHostCalls().size
}

class InMemorySessionLogStore : SessionLogStore {
    private val screenStates = ArrayDeque<ScreenState>()
    private val hostCalls = ArrayDeque<HostCallRecord>()

    override fun record(state: SessionUiState) = Unit

    override fun recordScreenState(screenState: ScreenState) {
        screenStates += screenState
        trim(screenStates)
    }

    override fun recordPerfEvent(event: PerfEventRecord) = Unit

    override fun recordScriptExecution(execution: ScriptExecutionRecord) = Unit

    override fun recordHostCall(hostCall: HostCallRecord) {
        hostCalls += hostCall
        trim(hostCalls)
    }

    override fun recentScreenStates(): List<ScreenState> = screenStates.toList()

    override fun recentHostCalls(): List<HostCallRecord> = hostCalls.toList()

    override fun recentHostCallCount(): Int = hostCalls.size

    private fun <T> trim(deque: ArrayDeque<T>) {
        while (deque.size > 50) {
            deque.removeFirst()
        }
    }
}
