package com.divyanshgolyan.claune.android.data.local

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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import pi.agent.core.AgentEvent
import pi.ai.core.AssistantMessage
import pi.ai.core.ImageContent
import pi.ai.core.Message
import pi.ai.core.TextContent
import pi.ai.core.ThinkingContent
import pi.ai.core.ToolCall
import pi.ai.core.ToolResultMessage
import pi.ai.core.UserMessage
import pi.ai.core.UserMessageContent

interface AgentRunArtifactStore {
    fun startRun(metadata: RunArtifactMetadata)

    fun recordState(state: SessionUiState)

    fun recordSnapshot(runId: String, snapshot: UiSnapshot)

    fun recordScriptExecution(runId: String, execution: ScriptExecutionRecord)

    fun recordHostCall(runId: String, hostCall: HostCallRecord)

    fun writeSystemPrompt(runId: String, systemPrompt: String)

    fun writeModelInput(runId: String, modelInput: String)

    fun writeFinalOutput(runId: String, finalOutput: String)

    fun writeMemoryReflectionPrompt(runId: String, prompt: String)

    fun writeMemoryReflectionOutput(runId: String, output: String)

    fun writeAgentMessages(runId: String, messages: List<Message>)

    fun writeAgentEvents(runId: String, events: List<SerializedAgentEvent>)

    fun recoverOrphanedRuns(reason: String) {}
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
                finishedAt =
                current.finishedAt
                    ?: state.status.takeIf {
                        it == SessionStatus.Completed || it == SessionStatus.Blocked || it == SessionStatus.Cancelled
                    }?.let { now().toString() },
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
    override fun writeMemoryReflectionPrompt(runId: String, prompt: String) {
        writeText(runId, MEMORY_REFLECTION_PROMPT_FILE_NAME, prompt)
    }

    @Synchronized
    override fun writeMemoryReflectionOutput(runId: String, output: String) {
        writeText(runId, MEMORY_REFLECTION_OUTPUT_FILE_NAME, output)
    }

    @Synchronized
    override fun writeAgentMessages(runId: String, messages: List<Message>) {
        writeJson(
            runDirectory(runId).resolve(AGENT_MESSAGES_FILE_NAME),
            ListSerializer(SerializedAgentMessage.serializer()),
            messages.map(AgentTranscriptSerializer::serializeMessage),
        )
    }

    @Synchronized
    override fun writeAgentEvents(runId: String, events: List<SerializedAgentEvent>) {
        writeJson(
            runDirectory(runId).resolve(AGENT_EVENTS_FILE_NAME),
            ListSerializer(SerializedAgentEvent.serializer()),
            events,
        )
    }

