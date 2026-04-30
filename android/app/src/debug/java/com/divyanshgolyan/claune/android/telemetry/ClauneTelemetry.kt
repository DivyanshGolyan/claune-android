package com.divyanshgolyan.claune.android.telemetry

import android.util.Log
import com.divyanshgolyan.claune.android.BuildConfig
import com.divyanshgolyan.claune.android.data.local.userFacingPromptText
import com.divyanshgolyan.claune.android.llm.CLAUNE_PROMPT_VERSION
import com.divyanshgolyan.claune.android.runtime.ModelTurnInput
import com.divyanshgolyan.claune.android.runtime.ModelTurnOutput
import com.divyanshgolyan.claune.android.runtime.toStatusMessageJson
import com.divyanshgolyan.claune.android.scripting.ScriptJson
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

object ClauneTelemetry {
    private const val TAG = "ClauneTelemetry"

    fun createRecorder(): ClauneTelemetryRecorder {
        if (!BuildConfig.DEBUG || !BuildConfig.CLAUNE_TELEMETRY_ENABLED) {
            Log.i(TAG, "Telemetry disabled.")
            return NoopClauneTelemetryRecorder
        }
        val apiUrl = nativeLangSmithApiUrl()
        if (
            apiUrl.isBlank() ||
            BuildConfig.LANGSMITH_PROJECT.isBlank() ||
            BuildConfig.LANGSMITH_API_KEY.isBlank()
        ) {
            Log.w(TAG, "Telemetry enabled but LangSmith config is incomplete.")
            return NoopClauneTelemetryRecorder
        }

        Log.i(TAG, "Configuring native LangSmith tracer apiUrl=$apiUrl project=${BuildConfig.LANGSMITH_PROJECT}")
        return NativeLangSmithClauneTelemetryRecorder(
            projectName = BuildConfig.LANGSMITH_PROJECT,
            transport =
            HttpLangSmithRunTransport(
                apiUrl = apiUrl,
                apiKey = BuildConfig.LANGSMITH_API_KEY,
            ),
        )
    }
}

