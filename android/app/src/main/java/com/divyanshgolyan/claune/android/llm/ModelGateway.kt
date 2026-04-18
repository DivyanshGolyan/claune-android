package com.divyanshgolyan.claune.android.llm

import com.divyanshgolyan.claune.android.data.local.AgentRunArtifactStore
import com.divyanshgolyan.claune.android.data.local.AgentTranscriptSerializer
import com.divyanshgolyan.claune.android.data.local.MemoryStore
import com.divyanshgolyan.claune.android.data.local.SerializedAgentEvent
import com.divyanshgolyan.claune.android.llm.tools.EditMemoryToolDefinition
import com.divyanshgolyan.claune.android.llm.tools.ExecuteScriptToolDefinition
import com.divyanshgolyan.claune.android.llm.tools.ReadMemoryToolDefinition
import com.divyanshgolyan.claune.android.llm.tools.SystemPromptBuilder
import com.divyanshgolyan.claune.android.llm.tools.ToolDefinition
import com.divyanshgolyan.claune.android.llm.tools.toAgentTool
import com.divyanshgolyan.claune.android.runtime.ModelTurnInput
import com.divyanshgolyan.claune.android.runtime.ModelTurnOutput
import com.divyanshgolyan.claune.android.runtime.PhoneObserver
import com.divyanshgolyan.claune.android.runtime.SessionCoordinator
import com.divyanshgolyan.claune.android.scripting.ScriptJson
import com.divyanshgolyan.claune.android.scripting.ScriptRuntime
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import pi.agent.core.Agent
import pi.agent.core.AgentEvent
import pi.agent.core.AgentOptions
import pi.agent.core.AgentThinkingLevel
import pi.agent.core.BeforeToolCallContext
import pi.agent.core.BeforeToolCallResult
import pi.ai.core.ANTHROPIC_PROVIDER
import pi.ai.core.CacheRetention
import pi.ai.core.Message
import pi.ai.core.Model
import pi.ai.core.ThinkingBudgets
import pi.ai.core.TextContent
import pi.ai.core.getModel

interface ModelGateway {
    suspend fun nextStep(input: ModelTurnInput): ModelTurnOutput
}

