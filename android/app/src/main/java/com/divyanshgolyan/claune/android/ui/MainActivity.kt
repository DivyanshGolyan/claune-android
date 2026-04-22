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
import com.divyanshgolyan.claune.android.runtime.SessionStatus
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
                    onStartSession = ::startAgentService,
                    onStopSession = ::stopAgentService,
                )
            }
        }

        reconcileOrphanedSessionState()
        maybeHandleDebugOverlayIntent(intent)
        maybeHandleDebugAutostart(intent?.getStringExtra(EXTRA_AUTOSTART_GOAL))
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
        maybeHandleDebugAutostart(intent.getStringExtra(EXTRA_AUTOSTART_GOAL))
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

    private fun maybeHandleDebugAutostart(goal: String?) {
        if (!BuildConfig.DEBUG || didHandleDebugAutostart) {
            return
        }
        val autostartGoal = goal?.trim().orEmpty()
        if (autostartGoal.isBlank()) {
            return
        }
        didHandleDebugAutostart = true
        startAgentService(autostartGoal)
    }

    private fun reconcileOrphanedSessionState() {
        val container = clauneContainer()
        val serviceRunning = ClauneAgentService.isRunning(this)
        if (!serviceRunning) {
            container.artifactStore.recoverOrphanedRuns(
                "Previous session ended unexpectedly. Resetting stale running state.",
            )
        }
        if (container.sessionCoordinator.uiState.value.status == SessionStatus.Running && !serviceRunning) {
            container.sessionCoordinator.recoverOrphanedSession(
                "Previous session ended unexpectedly. Resetting stale running state.",
            )
        }
    }

    private fun startAgentService(goal: String) {
        reconcileOrphanedSessionState()
        Toast.makeText(this, "Starting session...", Toast.LENGTH_SHORT).show()
        val intent = ClauneAgentService.startIntent(this, goal)
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
        const val EXTRA_AUTOSTART_GOAL = "extra_autostart_goal"
        const val EXTRA_DEBUG_OVERLAY = "extra_debug_overlay"
    }
}
