package com.divyanshgolyan.claune.android.llm.tools

import com.divyanshgolyan.claune.android.runtime.QuestionAnswer
import com.divyanshgolyan.claune.android.runtime.QuestionAnswerKind
import com.divyanshgolyan.claune.android.runtime.UserQuestionPrompter
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pi.agent.core.AgentToolResult
import pi.ai.core.TextContent

internal data class QuestionArguments(val prompt: String, val options: List<String>)

internal class QuestionToolDefinition(private val questionPrompter: UserQuestionPrompter) : ToolDefinition<QuestionArguments> {
    override val name: String = "question"
    override val label: String = "Question"
    override val description: String =
        "Ask the user to choose from 1 to 3 concrete options, with custom input available in the overlay."
    override val promptSnippet: String = "Ask the user a concrete question with 1 to 3 concise options."
    override val promptGuidelines: List<String> =
        listOf(
            "Use question when progress depends on a user decision. Provide 1 to 3 mutually exclusive options.",
            "Do not include a custom/free-text option yourself; the overlay always provides custom input.",
            "After calling question, wait for the tool result and continue the same run.",
        )
    override val parameters: JsonObject =
        objectParameters(
            properties =
            buildJsonObject {
                put("prompt", stringProperty("The concise user-facing question."))
                put(
                    "options",
                    buildJsonObject {
                        put("type", JsonPrimitive("array"))
                        put("description", JsonPrimitive("One to three concise user-selectable answers."))
                        put("minItems", JsonPrimitive(1))
                        put("maxItems", JsonPrimitive(3))
                        put("items", stringProperty("A concise option label."))
                    },
                )
            },
            required = listOf("prompt", "options"),
        )

    override fun validateArguments(arguments: JsonObject): QuestionArguments {
        val prompt = arguments.requiredString("prompt")
        val options = arguments.requiredStringList("options")
        require(options.size in 1..3) { "question requires 1 to 3 options" }
        return QuestionArguments(prompt = prompt, options = options)
    }

    override suspend fun execute(
        toolCallId: String,
        params: QuestionArguments,
        signal: pi.ai.core.AbortSignal?,
        onUpdate: pi.agent.core.AgentToolUpdateCallback<JsonElement>?,
    ): AgentToolResult<JsonElement> {
        val answer = questionPrompter.askQuestion(toolCallId, params.prompt, params.options, signal)
        return questionToolResult(answer)
    }
}

private fun questionToolResult(answer: QuestionAnswer): AgentToolResult<JsonElement> {
    val answerType =
        when (answer.kind) {
            QuestionAnswerKind.Option -> "option"
            QuestionAnswerKind.Custom -> "custom"
        }
    val details =
        buildJsonObject {
            put("kind", JsonPrimitive("question_answer"))
            put("answerType", JsonPrimitive(answerType))
            put("answer", JsonPrimitive(answer.text))
            answer.optionIndex?.let { put("optionIndex", JsonPrimitive(it)) }
        }
    return AgentToolResult(
        content =
        listOf(
            TextContent(
                if (answer.kind == QuestionAnswerKind.Option) {
                    "User selected: ${answer.text}"
                } else {
                    "User answered: ${answer.text}"
                },
            ),
        ),
        details = details,
    )
}
