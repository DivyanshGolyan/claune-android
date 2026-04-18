package com.divyanshgolyan.claune.android.llm

import com.divyanshgolyan.claune.android.data.local.AgentRunArtifactStore
import com.divyanshgolyan.claune.android.data.local.AgentTranscriptSerializer
import com.divyanshgolyan.claune.android.data.local.MemoryStore
import com.divyanshgolyan.claune.android.data.local.SerializedAgentEvent
import com.divyanshgolyan.claune.android.data.local.SettingsStore
import com.divyanshgolyan.claune.android.llm.tools.EditMemoryToolDefinition
import com.divyanshgolyan.claune.android.llm.tools.ExecuteScriptToolDefinition
import com.divyanshgolyan.claune.android.llm.tools.ReadMemoryToolDefinition
import com.divyanshgolyan.claune.android.llm.tools.SystemPromptBuilder
import com.divyanshgolyan.claune.android.llm.tools.ToolDefinition
import com.divyanshgolyan.claune.android.llm.tools.toAgentTool
import com.divyanshgolyan.claune.android.runtime.ModelTurnInput
import com.divyanshgolyan.claune.android.runtime.ModelTurnOutput
import com.divyanshgolyan.claune.android.runtime.PhoneObserver
import com.divyanshgolyan.claune.android.runtime.SessionStatus
import com.divyanshgolyan.claune.android.runtime.SessionCoordinator
import com.divyanshgolyan.claune.android.scripting.ScriptRuntime
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import pi.agent.core.Agent
import pi.agent.core.AgentEvent
import pi.agent.core.AgentOptions
import pi.agent.core.AgentThinkingLevel
import pi.ai.core.ANTHROPIC_PROVIDER
import pi.ai.core.CacheRetention
import pi.ai.core.Message
import pi.ai.core.Model
import pi.ai.core.TextContent
import pi.ai.core.ThinkingBudgets
import pi.ai.core.getModel

interface ModelGateway {
    suspend fun nextStep(input: ModelTurnInput): ModelTurnOutput
}

class PiAgentModelGateway(
    private val settingsStore: SettingsStore,
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

        val apiKey = settingsStore.state.value.anthropicApiKey
        if (apiKey.isBlank()) {
            return ModelTurnOutput.Blocked(
                "Anthropic API key is missing. Open Settings in the app, add your API key, and retry.",
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
            getApiKey = { settingsStore.state.value.anthropicApiKey },
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
        if (sessionCoordinator.uiState.value.status != SessionStatus.Running) {
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
            if (throwable is CancellationException) {
                return@onFailure
            }
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
