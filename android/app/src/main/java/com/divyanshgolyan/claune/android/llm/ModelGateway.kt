package com.divyanshgolyan.claune.android.llm

import com.divyanshgolyan.claune.android.data.local.AgentRunArtifactStore
import com.divyanshgolyan.claune.android.data.local.AgentTranscriptSerializer
import com.divyanshgolyan.claune.android.data.local.SerializedAgentEvent
import com.divyanshgolyan.claune.android.runtime.ModelTurnInput
import com.divyanshgolyan.claune.android.runtime.ModelTurnOutput
import com.divyanshgolyan.claune.android.runtime.PhoneObserver
import com.divyanshgolyan.claune.android.runtime.SessionCoordinator
import com.divyanshgolyan.claune.android.scripting.ClauneHostContract
import com.divyanshgolyan.claune.android.scripting.ScriptExecutionRequest
import com.divyanshgolyan.claune.android.scripting.ScriptJson
import com.divyanshgolyan.claune.android.scripting.ScriptRuntime
import com.divyanshgolyan.claune.android.scripting.UiSnapshotPayload
import com.divyanshgolyan.claune.android.scripting.toPayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import pi.agent.core.Agent
import pi.agent.core.AgentEvent
import pi.agent.core.AgentOptions
import pi.agent.core.AgentThinkingLevel
import pi.agent.core.AgentTool
import pi.agent.core.AgentToolResult
import pi.agent.core.BeforeToolCallContext
import pi.agent.core.BeforeToolCallResult
import pi.ai.core.ANTHROPIC_PROVIDER
import pi.ai.core.CacheRetention
import pi.ai.core.Message
import pi.ai.core.Model
import pi.ai.core.TextContent
import pi.ai.core.getModel

interface ModelGateway {
    suspend fun nextStep(input: ModelTurnInput): ModelTurnOutput
}