internal class NativeLangSmithClauneTelemetryRecorder(
    private val projectName: String,
    private val transport: LangSmithRunTransport,
    private val idGenerator: () -> String = { UuidV7.next() },
) : ClauneTelemetryRecorder {
    override val recordsRawProviderPayloads: Boolean = true

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val traces = linkedMapOf<String, ActiveTrace>()

    init {
        scope.launch {
            for (block in queue) {
                runCatching { block() }
                    .onFailure { throwable ->
                        Log.w("ClauneTelemetry", "LangSmith telemetry failed: ${throwable.message}", throwable)
                    }
            }
        }
    }

    override fun startRun(input: ModelTurnInput, provider: String, model: String, systemPrompt: String, modelInput: String) {
        enqueue {
            val rootId = idGenerator()
            val threadId = input.threadId()
            val trace =
                ActiveTrace(
                    input = input,
                    rootRunId = rootId,
                    threadId = threadId,
                    provider = provider,
                    model = model,
                )
            traces[input.runId] = trace
            transport.postRun(
                langSmithRunPost(
                    id = rootId,
                    name = deriveTraceTitle(input.userMessage),
                    runType = "chain",
                    inputs =
                    buildJsonObject {
                        put("user_message", userFacingPromptText(input.userMessage))
                        put("foreground_package", input.screenObservation.foregroundPackage)
                        input.screenObservation.baselineSnapshotId?.let { put("baseline_snapshot_id", it) }
                        put("current_snapshot_id", input.screenObservation.currentSnapshotId)
                        input.persistentSessionId?.let { put("persistent_session_id", it) }
                        input.persistentSessionPath?.let { put("persistent_session_path", it) }
                        put("provider", provider)
                        put("model", model)
                        put("prompt_version", CLAUNE_PROMPT_VERSION)
                        put("system_prompt_sha256", sha256(systemPrompt))
                        put("model_input_sha256", sha256(modelInput))
                        put(
                            "artifact_refs",
                            buildJsonObject {
                                put("system_prompt", "files/agent-runs/${input.runId}/system-prompt.md")
                                put("model_input", "files/agent-runs/${input.runId}/model-input.md")
                            },
                        )
                    },
                    projectName = projectName,
                    metadata = commonMetadata(trace, phase = ClauneTelemetryPhase.ROOT, spanKind = "agent.operation"),
                    tags = trace.tags(ClauneTelemetryPhase.ROOT),
                ),
            )
        }
    }

    override fun recordProviderPayload(context: ClauneTelemetryContext, payloadKind: String, request: JsonElement) {
        enqueue {
            val input = context.input
            val phase = context.phase
            val provider = context.provider
            val model = context.model
            val trace = traces[input.runId] ?: return@enqueue
            trace.provider = provider
            trace.model = model
            val messages = inputMessagesForLangSmith(request)
            val deltaMessages = deltaMessages(trace.lastProviderInputMessages, messages).ifEmptyJsonArray {
                trace.pendingDeltaMessages.ifEmptyJsonArray { messages }
            }
            trace.lastProviderInputMessages = messages
            trace.pendingDeltaMessages = buildJsonArray {}

            val step =
                trace.startStepIfNeeded(
                    phase = phase,
                    provider = provider,
                    model = model,
                    newEvents = deltaMessages,
                )
            val llmRunId = idGenerator()
            trace.llmCallCount += 1
            trace.openLlm = OpenLlmRun(id = llmRunId)
            step.hasLlm = true
            transport.postRun(
                langSmithRunPost(
                    id = llmRunId,
                    name = "${langSmithProvider(provider)} $model",
                    runType = "llm",
                    parentRunId = step.id,
                    projectName = projectName,
                    inputs =
                    buildJsonObject {
                        put("messages", messages)
                        put("delta_messages", deltaMessages)
                        extractToolDefinitions(request)?.let { put("tools", it) }
                        val modelParams = extractInvocationParams(request)
                        if (modelParams.isNotEmpty()) {
                            put("model_params", modelParams)
                        }
                        put("raw_request", request)
                    },
                    metadata =
                    commonMetadata(
                        trace = trace,
                        phase = phase,
                        spanKind = "llm.call",
                        sequenceIndex = step.sequenceIndex,
                        provider = provider,
                        model = model,
                    ) + buildJsonObject {
                        put("payload_kind", payloadKind)
                        put("ls_provider", langSmithProvider(provider))
                        put("ls_model_name", model)
                        put("full_context_message_count", messages.size)
                        put("delta_message_count", deltaMessages.size)
                    },
                    tags = trace.tags(phase),
                ),
            )
        }
    }

    override fun recordProviderResponse(context: ClauneTelemetryContext, status: Int, headers: Map<String, String>) {
        enqueue {
            val input = context.input
            val provider = context.provider
            val model = context.model
            val trace = traces[input.runId] ?: return@enqueue
            val llm = trace.openLlm ?: return@enqueue
            llm.response =
                buildJsonObject {
                    put("status", status)
                    put("header_names", headers.keys.sorted().joinToString(","))
                    put(
                        "headers",
                        buildJsonObject {
                            headers.toSortedMap().forEach { (name, value) ->
                                if (name.isSafeResponseHeader()) {
                                    put(name, value)
                                }
                            }
                        },
                    )
                }
            trace.provider = provider
            trace.model = model
        }
    }

    override fun recordProviderMessage(context: ClauneTelemetryContext, message: JsonObject) {
        enqueue {
            val input = context.input
            val provider = context.provider
            val model = context.model
            val trace = traces[input.runId] ?: return@enqueue
            val llm = trace.openLlm ?: return@enqueue
            val outputMessages = outputMessagesForLangSmith(message)
            trace.pendingDeltaMessages = trace.pendingDeltaMessages + outputMessages
            val outputs =
                buildJsonObject {
                    put("messages", outputMessages)
                    usageMetadataForLangSmith(message)?.let { put("usage_metadata", it) }
                    llm.response?.let { put("provider_response", it) }
                    put("raw_message", message)
                }
            transport.patchRun(llm.id, langSmithRunPatch(outputs = outputs))
            trace.openLlm = null
            val step = trace.currentStep
            if (step != null && !message.hasToolCall()) {
                transport.patchRun(
                    step.id,
                    langSmithRunPatch(
                        outputs =
                        buildJsonObject {
                            put("action_type", "assistant_message")
                            put("messages", outputMessages)
                        },
                    ),
                )
                step.closed = true
            }
            trace.provider = provider
            trace.model = model
        }
    }

    override fun recordToolCall(context: ClauneTelemetryContext, toolCallId: String, toolName: String, arguments: JsonElement) {
        enqueue {
            val input = context.input
            val phase = context.phase
            val provider = context.provider
            val model = context.model
            val trace = traces[input.runId] ?: return@enqueue
            val step =
                trace.currentStep?.takeUnless { it.closed }
                    ?: trace.startStepIfNeeded(
                        phase = phase,
                        provider = provider,
                        model = model,
                        newEvents =
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("role", "assistant")
                                    put(
                                        "content",
                                        buildJsonArray {
                                            add(
                                                buildJsonObject {
                                                    put("type", "tool_call")
                                                    put("id", toolCallId)
                                                    put("name", toolName)
                                                    put("args", arguments)
                                                },
                                            )
                                        },
                                    )
                                },
                            )
                        },
                    )
            val toolRunId = idGenerator()
            trace.toolCallCount += 1
            trace.openTools[toolCallId] = toolRunId
            transport.postRun(
                langSmithRunPost(
                    id = toolRunId,
                    name = toolName.ifBlank { "tool" },
                    runType = "tool",
                    parentRunId = step.id,
                    projectName = projectName,
                    inputs =
                    buildJsonObject {
                        put("tool_call_id", toolCallId)
                        put("name", toolName)
                        put("arguments", arguments)
                    },
                    metadata =
                    commonMetadata(
                        trace = trace,
                        phase = phase,
                        spanKind = "tool.call",
                        sequenceIndex = step.sequenceIndex,
                        provider = provider,
                        model = model,
                    ) + buildJsonObject {
                        put("tool_call_id", toolCallId)
                        put("tool_name", toolName)
                    },
                    tags = trace.tags(phase),
                ),
            )
            trace.provider = provider
            trace.model = model
        }
    }

    override fun recordToolResult(
        context: ClauneTelemetryContext,
        toolCallId: String,
        toolName: String,
        isError: Boolean,
        result: JsonObject,
    ) {
        enqueue {
            val input = context.input
            val provider = context.provider
            val model = context.model
            val trace = traces[input.runId] ?: return@enqueue
            val toolRunId = trace.openTools.remove(toolCallId)
            val outputs =
                buildJsonObject {
                    put("tool_call_id", toolCallId)
                    put("name", toolName)
                    put("is_error", isError)
                    put("result", result)
                }
            if (toolRunId != null) {
                transport.patchRun(
                    toolRunId,
                    langSmithRunPatch(
                        outputs = outputs,
                        error = if (isError) result.extractTextValue() else null,
                    ),
                )
            }
            val toolMessage = toolResultMessage(toolCallId, toolName, isError, result)
            trace.pendingDeltaMessages = trace.pendingDeltaMessages + JsonArray(listOf(toolMessage))
            trace.currentStep?.let { step ->
                transport.patchRun(
                    step.id,
                    langSmithRunPatch(
                        outputs =
                        buildJsonObject {
                            put("action_type", if (toolName == "finish_run") "final_answer" else "tool_call")
                            put(
                                "tool_calls",
                                buildJsonArray {
                                    add(
                                        buildJsonObject {
                                            put("tool_call_id", toolCallId)
                                            put("name", toolName)
                                            put("is_error", isError)
                                        },
                                    )
                                },
                            )
                        },
                    ),
                )
                step.closed = true
            }
            trace.provider = provider
            trace.model = model
        }
    }

    override fun recordCompactionStart(context: ClauneTelemetryContext, reason: String) {
        enqueue {
            val input = context.input
            val phase = context.phase
            val trace = traces[input.runId] ?: return@enqueue
            val runId = idGenerator()
            trace.openCompactions[phase.wireName] = runId
            transport.postRun(
                langSmithRunPost(
                    id = runId,
                    name = "Compaction: $reason",
                    runType = "chain",
                    parentRunId = trace.rootRunId,
                    projectName = projectName,
                    inputs =
                    buildJsonObject {
                        put("reason", reason)
                        put("phase", phase.wireName)
                    },
                    metadata = commonMetadata(trace, phase = phase, spanKind = "compaction"),
                    tags = trace.tags(phase),
                ),
            )
        }
    }

    override fun recordCompactionEnd(
        context: ClauneTelemetryContext,
        reason: String,
        aborted: Boolean,
        willRetry: Boolean,
        hasResult: Boolean,
        errorMessage: String?,
    ) {
        enqueue {
            val input = context.input
            val phase = context.phase
            val trace = traces[input.runId] ?: return@enqueue
            val runId = trace.openCompactions.remove(phase.wireName) ?: return@enqueue
            transport.patchRun(
                runId,
                langSmithRunPatch(
                    outputs =
                    buildJsonObject {
                        put("reason", reason)
                        put("aborted", aborted)
                        put("will_retry", willRetry)
                        put("has_result", hasResult)
                        errorMessage?.let { put("error_message", it) }
                    },
                    error = errorMessage,
                ),
            )
        }
    }

    override fun endRun(runId: String, output: ModelTurnOutput?) {
        enqueue {
            val trace = traces.remove(runId) ?: return@enqueue
            trace.openLlm?.let { llm ->
                transport.patchRun(
                    llm.id,
                    langSmithRunPatch(
                        outputs =
                        buildJsonObject {
                            put("status", "unknown")
                        },
                        error = "LLM run ended without an assistant message.",
                    ),
                )
            }
            trace.openTools.values.forEach { toolRunId ->
                transport.patchRun(
                    toolRunId,
                    langSmithRunPatch(
                        outputs =
                        buildJsonObject {
                            put("status", "unknown")
                        },
                        error = "Tool run ended without a result.",
                    ),
                )
            }
            trace.currentStep?.takeUnless { it.closed }?.let { step ->
                transport.patchRun(
                    step.id,
                    langSmithRunPatch(
                        outputs =
                        buildJsonObject {
                            put("action_type", "ended")
                        },
                    ),
                )
            }
            transport.patchRun(
                trace.rootRunId,
                langSmithRunPatch(
                    outputs =
                    buildJsonObject {
                        val outputJson = output?.toStatusMessageJson()
                        put("status", outputJson?.get("status") ?: JsonPrimitive("unknown"))
                        outputJson?.get("message")?.let { put("final_answer", it) }
                        put("llm_call_count", trace.llmCallCount)
                        put("tool_call_count", trace.toolCallCount)
                    },
                ),
            )
        }
    }

    internal suspend fun flushForTests() {
        val flushed = CompletableDeferred<Unit>()
        enqueue {
            flushed.complete(Unit)
        }
        flushed.await()
    }

    private suspend fun ActiveTrace.startStepIfNeeded(
        phase: ClauneTelemetryPhase,
        provider: String,
        model: String,
        newEvents: JsonArray,
    ): OpenStepRun {
        val existing = currentStep
        if (existing != null && !existing.closed && !existing.hasLlm) {
            return existing
        }
        if (existing != null && !existing.closed && existing.hasLlm) {
            existing.closed = true
        }
        val sequenceIndex = ++stepSequence
        val stepId = idGenerator()
        val step =
            OpenStepRun(
                id = stepId,
                sequenceIndex = sequenceIndex,
            )
        currentStep = step
        postStep(this, step, phase, provider, model, newEvents)
        return step
    }

    private suspend fun postStep(
        trace: ActiveTrace,
        step: OpenStepRun,
        phase: ClauneTelemetryPhase,
        provider: String,
        model: String,
        newEvents: JsonArray,
    ) {
        transport.postRun(
            langSmithRunPost(
                id = step.id,
                name = if (phase == ClauneTelemetryPhase.MEMORY_REFLECTION) {
                    "Memory reflection step ${step.sequenceIndex}"
                } else {
                    "Agent step ${step.sequenceIndex}"
                },
                runType = "chain",
                parentRunId = trace.rootRunId,
                projectName = projectName,
                inputs =
                buildJsonObject {
                    put("new_events_since_previous_step", newEvents)
                },
                metadata =
                commonMetadata(
                    trace = trace,
                    phase = phase,
                    spanKind = "agent.step",
                    sequenceIndex = step.sequenceIndex,
                    provider = provider,
                    model = model,
                ) + buildJsonObject {
                    put("display_summary", stepSummary(newEvents))
                },
                tags = trace.tags(phase),
            ),
        )
    }

    private fun enqueue(block: suspend () -> Unit) {
        val result = queue.trySend(block)
        if (result.isFailure) {
            Log.w("ClauneTelemetry", "Dropped LangSmith telemetry event because the queue is closed.")
        }
    }
}

