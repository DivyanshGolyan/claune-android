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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.divyanshgolyan.claune.android.app.clauneContainer
import com.divyanshgolyan.claune.android.data.preferences.PrototypeSettings
import com.divyanshgolyan.claune.android.runtime.SessionStatus
import com.divyanshgolyan.claune.android.runtime.SessionUiState
import com.divyanshgolyan.claune.android.service.ClauneAgentService
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = clauneContainer()

        setContent {
            ClauneTheme {
                val sessionState by container.sessionCoordinator.uiState.collectAsStateWithLifecycle()
                val settings by container.settingsStore.settings.collectAsStateWithLifecycle(
                    initialValue = PrototypeSettings(),
                )
                ClauneApp(
                    sessionState = sessionState,
                    settings = settings,
                    onStartSession = { startAgentService(it) },
                    onStopSession = { stopAgentService() },
                    onToggleScreenshots = { enabled ->
                        container.settingsStore.setScreenshotsEnabled(enabled)
                    },
                )
            }
        }
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
    settings: PrototypeSettings,
    onStartSession: (String) -> Unit,
    onStopSession: () -> Unit,
    onToggleScreenshots: suspend (Boolean) -> Unit,
) {
    var goal by rememberSaveable { mutableStateOf("Open Settings and inspect the Wi-Fi page") }
    val coroutineScope = rememberCoroutineScope()
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
                            text =
                            "Prototype-first Android agent shell with one active session, " +
                                "one tool call per turn, and accessibility-driven phone control.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFFD8C2B0),
                        )
                    }
                }

                item {
                    StatusRail(sessionState = sessionState)
                }

                item {
                    CurrentStatusCard(sessionState = sessionState)
                }

                item {
                    GoalComposer(
                        goal = goal,
                        onGoalChanged = { goal = it },
                        isRunning = sessionState.status == SessionStatus.Running,
                        onStart = { onStartSession(goal) },
                        onStop = onStopSession,
                    )
                }

                item {
                    SettingsCard(
                        settings = settings,
                        onToggleScreenshots = { enabled ->
                            coroutineScope.launch {
                                onToggleScreenshots(enabled)
                            }
                        },
                    )
                }

                item {
                    ArchitectureCard()
                }

                item {
                    Text(
                        text = "Execution log",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFF6E9DB),
                    )
                }

                items(sessionState.timeline) { event ->
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
private fun StatusRail(sessionState: SessionUiState) {
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
                text = "Live state",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFF6E9DB),
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatusChip("Session: ${sessionState.status.name.lowercase()}")
                StatusChip(
                    if (sessionState.accessibilityConnected) {
                        "Accessibility linked"
                    } else {
                        "Accessibility not linked"
                    },
                )
                StatusChip(
                    if (sessionState.foregroundServiceRunning) {
                        "Foreground service live"
                    } else {
                        "Foreground service idle"
                    },
                )
                StatusChip("Latest app: ${sessionState.lastKnownApp ?: "unknown"}")
            }
        }
    }
}

@Composable
private fun GoalComposer(goal: String, onGoalChanged: (String) -> Unit, isRunning: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xCCF6E9DB)),
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Session kickoff",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF22140E),
            )
            OutlinedTextField(
                value = goal,
                onValueChange = onGoalChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Goal") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 3,
                shape = RoundedCornerShape(18.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onStart, enabled = goal.isNotBlank() && !isRunning) {
                    Text("Start session")
                }
                OutlinedButton(onClick = onStop, enabled = isRunning) {
                    Text("Stop")
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(settings: PrototypeSettings, onToggleScreenshots: (Boolean) -> Unit) {
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
                text = "Prototype switches",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFF6E9DB),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Store screenshots",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFFF6E9DB),
                    )
                    Text(
                        text = "Off by default to match the replay guidance in the v1 architecture doc.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD8C2B0),
                    )
                }
                Switch(
                    checked = settings.screenshotsEnabled,
                    onCheckedChange = onToggleScreenshots,
                )
            }
        }
    }
}

@Composable
private fun ArchitectureCard() {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xCC201610)),
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Initial module posture",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFF6E9DB),
            )
            Text(
                text =
                "Day one stays in a single app module. The code is split into packages " +
                    "for ui, service, runtime, accessibility, llm, and data so it can be " +
                    "extracted later without slowing down the prototype.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFD8C2B0),
            )
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

@Composable
private fun StatusChip(text: String) {
    FilterChip(
        selected = false,
        onClick = {},
        label = { Text(text) },
        enabled = false,
    )
}