class PiAgentModelGateway(
    private val apiKey: String,
    private val scriptRuntime: ScriptRuntime,
    private val phoneObserver: PhoneObserver,
    private val sessionCoordinator: SessionCoordinator,
    private val artifactStore: AgentRunArtifactStore,
) : ModelGateway {
    override suspend fun nextStep(input: ModelTurnInput): ModelTurnOutput {
        val prompt = PiAgentPromptFormatter.format(input)
        runCatching {
            artifactStore.writeSystemPrompt(input.sessionId, PI_AGENT_SYSTEM_PROMPT.trimIndent())
            artifactStore.writeModelInput(input.sessionId, prompt)
        }

        if (apiKey.isBlank()) {
            return ModelTurnOutput.Blocked(
                "Anthropic API key is missing. Add claune.anthropicApiKey to android/local.properties, " +
                    "rebuild the debug app, and reinstall before retrying.",
            )
        }

        if (input.snapshot.actionableElements.isEmpty()) {
            return ModelTurnOutput.Blocked(
                "No actionable elements were captured. Enable the accessibility service, reopen the target app, and retry.",
            )
        }

        val eventTrace = mutableListOf<SerializedAgentEvent>()
        val toolBudget = ToolBudget(MAX_ITERATIONS)
        val agent = createAgent(input.sessionId, toolBudget)
        val unsubscribe =
            agent.subscribe { event, _ ->
                eventTrace += AgentTranscriptSerializer.serializeEvent(event)
                logAgentEvent(event)
            }

        sessionCoordinator.logEvent("Agent started.")

        val result =
            try {
                agent.prompt(prompt)
                val finalOutput = finalAssistantText(agent.state.messages)
                runCatching { artifactStore.writeFinalOutput(input.sessionId, finalOutput) }
                val assistantError = finalAssistantError(agent.state.messages)
                if (!assistantError.isNullOrBlank()) {
                    ModelTurnOutput.Blocked("Model request failed. $assistantError")
                } else {
                    PiAgentResultParser.parse(finalOutput)
                }
            } catch (cancelled: CancellationException) {
                agent.abort()
                throw cancelled
            } catch (throwable: Throwable) {
                sessionCoordinator.logEvent(
                    "Agent failed. ${throwable.message ?: throwable::class.simpleName ?: "Unknown failure."}",
                )
                ModelTurnOutput.Blocked(
                    "Model request failed. ${throwable.message ?: throwable::class.simpleName ?: "Unknown failure."}",
                )
            } finally {
                unsubscribe()
                withContext(NonCancellable) {
                    runCatching {
                        artifactStore.writeAgentMessages(input.sessionId, agent.state.messages.filterIsInstance<Message>())
                    }
                    runCatching { artifactStore.writeAgentEvents(input.sessionId, eventTrace) }
                }
            }

        return result
    }

    private fun createAgent(sessionId: String, toolBudget: ToolBudget): Agent = Agent(
        AgentOptions(
            initialState =
            pi.agent.core.InitialAgentState(
                systemPrompt = PI_AGENT_SYSTEM_PROMPT,
                model = model(),
                thinkingLevel = AgentThinkingLevel.OFF,
                tools = listOf(ExecuteScriptAgentTool(scriptRuntime, phoneObserver)),
            ),
            getApiKey = { apiKey },
            sessionId = sessionId,
            cacheRetention = CacheRetention.SHORT,
            toolExecution = pi.agent.core.ToolExecutionMode.SEQUENTIAL,
            beforeToolCall = { context, _ -> toolBudget.beforeToolCall(context) },
        ),
    )

    private fun model(): Model<String> = requireNotNull(getModel(ANTHROPIC_PROVIDER, MODEL_NAME)) {
        "Anthropic model $MODEL_NAME is not registered in pi-ai-core."
    }

    private fun logAgentEvent(event: AgentEvent) {
        when (event) {
            AgentEvent.AgentStart -> sessionCoordinator.logEvent("Agent loop started.")
            is AgentEvent.AgentEnd -> sessionCoordinator.logEvent("Agent loop finished.")
            AgentEvent.TurnStart -> sessionCoordinator.logEvent("Agent turn started.")
            is AgentEvent.ToolExecutionStart ->
                sessionCoordinator.logEvent("Agent tool ${event.toolName} starting.")
            is AgentEvent.ToolExecutionEnd ->
                sessionCoordinator.logEvent(
                    "Agent tool ${event.toolName} finished${if (event.isError) " with an error." else "."}",
                )
            else -> Unit
        }
    }

    private fun finalAssistantText(messages: List<Any>): String {
        val assistant = messages.filterIsInstance<pi.ai.core.AssistantMessage>().lastOrNull() ?: return ""
        return assistant.content.filterIsInstance<TextContent>().joinToString(separator = "\n") { it.text }
    }

    private fun finalAssistantError(messages: List<Any>): String? =
        messages.filterIsInstance<pi.ai.core.AssistantMessage>().lastOrNull()?.errorMessage

    companion object {
        const val MODEL_NAME = "claude-haiku-4-5"
        const val MAX_ITERATIONS = 100
        const val PROMPT_VERSION = "pi-agent-anthropic-v1"

        internal fun systemPromptForTests(): String = PI_AGENT_SYSTEM_PROMPT

        private val PI_AGENT_SYSTEM_PROMPT: String =
            buildString {
                val rules =
                    listOf(
                        "All phone interaction must happen through execute_script.",
                        "The TypeScript contract above is the source of truth for the script surface. " +
                            "Do not invent APIs or fields outside it.",
                        "claune APIs are synchronous plain function calls. Do not use await, Promise syntax, or async functions.",
                        "Every claune action except observePhone throws immediately if the host call fails. " +
                            "Do not assume a wait or tap succeeded unless the script continues past it.",
                        "Refs and element ids are snapshot-scoped. After navigation, scrolling, typing, tapping, " +
                            "or any UI-changing action, call observePhone() again before using new refs or ids.",
                        "waitForState(\"element\", value, timeoutMs) expects an element id from actionableElements, not a ref.",
                        "After navigation, prefer waitForState(\"package\", \"...\", timeoutMs) or " +
                            "waitForSelector({ ... }, timeoutMs) instead of waiting on a stale ref or element id.",
                        "Never invent refs or element IDs; use only values present in snapshots.",
                        "Never select elements by array index. Use selector matches, refs, ids, resource ids, or labels from the snapshot.",
                        "Prefer tapSelector({ text: \"...\" }) when text is distinctive, and tapRef(ref) " +
                            "when you already have a fresh ref from the current snapshot.",
                        "Use typeIntoFocused(text) when the current screen already has a focused editable field. " +
                            "Otherwise, select a specific editable element first and then type into it.",
                        "Prefer the fewest scripts possible. A single script may observe, take multiple actions, wait for state changes, " +
                            "and return a compact summary.",
                        "Keep scripts focused but not tiny; avoid spending iterations on trivial one-action probes " +
                            "when the next step is already clear.",
                        "If the current screen is confusing, off-plan, or repeated assumptions fail, re-establish a known state " +
                            "before continuing instead of persisting with a broken plan.",
                        "Prefer visible, directly actionable controls over indirect routes when both are available.",
                        "Distinguish between selecting content and triggering an action attached to that content.",
                        "Do not hardcode launcher package names; vendor launchers vary. After pressHome(), " +
                            "capture a fresh snapshot and tap the launcher icon you need.",
                        "For any task that changes app or device state, capture the relevant baseline before acting " +
                            "and verify an observable delta afterward.",
                        "Never claim success from pre-existing state. Verify what changed because of your actions.",
                        "If a launch, wait, or selector assumption fails, re-observe and adapt instead of repeating the same assumption.",
                        "If the goal is complete, stop using tools and return final JSON only.",
                        "If progress is impossible, stop using tools and return final JSON only.",
                    )

                appendLine("You are Claune Android, a phone-control agent operating an Android 12 device.")
                appendLine()
                appendLine(
                    "You must help the user achieve the goal by reasoning over the provided phone snapshot and recent session events.",
                )
                appendLine()
                appendLine("You have exactly one tool: execute_script.")
                appendLine("Use execute_script whenever you need to act on or re-check the phone.")
                appendLine("The script runs in a JS runtime with a global object named `claune`.")
                appendLine()
                appendLine(ClauneHostContract.modelContractBlock)
                appendLine()
                appendLine("Important rules:")
                rules.forEach { rule ->
                    append("- ")
                    appendLine(rule)
                }
                appendLine()
                appendLine("Example valid script:")
                appendLine("let screen = claune.observePhone();")
                appendLine("claune.pressHome();")
                appendLine("screen = claune.observePhone();")
                appendLine("claune.tapSelector({ text: \"Settings\" });")
                appendLine("claune.waitForState(\"package\", \"com.android.settings\", 3000);")
                appendLine("screen = claune.observePhone();")
                appendLine("claune.tapSelector({ text: \"Wi-Fi\" });")
                appendLine("claune.waitForSelector({ text: \"Wi-Fi assistant\", first: true }, 3000);")
                appendLine("return { stage: \"wifi_page\", foregroundPackage: screen.foregroundPackage };")
                appendLine()
                appendLine("Your final response must be a single valid JSON object with no prose before or after it.")
                appendLine("The first character of the final response must be { and the last character must be }.")
                appendLine("Use exactly one of these shapes:")
                appendLine("""{"kind":"completion","summary":"..."}""")
                appendLine("""{"kind":"blocked","reason":"..."}""")
                appendLine("""{"kind":"message","messageToUser":"..."}""")
                appendLine()
                appendLine("Do not wrap the final JSON in markdown fences.")
            }.trim()
    }
}

