package com.divyanshgolyan.claune.android.data.local

import com.divyanshgolyan.claune.android.runtime.ScreenState
import com.divyanshgolyan.claune.android.runtime.SessionStatus
import com.divyanshgolyan.claune.android.runtime.SessionUiState
import com.divyanshgolyan.claune.android.runtime.actionableNodes
import com.divyanshgolyan.claune.android.runtime.elapsedMs
import com.divyanshgolyan.claune.android.runtime.toCanonicalScreenText
import com.divyanshgolyan.claune.android.runtime.visibleNodes
import com.divyanshgolyan.claune.android.scripting.HostCallRecord
import com.divyanshgolyan.claune.android.scripting.ScreenWindowPayload
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

private const val MAX_WINDOW_VISIBLE_TEXT_RECORDS = 12

interface AgentRunArtifactStore {
    fun startRun(metadata: RunArtifactMetadata)

    fun recordState(state: SessionUiState)

    fun recordScreenState(runId: String, screenState: ScreenState)

    fun recordPerfEvent(runId: String, event: PerfEventRecord)

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
    private val lineJson = ScriptJson.codec

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
        val runId = state.activeRunId ?: return
        appendArray(
            runId = runId,
            fileName = STATES_FILE_NAME,
            serializer = RunStateRecord.serializer(),
            value =
            RunStateRecord(
                recordedAt = now().toString(),
                runId = runId,
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
    override fun recordScreenState(runId: String, screenState: ScreenState) {
        writeJson(
            runDirectory(runId).resolve(LATEST_SCREEN_STATE_FILE_NAME),
            ScreenState.serializer(),
            screenState,
        )
        appendArray(
            runId = runId,
            fileName = SCREEN_STATES_FILE_NAME,
            serializer = ScreenStateArtifactRecord.serializer(),
            value = screenState.toArtifactRecord(),
            maxEntries = MAX_SCREEN_STATE_RECORDS,
            resetIfLargerThanBytes = MAX_SCREEN_STATES_FILE_BYTES,
        )
    }

    @Synchronized
    override fun recordPerfEvent(runId: String, event: PerfEventRecord) {
        appendJsonLine(
            runId = runId,
            fileName = PERF_EVENTS_FILE_NAME,
            serializer = PerfEventRecord.serializer(),
            value = event.copy(runId = event.runId ?: runId),
            resetIfLargerThanBytes = MAX_PERF_EVENTS_FILE_BYTES,
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

    private fun <T> appendArray(
        runId: String,
        fileName: String,
        serializer: KSerializer<T>,
        value: T,
        maxEntries: Int? = null,
        resetIfLargerThanBytes: Long? = null,
    ) {
        val file = runDirectory(runId).resolve(fileName)
        val listSerializer = ListSerializer(serializer)
        val existing =
            if (resetIfLargerThanBytes != null && file.length() > resetIfLargerThanBytes) {
                rotateOversizedArtifact(file)
                mutableListOf()
            } else if (file.isFile) {
                json.decodeFromString(listSerializer, file.readText()).toMutableList()
            } else {
                mutableListOf()
            }
        existing += value
        if (maxEntries != null) {
            while (existing.size > maxEntries) {
                existing.removeAt(0)
            }
        }
        writeJson(file, listSerializer, existing)
    }

    private fun rotateOversizedArtifact(file: File) {
        val rotated = file.resolveSibling("${file.name}.legacy-${now().toEpochMilli()}")
        file.renameTo(rotated)
    }

    private fun <T> appendJsonLine(
        runId: String,
        fileName: String,
        serializer: KSerializer<T>,
        value: T,
        resetIfLargerThanBytes: Long? = null,
    ) {
        val file = runDirectory(runId).resolve(fileName)
        file.parentFile?.mkdirs()
        if (resetIfLargerThanBytes != null && file.length() > resetIfLargerThanBytes) {
            rotateOversizedArtifact(file)
        }
        file.appendText(lineJson.encodeToString(serializer, value))
        file.appendText("\n")
    }

    private fun <T> writeJson(file: File, serializer: KSerializer<T>, value: T) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(serializer, value))
    }

    private fun runDirectory(runId: String): File = rootDir.resolve(runId)

    private companion object {
        private const val METADATA_FILE_NAME = "metadata.json"
        private const val STATES_FILE_NAME = "states.json"
        private const val SCREEN_STATES_FILE_NAME = "screen-states.json"
        private const val LATEST_SCREEN_STATE_FILE_NAME = "latest-screen-state.json"
        private const val SCRIPT_EXECUTIONS_FILE_NAME = "script-executions.json"
        private const val HOST_CALLS_FILE_NAME = "host-calls.json"
        private const val PERF_EVENTS_FILE_NAME = "perf-events.jsonl"
        private const val SYSTEM_PROMPT_FILE_NAME = "system-prompt.txt"
        private const val MODEL_INPUT_FILE_NAME = "model-input.txt"
        private const val FINAL_OUTPUT_FILE_NAME = "final-output.txt"
        private const val MEMORY_REFLECTION_PROMPT_FILE_NAME = "memory-reflection-prompt.txt"
        private const val MEMORY_REFLECTION_OUTPUT_FILE_NAME = "memory-reflection-output.txt"
        private const val AGENT_MESSAGES_FILE_NAME = "agent-messages.json"
        private const val AGENT_EVENTS_FILE_NAME = "agent-events.json"
        private const val MAX_SCREEN_STATE_RECORDS = 80
        private const val MAX_SCREEN_STATES_FILE_BYTES = 1_000_000L
        private const val MAX_PERF_EVENTS_FILE_BYTES = 1_000_000L
    }
}

class ArtifactSessionLogStore(
    private val delegate: SessionLogStore,
    private val artifactStore: AgentRunArtifactStore,
    private val currentRunIdProvider: () -> String?,
) : SessionLogStore {
    override fun record(state: SessionUiState) {
        delegate.record(state)
        runCatching { artifactStore.recordState(state) }
    }

    override fun recordScreenState(screenState: ScreenState) {
        val started = System.nanoTime()
        delegate.recordScreenState(screenState)
        val delegateMs = elapsedMs(started)
        val runId = currentRunIdProvider() ?: return
        val artifactStarted = System.nanoTime()
        val result = runCatching { artifactStore.recordScreenState(runId, screenState) }
        val artifactMs = elapsedMs(artifactStarted)
        if (result.isSuccess) {
            runCatching {
                artifactStore.recordPerfEvent(
                    runId,
                    screenState.toScreenStatePerfEvent(
                        runId = runId,
                        delegateMs = delegateMs,
                        artifactMs = artifactMs,
                    ),
                )
            }
        }
    }

    override fun recordPerfEvent(event: PerfEventRecord) {
        delegate.recordPerfEvent(event)
        val runId = event.runId ?: currentRunIdProvider() ?: return
        runCatching { artifactStore.recordPerfEvent(runId, event.copy(runId = runId)) }
    }

    override fun recordScriptExecution(execution: ScriptExecutionRecord) {
        delegate.recordScriptExecution(execution)
        val runId = execution.runId ?: return
        runCatching { artifactStore.recordScriptExecution(runId, execution) }
    }

    override fun recordHostCall(hostCall: HostCallRecord) {
        delegate.recordHostCall(hostCall)
        val runId = hostCall.runId ?: return
        runCatching { artifactStore.recordHostCall(runId, hostCall) }
    }

    override fun recentScreenStates(): List<ScreenState> = delegate.recentScreenStates()

    override fun recentHostCalls(): List<HostCallRecord> = delegate.recentHostCalls()
}

private fun ScreenState.toScreenStatePerfEvent(runId: String, delegateMs: Long, artifactMs: Long): PerfEventRecord {
    val captureMetrics = metrics
    return PerfEventRecord(
        recordedAt = Instant.now().toString(),
        runId = runId,
        scope = PerfTelemetry.SCOPE_SCREEN_STATE,
        name = PerfTelemetry.RECORD_SCREEN_STATE,
        durationMs = delegateMs + artifactMs,
        attrs =
        buildMap {
            put("snapshotId", snapshotId)
            put("foregroundPackage", foregroundPackage)
            put("windowCount", windows.size.toString())
            if (captureMetrics != null) {
                put("captureTotalMs", captureMetrics.totalMs.toString())
                put("captureSelectRootMs", captureMetrics.selectRootMs.toString())
                put("captureBuildRootNodeMs", captureMetrics.buildRootNodeMs.toString())
                put("captureNodeCount", captureMetrics.nodeCount.toString())
                put("captureVisibleNodeCount", captureMetrics.visibleNodeCount.toString())
                put("captureMaxDepth", captureMetrics.maxDepth.toString())
                put("captureNodePropertyReadMs", captureMetrics.nodePropertyReadMs.toString())
                put("captureNodeChildAccessMs", captureMetrics.nodeChildAccessMs.toString())
                put("captureRootWindowEnumerationMs", captureMetrics.rootWindowEnumerationMs.toString())
                put("captureRootCandidateBuildMs", captureMetrics.rootCandidateBuildMs.toString())
                put("captureRootCandidateWindowRootMs", captureMetrics.rootCandidateWindowRootMs.toString())
                put("captureRootCandidateAnalysisMs", captureMetrics.rootCandidateAnalysisMs.toString())
                put("captureRootCandidateScoringMs", captureMetrics.rootCandidateScoringMs.toString())
                put("captureRootWindowPayloadMs", captureMetrics.rootWindowPayloadMs.toString())
                put("captureActiveRootFallbackMs", captureMetrics.activeRootFallbackMs.toString())
            }
        },
        phases =
        buildList {
            add(PerfPhaseRecord("delegateRecord", delegateMs))
            add(PerfPhaseRecord("artifactWrite", artifactMs))
            if (captureMetrics != null) {
                add(PerfPhaseRecord("capture.total", captureMetrics.totalMs))
                add(PerfPhaseRecord("capture.selectRoot", captureMetrics.selectRootMs))
                add(PerfPhaseRecord("capture.buildRootNode", captureMetrics.buildRootNodeMs))
                add(PerfPhaseRecord("capture.nodeBounds", captureMetrics.nodeBoundsMs))
                add(PerfPhaseRecord("capture.nodeLabel", captureMetrics.nodeLabelMs))
                add(PerfPhaseRecord("capture.nodeActions", captureMetrics.nodeActionsMs))
                add(PerfPhaseRecord("capture.nodeClickability", captureMetrics.nodeClickabilityMs))
                add(PerfPhaseRecord("capture.nodePropertyRead", captureMetrics.nodePropertyReadMs))
                add(PerfPhaseRecord("capture.nodeChildAccess", captureMetrics.nodeChildAccessMs))
                add(PerfPhaseRecord("capture.rootWindowEnumeration", captureMetrics.rootWindowEnumerationMs))
                add(PerfPhaseRecord("capture.rootCandidateBuild", captureMetrics.rootCandidateBuildMs))
                add(PerfPhaseRecord("capture.rootCandidateWindowRoot", captureMetrics.rootCandidateWindowRootMs))
                add(PerfPhaseRecord("capture.rootCandidateAnalysis", captureMetrics.rootCandidateAnalysisMs))
                add(PerfPhaseRecord("capture.rootCandidateScoring", captureMetrics.rootCandidateScoringMs))
                add(PerfPhaseRecord("capture.rootWindowPayload", captureMetrics.rootWindowPayloadMs))
                add(PerfPhaseRecord("capture.activeRootFallback", captureMetrics.activeRootFallbackMs))
            }
        },
    )
}

@Serializable
data class RunArtifactMetadata(
    val runId: String,
    val persistentSessionPath: String? = null,
    val persistentSessionId: String? = null,
    val userMessage: String,
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
    val runId: String,
    val status: String,
    val summaryLine: String,
    val lastKnownApp: String? = null,
    val accessibilityConnected: Boolean,
    val foregroundServiceRunning: Boolean,
    val timeline: List<String>,
)

@Serializable
data class ScreenStateArtifactRecord(
    val snapshotId: String,
    val capturedAt: String,
    val foregroundPackage: String,
    val selectedWindowReason: String? = null,
    val visibleNodeCount: Int,
    val actionableNodeCount: Int,
    val windowCount: Int,
    val selectedWindow: ScreenWindowPayload? = null,
    val canonicalText: String,
)

private fun ScreenState.toArtifactRecord(): ScreenStateArtifactRecord {
    val selectedWindow = windows.firstOrNull { it.selected }
    return ScreenStateArtifactRecord(
        snapshotId = snapshotId,
        capturedAt = capturedAt,
        foregroundPackage = foregroundPackage,
        selectedWindowReason = selectedWindowReason,
        visibleNodeCount = visibleNodes().size,
        actionableNodeCount = actionableNodes().size,
        windowCount = windows.size,
        selectedWindow = selectedWindow?.toPayload()?.copy(visibleText = selectedWindow.visibleText.take(MAX_WINDOW_VISIBLE_TEXT_RECORDS)),
        canonicalText = toCanonicalScreenText(),
    )
}

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
