package com.divyanshgolyan.claune.android.llm.tools

import com.divyanshgolyan.claune.android.runtime.ModelTurnOutput
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
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

internal data class CompleteTaskArguments(val summary: String)

internal data class BlockTaskArguments(val reason: String)

internal data class AskUserArguments(val messageToUser: String)

internal class CompleteTaskToolDefinition(private val recorder: TerminalOutcomeRecorder) : ToolDefinition<CompleteTaskArguments> {
    override val name: String = "complete_task"
    override val label: String = "Complete Task"
    override val description: String =
        "Mark the phone-control task as complete after the requested outcome has been verified on the phone."
    override val promptSnippet: String = "End the run as completed with a verified summary."
    override val promptGuidelines: List<String> = emptyList()
    override val parameters: JsonObject =
        buildJsonObject {
            put("type", JsonPrimitive("object"))
            put(
                "properties",
                buildJsonObject {
                    put("summary", stringProperty("A concise summary of the verified completed outcome."))
                },
            )
            put("required", kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("summary"))))
        }

    override fun validateArguments(arguments: JsonObject): CompleteTaskArguments = CompleteTaskArguments(
        summary = arguments.requiredString("summary"),
    )

    override suspend fun execute(
        toolCallId: String,
        params: CompleteTaskArguments,
        signal: pi.ai.core.AbortSignal?,
        onUpdate: pi.agent.core.AgentToolUpdateCallback<JsonElement>?,
    ): AgentToolResult<JsonElement> {
        recorder.record(ModelTurnOutput.Completion(params.summary))
        return terminalToolResult("Recorded task completion.", "completion", params.summary)
    }
}

internal class BlockTaskToolDefinition(private val recorder: TerminalOutcomeRecorder) : ToolDefinition<BlockTaskArguments> {
    override val name: String = "block_task"
    override val label: String = "Block Task"
    override val description: String =
        "Mark the phone-control task as blocked when progress is impossible, unsafe, or requires unresolved external state."
    override val promptSnippet: String = "End the run as blocked with the reason."
    override val promptGuidelines: List<String> = emptyList()
    override val parameters: JsonObject =
        buildJsonObject {
            put("type", JsonPrimitive("object"))
            put(
                "properties",
                buildJsonObject {
                    put("reason", stringProperty("A concise reason the task cannot continue."))
                },
            )
            put("required", kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("reason"))))
        }

    override fun validateArguments(arguments: JsonObject): BlockTaskArguments = BlockTaskArguments(
        reason = arguments.requiredString("reason"),
    )

    override suspend fun execute(
        toolCallId: String,
        params: BlockTaskArguments,
        signal: pi.ai.core.AbortSignal?,
        onUpdate: pi.agent.core.AgentToolUpdateCallback<JsonElement>?,
    ): AgentToolResult<JsonElement> {
        recorder.record(ModelTurnOutput.Blocked(params.reason))
        return terminalToolResult("Recorded task blocker.", "blocked", params.reason)
    }
}

internal class AskUserToolDefinition(private val recorder: TerminalOutcomeRecorder) : ToolDefinition<AskUserArguments> {
    override val name: String = "ask_user"
    override val label: String = "Ask User"
    override val description: String =
        "Pause the task and ask the user for a decision or clarification needed to continue safely."
    override val promptSnippet: String = "Pause the run with a user-facing question or decision request."
    override val promptGuidelines: List<String> = emptyList()
    override val parameters: JsonObject =
        buildJsonObject {
            put("type", JsonPrimitive("object"))
            put(
                "properties",
                buildJsonObject {
                    put("messageToUser", stringProperty("The concise question or decision request for the user."))
                },
            )
            put("required", kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("messageToUser"))))
        }

    override fun validateArguments(arguments: JsonObject): AskUserArguments = AskUserArguments(
        messageToUser = arguments.requiredString("messageToUser"),
    )

    override suspend fun execute(
        toolCallId: String,
        params: AskUserArguments,
        signal: pi.ai.core.AbortSignal?,
        onUpdate: pi.agent.core.AgentToolUpdateCallback<JsonElement>?,
    ): AgentToolResult<JsonElement> {
        recorder.record(ModelTurnOutput.Message(params.messageToUser))
        return terminalToolResult("Recorded user question.", "message", params.messageToUser)
    }
}

private fun stringProperty(description: String): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("string"))
    put("description", JsonPrimitive(description))
}

private fun JsonObject.requiredString(name: String): String =
    this[name]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: error("Missing $name")

private fun terminalToolResult(message: String, kind: String, value: String): AgentToolResult<JsonElement> {
    val details =
        buildJsonObject {
            put("kind", JsonPrimitive(kind))
            put("value", JsonPrimitive(value))
        }
    return AgentToolResult(
        content = listOf(TextContent(message)),
        details = details,
    )
}