private class ToolBudget(private val maxToolCalls: Int) {
    private var usedToolCalls: Int = 0

    fun beforeToolCall(context: BeforeToolCallContext): BeforeToolCallResult? {
        if (usedToolCalls >= maxToolCalls) {
            return BeforeToolCallResult(
                block = true,
                reason = "Tool budget exceeded after $maxToolCalls tool calls while handling the goal.",
            )
        }
        usedToolCalls += 1
        return null
    }
}

internal class ExecuteScriptAgentTool(private val scriptRuntime: ScriptRuntime, private val phoneObserver: PhoneObserver) :
    AgentTool<String> {
    override val name: String = "execute_script"
    override val label: String = "Execute Script"
    override val description: String =
        "Execute a JavaScript snippet against the live Claune host runtime. " +
            "Use this tool for any phone interaction or phone-state recheck. " +
            "Return value is JSON text with script result and a post-action snapshot."
    override val parameters =
        buildJsonObject {
            put("type", JsonPrimitive("object"))
            put(
                "properties",
                buildJsonObject {
                    put(
                        "script",
                        buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put(
                                "description",
                                JsonPrimitive(
                                    "A complete JavaScript snippet that uses the claune host APIs to observe the phone, " +
                                        "act on it, and return a compact result object.",
                                ),
                            )
                        },
                    )
                },
            )
            put("required", kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("script"))))
        }

    override fun validateArguments(arguments: kotlinx.serialization.json.JsonObject): String =
        arguments["script"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: error("Missing script")

    override suspend fun execute(
        toolCallId: String,
        params: String,
        signal: pi.ai.core.AbortSignal?,
        onUpdate: pi.agent.core.AgentToolUpdateCallback<JsonElement>?,
    ): AgentToolResult<JsonElement> {
        val result =
            scriptRuntime.execute(
                ScriptExecutionRequest(
                    script = params,
                    source = "pi_agent",
                ),
            )
        val postActionSnapshot = phoneObserver.captureSnapshot().toPayload()
        val payload =
            ExecuteScriptToolResult(
                ok = result.ok,
                summary = result.summary,
                error = result.error,
                scriptData = result.data,
                postActionSnapshot = postActionSnapshot,
            )
        val encoded = ScriptJson.codec.encodeToString(ExecuteScriptToolResult.serializer(), payload)
        return AgentToolResult(
            content = listOf(TextContent(encoded)),
            details = ScriptJson.codec.encodeToJsonElement(ExecuteScriptToolResult.serializer(), payload),
        )
    }
}

