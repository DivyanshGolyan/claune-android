package com.divyanshgolyan.claune.android.data.local

import com.divyanshgolyan.claune.android.llm.ClauneApiKeySlot
import com.divyanshgolyan.claune.android.llm.ClauneModelCatalog
import com.divyanshgolyan.claune.android.llm.apiKeyFor
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsStoreTest {
    @Test
    fun `stored model names resolve to configured provider models`() {
        assertEquals(ClauneModel.Haiku, ClauneModel.fromStorage("Haiku"))
        assertEquals(ClauneModel.GeminiFlashLite, ClauneModel.fromStorage("GeminiFlashLite"))
        assertEquals(ClauneModel.Haiku, ClauneModel.fromStorage("missing"))
        assertEquals(ClauneModel.Haiku, ClauneModel.fromStorage(null))
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
}
