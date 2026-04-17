package com.divyanshgolyan.claune.android.data.local

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import com.divyanshgolyan.claune.android.runtime.SessionStatus
import com.divyanshgolyan.claune.android.runtime.SessionUiState
import com.divyanshgolyan.claune.android.runtime.UiSnapshot
import com.divyanshgolyan.claune.android.scripting.HostCallRecord
import com.divyanshgolyan.claune.android.scripting.ScriptExecutionRecord
import com.divyanshgolyan.claune.android.scripting.ScriptJson
import com.divyanshgolyan.claune.android.scripting.toPayload
import java.io.File
import java.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

interface AgentRunArtifactStore {
    fun startRun(metadata: RunArtifactMetadata)

    fun recordState(state: SessionUiState)

    fun recordSnapshot(runId: String, snapshot: UiSnapshot)

    fun recordScriptExecution(runId: String, execution: ScriptExecutionRecord)

    fun recordHostCall(runId: String, hostCall: HostCallRecord)

    fun writeSystemPrompt(runId: String, systemPrompt: String)

    fun writeModelInput(runId: String, modelInput: String)

    fun writeFinalOutput(runId: String, finalOutput: String)

    fun writeKoogHistory(runId: String, history: List<Message>)
}

class FileAgentRunArtifactStore(private val rootDir: File, private val now: () -> Instant = { Instant.now() }) : AgentRunArtifactStore {
    private val json =
        Json(ScriptJson.codec) {
            prettyPrint = true
        }

    init {
        rootDir.mkdirs()
    }

    @Synchronized
    override fun startRun(metadata: RunArtifactMetadata) {
        val directory = runDirectory(metadata.runId).apply { mkdirs() }
        writeJson(directory.resolve(METADATA_FILE_NAME), RunArtifactMetadata.serializer(), metadata)
    }

    @Synchronized
    override fun recordState(state: SessionUiState) {
        val runId = state.sessionId ?: return
        appendArray(
            runId = runId,
            fileName = STATES_FILE_NAME,
            serializer = RunStateRecord.serializer(),
            value =
            RunStateRecord(
                recordedAt = now().toString(),
                sessionId = runId,
                status = state.status.name,
                summaryLine = state.summaryLine,
                lastKnownApp = state.lastKnownApp,
                accessibilityConnected = state.accessibilityConnected,
                foregroundServiceRunning = state.foregroundServiceRunning,
                timeline = state.timeline,
            ),
        )
        updateMetadata(runId) { current ->
            current.copy(
                status = state.status.name,
                latestSummary = state.summaryLine,
                lastKnownApp = state.lastKnownApp,
                accessibilityConnected = state.accessibilityConnected,
                foregroundServiceRunning = state.foregroundServiceRunning,
                finishedAt = state.status.takeIf { it != SessionStatus.Running }?.let { now().toString() }
                    ?: current.finishedAt,
            )
        }
    }

    @Synchronized
    override fun recordSnapshot(runId: String, snapshot: UiSnapshot) {
        appendArray(
            runId = runId,
            fileName = SNAPSHOTS_FILE_NAME,
            serializer = com.divyanshgolyan.claune.android.scripting.UiSnapshotPayload.serializer(),
            value = snapshot.toPayload(),
        )
    }

    @Synchronized
    override fun recordScriptExecution(runId: String, execution: ScriptExecutionRecord) {
        appendArray(runId, SCRIPT_EXECUTIONS_FILE_NAME, ScriptExecutionRecord.serializer(), execution)
    }

    @Synchronized
    override fun recordHostCall(runId: String, hostCall: HostCallRecord) {
        appendArray(runId, HOST_CALLS_FILE_NAME, HostCallRecord.serializer(), hostCall)
    }

    @Synchronized
    override fun writeSystemPrompt(runId: String, systemPrompt: String) {
        writeText(runId, SYSTEM_PROMPT_FILE_NAME, systemPrompt)
    }

    @Synchronized
    override fun writeModelInput(runId: String, modelInput: String) {
        writeText(runId, MODEL_INPUT_FILE_NAME, modelInput)
    }

    @Synchronized
    override fun writeFinalOutput(runId: String, finalOutput: String) {
        writeText(runId, FINAL_OUTPUT_FILE_NAME, finalOutput)
    }

    @Synchronized
    override fun writeKoogHistory(runId: String, history: List<Message>) {
        writeJson(
            runDirectory(runId).resolve(KOOG_HISTORY_FILE_NAME),
            ListSerializer(SerializedKoogMessage.serializer()),
            history.map(KoogHistorySerializer::serialize),
        )
    }

    private fun writeText(runId: String, fileName: String, value: String) {
        val file = runDirectory(runId).resolve(fileName)
        file.parentFile?.mkdirs()
        file.writeText(value)
    }

    private fun updateMetadata(runId: String, update: (RunArtifactMetadata) -> RunArtifactMetadata) {
        val file = runDirectory(runId).resolve(METADATA_FILE_NAME)
        if (!file.exists()) {
            return
        }
        val current = json.decodeFromString(RunArtifactMetadata.serializer(), file.readText())
        writeJson(file, RunArtifactMetadata.serializer(), update(current))
    }

