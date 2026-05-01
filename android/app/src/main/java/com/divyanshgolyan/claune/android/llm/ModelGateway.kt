package com.divyanshgolyan.claune.android.llm

import com.divyanshgolyan.claune.android.data.local.AgentRunArtifactStore
import com.divyanshgolyan.claune.android.data.local.AgentTranscriptSerializer
import com.divyanshgolyan.claune.android.data.local.CodingSessionStore
import com.divyanshgolyan.claune.android.data.local.SerializedAgentEvent
import com.divyanshgolyan.claune.android.data.local.SessionLogStore
import com.divyanshgolyan.claune.android.data.local.SettingsStore
import com.divyanshgolyan.claune.android.llm.tools.AskUserToolDefinition
import com.divyanshgolyan.claune.android.llm.tools.BashToolDefinition
import com.divyanshgolyan.claune.android.llm.tools.EditFileToolDefinition
import com.divyanshgolyan.claune.android.llm.tools.FinishRunToolDefinition
import com.divyanshgolyan.claune.android.llm.tools.ReadFileToolDefinition
import com.divyanshgolyan.claune.android.llm.tools.SystemPromptBuilder
import com.divyanshgolyan.claune.android.llm.tools.TerminalOutcomeRecorder
import com.divyanshgolyan.claune.android.llm.tools.ToolDefinition
import com.divyanshgolyan.claune.android.llm.tools.WriteFileToolDefinition
import com.divyanshgolyan.claune.android.llm.tools.toAgentTool
import com.divyanshgolyan.claune.android.runtime.ModelTurnInput
import com.divyanshgolyan.claune.android.runtime.ModelTurnOutput
import com.divyanshgolyan.claune.android.runtime.QuestionAnswer
import com.divyanshgolyan.claune.android.runtime.QuestionAnswerKind
import com.divyanshgolyan.claune.android.runtime.QuestionPromptCoordinator
import com.divyanshgolyan.claune.android.runtime.SessionCoordinator
import com.divyanshgolyan.claune.android.runtime.SessionStatus
import com.divyanshgolyan.claune.android.runtime.UserQuestionPrompter
import com.divyanshgolyan.claune.android.runtime.toStatusMessageJsonString
import com.divyanshgolyan.claune.android.shell.WorkspaceShell
import com.divyanshgolyan.claune.android.telemetry.ClauneTelemetryContext
import com.divyanshgolyan.claune.android.telemetry.ClauneTelemetryEventType
import com.divyanshgolyan.claune.android.telemetry.ClauneTelemetryPhase
import com.divyanshgolyan.claune.android.telemetry.ClauneTelemetryRecorder
import com.divyanshgolyan.claune.android.telemetry.NoopClauneTelemetryRecorder
import com.divyanshgolyan.claune.android.workspace.AgentWorkspace
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pi.agent.core.AfterToolCallContext
import pi.agent.core.AfterToolCallResult
import pi.agent.core.Agent
import pi.agent.core.AgentEvent
import pi.agent.core.BeforeToolCallContext
import pi.agent.core.BeforeToolCallResult
import pi.ai.core.AssistantMessage
import pi.ai.core.Message
import pi.ai.core.Model
import pi.ai.core.ProviderResponse
import pi.ai.core.TextContent
import pi.coding.agent.core.AgentSession

private class ActiveRunTrace(val input: ModelTurnInput) {
    private val events = ConcurrentLinkedQueue<SerializedAgentEvent>()

    fun add(event: SerializedAgentEvent) {
        events += event
    }

    fun snapshot(): List<SerializedAgentEvent> = events.toList()
}

private fun Agent.applyObservationHooks(hooks: AgentObservationHooks) {
    beforeToolCall = hooks.beforeToolCall
    afterToolCall = hooks.afterToolCall
    onPayload = hooks.onPayload
    onResponse = hooks.onResponse
}

interface ModelGateway {
    fun currentModelName(): String

    suspend fun nextStep(input: ModelTurnInput): ModelTurnOutput

    suspend fun steer(message: String): Boolean

    suspend fun abort()
}