@Serializable
internal data class ExecuteScriptToolResult(
    val ok: Boolean,
    val summary: String,
    val error: String? = null,
    val scriptData: JsonElement? = null,
    val postActionSnapshot: UiSnapshotPayload,
)

@Serializable
internal data class FinalAgentResponse(
    val kind: String,
    val summary: String? = null,
    val reason: String? = null,
    val messageToUser: String? = null,
)

internal object PiAgentResultParser {
    fun parse(raw: String): ModelTurnOutput {
        val payload = decodeFinalResponse(raw)
            ?: return ModelTurnOutput.Blocked(
                "Model returned malformed final output. Expected JSON with kind/message fields.",
            )

        return when (payload.kind) {
            "completion" -> payload.summary?.takeIf { it.isNotBlank() }?.let(ModelTurnOutput::Completion)
                ?: ModelTurnOutput.Blocked("Model returned completion without a summary.")

            "blocked" -> payload.reason?.takeIf { it.isNotBlank() }?.let(ModelTurnOutput::Blocked)
                ?: ModelTurnOutput.Blocked("Model returned blocked without a reason.")

            "message" -> payload.messageToUser?.takeIf { it.isNotBlank() }?.let(ModelTurnOutput::Message)
                ?: ModelTurnOutput.Blocked("Model returned message without messageToUser.")

            else -> ModelTurnOutput.Blocked("Model returned unsupported final kind '${payload.kind}'.")
        }
    }

    private fun decodeFinalResponse(raw: String): FinalAgentResponse? {
        runCatching {
            return ScriptJson.codec.decodeFromString(FinalAgentResponse.serializer(), raw.trim())
        }

        return extractCandidateJsonObjects(raw)
            .asReversed()
            .firstNotNullOfOrNull { candidate ->
                runCatching {
                    ScriptJson.codec.decodeFromString(FinalAgentResponse.serializer(), candidate)
                }.getOrNull()
            }
    }

    private fun extractCandidateJsonObjects(raw: String): List<String> {
        val candidates = mutableListOf<String>()
        var inString = false
        var escaping = false
        var depth = 0
        var startIndex = -1

        raw.forEachIndexed { index, char ->
            when {
                escaping -> escaping = false
                char == '\\' && inString -> escaping = true
                char == '"' -> inString = !inString
                inString -> Unit
                char == '{' -> {
                    if (depth == 0) {
                        startIndex = index
                    }
                    depth += 1
                }

                char == '}' && depth > 0 -> {
                    depth -= 1
                    if (depth == 0 && startIndex >= 0) {
                        candidates += raw.substring(startIndex, index + 1)
                        startIndex = -1
                    }
                }
            }
        }

        return candidates
    }
}

internal object PiAgentPromptFormatter {
    fun format(input: ModelTurnInput): String = buildString {
        appendLine("Goal:")
        appendLine(input.goal)
        appendLine()
        appendLine("Recent session events:")
        if (input.recentEvents.isEmpty()) {
            appendLine("- none")
        } else {
            input.recentEvents.takeLast(8).forEach { event ->
                append("- ")
                appendLine(event)
            }
        }
        appendLine()
        appendLine("Current phone snapshot:")
        appendLine("foregroundPackage: ${input.snapshot.foregroundPackage}")
        appendLine("focusedElementId: ${input.snapshot.focusedElementId ?: "none"}")
        appendLine("visibleText:")
        if (input.snapshot.visibleText.isEmpty()) {
            appendLine("- none")
        } else {
            input.snapshot.visibleText.take(20).forEach { line ->
                append("- ")
                appendLine(line)
            }
        }
        appendLine("actionableElements:")
        if (input.snapshot.actionableElements.isEmpty()) {
            appendLine("- none")
        } else {
            input.snapshot.actionableElements.take(20).forEach { element ->
                append("- ref=")
                append(element.ref)
                append(", id=")
                append(element.id)
                append(", role=")
                append(element.role)
                append(", label=")
                append(if (element.label.isBlank()) "<blank>" else element.label)
                append(", text=")
                append(element.text ?: "<none>")
                append(", contentDescription=")
                append(element.contentDescription ?: "<none>")
                append(", resourceId=")
                append(element.resourceId ?: "<none>")
                append(", clickable=")
                append(element.clickable)
                append(", editable=")
                append(element.editable)
                append(", enabled=")
                append(element.enabled)
                append(", checked=")
                append(element.checked)
                append(", scrollable=")
                append(element.scrollable)
                append(", focused=")
                appendLine(element.focused)
            }
        }
        appendLine()
        appendLine("Return final JSON only when the goal is complete or clearly blocked.")
    }
}
