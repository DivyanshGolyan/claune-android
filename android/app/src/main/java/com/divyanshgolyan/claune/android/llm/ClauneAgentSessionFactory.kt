package com.divyanshgolyan.claune.android.llm

import com.divyanshgolyan.claune.android.data.local.CodingSessionStore
import java.io.File
import pi.agent.core.AfterToolCallContext
import pi.agent.core.AfterToolCallResult
import pi.agent.core.Agent
import pi.agent.core.AgentOptions
import pi.agent.core.AgentThinkingLevel
import pi.agent.core.AgentTool
import pi.agent.core.BeforeToolCallContext
import pi.agent.core.BeforeToolCallResult
import pi.agent.core.InitialAgentState
import pi.ai.core.AbortSignal
import pi.ai.core.CacheRetention
import pi.ai.core.Model
import pi.ai.core.ProviderResponse
import pi.ai.core.ThinkingBudgets
import pi.coding.agent.core.AgentSession
import pi.coding.agent.core.AgentSessionConfig
import pi.coding.agent.core.AuthStorage
import pi.coding.agent.core.DefaultResourceLoader
import pi.coding.agent.core.DefaultResourceLoaderOptions
import pi.coding.agent.core.ModelRegistry
import pi.coding.agent.core.SettingsManager
import pi.coding.agent.core.compaction.CompactionSettings
import pi.coding.agent.core.convertToLlm

data class AgentObservationHooks(
    val beforeToolCall: (suspend (BeforeToolCallContext, AbortSignal?) -> BeforeToolCallResult?)? = null,
    val afterToolCall: (suspend (AfterToolCallContext, AbortSignal?) -> AfterToolCallResult?)? = null,
    val onPayload: (suspend (payload: Any, model: Model<*>) -> Any?)? = null,
    val onResponse: (suspend (response: ProviderResponse, model: Model<*>) -> Unit)? = null,
) {
    companion object {
        val None = AgentObservationHooks()
    }
}

class ClauneAgentSessionFactory(private val codingSessionStore: CodingSessionStore, private val agentDir: File) {
    suspend fun create(
        sessionPath: String?,
        systemPrompt: String,
        model: Model<*>,
        tools: List<AgentTool<*>>,
        authRequirement: ClauneAuthRequirement,
        apiKey: String? = null,
        thinkingLevel: AgentThinkingLevel = AgentThinkingLevel.MEDIUM,
        thinkingBudgets: ThinkingBudgets? = null,
        observationHooks: AgentObservationHooks = AgentObservationHooks.None,
    ): AgentSession {
        val authStorage = AuthStorage.create(File(agentDir, "auth.json").absolutePath)
        configureAuthStorageForModel(authStorage, model, authRequirement, apiKey)
        val modelRegistry = ModelRegistry.create(authStorage)
        val sessionManager = codingSessionStore.sessionManager(sessionPath)
        val settingsManager =
            SettingsManager.create(sessionManager.getCwd(), agentDir.absolutePath).apply {
                setDefaultModelAndProvider(model.provider, model.id)
                setDefaultThinkingLevel(thinkingLevel)
                setThinkingBudgets(thinkingBudgets)
                setCompactionSettings(CLAUNE_COMPACTION_SETTINGS)
            }
        val resourceLoader =
            DefaultResourceLoader(
                DefaultResourceLoaderOptions(
                    cwd = sessionManager.getCwd(),
                    agentDir = agentDir.absolutePath,
                    systemPrompt = systemPrompt,
                ),
            )
        resourceLoader.reload()

        val restored = sessionManager.buildSessionContext()
        val resolvedModel = model
        val resolvedThinkingLevel =
            if (resolvedModel.reasoning) {
                thinkingLevel
            } else {
                AgentThinkingLevel.OFF
            }

        val agent =
            Agent(
                AgentOptions(
                    initialState =
                    InitialAgentState(
                        systemPrompt = systemPrompt,
                        model = resolvedModel,
                        thinkingLevel = resolvedThinkingLevel,
                        tools = tools,
                        messages = restored.messages,
                    ),
                    convertToLlm = ::convertToLlm,
                    getApiKey = modelRegistry::getApiKey,
                    cacheRetention = CacheRetention.SHORT,
                    sessionId = sessionManager.getSessionId(),
                    toolExecution = pi.agent.core.ToolExecutionMode.SEQUENTIAL,
                    beforeToolCall = observationHooks.beforeToolCall,
                    steeringMode = settingsManager.getSteeringMode(),
                    followUpMode = settingsManager.getFollowUpMode(),
                    transport = settingsManager.getTransport(),
                    thinkingBudgets = settingsManager.getThinkingBudgets(),
                    maxRetryDelayMs = settingsManager.getRetrySettings().maxDelayMs,
                    afterToolCall = observationHooks.afterToolCall,
                    onPayload = observationHooks.onPayload,
                    onResponse = observationHooks.onResponse,
                ),
            )

        val restoredModelMatches =
            restored.model?.provider == resolvedModel.provider &&
                restored.model?.modelId == resolvedModel.id
        if (restored.messages.isEmpty() || !restoredModelMatches) {
            sessionManager.appendModelChange(resolvedModel.provider, resolvedModel.id)
        }
        val restoredThinkingLevel = parseThinkingLevel(restored.thinkingLevel)
        if (restoredThinkingLevel != resolvedThinkingLevel) {
            sessionManager.appendThinkingLevelChange(resolvedThinkingLevel.name.lowercase())
        }

        return AgentSession(
            AgentSessionConfig(
                agent = agent,
                sessionManager = sessionManager,
                settingsManager = settingsManager,
                cwd = sessionManager.getCwd(),
                resourceLoader = resourceLoader,
                modelRegistry = modelRegistry,
            ),
        )
    }

    private fun parseThinkingLevel(value: String?): AgentThinkingLevel? = when (value?.lowercase()) {
        "off" -> AgentThinkingLevel.OFF
        "minimal" -> AgentThinkingLevel.MINIMAL
        "low" -> AgentThinkingLevel.LOW
        "medium" -> AgentThinkingLevel.MEDIUM
        "high" -> AgentThinkingLevel.HIGH
        "xhigh" -> AgentThinkingLevel.XHIGH
        else -> null
    }

    private companion object {
        val CLAUNE_COMPACTION_SETTINGS = CompactionSettings(
            enabled = true,
            reserveTokens = 100_000,
            keepRecentTokens = 20_000,
        )
    }
}

internal fun configureAuthStorageForModel(
    authStorage: AuthStorage,
    model: Model<*>,
    authRequirement: ClauneAuthRequirement,
    apiKey: String?,
) {
    if (authRequirement is ClauneAuthRequirement.ApiKey) {
        authStorage.setApiKey(model.provider, apiKey.orEmpty())
    }
}