internal interface LangSmithRunTransport {
    suspend fun postRun(body: JsonObject)

    suspend fun patchRun(runId: String, body: JsonObject)
}

private class HttpLangSmithRunTransport(private val apiUrl: String, private val apiKey: String) : LangSmithRunTransport {
    override suspend fun postRun(body: JsonObject) {
        send("POST", "runs", body)
    }

    override suspend fun patchRun(runId: String, body: JsonObject) {
        send("PATCH", "runs/$runId", body)
    }

    private suspend fun send(method: String, path: String, body: JsonObject) = withContext(Dispatchers.IO) {
        val url = URL("${apiUrl.trimEnd('/')}/$path")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-api-key", apiKey)
        }
        try {
            connection.outputStream.bufferedWriter().use { writer ->
                writer.write(ScriptJson.encodeElement(body))
            }
            val status = connection.responseCode
            if (status >= 300) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("LangSmith $method /$path failed with HTTP $status: ${errorBody.take(500)}")
            }
        } finally {
            connection.disconnect()
        }
    }
}

private data class ActiveTrace(
    val input: ModelTurnInput,
    val rootRunId: String,
    val threadId: String,
    var provider: String,
    var model: String,
    var stepSequence: Int = 0,
    var currentStep: OpenStepRun? = null,
    var openLlm: OpenLlmRun? = null,
    var lastProviderInputMessages: JsonArray? = null,
    var pendingDeltaMessages: JsonArray = buildJsonArray {},
    val openTools: MutableMap<String, String> = linkedMapOf(),
    val openCompactions: MutableMap<String, String> = linkedMapOf(),
    var llmCallCount: Int = 0,
    var toolCallCount: Int = 0,
)

