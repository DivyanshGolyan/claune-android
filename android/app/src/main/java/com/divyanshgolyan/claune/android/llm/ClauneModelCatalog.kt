package com.divyanshgolyan.claune.android.llm

import com.divyanshgolyan.claune.android.data.local.ClauneModel
import com.divyanshgolyan.claune.android.data.local.ClauneThinkingLevel
import com.divyanshgolyan.claune.android.data.local.SettingsState
import pi.agent.core.AgentThinkingLevel
import pi.ai.core.ANTHROPIC_PROVIDER
import pi.ai.core.GOOGLE_PROVIDER
import pi.ai.core.Model
import pi.ai.core.OPENAI_CODEX_PROVIDER
import pi.ai.core.ThinkingBudgets
import pi.ai.core.getModel

enum class ClauneApiKeySlot {
    Anthropic,
    Gemini,
}

sealed interface ClauneAuthRequirement {
    data class ApiKey(val slot: ClauneApiKeySlot) : ClauneAuthRequirement

    data class OAuth(val provider: String) : ClauneAuthRequirement
}

data class ClauneModelOption(
    val id: ClauneModel,
    val provider: String,
    val modelId: String,
    val label: String,
    val groupLabel: String,
    val authRequirement: ClauneAuthRequirement,
) {
    val canonicalName: String = "$provider/$modelId"
    val apiKeySlot: ClauneApiKeySlot?
        get() = (authRequirement as? ClauneAuthRequirement.ApiKey)?.slot
}

data class ClauneThinkingConfig(val level: AgentThinkingLevel, val budgets: ThinkingBudgets?)

class ResolvedClauneModel {
    val option: ClauneModelOption
    val model: Model<*>
    val authRequirement: ClauneAuthRequirement

    constructor(option: ClauneModelOption, model: Model<*>, authRequirement: ClauneAuthRequirement) {
        this.option = option
        this.model = model
        this.authRequirement = authRequirement
    }

    val missingAuthMessage: String
        get() = when (option.apiKeySlot) {
            ClauneApiKeySlot.Anthropic ->
                "Anthropic API key is missing. Open Settings in the app, add your API key, and retry."
            ClauneApiKeySlot.Gemini ->
                "Gemini API key is missing. Open Settings in the app, add your API key, and retry."
            null -> "ChatGPT is not connected. Connect ChatGPT in Settings and retry."
        }

    val apiKeySlot: ClauneApiKeySlot?
        get() = (authRequirement as? ClauneAuthRequirement.ApiKey)?.slot
}

object ClauneModelCatalog {
    val options: List<ClauneModelOption> =
        listOf(
            ClauneModelOption(
                id = ClauneModel.Haiku,
                provider = ANTHROPIC_PROVIDER,
                modelId = "claude-haiku-4-5",
                label = "Haiku",
                groupLabel = "Anthropic",
                authRequirement = ClauneAuthRequirement.ApiKey(ClauneApiKeySlot.Anthropic),
            ),
            ClauneModelOption(
                id = ClauneModel.GeminiFlashLite,
                provider = GOOGLE_PROVIDER,
                modelId = "gemini-3.1-flash-lite-preview",
                label = "Gemini",
                groupLabel = "Gemini",
                authRequirement = ClauneAuthRequirement.ApiKey(ClauneApiKeySlot.Gemini),
            ),
            codex(ClauneModel.ChatGpt51, "gpt-5.1", "GPT-5.1"),
            codex(ClauneModel.ChatGpt51CodexMax, "gpt-5.1-codex-max", "GPT-5.1 Codex Max"),
            codex(ClauneModel.ChatGpt51CodexMini, "gpt-5.1-codex-mini", "GPT-5.1 Codex Mini"),
            codex(ClauneModel.ChatGpt52, "gpt-5.2", "GPT-5.2"),
            codex(ClauneModel.ChatGpt52Codex, "gpt-5.2-codex", "GPT-5.2 Codex"),
            codex(ClauneModel.ChatGpt53Codex, "gpt-5.3-codex", "GPT-5.3 Codex"),
            codex(ClauneModel.ChatGpt53CodexSpark, "gpt-5.3-codex-spark", "GPT-5.3 Codex Spark"),
            codex(ClauneModel.ChatGpt54, "gpt-5.4", "GPT-5.4"),
            codex(ClauneModel.ChatGpt54Mini, "gpt-5.4-mini", "GPT-5.4 Mini"),
        )

    fun optionFor(id: ClauneModel): ClauneModelOption = options.firstOrNull { it.id == id } ?: options.first()

    fun groupedOptions(): Map<String, List<ClauneModelOption>> = options.groupBy { it.groupLabel }

    fun selectedModelName(settingsState: SettingsState): String = optionFor(settingsState.selectedModel).modelId

    fun resolve(settingsState: SettingsState): ResolvedClauneModel {
        val option = optionFor(settingsState.selectedModel)
        val model = requireNotNull(getModel(option.provider, option.modelId)) {
            "Model ${option.canonicalName} is not registered in pi-ai-core."
        }
        return ResolvedClauneModel(
            option = option,
            model = model,
            authRequirement = option.authRequirement,
        )
    }

    private fun codex(id: ClauneModel, modelId: String, label: String): ClauneModelOption = ClauneModelOption(
        id = id,
        provider = OPENAI_CODEX_PROVIDER,
        modelId = modelId,
        label = label,
        groupLabel = "ChatGPT Subscription",
        authRequirement = ClauneAuthRequirement.OAuth(OPENAI_CODEX_PROVIDER),
    )
}

fun SettingsState.apiKeyFor(slot: ClauneApiKeySlot): String = when (slot) {
    ClauneApiKeySlot.Anthropic -> anthropicApiKey
    ClauneApiKeySlot.Gemini -> geminiApiKey
}

fun SettingsState.hasAuthFor(option: ClauneModelOption, codexConnected: Boolean): Boolean =
    when (val authRequirement = option.authRequirement) {
        is ClauneAuthRequirement.ApiKey -> apiKeyFor(authRequirement.slot).isNotBlank()
        is ClauneAuthRequirement.OAuth -> codexConnected
    }

fun SettingsState.thinkingConfigFor(model: ClauneModel): ClauneThinkingConfig {
    val level = thinkingLevel.toAgentThinkingLevel()
    val budgets =
        if (model == ClauneModel.Haiku && level != AgentThinkingLevel.OFF) {
            ThinkingBudgets(
                minimal = haikuThinkingBudget,
                low = haikuThinkingBudget,
                medium = haikuThinkingBudget,
                high = haikuThinkingBudget,
            )
        } else {
            null
        }
    return ClauneThinkingConfig(level = level, budgets = budgets)
}

private fun ClauneThinkingLevel.toAgentThinkingLevel(): AgentThinkingLevel = when (this) {
    ClauneThinkingLevel.Off -> AgentThinkingLevel.OFF
    ClauneThinkingLevel.Minimal -> AgentThinkingLevel.MINIMAL
    ClauneThinkingLevel.Low -> AgentThinkingLevel.LOW
    ClauneThinkingLevel.Medium -> AgentThinkingLevel.MEDIUM
    ClauneThinkingLevel.High -> AgentThinkingLevel.HIGH
    ClauneThinkingLevel.XHigh -> AgentThinkingLevel.XHIGH
}
