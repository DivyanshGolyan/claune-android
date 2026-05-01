package com.divyanshgolyan.claune.android.app

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.divyanshgolyan.claune.android.BuildConfig
import com.divyanshgolyan.claune.android.accessibility.AccessibilityBridge
import com.divyanshgolyan.claune.android.data.local.ArtifactSessionLogStore
import com.divyanshgolyan.claune.android.data.local.CodingSessionStore
import com.divyanshgolyan.claune.android.data.local.DataStoreSettingsStore
import com.divyanshgolyan.claune.android.data.local.FileAgentRunArtifactStore
import com.divyanshgolyan.claune.android.data.local.InMemorySessionLogStore
import com.divyanshgolyan.claune.android.data.local.SessionLogStore
import com.divyanshgolyan.claune.android.data.local.SettingsStore
import com.divyanshgolyan.claune.android.llm.CodexAuthRepository
import com.divyanshgolyan.claune.android.llm.PiAgentModelGateway
import com.divyanshgolyan.claune.android.overlay.SessionOverlayController
import com.divyanshgolyan.claune.android.runtime.AgentLoop
import com.divyanshgolyan.claune.android.runtime.QuestionPromptCoordinator
import com.divyanshgolyan.claune.android.runtime.SessionCoordinator
import com.divyanshgolyan.claune.android.scripting.AndroidInstalledAppRegistry
import com.divyanshgolyan.claune.android.scripting.QuickJsScriptRuntime
import com.divyanshgolyan.claune.android.shell.BashkitWorkspaceShell
import com.divyanshgolyan.claune.android.telemetry.ClauneTelemetry
import com.divyanshgolyan.claune.android.workspace.AgentWorkspace
import java.io.File

class ClauneApplication : Application() {
    val container: ClauneContainer by lazy {
        ClauneContainer(this)
    }

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    container.sessionCoordinator.setAppInForeground(true)
                }

                override fun onStop(owner: LifecycleOwner) {
                    container.sessionCoordinator.setAppInForeground(false)
                }
            },
        )
    }
}

class ClauneContainer(application: Application) {
    val workspace = AgentWorkspace(File(application.filesDir, "work")).also { it.initialize() }
    private val agentDir = workspace.piAgentDir
    private val memoryLogStore = InMemorySessionLogStore()
    val artifactStore = FileAgentRunArtifactStore(workspace.runsDir)
    val codingSessionStore = CodingSessionStore(cwd = workspace.rootDir.absolutePath, agentDir = agentDir)
    val codexAuthRepository = CodexAuthRepository(application, agentDir)
    val telemetryRecorder = ClauneTelemetry.createRecorder()
    val settingsStore: SettingsStore =
        DataStoreSettingsStore(
            context = application,
            defaultAnthropicApiKey = BuildConfig.ANTHROPIC_API_KEY,
            defaultGeminiApiKey = BuildConfig.GEMINI_API_KEY,
        )
    val logStore: SessionLogStore by lazy {
        ArtifactSessionLogStore(
            delegate = memoryLogStore,
            artifactStore = artifactStore,
            currentRunIdProvider = { sessionCoordinator.uiState.value.activeRunId },
        )
    }
    val sessionCoordinator = SessionCoordinator(logStore, codingSessionStore)
    val questionPromptCoordinator = QuestionPromptCoordinator(sessionCoordinator)
    val overlayController = SessionOverlayController(application, sessionCoordinator.uiState, questionPromptCoordinator)
    val accessibilityBridge = AccessibilityBridge(application, sessionCoordinator, overlayController)
    val scriptRuntime =
        QuickJsScriptRuntime(
            phoneObserver = accessibilityBridge,
            phoneActuator = accessibilityBridge,
            installedAppRegistry = AndroidInstalledAppRegistry(application),
            sessionCoordinator = sessionCoordinator,
            logStore = logStore,
        )
    val workspaceShell = BashkitWorkspaceShell(workspace, scriptRuntime)
    val modelGateway =
        PiAgentModelGateway(
            settingsStore = settingsStore,
            logStore = logStore,
            sessionCoordinator = sessionCoordinator,
            questionPromptCoordinator = questionPromptCoordinator,
            artifactStore = artifactStore,
            codingSessionStore = codingSessionStore,
            agentDir = agentDir,
            codexAuthRepository = codexAuthRepository,
            workspace = workspace,
            workspaceShell = workspaceShell,
            telemetryRecorder = telemetryRecorder,
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
