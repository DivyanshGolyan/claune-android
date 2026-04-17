package com.divyanshgolyan.claune.android.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.divyanshgolyan.claune.android.app.clauneContainer
import com.divyanshgolyan.claune.android.runtime.SessionStatus
import com.divyanshgolyan.claune.android.runtime.SessionUiState
import com.divyanshgolyan.claune.android.scripting.ClauneHostContract
import com.divyanshgolyan.claune.android.scripting.ScriptExecutionRequest
import com.divyanshgolyan.claune.android.scripting.ScriptExecutionResult
import com.divyanshgolyan.claune.android.service.ClauneAgentService
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = clauneContainer()

        setContent {
            ClauneTheme {
                val sessionState by container.sessionCoordinator.uiState.collectAsStateWithLifecycle()
                ClauneApp(
                    sessionState = sessionState,
                    onStartSession = { startAgentService(it) },
                    onStopSession = { stopAgentService() },
                    onRunScript = { script ->
                        container.scriptRuntime.execute(
                            ScriptExecutionRequest(
                                script = script,
                                source = "script_lab",
                            ),
                        )
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        clauneContainer().accessibilityBridge.refreshConnectionState()
    }

    private fun startAgentService(goal: String) {
        Toast.makeText(this, "Starting session...", Toast.LENGTH_SHORT).show()
        val intent = ClauneAgentService.startIntent(this, goal)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopAgentService() {
        Toast.makeText(this, "Stopping session...", Toast.LENGTH_SHORT).show()
        startService(ClauneAgentService.stopIntent(this))
    }
}

@Composable
private fun ClauneApp(
    sessionState: SessionUiState,
    onStartSession: (String) -> Unit,
    onStopSession: () -> Unit,
    onRunScript: suspend (String) -> ScriptExecutionResult,
) {
    var goal by rememberSaveable { mutableStateOf("Open Settings and inspect the Wi-Fi page") }
    val gradient =
        remember {
            Brush.linearGradient(
                colors =
                listOf(
                    Color(0xFF140F0A),
                    Color(0xFF25160F),
                    Color(0xFF4C2D1D),
                ),
            )
        }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF120D0A),
    ) {
        Box(
            modifier =
            Modifier
                .fillMaxSize()
                .background(gradient),
        ) {
            LazyColumn(
                modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .testTag("main_feed")
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Column(
                        modifier = Modifier.padding(top = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Claune Android",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF6E9DB),
                        )
                        Text(
                            text = "Testing console for the current session loop and embedded script runtime.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFD8C2B0),
                        )
                    }
                }

                item {
                    TestingConsoleCard(
                        sessionState = sessionState,
                        goal = goal,
                        onGoalChanged = { goal = it },
                        isServiceActive = sessionState.foregroundServiceRunning,
                        onStart = { onStartSession(goal) },
                        onStop = onStopSession,
                    )
                }

                item {
                    CurrentStatusCard(sessionState = sessionState)
                }

                item {
                    ScriptLabCard(onRunScript = onRunScript)
                }

                item {
                    Text(
                        text = "Execution log",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFF6E9DB),
                    )
                }

                items(sessionState.timeline.takeLast(6).asReversed()) { event ->
                    EventCard(event)
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun CurrentStatusCard(sessionState: SessionUiState) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor =
            when (sessionState.status) {
                SessionStatus.Running -> Color(0xCC3F2917)
                SessionStatus.Blocked -> Color(0xCC4B1E16)
                SessionStatus.Completed -> Color(0xCC1F3520)
                SessionStatus.Cancelled -> Color(0xCC2B2521)
                SessionStatus.Idle -> Color(0xCC201610)
            },
        ),
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Latest runtime state",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFF6E9DB),
            )
            Text(
                text = sessionState.summaryLine,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFF6E9DB),
            )
            if (!sessionState.accessibilityConnected) {
                Text(
                    text = "Accessibility is still off. The first run will only prove service + notification wiring.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD8C2B0),
                )
            }
        }
    }
}

