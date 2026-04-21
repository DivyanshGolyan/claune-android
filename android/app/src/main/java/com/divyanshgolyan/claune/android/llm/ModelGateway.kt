package com.divyanshgolyan.claune.android.llm

import com.divyanshgolyan.claune.android.data.local.AgentRunArtifactStore
import com.divyanshgolyan.claune.android.data.local.AgentTranscriptSerializer
import com.divyanshgolyan.claune.android.data.local.CodingSessionStore
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
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pi.agent.core.AgentEvent
import pi.agent.core.AgentThinkingLevel
import pi.ai.core.ANTHROPIC_PROVIDER
import pi.ai.core.Message
import pi.ai.core.Model
import pi.ai.core.TextContent
import pi.ai.core.ThinkingBudgets
import pi.ai.core.getModel
import pi.coding.agent.core.AgentSession

interface ModelGateway {
    suspend fun nextStep(input: ModelTurnInput): ModelTurnOutput

    suspend fun steer(message: String): Boolean

    suspend fun abort()
}

class PiAgentModelGateway(
    private val settingsStore: SettingsStore,
    private val memoryStore: MemoryStore,
    private val scriptRuntime: ScriptRuntime,
    private val phoneObserver: PhoneObserver,
    private val sessionCoordinator: SessionCoordinator,
    private val artifactStore: AgentRunArtifactStore,
    private val codingSessionStore: CodingSessionStore,
    private val agentDir: File,
) : ModelGateway {
    private val sessionFactory = ClauneAgentSessionFactory(codingSessionStore, agentDir)
    private val activeSessionLock = Mutex()
    private var activeAgentSession: AgentSession? = null
    private var activeSessionPath: String? = null
    private var activeSystemPrompt: String? = null
    private var activeApiKey: String? = null
    private var activeSessionUnsubscribe: (() -> Unit)? = null
    private var activeRunId: String? = null
    private var activeEventTrace = CopyOnWriteArrayList<SerializedAgentEvent>()

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

        val agentSession = ensureMainSession(input, systemPrompt, apiKey)
        activeRunId = input.sessionId
        activeEventTrace = CopyOnWriteArrayList()
        sessionCoordinator.logEvent("Agent started.")
        sessionCoordinator.setStreaming(true)

        val result =
            try {
                agentSession.prompt(prompt)
                val finalOutput = finalAssistantText(agentSession.messages)
                runCatching { artifactStore.writeFinalOutput(input.sessionId, finalOutput) }
                val assistantError = finalAssistantError(agentSession.messages)
                val parsedResult = if (!assistantError.isNullOrBlank()) {
                    ModelTurnOutput.Blocked("Model request failed. $assistantError")
                } else {
                    PiAgentResultParser.parse(finalOutput)
                }
                runMemoryReflection(input, parsedResult, apiKey)
                parsedResult
            } catch (cancelled: CancellationException) {
                agentSession.abort()
                throw cancelled
            } catch (throwable: Throwable) {
                sessionCoordinator.logEvent(
                    "Agent failed. ${throwable.message ?: throwable::class.simpleName ?: "Unknown failure."}",
                )
                ModelTurnOutput.Blocked(
                    "Model request failed. ${throwable.message ?: throwable::class.simpleName ?: "Unknown failure."}",
                )
            } finally {
                sessionCoordinator.setStreaming(false)
                withContext(NonCancellable) {
                    val messageSnapshot =
                        runCatching {
                            agentSession.messages.toList().filterIsInstance<Message>()
                        }.getOrElse { emptyList() }
                    runCatching {
                        artifactStore.writeAgentMessages(input.sessionId, messageSnapshot)
                    }
                    runCatching { artifactStore.writeAgentEvents(input.sessionId, activeEventTrace.toList()) }
                }
                activeRunId = null
            }

        return result
    }

    override suspend fun steer(message: String): Boolean = activeSessionLock.withLock {
        val session = activeAgentSession ?: return false
        if (!session.isStreaming) {
            return false
        }
        session.steer(message)
        true
    }

    override suspend fun abort() {
        activeSessionLock.withLock {
            activeAgentSession?.abort()
        }
    }

    private suspend fun createMainSession(
        input: ModelTurnInput,
        systemPrompt: String,
        apiKey: String,
    ): AgentSession = sessionFactory.create(
        sessionPath = input.persistentSessionPath,
        systemPrompt = systemPrompt,
        model = model(),
        tools = agentTools(toolDefinitions()),
        apiKey = apiKey,
        thinkingLevel = AgentThinkingLevel.MEDIUM,
        thinkingBudgets = ThinkingBudgets(medium = 4096),
    )

    private suspend fun ensureMainSession(
        input: ModelTurnInput,
        systemPrompt: String,
        apiKey: String,
    ): AgentSession = activeSessionLock.withLock {
        val needsReplacement =
            activeAgentSession == null ||
                activeSessionPath != input.persistentSessionPath ||
                activeSystemPrompt != systemPrompt ||
                activeApiKey != apiKey
        if (needsReplacement) {
            activeSessionUnsubscribe?.invoke()
            activeAgentSession?.dispose()
            val session = createMainSession(input, systemPrompt, apiKey)
            activeSessionPath = input.persistentSessionPath
            activeSystemPrompt = systemPrompt
            activeApiKey = apiKey
            activeAgentSession = session
            activeSessionUnsubscribe =
                session.subscribe { sessionEvent ->
                    when (sessionEvent) {
                        is pi.coding.agent.core.AgentSessionEvent.Agent -> {
                            runCatching {
                                activeRunId?.let { runId ->
                                    activeEventTrace += AgentTranscriptSerializer.serializeEvent(sessionEvent.event)
                                }
                            }
                            runCatching {
                                logAgentEvent(sessionEvent.event)
                            }
                            updateAssistantText(session)
                        }

                        is pi.coding.agent.core.AgentSessionEvent.QueueUpdate -> {
                            sessionCoordinator.setPendingSteeringCount(sessionEvent.steering.size)
                        }

                        is pi.coding.agent.core.AgentSessionEvent.CompactionStart -> {
                            sessionCoordinator.setCompacting(true)
                            sessionCoordinator.logEvent("Compacting session context…")
                        }

                        is pi.coding.agent.core.AgentSessionEvent.CompactionEnd -> {
                            sessionCoordinator.setCompacting(false)
                            if (sessionEvent.aborted) {
                                sessionCoordinator.logEvent("Compaction was aborted.")
                            } else if (!sessionEvent.errorMessage.isNullOrBlank()) {
                                sessionCoordinator.logEvent("Compaction failed. ${sessionEvent.errorMessage}")
                            } else {
                                sessionCoordinator.logEvent("Compaction finished.")
                            }
                        }
                    }
                }
        }
        activeAgentSession!!
    }

    private fun model(): Model<String> = requireNotNull(getModel(ANTHROPIC_PROVIDER, MODEL_NAME)) {
        "Anthropic model $MODEL_NAME is not registered in pi-ai-core."
    }

    private fun toolDefinitions(): List<ToolDefinition<*>> = listOf(
        ExecuteScriptToolDefinition(scriptRuntime, phoneObserver),
        ReadMemoryToolDefinition(memoryStore),
        EditMemoryToolDefinition(memoryStore),
    )

    @Suppress("UNCHECKED_CAST")
    private fun agentTools(definitions: List<ToolDefinition<*>>) = definitions.map { definition ->
        toAgentTool(definition as ToolDefinition<Any?>)
    }

    private fun buildSystemPrompt(memoryContent: String): String = SystemPromptBuilder.build(
        memoryContent = memoryContent,
        tools = toolDefinitions(),
    )

    private suspend fun runMemoryReflection(input: ModelTurnInput, result: ModelTurnOutput, apiKey: String) {
        if (result !is ModelTurnOutput.Completion && result !is ModelTurnOutput.Blocked) {
            return
        }
        if (sessionCoordinator.uiState.value.status != SessionStatus.Running) {
            return
        }

        val reflectionBudget = ToolBudget(maxReflectionToolCalls = 4).apply { enterReflectionPhase() }
        val reflectionPrompt = MemoryReflectionPromptBuilder.format(input, result)
        runCatching { artifactStore.writeMemoryReflectionPrompt(input.sessionId, reflectionPrompt) }
        sessionCoordinator.logEvent("Agent memory reflection started.")

        val reflectionSession =
            sessionFactory.create(
                sessionPath = input.persistentSessionPath,
                systemPrompt = buildSystemPrompt(memoryStore.read()),
                model = model(),
                tools =
                listOf(
                    ReadMemoryToolDefinition(memoryStore),
                    EditMemoryToolDefinition(memoryStore),
                ).let(::agentTools),
                apiKey = apiKey,
                thinkingLevel = AgentThinkingLevel.MEDIUM,
                thinkingBudgets = ThinkingBudgets(medium = 4096),
                beforeToolCall = { context, _ -> reflectionBudget.beforeToolCall(context) },
            )

        runCatching {
            reflectionSession.prompt(reflectionPrompt)
            val reflectionOutput = finalAssistantText(reflectionSession.messages)
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
            AgentEvent.AgentStart -> {
                sessionCoordinator.setStreaming(true)
                sessionCoordinator.logEvent("Agent loop started.")
            }

            is AgentEvent.AgentEnd -> {
                sessionCoordinator.setStreaming(false)
                sessionCoordinator.logEvent("Agent loop finished.")
            }

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

    private fun updateAssistantText(session: AgentSession) {
        val lastAssistant =
            session.messages
                .filterIsInstance<pi.ai.core.AssistantMessage>()
                .lastOrNull()
                ?.content
                ?.filterIsInstance<TextContent>()
                ?.joinToString(separator = "\n") { it.text }
                .orEmpty()
        if (lastAssistant.isNotBlank()) {
            sessionCoordinator.setLastAssistantText(lastAssistant)
        }
    }

    companion object {
        const val MODEL_NAME = "claude-haiku-4-5"
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