    @Synchronized
    override fun recoverOrphanedRuns(reason: String) {
        rootDir.listFiles()
            ?.filter { it.isDirectory }
            ?.forEach { directory ->
                val metadataFile = directory.resolve(METADATA_FILE_NAME)
                if (!metadataFile.exists()) {
                    return@forEach
                }
                val metadata = json.decodeFromString(RunArtifactMetadata.serializer(), metadataFile.readText())
                if (metadata.status != SessionStatus.Running.name) {
                    return@forEach
                }
                writeJson(
                    metadataFile,
                    RunArtifactMetadata.serializer(),
                    metadata.copy(
                        status = SessionStatus.Cancelled.name,
                        latestSummary = reason,
                        foregroundServiceRunning = false,
                        finishedAt = now().toString(),
                    ),
                )
            }
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
        private const val MEMORY_REFLECTION_PROMPT_FILE_NAME = "memory-reflection-prompt.txt"
        private const val MEMORY_REFLECTION_OUTPUT_FILE_NAME = "memory-reflection-output.txt"
        private const val AGENT_MESSAGES_FILE_NAME = "agent-messages.json"
        private const val AGENT_EVENTS_FILE_NAME = "agent-events.json"
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

    override fun recentSnapshots(): List<UiSnapshot> = delegate.recentSnapshots()

    override fun recentHostCalls(): List<HostCallRecord> = delegate.recentHostCalls()
}

@Serializable
data class RunArtifactMetadata(
    val runId: String,
    val persistentSessionPath: String? = null,
    val persistentSessionId: String? = null,
    val goal: String,
    val startedAt: String,
    val finishedAt: String? = null,
    val status: String = SessionStatus.Running.name,
    val model: String,
    val maxIterations: Int? = null,
    val promptVersion: String,
    val harness: String = "pi-agent-core",
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
data class SerializedAgentMessage(val type: String, val payload: JsonObject)

@Serializable
data class SerializedAgentEvent(val type: String, val payload: JsonObject)

object AgentTranscriptSerializer {
    fun serializeMessage(message: Message): SerializedAgentMessage = when (message) {
        is UserMessage -> SerializedAgentMessage("user", serializeUserMessage(message))
        is AssistantMessage -> SerializedAgentMessage("assistant", serializeAssistantMessage(message))
        is ToolResultMessage -> SerializedAgentMessage("tool_result", serializeToolResultMessage(message))
        else ->
            SerializedAgentMessage(
                "unknown",
                buildJsonObject {
                    put("role", JsonPrimitive(message.role))
                    put("timestamp", JsonPrimitive(message.timestamp))
                },
            )
    }

    fun serializeEvent(event: AgentEvent): SerializedAgentEvent = when (event) {
        AgentEvent.AgentStart -> SerializedAgentEvent("agent_start", buildJsonObject {})
        is AgentEvent.AgentEnd ->
            SerializedAgentEvent(
                "agent_end",
                buildJsonObject {
                    put("messageCount", JsonPrimitive(event.messages.size))
                },
            )
        AgentEvent.TurnStart -> SerializedAgentEvent("turn_start", buildJsonObject {})
        is AgentEvent.TurnEnd ->
            SerializedAgentEvent(
                "turn_end",
                buildJsonObject {
                    put("toolResultCount", JsonPrimitive(event.toolResults.size))
                    put("message", serializeMessage(event.message).payload)
                },
            )
        is AgentEvent.MessageStart ->
            SerializedAgentEvent(
                "message_start",
                buildJsonObject {
                    put("message", serializeMessage(event.message).payload)
                },
            )
        is AgentEvent.MessageUpdate ->
            SerializedAgentEvent(
                "message_update",
                buildJsonObject {
                    put("message", serializeMessage(event.message).payload)
                    put("assistantEventType", JsonPrimitive(event.assistantMessageEvent::class.simpleName ?: "unknown"))
                },
            )
        is AgentEvent.MessageEnd ->
            SerializedAgentEvent(
                "message_end",
                buildJsonObject {
                    put("message", serializeMessage(event.message).payload)
                },
            )
        is AgentEvent.ToolExecutionStart ->
            SerializedAgentEvent(
                "tool_execution_start",
                buildJsonObject {
                    put("toolCallId", JsonPrimitive(event.toolCallId))
                    put("toolName", JsonPrimitive(event.toolName))
                    put("arguments", event.args)
                },
            )
        is AgentEvent.ToolExecutionUpdate ->
            SerializedAgentEvent(
                "tool_execution_update",
                buildJsonObject {
                    put("toolCallId", JsonPrimitive(event.toolCallId))
                    put("toolName", JsonPrimitive(event.toolName))
                    put("arguments", event.args)
                    put("partialResult", serializeAgentToolResult(event.partialResult))
                },
            )
        is AgentEvent.ToolExecutionEnd ->
            SerializedAgentEvent(
                "tool_execution_end",
                buildJsonObject {
                    put("toolCallId", JsonPrimitive(event.toolCallId))
                    put("toolName", JsonPrimitive(event.toolName))
                    put("isError", JsonPrimitive(event.isError))
                    put("result", serializeAgentToolResult(event.result))
                },
            )
    }

    private fun serializeUserMessage(message: UserMessage): JsonObject = buildJsonObject {
        put("role", JsonPrimitive(message.role))
        put("timestamp", JsonPrimitive(message.timestamp))
        put("content", serializeUserContent(message.content))
    }

    private fun serializeAssistantMessage(message: AssistantMessage): JsonObject = buildJsonObject {
        val content = message.content.toList()
        put("role", JsonPrimitive(message.role))
        put("timestamp", JsonPrimitive(message.timestamp))
        put("api", JsonPrimitive(message.api))
        put("provider", JsonPrimitive(message.provider))
        put("model", JsonPrimitive(message.model))
        put("stopReason", JsonPrimitive(message.stopReason.name))
        message.errorMessage?.let { put("errorMessage", JsonPrimitive(it)) }
        message.responseId?.let { put("responseId", JsonPrimitive(it)) }
        put("usage", serializeUsage(message.usage))
        put(
            "content",
            buildJsonArray {
                content.forEach { block ->
                    add(serializeAssistantContentBlock(block))
                }
            },
        )
    }

    private fun serializeToolResultMessage(message: ToolResultMessage): JsonObject = buildJsonObject {
        val content = message.content.toList()
        put("role", JsonPrimitive(message.role))
        put("timestamp", JsonPrimitive(message.timestamp))
        put("toolCallId", JsonPrimitive(message.toolCallId))
        put("toolName", JsonPrimitive(message.toolName))
        put("isError", JsonPrimitive(message.isError))
        message.details?.let { put("details", it) }
        put(
            "content",
            buildJsonArray {
                content.forEach { part ->
                    add(serializeToolResultContentPart(part))
                }
            },
        )
    }

    private fun serializeUserContent(content: UserMessageContent): JsonElement = when (content) {
        is UserMessageContent.Text ->
            buildJsonObject {
                put("type", JsonPrimitive("text"))
                put("value", JsonPrimitive(content.value))
            }
        is UserMessageContent.Structured ->
            JsonArray(content.parts.toList().map(::serializeUserContentPart))
    }

    private fun serializeUserContentPart(part: pi.ai.core.UserContentPart): JsonObject = when (part) {
        is TextContent ->
            buildJsonObject {
                put("type", JsonPrimitive("text"))
                put("text", JsonPrimitive(part.text))
                part.textSignature?.let { put("textSignature", JsonPrimitive(it)) }
            }
        is ImageContent ->
            buildJsonObject {
                put("type", JsonPrimitive("image"))
                put("mimeType", JsonPrimitive(part.mimeType))
                put("data", JsonPrimitive(part.data))
            }
    }

    private fun serializeAssistantContentBlock(block: pi.ai.core.AssistantContentBlock): JsonObject = when (block) {
        is TextContent ->
            buildJsonObject {
                put("type", JsonPrimitive("text"))
                put("text", JsonPrimitive(block.text))
                block.textSignature?.let { put("textSignature", JsonPrimitive(it)) }
            }
        is ThinkingContent ->
            buildJsonObject {
                put("type", JsonPrimitive("thinking"))
                put("thinking", JsonPrimitive(block.thinking))
                block.thinkingSignature?.let { put("thinkingSignature", JsonPrimitive(it)) }
                put("redacted", JsonPrimitive(block.redacted))
            }
        is ToolCall ->
            buildJsonObject {
                put("type", JsonPrimitive("tool_call"))
                put("id", JsonPrimitive(block.id))
                put("name", JsonPrimitive(block.name))
                put("arguments", block.arguments)
                block.thoughtSignature?.let { put("thoughtSignature", JsonPrimitive(it)) }
            }
        else -> buildJsonObject { put("type", JsonPrimitive("unknown")) }
    }

    private fun serializeToolResultContentPart(part: pi.ai.core.ToolResultContentPart): JsonObject = when (part) {
        is TextContent ->
            buildJsonObject {
                put("type", JsonPrimitive("text"))
                put("text", JsonPrimitive(part.text))
                part.textSignature?.let { put("textSignature", JsonPrimitive(it)) }
            }
        is ImageContent ->
            buildJsonObject {
                put("type", JsonPrimitive("image"))
                put("mimeType", JsonPrimitive(part.mimeType))
                put("data", JsonPrimitive(part.data))
            }
    }

    private fun serializeUsage(usage: pi.ai.core.Usage): JsonObject = buildJsonObject {
        put("input", JsonPrimitive(usage.input))
        put("output", JsonPrimitive(usage.output))
        put("cacheRead", JsonPrimitive(usage.cacheRead))
        put("cacheWrite", JsonPrimitive(usage.cacheWrite))
        put("totalTokens", JsonPrimitive(usage.totalTokens))
        put(
            "cost",
            buildJsonObject {
                put("input", JsonPrimitive(usage.cost.input))
                put("output", JsonPrimitive(usage.cost.output))
                put("cacheRead", JsonPrimitive(usage.cost.cacheRead))
                put("cacheWrite", JsonPrimitive(usage.cost.cacheWrite))
                put("total", JsonPrimitive(usage.cost.total))
            },
        )
    }

    private fun serializeAgentToolResult(result: pi.agent.core.AgentToolResult<*>): JsonObject = buildJsonObject {
        put(
            "content",
            buildJsonArray {
                result.content.forEach { part ->
                    add(serializeToolResultContentPart(part))
                }
            },
        )
        (result.details as? JsonElement)?.let { put("details", it) }
    }
}