class PiAgentModelGateway(
    private val apiKey: String,
    private val memoryStore: MemoryStore,
    private val scriptRuntime: ScriptRuntime,
    private val phoneObserver: PhoneObserver,
    private val sessionCoordinator: SessionCoordinator,
    private val artifactStore: AgentRunArtifactStore,
) : ModelGateway {
    override suspend fun nextStep(input: ModelTurnInput): ModelTurnOutput {
        val prompt = PiAgentPromptFormatter.format(input)
        val systemPrompt = buildSystemPrompt(memoryStore.read())
        runCatching {
            artifactStore.writeSystemPrompt(input.sessionId, systemPrompt)
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

        val eventTrace = CopyOnWriteArrayList<SerializedAgentEvent>()
        val toolBudget = ToolBudget(MAX_ITERATIONS)
        val agent = createAgent(input.sessionId, toolBudget, systemPrompt)
        val unsubscribe =
            agent.subscribe { event, _ ->
                runCatching {
                    eventTrace += AgentTranscriptSerializer.serializeEvent(event)
                }
                runCatching {
                    logAgentEvent(event)
                }
            }

        sessionCoordinator.logEvent("Agent started.")

        val result =
            try {
                agent.prompt(prompt)
                val finalOutput = finalAssistantText(agent.state.messages)
                runCatching { artifactStore.writeFinalOutput(input.sessionId, finalOutput) }
                val assistantError = finalAssistantError(agent.state.messages)
                val parsedResult = if (!assistantError.isNullOrBlank()) {
                    ModelTurnOutput.Blocked("Model request failed. $assistantError")
                } else {
                    PiAgentResultParser.parse(finalOutput)
                }
                runMemoryReflection(agent, input, toolBudget, parsedResult)
                parsedResult
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
                    val messageSnapshot =
                        runCatching {
                            agent.state.messages.toList().filterIsInstance<Message>()
                        }.getOrElse { emptyList() }
                    runCatching {
                        artifactStore.writeAgentMessages(input.sessionId, messageSnapshot)
                    }
                    runCatching { artifactStore.writeAgentEvents(input.sessionId, eventTrace.toList()) }
                }
            }

        return result
    }

    private fun createAgent(sessionId: String, toolBudget: ToolBudget, systemPrompt: String): Agent = Agent(
        AgentOptions(
            initialState =
            pi.agent.core.InitialAgentState(
                systemPrompt = systemPrompt,
                model = model(),
                thinkingLevel = AgentThinkingLevel.MEDIUM,
                tools = toolDefinitions().map { toAgentTool(it) },
            ),
            getApiKey = { apiKey },
            sessionId = sessionId,
            cacheRetention = CacheRetention.SHORT,
            thinkingBudgets = ThinkingBudgets(medium = 4096),
            toolExecution = pi.agent.core.ToolExecutionMode.SEQUENTIAL,
            beforeToolCall = { context, _ -> toolBudget.beforeToolCall(context) },
        ),
    )

    private fun model(): Model<String> = requireNotNull(getModel(ANTHROPIC_PROVIDER, MODEL_NAME)) {
        "Anthropic model $MODEL_NAME is not registered in pi-ai-core."
    }

    private fun toolDefinitions(): List<ToolDefinition<*>> = listOf(
        ExecuteScriptToolDefinition(scriptRuntime, phoneObserver),
        ReadMemoryToolDefinition(memoryStore),
        EditMemoryToolDefinition(memoryStore),
    )

    private fun buildSystemPrompt(memoryContent: String): String = SystemPromptBuilder.build(
        memoryContent = memoryContent,
        tools = toolDefinitions(),
    )

    private suspend fun runMemoryReflection(agent: Agent, input: ModelTurnInput, toolBudget: ToolBudget, result: ModelTurnOutput) {
        if (result !is ModelTurnOutput.Completion && result !is ModelTurnOutput.Blocked) {
            return
        }

        toolBudget.enterReflectionPhase()
        val reflectionPrompt = MemoryReflectionPromptBuilder.format(input, result)
        runCatching { artifactStore.writeMemoryReflectionPrompt(input.sessionId, reflectionPrompt) }
        sessionCoordinator.logEvent("Agent memory reflection started.")

        runCatching {
            agent.prompt(reflectionPrompt)
            val reflectionOutput = finalAssistantText(agent.state.messages)
            runCatching { artifactStore.writeMemoryReflectionOutput(input.sessionId, reflectionOutput) }
            when (val parsed = MemoryReflectionResultParser.parse(reflectionOutput)) {
                is MemoryReflectionResult.NoUpdate ->
                    sessionCoordinator.logEvent("Memory reflection made no durable update. ${parsed.summary}")

                is MemoryReflectionResult.Updated ->
                    sessionCoordinator.logEvent("Memory reflection updated memory.md. ${parsed.summary}")
            }
        }.onFailure { throwable ->
            sessionCoordinator.logEvent(
                "Memory reflection failed. ${throwable.message ?: throwable::class.simpleName ?: "Unknown failure."}",
            )
        }
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
        const val PROMPT_VERSION = "pi-agent-anthropic-v6"

        internal fun systemPromptForTests(memoryContent: String = "# Claune Memory\n\n"): String = SystemPromptBuilder.build(
            memoryContent = memoryContent,
            tools = listOf(
                ExecuteScriptToolDefinition(FailingScriptRuntime, FailingPhoneObserver),
                ReadMemoryToolDefinition(FailingMemoryStore),
                EditMemoryToolDefinition(FailingMemoryStore),
            ),
        )
    }
}

private class ToolBudget(private val maxToolCalls: Int, private val maxReflectionToolCalls: Int = 4) {
    private var inReflectionPhase: Boolean = false
    private var usedToolCalls: Int = 0
    private var usedReflectionToolCalls: Int = 0

    fun enterReflectionPhase() {
        inReflectionPhase = true
        usedReflectionToolCalls = 0
    }