private data class OpenStepRun(val id: String, val sequenceIndex: Int, var hasLlm: Boolean = false, var closed: Boolean = false)

private data class OpenLlmRun(val id: String, var response: JsonObject? = null)

private fun commonMetadata(
    trace: ActiveTrace,
    phase: ClauneTelemetryPhase,
    spanKind: String,
    sequenceIndex: Int? = null,
    provider: String = trace.provider,
    model: String = trace.model,
): JsonObject = buildJsonObject {
    put("thread_id", trace.threadId)
    put("claune_run_id", trace.input.runId)
    trace.input.persistentSessionId?.let { put("persistent_session_id", it) }
    put("phase", phase.wireName)
    put("span_kind", spanKind)
    sequenceIndex?.let { put("sequence_index", it) }
    put("provider", provider)
    put("model", model)
    put("prompt_version", CLAUNE_PROMPT_VERSION)
    put("foreground_package", trace.input.screenObservation.foregroundPackage)
    trace.input.screenObservation.baselineSnapshotId?.let { put("baseline_snapshot_id", it) }
    put("current_snapshot_id", trace.input.screenObservation.currentSnapshotId)
    put("app_build_type", BuildConfig.BUILD_TYPE)
    put("app_version_name", BuildConfig.VERSION_NAME)
    put("app_version_code", BuildConfig.VERSION_CODE)
}

