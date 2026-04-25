package com.divyanshgolyan.claune.android.llm

import com.divyanshgolyan.claune.android.data.local.ClauneModel
import com.divyanshgolyan.claune.android.data.local.SettingsState
import pi.ai.core.Model
import pi.ai.core.getModel

enum class ClauneApiKeySlot {
    Anthropic,
    Gemini,
}

data class ClauneModelOption(
    val id: ClauneModel,
    val provider: String,
    val modelId: String,
    val label: String,
    val apiKeySlot: ClauneApiKeySlot,
) {
    val canonicalName: String = "$provider/$modelId"
}

class ResolvedClauneModel {
    val option: ClauneModelOption
    val model: Model<*>
    val apiKey: String

    constructor(option: ClauneModelOption, model: Model<*>, apiKey: String) {
        this.option = option
        this.model = model
        this.apiKey = apiKey
    }

    val missingKeyMessage: String
        get() = when (option.apiKeySlot) {
            ClauneApiKeySlot.Anthropic ->
                "Anthropic API key is missing. Open Settings in the app, add your API key, and retry."
            ClauneApiKeySlot.Gemini ->
                "Gemini API key is missing. Open Settings in the app, add your API key, and retry."
        }
}

object ClauneModelCatalog {
    val options: List<ClauneModelOption> =
        listOf(
            ClauneModelOption(
                id = ClauneModel.Haiku,
                provider = "anthropic",
                modelId = "claude-haiku-4-5",
                label = "Haiku",
                apiKeySlot = ClauneApiKeySlot.Anthropic,
            ),
            ClauneModelOption(
                id = ClauneModel.GeminiFlashLite,
                provider = "google",
                modelId = "gemini-3.1-flash-lite-preview",
                label = "Gemini",
                apiKeySlot = ClauneApiKeySlot.Gemini,
            ),
        )

    fun optionFor(id: ClauneModel): ClauneModelOption = options.firstOrNull { it.id == id } ?: options.first()

    fun selectedModelName(settingsState: SettingsState): String = optionFor(settingsState.selectedModel).modelId

    fun resolve(settingsState: SettingsState): ResolvedClauneModel {
        val option = optionFor(settingsState.selectedModel)
        val model = requireNotNull(getModel(option.provider, option.modelId)) {
            "Model ${option.canonicalName} is not registered in pi-ai-core."
        }
        return ResolvedClauneModel(
            option = option,
            model = model,
            apiKey = settingsState.apiKeyFor(option.apiKeySlot),
        )
    }
}

fun SettingsState.apiKeyFor(slot: ClauneApiKeySlot): String = when (slot) {
    ClauneApiKeySlot.Anthropic -> anthropicApiKey
    ClauneApiKeySlot.Gemini -> geminiApiKey
}