class PiAgentModelGateway(
    private val settingsStore: SettingsStore,
    private val logStore: SessionLogStore,
    private val sessionCoordinator: SessionCoordinator,
    private val questionPromptCoordinator: QuestionPromptCoordinator,
    private val artifactStore: AgentRunArtifactStore,
    private val codingSessionStore: CodingSessionStore,
    private val agentDir: File,
    private val codexAuthRepository: CodexAuthRepository,
    private val workspace: AgentWorkspace,
    private val workspaceShell: WorkspaceShell,
    private val telemetryRecorder: ClauneTelemetryRecorder = NoopClauneTelemetryRecorder,
) : ModelGateway {
    private val sessionFactory = ClauneAgentSessionFactory(codingSessionStore, agentDir)
    private val activeSessionLock = Mutex()
    private var activeAgentSession: AgentSession? = null
    private var activeSessionPath: String? = null
    private var activeSystemPrompt: String? = null
    private var activeAuthMarker: String? = null
    private var activeModelProvider: String? = null
    private var activeModelId: String? = null
    private var activeThinkingConfig: ClauneThinkingConfig? = null
    private var activeSessionUnsubscribe: (() -> Unit)? = null
    private var activeRunTrace: ActiveRunTrace? = null
    private val terminalOutcomeRecorder = TerminalOutcomeRecorder()

    override fun currentModelName(): String = ClauneModelCatalog.selectedModelName(settingsStore.state.value)

    override suspend fun nextStep(input: ModelTurnInput): ModelTurnOutput {
        val prompt = PiAgentPromptFormatter.format(input)
        terminalOutcomeRecorder.clear()
        val mainTools = mainToolDefinitions()
        val systemPrompt = buildSystemPrompt(activeWorkspace().memoryTree(), mainTools)
        runCatching {
            artifactStore.writeSystemPrompt(input.runId, systemPrompt)
            artifactStore.writeModelInput(input.runId, prompt)
        }

        val settings = settingsStore.state.value
        val modelConfig = ClauneModelCatalog.resolve(settings)
        val thinkingConfig = settings.thinkingConfigFor(modelConfig.option.id)
        var apiKeyForSession: String? = null
        val authMarker =
            when (val authRequirement = modelConfig.authRequirement) {
                is ClauneAuthRequirement.ApiKey -> {
                    val apiKey = settings.apiKeyFor(authRequirement.slot)
                    if (apiKey.isBlank()) {
                        return ModelTurnOutput.Blocked(modelConfig.missingAuthMessage)
                    }
                    apiKeyForSession = apiKey
                    "api-key:${authRequirement.slot}:$apiKey"
                }
                is ClauneAuthRequirement.OAuth -> {
                    if (!codexAuthRepository.hasUsableCredentials()) {
                        codexAuthRepository.refresh()
                        return ModelTurnOutput.Blocked(modelConfig.missingAuthMessage)
                    }
                    "oauth:${authRequirement.provider}:${codexAuthRepository.credentialMarker()}"
                }
            }

        if (modelConfig.model.provider != modelConfig.option.provider) {
            return ModelTurnOutput.Blocked("Selected model provider is inconsistent with the catalog.")
        }

        telemetryRecorder.startRun(input, modelConfig.model.provider, modelConfig.model.id, systemPrompt, prompt)

        if (input.screenObservation.foregroundPackage == "unavailable") {
            val blocked =
                ModelTurnOutput.Blocked(
                    "No screen state was captured. Enable the accessibility service, reopen the target app, and retry.",
                )
            telemetryRecorder.endRun(input.runId, blocked)
            return blocked
        }

        activeRunTrace = ActiveRunTrace(input)
        sessionCoordinator.logEvent("Agent started.")
        sessionCoordinator.setStreaming(true)

        var agentSession: AgentSession? = null
        var finalOutput: ModelTurnOutput? = null
        val result =
            try {
                agentSession =
                    ensureMainSession(
                        input,
                        systemPrompt,
                        modelConfig.model,
                        modelConfig.authRequirement,
                        apiKeyForSession,
                        authMarker,
                        thinkingConfig,
                        mainTools,
                    )
                agentSession.prompt(prompt)
                val assistantError = finalAssistantError(agentSession.messages)
                val terminalOutcome = terminalOutcomeRecorder.outcome
                val parsedResult = if (!assistantError.isNullOrBlank()) {
                    ModelTurnOutput.Blocked("Model request failed. $assistantError")
                } else if (terminalOutcome != null) {
                    terminalOutcome
                } else {
                    ModelTurnOutput.Blocked(
                        "The model ended without calling finish_run. Retry with a clearer request or inspect the run artifacts.",
                    )
                }
                finalOutput = parsedResult
                runCatching { artifactStore.writeFinalOutput(input.runId, parsedResult.toStatusMessageJsonString()) }
                if (assistantError.isNullOrBlank() && terminalOutcome != null) {
                    runMemoryReflection(
                        input,
                        parsedResult,
                        modelConfig.model,
                        modelConfig.authRequirement,
                        apiKeyForSession,
                        thinkingConfig,
                    )
                }
                parsedResult
            } catch (cancelled: CancellationException) {
                agentSession?.abort()
                throw cancelled
            } catch (throwable: Throwable) {
                sessionCoordinator.logEvent(
                    "Agent failed. ${throwable.message ?: throwable::class.simpleName ?: "Unknown failure."}",
                )
                ModelTurnOutput.Blocked(
                    "Model request failed. ${throwable.message ?: throwable::class.simpleName ?: "Unknown failure."}",
                ).also { finalOutput = it }
            } finally {
                sessionCoordinator.setStreaming(false)
                withContext(NonCancellable) {
                    val messageSnapshot =
                        runCatching {
                            agentSession?.messages.orEmpty().toList().filterIsInstance<Message>()
                        }.getOrElse { emptyList() }
                    runCatching {
                        artifactStore.writeAgentMessages(input.runId, messageSnapshot)
                    }
                    runCatching { artifactStore.writeAgentEvents(input.runId, activeRunTrace?.snapshot().orEmpty()) }
                }
                telemetryRecorder.endRun(input.runId, finalOutput)
                activeRunTrace = null
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
        questionPromptCoordinator.cancelActiveQuestion("Question was cancelled.")
        activeSessionLock.withLock {
            activeAgentSession?.abort()
        }
    }

    @Suppress("ktlint:standard:function-signature")
    private suspend fun createMainSession(
        input: ModelTurnInput,
        systemPrompt: String,
        model: Model<*>,
        authRequirement: ClauneAuthRequirement,
        apiKey: String?,
        thinkingConfig: ClauneThinkingConfig,
        tools: List<ToolDefinition<*>>,
    ): AgentSession = sessionFactory.create(
        sessionPath = input.persistentSessionPath,
        systemPrompt = systemPrompt,
        model = model,
        tools = agentTools(tools),
        authRequirement = authRequirement,
        apiKey = apiKey,
        thinkingLevel = thinkingConfig.level,
        thinkingBudgets = thinkingConfig.budgets,
        observationHooks = observationHooks(input, ClauneTelemetryPhase.MAIN),
    )

    @Suppress("ktlint:standard:function-signature")
    private suspend fun ensureMainSession(
        input: ModelTurnInput,
        systemPrompt: String,
        model: Model<*>,
        authRequirement: ClauneAuthRequirement,
        apiKey: String?,
        authMarker: String,
        thinkingConfig: ClauneThinkingConfig,
        tools: List<ToolDefinition<*>>,
    ): AgentSession = activeSessionLock.withLock {
        val needsReplacement = when {
            activeAgentSession == null -> true
            activeSessionPath != input.persistentSessionPath -> true
            activeSystemPrompt != systemPrompt -> true
            activeAuthMarker != authMarker -> true
            activeModelProvider != model.provider -> true
            activeModelId != model.id -> true
            activeThinkingConfig != thinkingConfig -> true
            else -> false
        }
        if (needsReplacement) {
            activeSessionUnsubscribe?.invoke()
            activeAgentSession?.dispose()
            val session = createMainSession(input, systemPrompt, model, authRequirement, apiKey, thinkingConfig, tools)
            activeSessionPath = input.persistentSessionPath
            activeSystemPrompt = systemPrompt
            activeAuthMarker = authMarker
            activeModelProvider = model.provider
            activeModelId = model.id
            activeThinkingConfig = thinkingConfig
            activeAgentSession = session
            activeSessionUnsubscribe =
                session.subscribe { sessionEvent ->
                    when (sessionEvent) {
                        is pi.coding.agent.core.AgentSessionEvent.Agent -> {
                            runCatching {
                                activeRunTrace?.add(AgentTranscriptSerializer.serializeEvent(sessionEvent.event))
                            }
                            runCatching {
                                observeProviderMessageEnd(sessionEvent.event)
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
                            activeRunTrace?.input?.let { runInput ->
                                val reason = sessionEvent.reason.name.lowercase()
                                val payload =
                                    buildJsonObject {
                                        put("reason", reason)
                                    }
                                appendObservationEvent(
                                    ClauneTelemetryEventType.COMPACTION_START,
                                    input = runInput,
                                    phase = ClauneTelemetryPhase.MAIN,
                                    payload = payload,
                                )
                                telemetryRecorder.recordCompactionStart(
                                    telemetryContext(runInput, ClauneTelemetryPhase.MAIN),
                                    reason = reason,
                                )
                            }
                            sessionCoordinator.setCompacting(true)
                            sessionCoordinator.logEvent("Compacting session context…")
                        }

                        is pi.coding.agent.core.AgentSessionEvent.CompactionEnd -> {
                            activeRunTrace?.input?.let { runInput ->
                                val reason = sessionEvent.reason.name.lowercase()
                                val payload =
                                    buildJsonObject {
                                        put("reason", reason)
                                        put("aborted", sessionEvent.aborted)
                                        put("willRetry", sessionEvent.willRetry)
                                        put("hasResult", sessionEvent.result != null)
                                        sessionEvent.errorMessage?.let { put("errorMessage", it) }
                                    }
                                appendObservationEvent(
                                    ClauneTelemetryEventType.COMPACTION_END,
                                    input = runInput,
                                    phase = ClauneTelemetryPhase.MAIN,
                                    payload = payload,
                                )
                                telemetryRecorder.recordCompactionEnd(
                                    context = telemetryContext(runInput, ClauneTelemetryPhase.MAIN),
                                    reason = reason,
                                    aborted = sessionEvent.aborted,
                                    willRetry = sessionEvent.willRetry,
                                    hasResult = sessionEvent.result != null,
                                    errorMessage = sessionEvent.errorMessage,
                                )
                            }
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
        activeAgentSession!!.also { session ->
            session.agent.applyObservationHooks(observationHooks(input, ClauneTelemetryPhase.MAIN))
        }
    }

    private fun mainToolDefinitions(): List<ToolDefinition<*>> = buildList {
        addAll(workspaceFileToolDefinitions())
        add(workspaceBashToolDefinition())
        add(FinishRunToolDefinition(terminalOutcomeRecorder))
        add(AskUserToolDefinition(questionPromptCoordinator))
    }

    private fun workspaceFileToolDefinitions(): List<ToolDefinition<*>> {
        val activeWorkspace = activeWorkspace()
        return listOf(
            ReadFileToolDefinition(activeWorkspace),
            WriteFileToolDefinition(activeWorkspace),
            EditFileToolDefinition(activeWorkspace),
        )
    }

    private fun memoryFileToolDefinitions(): List<ToolDefinition<*>> = listOf(
        ReadFileToolDefinition(workspace, workspace::requireMemoryPath),
        WriteFileToolDefinition(workspace, workspace::requireMemoryPath),
        EditFileToolDefinition(workspace, workspace::requireMemoryPath),
    )

    private fun activeWorkspace(): AgentWorkspace = workspace

    private fun workspaceBashToolDefinition(): ToolDefinition<*> {
        val activeWorkspace = activeWorkspace()
        return BashToolDefinition(shell = workspaceShell, workspace = activeWorkspace)
    }

    @Suppress("UNCHECKED_CAST")
    private fun agentTools(definitions: List<ToolDefinition<*>>) = definitions.map { definition ->
        toAgentTool(definition as ToolDefinition<Any?>)
    }

    private fun buildSystemPrompt(memoryTree: String, tools: List<ToolDefinition<*>>): String = SystemPromptBuilder.build(
        memoryTree = memoryTree,
        tools = tools,
    )

    private fun observationHooks(
        input: ModelTurnInput,
        phase: ClauneTelemetryPhase,
        beforeToolCallDelegate: (suspend (BeforeToolCallContext, pi.ai.core.AbortSignal?) -> BeforeToolCallResult?)? = null,
    ): AgentObservationHooks = AgentObservationHooks(
        beforeToolCall = { context, signal ->
            observeBeforeToolCall(input, phase, context, signal)
            beforeToolCallDelegate?.invoke(context, signal)
        },
        afterToolCall = { context, signal -> observeAfterToolCall(input, phase, context, signal) },
        onPayload = { payload, model -> observeProviderPayload(input, phase, payload, model) },
        onResponse = { response, model -> observeProviderResponse(input, phase, response, model) },
    )

    @Suppress("ktlint:standard:function-signature")
    private suspend fun runMemoryReflection(
        input: ModelTurnInput,
        result: ModelTurnOutput,
        model: Model<*>,
        authRequirement: ClauneAuthRequirement,
        apiKey: String?,
        thinkingConfig: ClauneThinkingConfig,
    ) {
        if (result !is ModelTurnOutput.Completion && result !is ModelTurnOutput.Blocked) {
            return
        }
        if (sessionCoordinator.uiState.value.status != SessionStatus.Running) {
            return
        }

        val reflectionBudget = ToolBudget(maxReflectionToolCalls = 4).apply { enterReflectionPhase() }
        val reflectionPrompt = MemoryReflectionPromptBuilder.format(input, result)
        runCatching { artifactStore.writeMemoryReflectionPrompt(input.runId, reflectionPrompt) }
        sessionCoordinator.logEvent("Agent memory reflection started.")
        val memoryBeforeReflection = activeWorkspace().memorySignature()
        val memoryTreeBeforeReflection = activeWorkspace().memoryTree()
        val reflectionTools = memoryFileToolDefinitions()

        val reflectionSession =
            sessionFactory.create(
                sessionPath = input.persistentSessionPath,
                systemPrompt = MemoryReflectionPromptBuilder.systemPrompt(memoryTreeBeforeReflection),
                model = model,
                tools = agentTools(reflectionTools),
                authRequirement = authRequirement,
                apiKey = apiKey,
                thinkingLevel = thinkingConfig.level,
                thinkingBudgets = thinkingConfig.budgets,
                observationHooks =
                observationHooks(input, ClauneTelemetryPhase.MEMORY_REFLECTION) { context, _ ->
                    reflectionBudget.beforeToolCall(context)
                },
            )

        val reflectionUnsubscribe =
            reflectionSession.subscribe { sessionEvent ->
                if (sessionEvent is pi.coding.agent.core.AgentSessionEvent.Agent) {
                    runCatching {
                        activeRunTrace?.add(AgentTranscriptSerializer.serializeEvent(sessionEvent.event))
                    }
                    runCatching {
                        val message = (sessionEvent.event as? AgentEvent.MessageEnd)?.message as? AssistantMessage
                        if (message != null) {
                            appendProviderMessageObservation(input, ClauneTelemetryPhase.MEMORY_REFLECTION, message)
                        }
                    }
                }
            }

        runCatching {
            try {
                reflectionSession.prompt(reflectionPrompt)
            } finally {
                reflectionUnsubscribe()
            }
            val reflectionOutput = finalAssistantText(reflectionSession.messages)
            runCatching { artifactStore.writeMemoryReflectionOutput(input.runId, reflectionOutput) }
            val memoryChanged = activeWorkspace().memorySignature() != memoryBeforeReflection
            val note = reflectionOutput.trim().take(180)
            if (memoryChanged) {
                sessionCoordinator.logEvent(
                    "Memory reflection updated /work/memory.${note.takeIf(String::isNotBlank)?.let { " $it" } ?: ""}",
                )
            } else {
                sessionCoordinator.logEvent(
                    "Memory reflection made no durable update.${note.takeIf(String::isNotBlank)?.let { " $it" } ?: ""}",
                )
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

    private suspend fun observeBeforeToolCall(
        input: ModelTurnInput,
        phase: ClauneTelemetryPhase,
        context: BeforeToolCallContext,
        @Suppress("UNUSED_PARAMETER") signal: pi.ai.core.AbortSignal?,
    ): BeforeToolCallResult? {
        val payload =
            buildJsonObject {
                put("toolCallId", context.toolCall.id)
                put("toolName", context.toolCall.name)
                put("arguments", context.toolCall.arguments)
            }
        appendObservationEvent(
            ClauneTelemetryEventType.TOOL_CALL,
            input = input,
            phase = phase,
            payload = payload,
        )
        telemetryRecorder.recordToolCall(
            context = telemetryContext(input, phase),
            toolCallId = context.toolCall.id,
            toolName = context.toolCall.name,
            arguments = context.toolCall.arguments,
        )
        return null
    }

    private suspend fun observeAfterToolCall(
        input: ModelTurnInput,
        phase: ClauneTelemetryPhase,
        context: AfterToolCallContext,
        @Suppress("UNUSED_PARAMETER") signal: pi.ai.core.AbortSignal?,
    ): AfterToolCallResult? {
        val result = AgentTranscriptSerializer.serializeToolResult(context.result)
        val payload =
            buildJsonObject {
                put("toolCallId", context.toolCall.id)
                put("toolName", context.toolCall.name)
                put("isError", context.isError)
                put("result", result)
            }
        appendObservationEvent(
            ClauneTelemetryEventType.TOOL_RESULT,
            input = input,
            phase = phase,
            payload = payload,
        )
        telemetryRecorder.recordToolResult(
            context = telemetryContext(input, phase),
            toolCallId = context.toolCall.id,
            toolName = context.toolCall.name,
            isError = context.isError,
            result = result,
        )
        return null
    }

    @Suppress("ktlint:standard:function-signature")
    private suspend fun observeProviderPayload(input: ModelTurnInput, phase: ClauneTelemetryPhase, payload: Any, model: Model<*>): Any {
        val payloadKind = payload::class.simpleName ?: "unknown"
        val request = if (telemetryRecorder.recordsRawProviderPayloads) providerPayloadToJson(payload) else null
        appendObservationEvent(
            ClauneTelemetryEventType.PROVIDER_PAYLOAD,
            input = input,
            phase = phase,
            model = model,
            payload =
            buildJsonObject {
                put("payloadKind", payloadKind)
                if (request == null) {
                    put("requestOmitted", true)
                } else {
                    put("request", request)
                }
            },
        )
        if (request != null) {
            telemetryRecorder.recordProviderPayload(
                context = telemetryContext(input, phase, model),
                payloadKind = payloadKind,
                request = request,
            )
        }
        return payload
    }

    @Suppress("ktlint:standard:function-signature")
    private suspend fun observeProviderResponse(
        input: ModelTurnInput,
        phase: ClauneTelemetryPhase,
        response: ProviderResponse,
        model: Model<*>,
    ) {
        val headers = response.headers.toSortedMap()
        appendObservationEvent(
            ClauneTelemetryEventType.PROVIDER_RESPONSE,
            input = input,
            phase = phase,
            model = model,
            payload =
            buildJsonObject {
                put("status", response.status)
                put("headerNames", headers.keys.joinToString(","))
                put(
                    "headers",
                    buildJsonObject {
                        headers.forEach { (name, value) ->
                            put(name, value)
                        }
                    },
                )
            },
        )
        telemetryRecorder.recordProviderResponse(
            context = telemetryContext(input, phase, model),
            status = response.status,
            headers = headers,
        )
    }

    private fun observeProviderMessageEnd(event: AgentEvent) {
        val runInput = activeRunTrace?.input ?: return
        val message = (event as? AgentEvent.MessageEnd)?.message as? AssistantMessage ?: return
        appendProviderMessageObservation(runInput, ClauneTelemetryPhase.MAIN, message)
    }

    private fun appendProviderMessageObservation(input: ModelTurnInput, phase: ClauneTelemetryPhase, message: AssistantMessage) {
        val messageJson = AgentTranscriptSerializer.serializeMessage(message).payload
        appendObservationEvent(
            ClauneTelemetryEventType.PROVIDER_MESSAGE,
            input = input,
            phase = phase,
            payload =
            buildJsonObject {
                put("message", messageJson)
            },
        )
        telemetryRecorder.recordProviderMessage(telemetryContext(input, phase), messageJson)
    }

    private fun appendObservationEvent(
        type: ClauneTelemetryEventType,
        input: ModelTurnInput,
        phase: ClauneTelemetryPhase,
        model: Model<*>? = null,
        payload: JsonElement = buildJsonObject {},
    ) {
        val runTrace = activeRunTrace ?: return
        if (runTrace.input.runId != input.runId) {
            return
        }
        val event =
            SerializedAgentEvent(
                type.wireName,
                buildJsonObject {
                    put("runId", input.runId)
                    input.persistentSessionId?.let { put("sessionId", it) }
                    put("phase", phase.wireName)
                    put("selectedProvider", model?.provider ?: activeModelProvider.orEmpty())
                    put("selectedModel", model?.id ?: activeModelId.orEmpty())
                    put("foregroundPackage", input.screenObservation.foregroundPackage)
                    input.screenObservation.baselineSnapshotId?.let { put("baselineSnapshotId", it) }
                    put("currentSnapshotId", input.screenObservation.currentSnapshotId)
                    put("hostCallCount", logStore.recentHostCallCount())
                    put("status", sessionCoordinator.uiState.value.status.name)
                    put("payload", payload)
                },
            )
        runTrace.add(event)
    }

    private fun telemetryContext(input: ModelTurnInput, phase: ClauneTelemetryPhase, model: Model<*>? = null): ClauneTelemetryContext =
        ClauneTelemetryContext(
            input = input,
            phase = phase,
            provider = model?.provider ?: activeModelProvider.orEmpty(),
            model = model?.id ?: activeModelId.orEmpty(),
        )

    private fun providerPayloadToJson(payload: Any): JsonElement = when (payload) {
        is JsonElement -> payload
        else -> JsonPrimitive(payload.toString())
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
        const val PROMPT_VERSION = CLAUNE_PROMPT_VERSION
        private val TEST_AGENT_WORKSPACE = AgentWorkspace(
            File(System.getProperty("java.io.tmpdir"), "claune-system-prompt-test-work"),
        )

        internal fun systemPromptForTests(memoryTree: String = "/work/memory/\n"): String = SystemPromptBuilder.build(
            memoryTree = memoryTree,
            tools = listOf(
                ReadFileToolDefinition(TEST_AGENT_WORKSPACE),
                WriteFileToolDefinition(TEST_AGENT_WORKSPACE),
                EditFileToolDefinition(TEST_AGENT_WORKSPACE),
                BashToolDefinition(FailingWorkspaceShell, TEST_AGENT_WORKSPACE),
                FinishRunToolDefinition(TerminalOutcomeRecorder()),
                AskUserToolDefinition(FailingQuestionPrompter),
            ),
        )
    }
}

private object FailingQuestionPrompter : UserQuestionPrompter {
    override suspend fun askQuestion(
        toolCallId: String,
        prompt: String,
        options: List<String>,
        signal: pi.ai.core.AbortSignal?,
    ): QuestionAnswer = QuestionAnswer(options.firstOrNull().orEmpty(), QuestionAnswerKind.Option, optionIndex = 0)
}

private object FailingWorkspaceShell : WorkspaceShell {
    override suspend fun execute(command: String, timeoutSeconds: Int?) = error("Workspace shell is unavailable in prompt-only tests.")
}