    private fun <T> appendArray(runId: String, fileName: String, serializer: KSerializer<T>, value: T) {
        val file = runDirectory(runId).resolve(fileName)
        val listSerializer = ListSerializer(serializer)
        val existing =
            if (file.exists()) {
                json.decodeFromString(listSerializer, file.readText()).toMutableList()
            } else {
                mutableListOf()
            }
        existing += value
        writeJson(file, listSerializer, existing)
    }

    private fun <T> writeJson(file: File, serializer: KSerializer<T>, value: T) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(serializer, value))
    }

    private fun runDirectory(runId: String): File = rootDir.resolve(runId)

    private companion object {
        private const val METADATA_FILE_NAME = "metadata.json"
        private const val STATES_FILE_NAME = "states.json"
        private const val SNAPSHOTS_FILE_NAME = "snapshots.json"
        private const val SCRIPT_EXECUTIONS_FILE_NAME = "script-executions.json"
        private const val HOST_CALLS_FILE_NAME = "host-calls.json"
        private const val SYSTEM_PROMPT_FILE_NAME = "system-prompt.txt"
        private const val MODEL_INPUT_FILE_NAME = "model-input.txt"
        private const val FINAL_OUTPUT_FILE_NAME = "final-output.txt"
        private const val KOOG_HISTORY_FILE_NAME = "koog-history.json"
    }
}

class ArtifactSessionLogStore(
    private val delegate: SessionLogStore,
    private val artifactStore: AgentRunArtifactStore,
    private val currentSessionIdProvider: () -> String?,
) : SessionLogStore {
    override fun record(state: SessionUiState) {
        delegate.record(state)
        runCatching { artifactStore.recordState(state) }
    }

    override fun recordSnapshot(snapshot: UiSnapshot) {
        delegate.recordSnapshot(snapshot)
        val runId = currentSessionIdProvider() ?: return
        runCatching { artifactStore.recordSnapshot(runId, snapshot) }
    }

    override fun recordScriptExecution(execution: ScriptExecutionRecord) {
        delegate.recordScriptExecution(execution)
        val runId = execution.sessionId ?: return
        runCatching { artifactStore.recordScriptExecution(runId, execution) }
    }

    override fun recordHostCall(hostCall: HostCallRecord) {
        delegate.recordHostCall(hostCall)
        val runId = hostCall.sessionId ?: return
        runCatching { artifactStore.recordHostCall(runId, hostCall) }
    }

    override fun recentStates(): List<SessionUiState> = delegate.recentStates()

    override fun recentSnapshots(): List<UiSnapshot> = delegate.recentSnapshots()

    override fun recentScriptExecutions(): List<ScriptExecutionRecord> = delegate.recentScriptExecutions()

    override fun recentHostCalls(): List<HostCallRecord> = delegate.recentHostCalls()
}

@Serializable
data class RunArtifactMetadata(
    val runId: String,
    val goal: String,
    val startedAt: String,
    val finishedAt: String? = null,
    val status: String = SessionStatus.Running.name,
    val model: String,
    val maxIterations: Int,
    val promptVersion: String,
    val latestSummary: String? = null,
    val lastKnownApp: String? = null,
    val accessibilityConnected: Boolean = false,
    val foregroundServiceRunning: Boolean = false,
)

@Serializable
data class RunStateRecord(
    val recordedAt: String,
    val sessionId: String,
    val status: String,
    val summaryLine: String,
    val lastKnownApp: String? = null,
    val accessibilityConnected: Boolean,
    val foregroundServiceRunning: Boolean,
    val timeline: List<String>,
)

@Serializable
data class SerializedKoogMessage(val type: String, val payload: JsonObject)

object KoogHistorySerializer {
    private val json = ScriptJson.codec

    fun serialize(message: Message): SerializedKoogMessage = when (message) {
        is Message.User -> SerializedKoogMessage("user", json.encodeToJsonElement(Message.User.serializer(), message).jsonObject)
        is Message.Assistant ->
            SerializedKoogMessage(
                "assistant",
                json.encodeToJsonElement(Message.Assistant.serializer(), message).jsonObject,
            )

        is Message.System -> SerializedKoogMessage("system", json.encodeToJsonElement(Message.System.serializer(), message).jsonObject)
        is Message.Tool.Call ->
            SerializedKoogMessage(
                "tool_call",
                json.encodeToJsonElement(Message.Tool.Call.serializer(), message).jsonObject,
            )

        is Message.Tool.Result ->
            SerializedKoogMessage(
                "tool_result",
                json.encodeToJsonElement(Message.Tool.Result.serializer(), message).jsonObject,
            )

        is Message.Reasoning ->
            SerializedKoogMessage(
                "reasoning",
                json.encodeToJsonElement(Message.Reasoning.serializer(), message).jsonObject,
            )
    }

    private fun serializeMetaInfo(metaInfo: ai.koog.prompt.message.MessageMetaInfo): JsonObject = when (metaInfo) {
        is RequestMetaInfo -> ScriptJson.codec.encodeToJsonElement(RequestMetaInfo.serializer(), metaInfo).jsonObject
        is ResponseMetaInfo -> ScriptJson.codec.encodeToJsonElement(ResponseMetaInfo.serializer(), metaInfo).jsonObject
    }
}
