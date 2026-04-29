package com.divyanshgolyan.claune.android.llm

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import pi.ai.core.OPENAI_CODEX_PROVIDER
import pi.ai.core.getModel
import pi.ai.core.providers.OpenAICodexOAuthCredentials
import pi.coding.agent.core.AuthStorage

class ClauneAgentSessionFactoryTest {
    @Test
    fun `api key models write provider api key auth`() {
        val auth = newAuthStorage()
        val model = requireNotNull(getModel("anthropic", "claude-haiku-4-5"))

        configureAuthStorageForModel(
            authStorage = auth,
            model = model,
            authRequirement = ClauneAuthRequirement.ApiKey(ClauneApiKeySlot.Anthropic),
            apiKey = "anthropic-key",
        )

        assertEquals("anthropic-key", auth.getApiKey("anthropic"))
        assertNull(auth.getOAuthCredentials(OPENAI_CODEX_PROVIDER))
    }

    @Test
    fun `codex models do not overwrite oauth credentials with an api key`() {
        val auth = newAuthStorage()
        val credentials =
            OpenAICodexOAuthCredentials(
                access = "access-token",
                refresh = "refresh-token",
                expires = System.currentTimeMillis() + 60_000,
                accountId = "acct_123",
            )
        auth.setOAuthCredentials(OPENAI_CODEX_PROVIDER, credentials)
        val model = requireNotNull(getModel(OPENAI_CODEX_PROVIDER, "gpt-5.4-mini"))

        configureAuthStorageForModel(
            authStorage = auth,
            model = model,
            authRequirement = ClauneAuthRequirement.OAuth(OPENAI_CODEX_PROVIDER),
            apiKey = "",
        )

        val stored = auth.getOAuthCredentials(OPENAI_CODEX_PROVIDER)
        assertNotNull(stored)
        assertEquals("access-token", stored?.access)
        assertEquals("refresh-token", stored?.refresh)
        assertEquals("acct_123", stored?.accountId)
        assertEquals("access-token", auth.getApiKey(OPENAI_CODEX_PROVIDER))
    }

    private fun newAuthStorage(): AuthStorage {
        val file = Files.createTempDirectory("claune-auth-test").resolve("auth.json")
        return AuthStorage.create(file.toString())
    }
}
