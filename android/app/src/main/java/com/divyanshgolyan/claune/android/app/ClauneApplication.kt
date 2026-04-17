package com.divyanshgolyan.claune.android.app

import android.app.Application
import android.content.Context
import com.divyanshgolyan.claune.android.BuildConfig
import com.divyanshgolyan.claune.android.accessibility.AccessibilityBridge
import com.divyanshgolyan.claune.android.data.local.ArtifactSessionLogStore
import com.divyanshgolyan.claune.android.data.local.FileAgentRunArtifactStore
import com.divyanshgolyan.claune.android.data.local.InMemorySessionLogStore
import com.divyanshgolyan.claune.android.data.local.SessionLogStore
import com.divyanshgolyan.claune.android.llm.PiAgentModelGateway
import com.divyanshgolyan.claune.android.runtime.AgentLoop
import com.divyanshgolyan.claune.android.runtime.SessionCoordinator
import com.divyanshgolyan.claune.android.scripting.QuickJsScriptRuntime
import java.io.File

class ClauneApplication : Application() {
    val container: ClauneContainer by lazy {
        ClauneContainer(this)
    }
}

class ClauneContainer(application: Application) {
    private val memoryLogStore = InMemorySessionLogStore()
    val artifactStore = FileAgentRunArtifactStore(File(application.filesDir, "agent-runs"))
    val logStore: SessionLogStore by lazy {
        ArtifactSessionLogStore(
            delegate = memoryLogStore,
            artifactStore = artifactStore,
            currentSessionIdProvider = { sessionCoordinator.uiState.value.sessionId },
        )
    }
    val sessionCoordinator = SessionCoordinator(logStore)
    val accessibilityBridge = AccessibilityBridge(sessionCoordinator)
    val scriptRuntime =
        QuickJsScriptRuntime(
            phoneObserver = accessibilityBridge,
            phoneActuator = accessibilityBridge,
            sessionCoordinator = sessionCoordinator,
            logStore = logStore,
        )
    val modelGateway =
        PiAgentModelGateway(
            apiKey = BuildConfig.ANTHROPIC_API_KEY,
            scriptRuntime = scriptRuntime,
            phoneObserver = accessibilityBridge,
            sessionCoordinator = sessionCoordinator,
            artifactStore = artifactStore,
        )
    val agentLoop =
        AgentLoop(
            phoneObserver = accessibilityBridge,
            modelGateway = modelGateway,
            sessionCoordinator = sessionCoordinator,
            logStore = logStore,
            artifactStore = artifactStore,
        )
}

fun Context.clauneContainer(): ClauneContainer {
    val application =
        applicationContext as? ClauneApplication
            ?: error("Application is not ClauneApplication")
    return application.container
}