private fun langSmithRunPost(
    id: String,
    name: String,
    runType: String,
    inputs: JsonObject,
    metadata: JsonObject,
    tags: List<String>,
    parentRunId: String? = null,
    projectName: String = BuildConfig.LANGSMITH_PROJECT,
): JsonObject = buildJsonObject {
    put("id", id)
    put("name", name)
    put("run_type", runType)
    put("inputs", inputs)
    put("start_time", Instant.now().toString())
    put("session_name", projectName)
    parentRunId?.let { put("parent_run_id", it) }
    put("extra", buildJsonObject { put("metadata", metadata) })
    put(
        "tags",
        buildJsonArray {
            tags.distinct().forEach { add(JsonPrimitive(it)) }
        },
    )
}

private fun langSmithRunPatch(outputs: JsonObject, error: String? = null): JsonObject = buildJsonObject {
    put("outputs", outputs)
    put("end_time", Instant.now().toString())
    error?.takeIf(String::isNotBlank)?.let { put("error", it) }
}

internal fun deriveTraceTitle(userMessage: String): String {
    val singleLine = userFacingPromptText(userMessage).replace(Regex("\\s+"), " ")
    return singleLine.take(100).ifBlank { "Claune run" }
}

internal fun inputMessagesForLangSmith(requestPayload: JsonElement): JsonArray {
    val requestObject = requestPayload as? JsonObject
    val messages = requestObject?.get("input") ?: requestObject?.get("messages") ?: requestPayload
    return normalizeMessages(messages)
}

internal fun outputMessagesForLangSmith(messagePayload: JsonElement): JsonArray = normalizeMessages(messagePayload)

internal fun extractInvocationParams(requestPayload: JsonElement): JsonObject {
    val requestObject = requestPayload as? JsonObject ?: return buildJsonObject {}
    return buildJsonObject {
        listOf(
            "temperature",
            "top_p",
            "topP",
            "max_tokens",
            "maxTokens",
            "stop",
            "tool_choice",
            "toolChoice",
            "parallel_tool_calls",
            "reasoning",
            "reasoning_effort",
            "thinking",
            "thinkingBudget",
            "thinkingBudgets",
        ).forEach { name ->
            requestObject[name]?.let { put(name, it) }
        }
    }
}

