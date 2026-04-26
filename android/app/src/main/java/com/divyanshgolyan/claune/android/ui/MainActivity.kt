package com.divyanshgolyan.claune.android.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.divyanshgolyan.claune.android.BuildConfig
import com.divyanshgolyan.claune.android.app.clauneContainer
import com.divyanshgolyan.claune.android.service.ClauneAgentService

class MainActivity : ComponentActivity() {
    private var didHandleDebugAutostart = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = clauneContainer()

        setContent {
            ClauneTheme {
                val clauneViewModel: ClauneViewModel = viewModel(factory = ClauneViewModel.Factory(container))
                val uiState by clauneViewModel.uiState.collectAsStateWithLifecycle()

                ClauneApp(
                    uiState = uiState,
                    effects = clauneViewModel.effects,
                    onEvent = clauneViewModel::onEvent,
                    onOpenAccessibilitySettings = ::openAccessibilitySettings,
                    onSubmitMessage = ::submitMessageToAgentService,
                    onStopSession = ::stopAgentService,
                )
            }
        }

        reconcileOrphanedSessionState()
        maybeHandleDebugOverlayIntent(intent)
        maybeHandleDebugAutostart(intent)
    }

    override fun onResume() {
        super.onResume()
        clauneContainer().accessibilityBridge.refreshConnectionState()
        reconcileOrphanedSessionState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        maybeHandleDebugOverlayIntent(intent)
        maybeHandleDebugAutostart(intent)
    }

    private fun maybeHandleDebugOverlayIntent(intent: Intent?) {
        if (!BuildConfig.DEBUG || intent?.hasExtra(EXTRA_DEBUG_OVERLAY) != true) {
            return
        }
        val visible = intent.getBooleanExtra(EXTRA_DEBUG_OVERLAY, false)
        clauneContainer().overlayController.setDebugOverlayVisible(visible)
        Toast.makeText(
            this,
            if (visible) "Showing test overlay..." else "Hiding test overlay...",
            Toast.LENGTH_SHORT,
        ).show()
        if (visible) {
            moveTaskToBack(true)
        }
    }

    private fun maybeHandleDebugAutostart(intent: Intent?) {
        if (!BuildConfig.DEBUG || didHandleDebugAutostart || intent?.action != ACTION_DEBUG_AUTOSTART) {
            return
        }
        val autostartMessage = intent.getStringExtra(EXTRA_AUTOSTART_MESSAGE)?.trim().orEmpty()
        intent.removeExtra(EXTRA_AUTOSTART_MESSAGE)
        if (autostartMessage.isBlank()) {
            return
        }
        didHandleDebugAutostart = true
        submitMessageToAgentService(autostartMessage)
    }

    private fun reconcileOrphanedSessionState() {
        val container = clauneContainer()
        val serviceRunning = ClauneAgentService.isRunning(this)
        if (!serviceRunning) {
            container.artifactStore.recoverOrphanedRuns(
                "Previous session ended unexpectedly. Resetting stale running state.",
            )
        }
        if (container.sessionCoordinator.uiState.value.foregroundServiceRunning && !serviceRunning) {
            container.sessionCoordinator.recoverOrphanedSession(
                "Previous session ended unexpectedly. Resetting stale running state.",
            )
        }
    }

    private fun submitMessageToAgentService(message: String) {
        reconcileOrphanedSessionState()
        Toast.makeText(this, "Starting run...", Toast.LENGTH_SHORT).show()
        val intent = ClauneAgentService.startIntent(this, message)
        ContextCompat.startForegroundService(this, intent)
        moveTaskToBack(true)
    }

    private fun stopAgentService() {
        Toast.makeText(this, "Stopping session...", Toast.LENGTH_SHORT).show()
        startService(ClauneAgentService.stopIntent(this))
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    companion object {
        const val ACTION_DEBUG_AUTOSTART = "com.divyanshgolyan.claune.android.DEBUG_AUTOSTART"
        const val EXTRA_AUTOSTART_MESSAGE = "extra_autostart_message"
        const val EXTRA_DEBUG_OVERLAY = "extra_debug_overlay"
    }
}