@Composable
private fun TestingConsoleCard(
    sessionState: SessionUiState,
    goal: String,
    onGoalChanged: (String) -> Unit,
    isServiceActive: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val canStart = goal.isNotBlank() && !isServiceActive
    val canStop = isServiceActive

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xCC201610)),
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Testing controls",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFF6E9DB),
            )
            CompactStatusLine(
                label = "Session",
                value = sessionState.status.name.lowercase(),
            )
            CompactStatusLine(
                label = "Accessibility",
                value = if (sessionState.accessibilityConnected) "linked" else "not linked",
            )
            CompactStatusLine(
                label = "Foreground service",
                value = if (sessionState.foregroundServiceRunning) "live" else "idle",
            )
            CompactStatusLine(
                label = "Latest app",
                value = sessionState.lastKnownApp ?: "unknown",
            )
            OutlinedTextField(
                value = goal,
                onValueChange = onGoalChanged,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("goal_input"),
                label = { Text("Goal") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 2,
                shape = RoundedCornerShape(18.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    modifier = Modifier.testTag("start_session_button"),
                    onClick = onStart,
                    enabled = canStart,
                ) {
                    Text("Start session")
                }
                OutlinedButton(
                    modifier = Modifier.testTag("stop_session_button"),
                    onClick = onStop,
                    enabled = canStop,
                ) {
                    Text("Stop")
                }
            }
        }
    }
}

@Composable
private fun CompactStatusLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFD8C2B0),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFFF6E9DB),
        )
    }
}

@Composable
private fun ScriptLabCard(onRunScript: suspend (String) -> ScriptExecutionResult) {
    var script by rememberSaveable { mutableStateOf(DEFAULT_SCRIPT_SAMPLE) }
    var isRunning by remember { mutableStateOf(false) }
    var latestResult by remember { mutableStateOf<ScriptExecutionResult?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xCC201610)),
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Script Lab",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFF6E9DB),
            )
            Text(
                text = ClauneHostContract.scriptLabSummary,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFD8C2B0),
            )
            OutlinedTextField(
                value = script,
                onValueChange = { script = it },
                modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("script_lab_input"),
                label = { Text("Script") },
                minLines = 6,
                shape = RoundedCornerShape(18.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    modifier = Modifier.testTag("script_lab_run_button"),
                    onClick = {
                        coroutineScope.launch {
                            isRunning = true
                            latestResult = onRunScript(script)
                            isRunning = false
                        }
                    },
                    enabled = script.isNotBlank() && !isRunning,
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(18.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFFF6E9DB),
                        )
                    } else {
                        Text("Run script")
                    }
                }
                OutlinedButton(
                    onClick = {
                        script = DEFAULT_SCRIPT_SAMPLE
                    },
                    enabled = !isRunning,
                ) {
                    Text("Reset sample")
                }
            }
            latestResult?.let { result ->
                Card(
                    modifier = Modifier.testTag("script_lab_result"),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (result.ok) Color(0xCC1F3520) else Color(0xCC4B1E16),
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = if (result.ok) "Latest result: success" else "Latest result: failed",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color(0xFFF6E9DB),
                        )
                        Text(
                            text = result.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFF6E9DB),
                        )
                        Text(
                            text = "Host calls: ${result.hostCalls.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFD8C2B0),
                        )
                        result.data?.let { data ->
                            Text(
                                text = data.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFD8C2B0),
                            )
                        }
                        result.error?.let { error ->
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFFD2C7),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventCard(event: String) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x99402A1E)),
    ) {
        Text(
            text = event,
            modifier = Modifier.padding(16.dp),
            color = Color(0xFFF6E9DB),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private const val DEFAULT_SCRIPT_SAMPLE =
    """
const snapshot = claune.observePhone();
return {
  foregroundPackage: snapshot.foregroundPackage,
  actionableCount: snapshot.actionableElements.length,
  firstElement: snapshot.actionableElements[0]?.label ?? null,
  firstRef: snapshot.actionableElements[0]?.ref ?? null,
};
"""