internal fun usageMetadataForLangSmith(messagePayload: JsonObject): JsonObject? {
    val usage = messagePayload["usage"] as? JsonObject ?: return null
    return buildJsonObject {
        usage["input"]?.jsonLongOrNull()?.let { put("input_tokens", it) }
        usage["output"]?.jsonLongOrNull()?.let { put("output_tokens", it) }
        usage["totalTokens"]?.jsonLongOrNull()?.let { put("total_tokens", it) }
        val inputDetails =
            buildJsonObject {
                usage["cacheRead"]?.jsonLongOrNull()?.let { put("cache_read", it) }
                usage["cacheWrite"]?.jsonLongOrNull()?.let { put("cache_creation", it) }
            }
        if (inputDetails.isNotEmpty()) {
            put("input_token_details", inputDetails)
        }
        (usage["cost"] as? JsonObject)?.let { cost ->
            cost["input"]?.jsonDoubleOrNull()?.let { put("input_cost", it) }
            cost["output"]?.jsonDoubleOrNull()?.let { put("output_cost", it) }
            cost["total"]?.jsonDoubleOrNull()?.let { put("total_cost", it) }
        }
    }.takeUnless { it.isEmpty() }
}

private fun normalizeMessages(messages: JsonElement): JsonArray {
    val source = when (messages) {
        is JsonArray -> messages
        is JsonObject -> JsonArray(listOf(messages))
        is JsonPrimitive ->
            JsonArray(
                listOf(
                    buildJsonObject {
                        put("role", "user")
                        put("content", messages.contentOrNull.orEmpty())
                    },
                ),
            )
    }
    val normalizedMessages = mutableListOf<JsonObject>()
    source.forEach { message ->
        normalizeMessage(message)?.let { normalized ->
            appendNormalizedMessage(normalizedMessages, normalized)
        }
    }
    return JsonArray(normalizedMessages)
}

private fun normalizeMessage(message: JsonElement): JsonObject? {
    val obj = message as? JsonObject
        ?: return buildJsonObject {
            put("role", "user")
            put(
                "content",
                buildJsonArray {
                    add(textPart(message.toString()))
                },
            )
        }
    obj["type"]?.jsonStringOrNull()?.let { type ->
        if (obj["role"] == null) {
            return normalizeResponsesItem(type, obj)
        }
    }
    val role = normalizedRole(obj["role"]?.jsonStringOrNull())
    return buildJsonObject {
        put("role", role)
        obj["toolCallId"]?.let { put("tool_call_id", it) }
        obj["tool_call_id"]?.let { put("tool_call_id", it) }
        obj["toolName"]?.let { put("name", it) }
        obj["name"]?.let { put("name", it) }
        put("content", normalizeContentParts(obj["content"], role))
    }
}

private fun appendNormalizedMessage(messages: MutableList<JsonObject>, message: JsonObject) {
    if (message.hasEmptyContent()) {
        return
    }
    val role = message["role"]?.jsonStringOrNull()
    val last = messages.lastOrNull()
    if (role == "assistant" && last?.get("role")?.jsonStringOrNull() == "assistant") {
        messages[messages.lastIndex] = last.withAppendedContent(message)
    } else {
        messages.add(message)
    }
}

private fun JsonObject.withAppendedContent(message: JsonObject): JsonObject = buildJsonObject {
    this@withAppendedContent.forEach { (key, value) ->
        if (key != "content") {
            put(key, value)
        }
    }
    put(
        "content",
        JsonArray(
            ((this@withAppendedContent["content"] as? JsonArray)?.toList().orEmpty()) +
                ((message["content"] as? JsonArray)?.toList().orEmpty()),
        ),
    )
}

private fun JsonObject.hasEmptyContent(): Boolean {
    val content = this["content"] as? JsonArray ?: return false
    return content.isEmpty() && !hasToolCall()
}

private fun normalizeResponsesItem(type: String, item: JsonObject): JsonObject? = when (type) {
    "reasoning" ->
        reasoningText(item)?.let { text ->
            buildJsonObject {
                put("role", "assistant")
                put(
                    "content",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("type", "reasoning")
                                put("text", text)
                            },
                        )
                    },
                )
            }
        }
    "function_call" ->
        buildJsonObject {
            put("role", "assistant")
            put(
                "content",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "tool_call")
                            put("id", item["call_id"] ?: item["id"] ?: JsonPrimitive(""))
                            put("name", item["name"]?.jsonStringOrNull().orEmpty())
                            put("args", parseJsonObjectOrString(item["arguments"]))
                        },
                    )
                },
            )
        }
    "function_call_output" ->
        buildJsonObject {
            put("role", "tool")
            item["call_id"]?.let { put("tool_call_id", it) }
            put(
                "content",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "text")
                            put("text", item["output"]?.jsonStringOrNull() ?: item["output"]?.toString().orEmpty())
                        },
                    )
                },
            )
        }
    "message" ->
        buildJsonObject {
            put("role", normalizedRole(item["role"]?.jsonStringOrNull()))
            put("content", normalizeContentParts(item["content"], normalizedRole(item["role"]?.jsonStringOrNull())))
        }
    else ->
        buildJsonObject {
            put("role", "assistant")
            put(
                "content",
                buildJsonArray {
                    add(textPart(item.toString()))
                },
            )
        }
}