    fun beforeToolCall(context: BeforeToolCallContext): BeforeToolCallResult? {
        if (inReflectionPhase) {
            if (context.toolCall.name == "execute_script") {
                return BeforeToolCallResult(
                    block = true,
                    reason = "Phone interaction is not allowed during memory reflection.",
                )
            }
            if (usedReflectionToolCalls >= maxReflectionToolCalls) {
                return BeforeToolCallResult(
                    block = true,
                    reason = "Memory reflection tool budget exceeded after $maxReflectionToolCalls tool calls.",
                )
            }
            usedReflectionToolCalls += 1
            return null
        }

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

@Serializable
internal data class FinalAgentResponse(
    val kind: String,
    val summary: String? = null,
    val reason: String? = null,
    val messageToUser: String? = null,
)

@Serializable
internal data class MemoryReflectionResponse(val kind: String, val summary: String)

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

private object MemoryReflectionPromptBuilder {
    fun format(input: ModelTurnInput, result: ModelTurnOutput): String = buildString {
        appendLine("System follow-up: reflect on the run for long-term memory only.")
        appendLine()
        appendLine("Original user goal:")
        appendLine(input.goal)
        appendLine()
        appendLine("Run outcome:")
        when (result) {
            is ModelTurnOutput.Blocked -> appendLine("blocked: ${result.reason}")
            is ModelTurnOutput.Completion -> appendLine("completion: ${result.summary}")
            is ModelTurnOutput.Message -> appendLine("message: ${result.messageToUser}")
        }
        appendLine()
        appendLine("Rules for this reflection turn:")
        appendLine("- Do not use execute_script.")
        appendLine("- Read memory.md first if you might update it.")
        appendLine("- Use edit_memory for a surgical update, not a full rewrite.")
        appendLine(
            "- Only store durable long-term facts: stable app facts, stable device facts, recurring workflow rules, or user preferences.",
        )
        appendLine(
            "- Do not store time-sensitive, situational, or one-off observations. Memory is for durable facts that are likely to remain useful across many future runs.",
        )
        appendLine(
            "- If a candidate fact depends on temporary state, recent search results, ephemeral availability, current contents, or a single named item/entity from one task, treat it as transient and do not store it.",
        )
        appendLine("- Do not store one-off task outcomes, transient UI state, generic prompting rules, or short-term learnings.")
        appendLine("- If there is no durable long-term learning worth saving, do not update memory.md.")
        appendLine()
        appendLine("Few-shot examples:")
        appendLine("""- Good durable memory: "On this device, App X uses package com.example.realapp instead of the branded name." """)
        appendLine("""- Why good: that is a stable app/device fact that can prevent the same mistake in future runs.""")
        appendLine("""- Bad transient memory: "Today the search results showed oranges unavailable." """)
        appendLine("""- Why bad: that depends on temporary inventory or current results and may be false later.""")
        appendLine("""- Good durable memory: "In App Y, tapping the visible search wrapper does not focus an editable field; verify focus before typing." """)
        appendLine("""- Why good: that is a recurring interaction fact about the app surface, not a one-off task result.""")
        appendLine("""- Bad transient memory: "This run succeeded after opening Screen Z and tapping the third visible item." """)
        appendLine("""- Why bad: that is just a one-run tactic, not a durable fact worth injecting into future prompts.""")
        appendLine()
        appendLine("Return a single JSON object with exactly one of these shapes:")
        appendLine("""{"kind":"no_update","summary":"..."}""")
        appendLine("""{"kind":"updated","summary":"..."}""")
        appendLine()
        appendLine("Do not wrap the final JSON in markdown fences.")
    }.trim()
}

private sealed interface MemoryReflectionResult {
    val summary: String

    data class NoUpdate(override val summary: String) : MemoryReflectionResult

    data class Updated(override val summary: String) : MemoryReflectionResult
}

private object MemoryReflectionResultParser {
    fun parse(raw: String): MemoryReflectionResult {
        val payload =
            runCatching {
                ScriptJson.codec.decodeFromString(MemoryReflectionResponse.serializer(), raw.trim())
            }.getOrElse {
                extractCandidateJsonObjects(raw)
                    .asReversed()
                    .firstNotNullOfOrNull { candidate ->
                        runCatching {
                            ScriptJson.codec.decodeFromString(MemoryReflectionResponse.serializer(), candidate)
                        }.getOrNull()
                    }
            } ?: return MemoryReflectionResult.NoUpdate("Malformed reflection output.")

        return when (payload.kind) {
            "no_update" -> MemoryReflectionResult.NoUpdate(payload.summary)
            "updated" -> MemoryReflectionResult.Updated(payload.summary)
            else -> MemoryReflectionResult.NoUpdate("Unsupported reflection kind '${payload.kind}'.")
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
                !inString && char == '{' -> {
                    if (depth == 0) {
                        startIndex = index
                    }
                    depth += 1
                }

                !inString && char == '}' && depth > 0 -> {
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
                append(", idForIdOnlyApis=")
                append(element.id)
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

private object FailingScriptRuntime : ScriptRuntime {
    override suspend fun execute(request: com.divyanshgolyan.claune.android.scripting.ScriptExecutionRequest) =
        error("Test helper should not execute scripts")
}

private object FailingPhoneObserver : PhoneObserver {
    override suspend fun captureSnapshot() = error("Test helper should not capture snapshots")
}

private object FailingMemoryStore : MemoryStore {
    override suspend fun read(): String = "# Claune Memory\n\n"

    override suspend fun edit(oldText: String, newText: String) {
        error("Test helper should not edit memory")
    }

    override suspend fun overwrite(content: String) {
        error("Test helper should not overwrite memory")
    }
}
