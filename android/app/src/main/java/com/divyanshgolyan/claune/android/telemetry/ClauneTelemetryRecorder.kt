package com.divyanshgolyan.claune.android.telemetry

import com.divyanshgolyan.claune.android.runtime.ModelTurnInput
import com.divyanshgolyan.claune.android.runtime.ModelTurnOutput
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

data class ClauneTelemetryContext(val input: ModelTurnInput, val phase: ClauneTelemetryPhase, val provider: String, val model: String)

interface ClauneTelemetryRecorder {
    val recordsRawProviderPayloads: Boolean

    fun startRun(input: ModelTurnInput, provider: String, model: String, systemPrompt: String, modelInput: String)

    fun recordToolCall(context: ClauneTelemetryContext, toolCallId: String, toolName: String, arguments: JsonElement)

    fun recordToolResult(context: ClauneTelemetryContext, toolCallId: String, toolName: String, isError: Boolean, result: JsonObject)

    fun recordProviderPayload(context: ClauneTelemetryContext, payloadKind: String, request: JsonElement)

    fun recordProviderResponse(context: ClauneTelemetryContext, status: Int, headers: Map<String, String>)

    fun recordProviderMessage(context: ClauneTelemetryContext, message: JsonObject)

    fun recordCompactionStart(context: ClauneTelemetryContext, reason: String)

    fun recordCompactionEnd(
        context: ClauneTelemetryContext,
        reason: String,
        aborted: Boolean,
        willRetry: Boolean,
        hasResult: Boolean,
        errorMessage: String?,
    )

    fun endRun(runId: String, output: ModelTurnOutput?)
}

object NoopClauneTelemetryRecorder : ClauneTelemetryRecorder {
    override val recordsRawProviderPayloads: Boolean = false

    override fun startRun(input: ModelTurnInput, provider: String, model: String, systemPrompt: String, modelInput: String) = Unit

    override fun recordToolCall(context: ClauneTelemetryContext, toolCallId: String, toolName: String, arguments: JsonElement) = Unit

    override fun recordToolResult(
        context: ClauneTelemetryContext,
        toolCallId: String,
        toolName: String,
        isError: Boolean,
        result: JsonObject,
    ) = Unit

    override fun recordProviderPayload(context: ClauneTelemetryContext, payloadKind: String, request: JsonElement) = Unit

    override fun recordProviderResponse(context: ClauneTelemetryContext, status: Int, headers: Map<String, String>) = Unit

    override fun recordProviderMessage(context: ClauneTelemetryContext, message: JsonObject) = Unit

    override fun recordCompactionStart(context: ClauneTelemetryContext, reason: String) = Unit

    override fun recordCompactionEnd(
        context: ClauneTelemetryContext,
        reason: String,
        aborted: Boolean,
        willRetry: Boolean,
        hasResult: Boolean,
        errorMessage: String?,
    ) = Unit

    override fun endRun(runId: String, output: ModelTurnOutput?) = Unit
}

enum class ClauneTelemetryEventType(val wireName: String) {
    TOOL_CALL("tool_call"),
    TOOL_RESULT("tool_result"),
    PROVIDER_PAYLOAD("provider_payload"),
    PROVIDER_RESPONSE("provider_response"),
    PROVIDER_MESSAGE("provider_message"),
    COMPACTION_START("compaction_start"),
    COMPACTION_END("compaction_end"),
}

enum class ClauneTelemetryPhase(val wireName: String) {
    ROOT("root"),
    MAIN("main"),
    MEMORY_REFLECTION("memory_reflection"),
}