private fun reasoningText(item: JsonObject): String? {
    val summary = item["summary"] as? JsonArray
    val summaryText =
        summary
            ?.mapNotNull { part ->
                val partObject = part as? JsonObject
                partObject?.get("text")?.jsonStringOrNull()
            }
            ?.joinToString("\n")
            ?.takeIf(String::isNotBlank)
    return summaryText ?: item["text"]?.jsonStringOrNull()
}

private fun parseJsonObjectOrString(value: JsonElement?): JsonElement {
    val text = value?.jsonStringOrNull() ?: return value ?: buildJsonObject {}
    return runCatching { ScriptJson.codec.parseToJsonElement(text) }
        .getOrElse { JsonPrimitive(text) }
}

private fun normalizeContentParts(content: JsonElement?, role: String): JsonArray {
    if (content == null || content is JsonNull) {
        return buildJsonArray {}
    }
    if (content is JsonPrimitive) {
        return buildJsonArray {
            add(textPart(content.contentOrNull.orEmpty()))
        }
    }
    if (content is JsonObject) {
        return buildJsonArray {
            add(normalizeContentPart(content, role))
        }
    }
    val array = content as? JsonArray ?: return buildJsonArray { add(textPart(content.toString())) }
    return buildJsonArray {
        array.forEach { part ->
            val partObject = part as? JsonObject
            if (partObject == null) {
                add(textPart(part.toString()))
            } else {
                add(normalizeContentPart(partObject, role))
            }
        }
    }
}

private fun normalizeContentPart(part: JsonObject, role: String): JsonObject {
    val type = part["type"]?.jsonStringOrNull()
    return when (type) {
        "text", "input_text", "output_text" ->
            textPart(part["text"]?.jsonStringOrNull() ?: part["value"]?.jsonStringOrNull().orEmpty())
        "thinking", "reasoning" ->
            buildJsonObject {
                put("type", "reasoning")
                put("text", part["thinking"]?.jsonStringOrNull() ?: part["text"]?.jsonStringOrNull().orEmpty())
            }
        "tool_call" ->
            buildJsonObject {
                put("type", "tool_call")
                part["id"]?.let { put("id", it) }
                put("name", part["name"]?.jsonStringOrNull().orEmpty())
                put("args", part["args"] ?: part["arguments"] ?: buildJsonObject {})
            }
        "server_tool_call" ->
            buildJsonObject {
                put("type", "server_tool_call")
                part["id"]?.let { put("id", it) }
                put("name", part["name"]?.jsonStringOrNull().orEmpty())
                put("args", part["args"] ?: part["arguments"] ?: buildJsonObject {})
            }
        "server_tool_result" ->
            buildJsonObject {
                put("type", "server_tool_result")
                part["tool_call_id"]?.let { put("tool_call_id", it) }
                put("status", part["status"] ?: JsonPrimitive("success"))
                part["output"]?.let { put("output", it) }
            }
        "image", "input_image" ->
            buildJsonObject {
                put("type", "image")
                part["url"]?.let { put("url", it) }
                part["data"]?.let { put("base64", it) }
                part["base64"]?.let { put("base64", it) }
                part["mimeType"]?.let { put("mime_type", it) }
                part["mime_type"]?.let { put("mime_type", it) }
            }
        else -> {
            if (role == "tool") {
                textPart(part.toString())
            } else {
                textPart(part["text"]?.jsonStringOrNull() ?: part["value"]?.jsonStringOrNull() ?: part.toString())
            }
        }
    }
}

private fun textPart(text: String): JsonObject = buildJsonObject {
    put("type", "text")
    put("text", text)
}

private fun toolResultMessage(toolCallId: String, toolName: String, isError: Boolean, result: JsonObject): JsonObject = buildJsonObject {
    put("role", "tool")
    put("tool_call_id", toolCallId)
    put("name", toolName)
    put(
        "content",
        buildJsonArray {
            add(
                buildJsonObject {
                    put("type", "text")
                    put(
                        "text",
                        buildJsonObject {
                            put("is_error", isError)
                            put("result", result)
                        }.toString(),
                    )
                },
            )
        },
    )
}

