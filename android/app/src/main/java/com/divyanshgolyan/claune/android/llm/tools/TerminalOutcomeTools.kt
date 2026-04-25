package com.divyanshgolyan.claune.android.llm.tools

import com.divyanshgolyan.claune.android.runtime.ModelTurnOutput
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import pi.agent.core.AgentToolResult
import pi.ai.core.TextContent

internal class TerminalOutcomeRecorder {
    var outcome: ModelTurnOutput? = null
        private set

    fun clear() {
        outcome = null
    }

    fun record(next: ModelTurnOutput) {
        if (outcome == null) {
            outcome = next
        }
    }
}

internal data class FinishRunArguments(val status: FinishRunStatus, val message: String, val evidence: String?)

internal enum class FinishRunStatus {
    Completed,
    Blocked,
}

internal class FinishRunToolDefinition(private val recorder: TerminalOutcomeRecorder) : ToolDefinition<FinishRunArguments> {
    override val name: String = "finish_run"
    override val label: String = "Finish Run"
    override val description: String =
        "End the current phone-control run as completed or blocked with one user-visible outcome message."
    override val promptSnippet: String = "End the current run with status completed or blocked."
    override val promptGuidelines: List<String> =
        listOf(
            "Use finish_run exactly once when the current request is resolved.",
            "Use status completed only after phone evidence verifies the requested outcome.",
            "Use status blocked when progress is impossible, unsafe, or incomplete.",
            "The finish_run message is shown to the user. Do not send another assistant message after finish_run.",
        )
    override val parameters: JsonObject =
        objectParameters(
            properties =
            buildJsonObject {
                put("status", stringProperty("Either completed or blocked."))
                put("message", stringProperty("The concise user-visible final outcome message."))
                put("evidence", stringProperty("Required for completed runs: the phone evidence that verifies the outcome."))
            },
            required = listOf("status", "message"),
        )

    override fun validateArguments(arguments: JsonObject): FinishRunArguments {
        val status =
            when (arguments.requiredString("status").lowercase()) {
                "completed" -> FinishRunStatus.Completed
                "blocked" -> FinishRunStatus.Blocked
                else -> error("status must be completed or blocked")
            }
        val message = arguments.requiredString("message")
        val evidence = arguments["evidence"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
        require(status != FinishRunStatus.Completed || evidence != null) {
            "finish_run requires evidence when status is completed"
        }
        return FinishRunArguments(status = status, message = message, evidence = evidence)
    }

    override suspend fun execute(
        toolCallId: String,
        params: FinishRunArguments,
        signal: pi.ai.core.AbortSignal?,
        onUpdate: pi.agent.core.AgentToolUpdateCallback<JsonElement>?,
    ): AgentToolResult<JsonElement> {
        val output =
            when (params.status) {
                FinishRunStatus.Completed -> ModelTurnOutput.Completion(params.message)
                FinishRunStatus.Blocked -> ModelTurnOutput.Blocked(params.message)
            }
        recorder.record(output)
        return terminalToolResult(
            message = "Recorded run ${params.status.name.lowercase()}.",
            status = params.status.name.lowercase(),
            value = params.message,
            evidence = params.evidence,
        )
    }
}

private fun terminalToolResult(message: String, status: String, value: String, evidence: String?): AgentToolResult<JsonElement> {
    val details =
        buildJsonObject {
            put("kind", JsonPrimitive("run_outcome"))
            put("status", JsonPrimitive(status))
            put("value", JsonPrimitive(value))
            evidence?.let { put("evidence", JsonPrimitive(it)) }
        }
    return AgentToolResult(
        content = listOf(TextContent(message)),
        details = details,
    )
}
