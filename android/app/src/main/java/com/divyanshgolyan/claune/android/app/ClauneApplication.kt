package com.divyanshgolyan.claune.android.app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.divyanshgolyan.claune.android.BuildConfig
import com.divyanshgolyan.claune.android.accessibility.AccessibilityBridge
import com.divyanshgolyan.claune.android.data.local.ArtifactSessionLogStore
import com.divyanshgolyan.claune.android.data.local.CodingSessionStore
import com.divyanshgolyan.claune.android.data.local.FileAgentRunArtifactStore
import com.divyanshgolyan.claune.android.data.local.FileMemoryStore
import com.divyanshgolyan.claune.android.data.local.InMemorySessionLogStore
import com.divyanshgolyan.claune.android.data.local.MemoryStore
import com.divyanshgolyan.claune.android.data.local.SessionLogStore
import com.divyanshgolyan.claune.android.data.local.SettingsStore
import com.divyanshgolyan.claune.android.data.local.SharedPreferencesSettingsStore
import com.divyanshgolyan.claune.android.llm.PiAgentModelGateway
import com.divyanshgolyan.claune.android.overlay.SessionOverlayController
import com.divyanshgolyan.claune.android.runtime.AgentLoop
import com.divyanshgolyan.claune.android.runtime.SessionCoordinator
import com.divyanshgolyan.claune.android.scripting.QuickJsScriptRuntime
import java.io.File

class ClauneApplication : Application() {
    val container: ClauneContainer by lazy {
        ClauneContainer(this)
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                private var startedCount = 0

                override fun onActivityStarted(activity: Activity) {
                    startedCount += 1
                    container.sessionCoordinator.setAppInForeground(true)
                }

                override fun onActivityStopped(activity: Activity) {
                    startedCount = (startedCount - 1).coerceAtLeast(0)
                    container.sessionCoordinator.setAppInForeground(startedCount > 0)
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }
}

class ClauneContainer(application: Application) {
    private val agentDir = File(application.filesDir, "pi-agent")
    private val memoryLogStore = InMemorySessionLogStore()
    val artifactStore = FileAgentRunArtifactStore(File(application.filesDir, "agent-runs"))
    val memoryStore: MemoryStore = FileMemoryStore(File(application.filesDir, "memory.md"))
    val codingSessionStore = CodingSessionStore(cwd = application.filesDir.absolutePath, agentDir = agentDir)
    val settingsStore: SettingsStore =
        SharedPreferencesSettingsStore(
            context = application,
            defaultAnthropicApiKey = BuildConfig.ANTHROPIC_API_KEY,
        )
    val logStore: SessionLogStore by lazy {
        ArtifactSessionLogStore(
            delegate = memoryLogStore,
            artifactStore = artifactStore,
            currentSessionIdProvider = { sessionCoordinator.uiState.value.sessionId },
        )
    }
    val sessionCoordinator = SessionCoordinator(logStore, codingSessionStore)
    val accessibilityBridge = AccessibilityBridge(application, sessionCoordinator)
    val overlayController = SessionOverlayController(application, sessionCoordinator.uiState)
    val scriptRuntime =
        QuickJsScriptRuntime(
            phoneObserver = accessibilityBridge,
            phoneActuator = accessibilityBridge,
            sessionCoordinator = sessionCoordinator,
            logStore = logStore,
        )
    val modelGateway =
        PiAgentModelGateway(
            settingsStore = settingsStore,
            memoryStore = memoryStore,
            scriptRuntime = scriptRuntime,
            phoneObserver = accessibilityBridge,
            sessionCoordinator = sessionCoordinator,
            artifactStore = artifactStore,
            codingSessionStore = codingSessionStore,
            agentDir = agentDir,
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