private fun extractToolDefinitions(requestPayload: JsonElement): JsonElement? {
    val requestObject = requestPayload as? JsonObject ?: return null
    return requestObject["tools"] ?: requestObject["toolDefinitions"]
}

private fun deltaMessages(previous: JsonArray?, current: JsonArray): JsonArray {
    if (previous == null || previous.size > current.size) {
        return current
    }
    for (index in previous.indices) {
        if (previous[index] != current[index]) {
            return current
        }
    }
    return JsonArray(current.drop(previous.size))
}

private fun JsonArray.ifEmptyJsonArray(fallback: () -> JsonArray): JsonArray = if (isEmpty()) fallback() else this

private operator fun JsonArray.plus(other: JsonArray): JsonArray = JsonArray(toList() + other.toList())

private operator fun JsonObject.plus(other: JsonObject): JsonObject = JsonObject(toMap() + other.toMap())

private fun JsonObject.hasToolCall(): Boolean = (this["content"] as? JsonArray)?.any { part ->
    (part as? JsonObject)?.get("type")?.jsonStringOrNull() == "tool_call"
} == true

private fun stepSummary(events: JsonArray): String {
    val roles = events.mapNotNull { (it as? JsonObject)?.get("role")?.jsonStringOrNull() }.joinToString(",")
    return when {
        events.isEmpty() -> "No new events."
        roles.isBlank() -> "New context events: ${events.size}."
        else -> "New context events: ${events.size} [$roles]."
    }
}

private fun ActiveTrace.tags(phase: ClauneTelemetryPhase): List<String> =
    listOf("claune", langSmithProvider(provider), model, phase.wireName).filter(String::isNotBlank)

private fun ModelTurnInput.threadId(): String = persistentSessionId?.takeIf(String::isNotBlank) ?: runId

private fun normalizedRole(role: String?): String = when (role) {
    "tool_result" -> "tool"
    null, "" -> "user"
    else -> role
}

private fun langSmithProvider(provider: String): String = when {
    provider.contains("openai", ignoreCase = true) -> "openai"
    provider.contains("anthropic", ignoreCase = true) -> "anthropic"
    provider.contains("gemini", ignoreCase = true) || provider.contains("google", ignoreCase = true) -> "google_genai"
    else -> provider.ifBlank { "unknown" }
}

private fun nativeLangSmithApiUrl(): String {
    if (BuildConfig.LANGSMITH_API_URL.isNotBlank()) {
        return BuildConfig.LANGSMITH_API_URL
    }
    val endpoint = BuildConfig.LANGSMITH_ENDPOINT
    return when {
        endpoint.endsWith("/otel/v1/traces") -> endpoint.removeSuffix("/otel/v1/traces")
        endpoint.isNotBlank() -> endpoint
        else -> "https://api.smith.langchain.com"
    }
}

private fun sha256(value: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return bytes.joinToString(separator = "") { "%02x".format(it) }
}

private fun String.isSafeResponseHeader(): Boolean {
    val normalized = lowercase()
    return normalized in setOf(
        "content-type",
        "x-request-id",
        "request-id",
        "openai-processing-ms",
        "anthropic-ratelimit-requests-limit",
        "anthropic-ratelimit-tokens-limit",
    )
}

private fun JsonElement.jsonStringOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull

private fun JsonElement.jsonLongOrNull(): Long? = (this as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

private fun JsonElement.jsonDoubleOrNull(): Double? = (this as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

private fun JsonElement.extractTextValue(): String? {
    val primitive = this as? JsonPrimitive
    if (primitive != null) {
        return primitive.contentOrNull
    }
    val obj = this as? JsonObject ?: return null
    return obj["message"]?.jsonStringOrNull()
        ?: obj["text"]?.jsonStringOrNull()
        ?: obj["value"]?.jsonStringOrNull()
        ?: obj["content"]?.toString()
}

private object UuidV7 {
    private val random = SecureRandom()

    fun next(): String {
        val timestamp = System.currentTimeMillis() and 0x0000_FFFF_FFFF_FFFFL
        val random12 = random.nextInt(0x1000).toLong()
        val msb = (timestamp shl 16) or 0x7000L or random12
        val lsb = (random.nextLong() and 0x3FFF_FFFF_FFFF_FFFFL) or Long.MIN_VALUE
        return UUID(msb, lsb).toString()
    }
}
