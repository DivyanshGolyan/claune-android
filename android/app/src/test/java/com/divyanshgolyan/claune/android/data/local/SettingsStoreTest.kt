package com.divyanshgolyan.claune.android.data.local

import com.divyanshgolyan.claune.android.llm.ClauneApiKeySlot
import com.divyanshgolyan.claune.android.llm.ClauneAuthRequirement
import com.divyanshgolyan.claune.android.llm.ClauneModelCatalog
import com.divyanshgolyan.claune.android.llm.apiKeyFor
import com.divyanshgolyan.claune.android.llm.hasAuthFor
import com.divyanshgolyan.claune.android.llm.thinkingConfigFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pi.agent.core.AgentThinkingLevel

class SettingsStoreTest {
    @Test
    fun `stored model names resolve to configured provider models`() {
        assertEquals(ClauneModel.Haiku, ClauneModel.fromStorage("Haiku"))
        assertEquals(ClauneModel.GeminiFlashLite, ClauneModel.fromStorage("GeminiFlashLite"))
        assertEquals(ClauneModel.ChatGpt54, ClauneModel.fromStorage("ChatGpt54"))
        assertEquals(ClauneModel.Haiku, ClauneModel.fromStorage("missing"))
        assertEquals(ClauneModel.Haiku, ClauneModel.fromStorage(null))
    }

    @Test
    fun `stored thinking levels resolve to configured values`() {
        assertEquals(ClauneThinkingLevel.Off, ClauneThinkingLevel.fromStorage("Off"))
        assertEquals(ClauneThinkingLevel.Minimal, ClauneThinkingLevel.fromStorage("Minimal"))
        assertEquals(ClauneThinkingLevel.Low, ClauneThinkingLevel.fromStorage("Low"))
        assertEquals(ClauneThinkingLevel.Medium, ClauneThinkingLevel.fromStorage("Medium"))
        assertEquals(ClauneThinkingLevel.High, ClauneThinkingLevel.fromStorage("High"))
        assertEquals(ClauneThinkingLevel.XHigh, ClauneThinkingLevel.fromStorage("XHigh"))
        assertEquals(ClauneThinkingLevel.Medium, ClauneThinkingLevel.fromStorage("missing"))
        assertEquals(ClauneThinkingLevel.Medium, ClauneThinkingLevel.fromStorage(null))
    }

    @Test
    fun `model catalog owns provider and model mapping`() {
        val haiku = ClauneModelCatalog.optionFor(ClauneModel.Haiku)
        val gemini = ClauneModelCatalog.optionFor(ClauneModel.GeminiFlashLite)

        assertEquals("anthropic", haiku.provider)
        assertEquals("claude-haiku-4-5", haiku.modelId)
        assertEquals("google", gemini.provider)
        assertEquals("gemini-3.1-flash-lite-preview", gemini.modelId)
    }

    @Test
    fun `model catalog exposes local Codex subscription models`() {
        val codexModels = ClauneModelCatalog.options.filter { it.provider == "openai-codex" }

        assertEquals(
            listOf(
                "gpt-5.1",
                "gpt-5.1-codex-max",
                "gpt-5.1-codex-mini",
                "gpt-5.2",
                "gpt-5.2-codex",
                "gpt-5.3-codex",
                "gpt-5.3-codex-spark",
                "gpt-5.4",
                "gpt-5.4-mini",
            ),
            codexModels.map { it.modelId },
        )
        codexModels.forEach { option ->
            assertEquals(ClauneAuthRequirement.OAuth("openai-codex"), option.authRequirement)
        }
    }

    @Test
    fun `api key slots choose matching provider key`() {
        val haikuSettings =
            SettingsState(
                selectedModel = ClauneModel.Haiku,
                anthropicApiKey = "anthropic-key",
                geminiApiKey = "gemini-key",
            )

        assertEquals("anthropic-key", haikuSettings.apiKeyFor(ClauneApiKeySlot.Anthropic))
        assertEquals("gemini-key", haikuSettings.apiKeyFor(ClauneApiKeySlot.Gemini))
    }

    @Test
    fun `auth readiness separates api keys from codex oauth`() {
        val settings =
            SettingsState(
                selectedModel = ClauneModel.Haiku,
                anthropicApiKey = "anthropic-key",
                geminiApiKey = "",
            )

        assertEquals(true, settings.hasAuthFor(ClauneModelCatalog.optionFor(ClauneModel.Haiku), codexConnected = false))
        assertEquals(false, settings.hasAuthFor(ClauneModelCatalog.optionFor(ClauneModel.GeminiFlashLite), codexConnected = false))
        assertEquals(false, settings.hasAuthFor(ClauneModelCatalog.optionFor(ClauneModel.ChatGpt54), codexConnected = false))
        assertEquals(true, settings.hasAuthFor(ClauneModelCatalog.optionFor(ClauneModel.ChatGpt54), codexConnected = true))
    }

    @Test
    fun `thinking settings apply budget only to haiku`() {
        val settings =
            SettingsState(
                thinkingLevel = ClauneThinkingLevel.High,
                haikuThinkingBudget = 4096,
            )

        val haikuThinking = settings.thinkingConfigFor(ClauneModel.Haiku)
        assertEquals(AgentThinkingLevel.HIGH, haikuThinking.level)
        assertEquals(4096, haikuThinking.budgets?.high)

        val geminiThinking = settings.thinkingConfigFor(ClauneModel.GeminiFlashLite)
        assertEquals(AgentThinkingLevel.HIGH, geminiThinking.level)
        assertNull(geminiThinking.budgets)
    }

    @Test
    fun `off thinking does not carry a haiku budget`() {
        val settings =
            SettingsState(
                thinkingLevel = ClauneThinkingLevel.Off,
                haikuThinkingBudget = 4096,
            )

        val config = settings.thinkingConfigFor(ClauneModel.Haiku)
        assertEquals(AgentThinkingLevel.OFF, config.level)
        assertNull(config.budgets)
    }
}
