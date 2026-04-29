package com.divyanshgolyan.claune.android.llm

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.browser.customtabs.CustomTabsIntent
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import pi.ai.core.OPENAI_CODEX_PROVIDER
import pi.ai.core.providers.OAuthAuthInfo
import pi.ai.core.providers.OAuthLoginCallbacks
import pi.ai.core.providers.OAuthPrompt
import pi.coding.agent.core.AuthStorage

data class CodexAuthState(
    val connected: Boolean = false,
    val loginPending: Boolean = false,
    val awaitingManualCode: Boolean = false,
    val statusText: String = "Not connected",
    val errorText: String? = null,
)

class CodexAuthRepository(private val context: Context, agentDir: File) {
    private val applicationContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val authStorage = AuthStorage.create(File(agentDir, "auth.json").absolutePath)
    private val mutableState = MutableStateFlow(readState())
    private var manualCodeInput: CompletableDeferred<String>? = null

    val state: StateFlow<CodexAuthState> = mutableState.asStateFlow()

    fun isConnected(): Boolean = authStorage.getOAuthCredentials(OPENAI_CODEX_PROVIDER) != null

    fun hasUsableCredentials(): Boolean = authStorage.getApiKey(OPENAI_CODEX_PROVIDER) != null

    fun credentialMarker(): String {
        val credentials = authStorage.getOAuthCredentials(OPENAI_CODEX_PROVIDER) ?: return "missing"
        return "${credentials.accountId.orEmpty()}:${credentials.expires}:${credentials.access.hashCode()}"
    }

    suspend fun login() {
        if (mutableState.value.loginPending) {
            return
        }
        mutableState.value =
            CodexAuthState(
                connected = isConnected(),
                loginPending = true,
                statusText = "Waiting for ChatGPT login...",
            )
        val manualInput = CompletableDeferred<String>()
        manualCodeInput = manualInput
        try {
            withContext(Dispatchers.IO) {
                authStorage.login(
                    OPENAI_CODEX_PROVIDER,
                    OAuthLoginCallbacks(
                        onAuth = ::openAuthUrl,
                        onPrompt = ::promptForManualCode,
                        onProgress = { message ->
                            mutableState.value =
                                mutableState.value.copy(
                                    statusText = message,
                                    errorText = null,
                                )
                        },
                        onManualCodeInput = {
                            mutableState.value =
                                mutableState.value.copy(
                                    awaitingManualCode = true,
                                    statusText = "Paste the authorization code if the browser does not return automatically.",
                                )
                            manualInput.await()
                        },
                        originator = "claune-android",
                    ),
                )
            }
            mutableState.value =
                readState().copy(
                    statusText = "Connected",
                    errorText = null,
                )
        } catch (throwable: Throwable) {
            mutableState.value =
                readState().copy(
                    loginPending = false,
                    awaitingManualCode = false,
                    errorText = throwable.message ?: throwable::class.simpleName ?: "ChatGPT login failed.",
                )
        } finally {
            manualCodeInput = null
        }
    }

    fun submitManualCode(input: String) {
        val trimmed = input.trim()
        if (trimmed.isNotBlank()) {
            manualCodeInput?.complete(trimmed)
        }
    }

    fun logout() {
        manualCodeInput?.cancel()
        manualCodeInput = null
        authStorage.logout(OPENAI_CODEX_PROVIDER)
        mutableState.value = readState()
    }

    fun refresh() {
        mutableState.value = readState()
    }

    private fun readState(): CodexAuthState {
        val connected = isConnected()
        return CodexAuthState(
            connected = connected,
            statusText = if (connected) "Connected" else "Not connected",
        )
    }

    private fun openAuthUrl(authInfo: OAuthAuthInfo) {
        mutableState.value =
            mutableState.value.copy(
                statusText = authInfo.instructions ?: "Complete ChatGPT login in the browser.",
                errorText = null,
            )
        mainHandler.post {
            val uri = Uri.parse(authInfo.url)
            runCatching {
                CustomTabsIntent.Builder()
                    .build()
                    .launchUrl(context, uri)
            }.onFailure {
                applicationContext.startActivity(
                    Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    private suspend fun promptForManualCode(prompt: OAuthPrompt): String {
        mutableState.value =
            mutableState.value.copy(
                awaitingManualCode = true,
                statusText = prompt.message,
                errorText = null,
            )
        return manualCodeInput?.await().orEmpty()
    }
}
