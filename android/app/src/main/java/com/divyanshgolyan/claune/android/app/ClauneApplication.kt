package com.divyanshgolyan.claune.android.app

import android.app.Application
import android.content.Context
import com.divyanshgolyan.claune.android.accessibility.AccessibilityBridge
import com.divyanshgolyan.claune.android.data.local.InMemorySessionLogStore
import com.divyanshgolyan.claune.android.data.preferences.DataStoreSettingsStore
import com.divyanshgolyan.claune.android.llm.StubModelGateway
import com.divyanshgolyan.claune.android.runtime.AgentLoop
import com.divyanshgolyan.claune.android.runtime.SessionCoordinator

class ClauneApplication : Application() {
    val container: ClauneContainer by lazy {
        ClauneContainer(this)
    }
}

class ClauneContainer(appContext: Context) {
    val settingsStore = DataStoreSettingsStore(appContext)
    val logStore = InMemorySessionLogStore()
    val sessionCoordinator = SessionCoordinator(logStore)
    val accessibilityBridge = AccessibilityBridge(sessionCoordinator)
    val modelGateway = StubModelGateway()
    val agentLoop =
        AgentLoop(
            phoneObserver = accessibilityBridge,
            modelGateway = modelGateway,
            sessionCoordinator = sessionCoordinator,
            logStore = logStore,
        )
}

fun Context.clauneContainer(): ClauneContainer {
    val application =
        applicationContext as? ClauneApplication
            ?: error("Application is not ClauneApplication")
    return application.container
}
