package com.divyanshgolyan.claune.android.app

import android.app.Application
import android.content.Context
import com.divyanshgolyan.claune.android.accessibility.AccessibilityBridge
import com.divyanshgolyan.claune.android.data.local.InMemorySessionLogStore
import com.divyanshgolyan.claune.android.data.preferences.DataStoreSettingsStore
import com.divyanshgolyan.claune.android.llm.StubModelGateway
import com.divyanshgolyan.claune.android.phone.DemoPhoneBridge
import com.divyanshgolyan.claune.android.phone.PhoneControlMode
import com.divyanshgolyan.claune.android.phone.RoutedPhoneBridge
import com.divyanshgolyan.claune.android.runtime.AgentLoop
import com.divyanshgolyan.claune.android.runtime.SessionCoordinator
import com.divyanshgolyan.claune.android.scripting.QuickJsScriptRuntime

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
    val demoPhoneBridge = DemoPhoneBridge()
    val routedPhoneBridge = RoutedPhoneBridge(accessibilityBridge, accessibilityBridge, demoPhoneBridge)
    val modelGateway = StubModelGateway()
    val scriptRuntime =
        QuickJsScriptRuntime(
            phoneObserver = routedPhoneBridge,
            phoneActuator = routedPhoneBridge,
            sessionCoordinator = sessionCoordinator,
            logStore = logStore,
        )
    val agentLoop =
        AgentLoop(
            phoneObserver = routedPhoneBridge,
            modelGateway = modelGateway,
            sessionCoordinator = sessionCoordinator,
            logStore = logStore,
        )

    fun setDemoPhoneEnabled(enabled: Boolean) {
        val targetMode =
            if (enabled) {
                PhoneControlMode.DemoPhone
            } else {
                PhoneControlMode.LiveAccessibility
            }
        if (routedPhoneBridge.currentMode() == targetMode) {
            return
        }
        routedPhoneBridge.setMode(targetMode)
        sessionCoordinator.logEvent(
            if (enabled) {
                "Demo phone mode enabled."
            } else {
                "Live accessibility mode enabled."
            },
        )
    }
}

fun Context.clauneContainer(): ClauneContainer {
    val application =
        applicationContext as? ClauneApplication
            ?: error("Application is not ClauneApplication")
    return application.container
}
