package com.divyanshgolyan.claune.android.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.divyanshgolyan.claune.android.BuildConfig
import com.divyanshgolyan.claune.android.app.clauneContainer
import com.divyanshgolyan.claune.android.data.local.RunArtifactMetadata
import com.divyanshgolyan.claune.android.data.local.SettingsState
import com.divyanshgolyan.claune.android.runtime.SessionStatus
import com.divyanshgolyan.claune.android.runtime.SessionUiState
import com.divyanshgolyan.claune.android.service.ClauneAgentService

class MainActivity : ComponentActivity() {
    private var didHandleDebugAutostart = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = clauneContainer()

        setContent {
            ClauneTheme {
                val sessionState by container.sessionCoordinator.uiState.collectAsStateWithLifecycle()
                val settingsState by container.settingsStore.state.collectAsStateWithLifecycle()
                val historyEntries =
                    remember(sessionState) {
                        artifactHistoryEntries(container.artifactStore.recentRunMetadata(limit = 8))
                    }
                ClauneApp(
                    sessionState = sessionState,
                    settingsState = settingsState,
                    historyEntries = historyEntries,
                    onStartSession = { startAgentService(it) },
                    onStopSession = { stopAgentService() },
                    onOpenAccessibilitySettings = { openAccessibilitySettings() },
                    onUpdateAnthropicKey = { container.settingsStore.updateAnthropicApiKey(it) },
                )
            }
        }

        reconcileOrphanedSessionState()
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
        maybeHandleDebugAutostart(intent.getStringExtra(EXTRA_AUTOSTART_GOAL))
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
        if (container.sessionCoordinator.uiState.value.status == SessionStatus.Running &&
            !serviceRunning
        ) {
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
    }
}

private enum class ClauneScreen {
    Home,
    Settings,
}

private enum class HomeMode {
    Idle,
    Composer,
}

@Composable
private fun ClauneApp(
    sessionState: SessionUiState,
    settingsState: SettingsState,
    historyEntries: List<SessionHistoryEntry>,
    onStartSession: (String) -> Unit,
    onStopSession: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onUpdateAnthropicKey: (String) -> Unit,
) {
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(ClauneScreen.Home.name) }
    var goal by rememberSaveable { mutableStateOf("Open Settings and inspect the Wi-Fi page") }
    var homeMode by rememberSaveable { mutableStateOf(HomeMode.Idle.name) }
    var voiceUiState by remember { mutableStateOf(VoiceUiState()) }
    val speechRecognizer =
        remember {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                SpeechRecognizer.createSpeechRecognizer(context)
            } else {
                null
            }
        }
    var pendingVoiceSend by remember { mutableStateOf(false) }

    DisposableEffect(speechRecognizer) {
        if (speechRecognizer == null) {
            onDispose { }
        } else {
            val listener =
                object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        voiceUiState = voiceUiState.copy(isListening = true, errorMessage = null)
                    }

                    override fun onBeginningOfSpeech() = Unit

                    override fun onRmsChanged(rmsdB: Float) {
                        voiceUiState = voiceUiState.copy(level = rmsdB.coerceAtLeast(0f))
                    }

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        voiceUiState = voiceUiState.copy(isListening = false)
                    }

                    override fun onError(error: Int) {
                        val message = speechErrorLabel(error)
                        if (error == SpeechRecognizer.ERROR_CLIENT && pendingVoiceSend) {
                            pendingVoiceSend = false
                            voiceUiState = voiceUiState.copy(isListening = false)
                            return
                        }
                        voiceUiState =
                            voiceUiState.copy(
                                isListening = false,
                                errorMessage = message,
                            )
                        if (pendingVoiceSend) {
                            pendingVoiceSend = false
                            homeMode = HomeMode.Composer.name
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val transcript = extractSpeechTranscript(results)
                        if (transcript.isNotBlank()) {
                            voiceUiState =
                                voiceUiState.copy(
                                    transcript = transcript,
                                    previewTranscript = transcript,
                                    isListening = false,
                                    errorMessage = null,
                                )
                            goal = transcript
                            pendingVoiceSend = false
                            homeMode = HomeMode.Composer.name
                        } else {
                            pendingVoiceSend = false
                            homeMode = HomeMode.Composer.name
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val transcript = extractSpeechTranscript(partialResults)
                        if (transcript.isNotBlank()) {
                            voiceUiState = voiceUiState.copy(previewTranscript = transcript)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                }
            speechRecognizer.setRecognitionListener(listener)
            onDispose {
                speechRecognizer.destroy()
            }
        }
    }

    val microphonePermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) {
                homeMode = HomeMode.Composer.name
                voiceUiState = VoiceUiState(startedAtMillis = SystemClock.elapsedRealtime())
                startSpeechRecognition(speechRecognizer, context)
            } else {
                Toast.makeText(context, "Microphone access is required for voice input.", Toast.LENGTH_SHORT).show()
            }
        }

    LaunchedEffect(homeMode, voiceUiState.isListening) {
        if (voiceUiState.isListening) {
            while (voiceUiState.isListening) {
                voiceUiState =
                    voiceUiState.copy(
                        elapsedSeconds = ((SystemClock.elapsedRealtime() - voiceUiState.startedAtMillis) / 1000L).toInt(),
                    )
                kotlinx.coroutines.delay(250)
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SoftKraftPalette.Background,
    ) {
        when (ClauneScreen.valueOf(screen)) {
            ClauneScreen.Home ->
                SoftKraftHomeScreen(
                    sessionState = sessionState,
                    settingsState = settingsState,
                    historyEntries = historyEntries,
                    mode = HomeMode.valueOf(homeMode),
                    goal = goal,
                    voiceUiState = voiceUiState,
                    onGoalChanged = { goal = it },
                    onReuseGoal = { goal = it },
                    onStart = {
                        homeMode = HomeMode.Idle.name
                        onStartSession(goal)
                    },
                    onStop = onStopSession,
                    onOpenTyping = { homeMode = HomeMode.Composer.name },
                    onCloseComposer = {
                        homeMode = HomeMode.Idle.name
                        voiceUiState = VoiceUiState()
                    },
                    onStartVoiceCapture = {
                        if (!sessionState.foregroundServiceRunning &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            homeMode = HomeMode.Composer.name
                            voiceUiState = VoiceUiState(startedAtMillis = SystemClock.elapsedRealtime())
                            startSpeechRecognition(speechRecognizer, context)
                        } else if (!sessionState.foregroundServiceRunning) {
                            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onStopVoiceCapture = {
                        pendingVoiceSend = true
                        speechRecognizer?.stopListening()
                    },
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onOpenSettings = { screen = ClauneScreen.Settings.name },
                )

            ClauneScreen.Settings ->
                SettingsScreen(
                    settingsState = settingsState,
                    accessibilityConnected = sessionState.accessibilityConnected,
                    onBack = { screen = ClauneScreen.Home.name },
                    onSaveKey = onUpdateAnthropicKey,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                )
        }
    }
}

@Composable
private fun SoftKraftHomeScreen(
    sessionState: SessionUiState,
    settingsState: SettingsState,
    historyEntries: List<SessionHistoryEntry>,
    mode: HomeMode,
    goal: String,
    voiceUiState: VoiceUiState,
    onGoalChanged: (String) -> Unit,
    onReuseGoal: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenTyping: () -> Unit,
    onCloseComposer: () -> Unit,
    onStartVoiceCapture: () -> Unit,
    onStopVoiceCapture: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val gradient =
        remember {
            Brush.verticalGradient(
                colors = listOf(SoftKraftPalette.Background, SoftKraftPalette.BackgroundDeep),
            )
        }
    val isReady = settingsState.anthropicApiKey.isNotBlank() && sessionState.accessibilityConnected

    Box(
        modifier =
        Modifier
            .fillMaxSize()
            .background(gradient),
    ) {
        when (mode) {
            HomeMode.Idle ->
                IdleHomeScreen(
                    sessionState = sessionState,
                    settingsState = settingsState,
                    historyEntries = historyEntries,
                    isReady = isReady,
                    onReuseGoal = onReuseGoal,
                    onOpenTyping = onOpenTyping,
                    onStartVoiceCapture = onStartVoiceCapture,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onOpenSettings = onOpenSettings,
                )

            HomeMode.Composer ->
                TaskComposerScreen(
                    goal = goal,
                    previewGoal = voiceUiState.previewTranscript,
                    isListening = voiceUiState.isListening,
                    listeningElapsedSeconds = voiceUiState.elapsedSeconds,
                    listeningLevel = voiceUiState.level,
                    listeningError = voiceUiState.errorMessage,
                    canEdit = !sessionState.foregroundServiceRunning && isReady,
                    canStart = goal.isNotBlank() && !sessionState.foregroundServiceRunning && isReady,
                    canStop = sessionState.foregroundServiceRunning,
                    onGoalChanged = onGoalChanged,
                    onClose = onCloseComposer,
                    onStartVoiceCapture = onStartVoiceCapture,
                    onStopVoiceCapture = onStopVoiceCapture,
                    onStart = onStart,
                    onStop = onStop,
                )
        }
    }
}

@Composable
private fun IdleHomeScreen(
    sessionState: SessionUiState,
    settingsState: SettingsState,
    historyEntries: List<SessionHistoryEntry>,
    isReady: Boolean,
    onReuseGoal: (String) -> Unit,
    onOpenTyping: () -> Unit,
    onStartVoiceCapture: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .testTag("main_feed"),
            contentPadding =
            PaddingValues(
                start = 20.dp,
                top = 24.dp,
                end = 20.dp,
                bottom = 164.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                IdleHeader(onOpenSettings = onOpenSettings)
            }

            if (!isReady) {
                item {
                    SetupRunwayCard(
                        hasApiKey = settingsState.anthropicApiKey.isNotBlank(),
                        accessibilityConnected = sessionState.accessibilityConnected,
                        onOpenSettings = onOpenSettings,
                        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    )
                }
            }

            val latestEntry = historyEntries.firstOrNull { it.goal.isNotBlank() }
            latestEntry?.let { latest ->
                item {
                    ContinueSessionCard(
                        entry = latest,
                        onReuseGoal = { onReuseGoal(latest.goal) },
                    )
                }
            }

            val recentEntries =
                if (latestEntry != null) {
                    historyEntries.filterNot {
                        it.sessionId == latestEntry.sessionId || it.goal == latestEntry.goal
                    }
                } else {
                    historyEntries
                }

            if (recentEntries.isNotEmpty()) {
                item {
                    SectionLabel("Recent")
                }
            }

            items(recentEntries.take(3)) { entry ->
                SessionHistoryRow(
                    entry = entry,
                    onReuseGoal = { onReuseGoal(entry.goal) },
                )
            }
        }

        IdleDock(
            modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 14.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            listeningEnabled = isReady && !sessionState.foregroundServiceRunning,
            onOpenTyping = onOpenTyping,
            onStartVoiceCapture = onStartVoiceCapture,
        )
    }
}

@Composable
private fun IdleHeader(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Fri Apr 18 · 9:30")
            Text(
                text = "what do you\nneed done?",
                style = MaterialTheme.typography.headlineLarge,
                color = SoftKraftPalette.Ink,
            )
        }

        TextButton(onClick = onOpenSettings) {
            Text(
                text = "Settings",
                color = SoftKraftPalette.AccentDeep,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun IdleDock(modifier: Modifier = Modifier, listeningEnabled: Boolean, onOpenTyping: () -> Unit, onStartVoiceCapture: () -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Card(
            modifier =
            Modifier
                .weight(1f)
                .shadow(
                    elevation = 22.dp,
                    shape = RoundedCornerShape(34.dp),
                    ambientColor = SoftKraftPalette.AccentDeep.copy(alpha = 0.14f),
                    spotColor = SoftKraftPalette.Ink.copy(alpha = 0.1f),
                )
                .alpha(if (listeningEnabled) 1f else 0.6f)
                .clickable(enabled = listeningEnabled, onClick = onStartVoiceCapture),
            shape = RoundedCornerShape(34.dp),
            colors = CardDefaults.cardColors(containerColor = SoftKraftPalette.SurfaceRaised),
        ) {
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "MIC",
                    color = SoftKraftPalette.InkSoft,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = "tap to talk",
                    style = MaterialTheme.typography.titleMedium,
                    color = SoftKraftPalette.Ink,
                )
            }
        }

        Card(
            modifier =
            Modifier
                .size(68.dp)
                .clickable(onClick = onOpenTyping),
            shape = CircleShape,
            colors = CardDefaults.cardColors(containerColor = SoftKraftPalette.SurfaceRaised),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "TYPE",
                    color = SoftKraftPalette.InkSoft,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun TaskComposerScreen(
    goal: String,
    previewGoal: String,
    isListening: Boolean,
    listeningElapsedSeconds: Int,
    listeningLevel: Float,
    listeningError: String?,
    canEdit: Boolean,
    canStart: Boolean,
    canStop: Boolean,
    onGoalChanged: (String) -> Unit,
    onClose: () -> Unit,
    onStartVoiceCapture: () -> Unit,
    onStopVoiceCapture: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ComposerTopBar(
            statusText = if (isListening) "Listening · ${elapsedLabel(listeningElapsedSeconds)}" else null,
            onClose = onClose,
        )
        Card(
            modifier =
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .shadow(
                    elevation = 22.dp,
                    shape = RoundedCornerShape(30.dp),
                    ambientColor = SoftKraftPalette.AccentDeep.copy(alpha = 0.14f),
                    spotColor = SoftKraftPalette.Ink.copy(alpha = 0.1f),
                ),
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(containerColor = SoftKraftPalette.SurfaceRaised),
        ) {
            Column(
                modifier =
                Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ComposerVoiceHero(
                    isListening = isListening,
                    level = listeningLevel,
                )
                BasicTextField(
                    value = if (isListening) previewGoal.ifBlank { goal } else goal,
                    onValueChange = onGoalChanged,
                    enabled = canEdit && !isListening,
                    textStyle =
                    TextStyle(
                        color = SoftKraftPalette.Ink,
                        fontSize = 28.sp,
                        lineHeight = 34.sp,
                    ),
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("goal_input"),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier =
                            Modifier
                                .fillMaxSize()
                                .background(SoftKraftPalette.SurfaceInput, RoundedCornerShape(24.dp))
                                .border(1.dp, SoftKraftPalette.Rule, RoundedCornerShape(24.dp))
                                .padding(horizontal = 18.dp, vertical = 18.dp),
                        ) {
                            if ((if (isListening) previewGoal.ifBlank { goal } else goal).isBlank()) {
                                Text(
                                    text = "Tell Claune what you need done.",
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = SoftKraftPalette.InkFaint,
                                )
                            }
                            innerTextField()
                        }
                    },
                )

                listeningError?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SoftKraftPalette.Warning,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = if (isListening) onStopVoiceCapture else onStartVoiceCapture,
                    ) {
                        Text(if (isListening) "Stop voice" else "Use voice")
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (canStop) {
                        OutlinedButton(onClick = onStop) {
                            Text("Stop")
                        }
                    } else {
                        Button(
                            onClick = onStart,
                            enabled = canStart && !isListening,
                            colors =
                            ButtonDefaults.buttonColors(
                                containerColor = SoftKraftPalette.Accent,
                                contentColor = SoftKraftPalette.Background,
                            ),
                        ) {
                            Text("Start")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerTopBar(statusText: String?, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (statusText != null) {
            Text(
                text = statusText.uppercase(),
                color = SoftKraftPalette.AccentDeep,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                letterSpacing = 1.4.sp,
            )
        } else {
            Spacer(modifier = Modifier)
        }
        TextButton(onClick = onClose) {
            Text("Close", color = SoftKraftPalette.AccentDeep)
        }
    }
}

@Composable
private fun ComposerVoiceHero(isListening: Boolean, level: Float) {
    val bars =
        remember(isListening, level) {
            val scale = if (isListening) (0.82f + (level / 12f)).coerceIn(0.82f, 1.35f) else 0.42f
            listOf(0.22f, 0.34f, 0.48f, 0.62f, 0.76f, 0.9f, 1f, 0.86f, 0.7f, 0.56f, 0.42f, 0.3f)
                .map { base -> (base * scale).coerceIn(0.12f, 1f) }
        }

    Box(
        modifier =
        Modifier
            .fillMaxWidth()
            .height(148.dp)
            .background(
                if (isListening) {
                    SoftKraftPalette.AccentSoft.copy(alpha = 0.32f)
                } else {
                    SoftKraftPalette.AccentSoft.copy(alpha = 0.16f)
                },
                RoundedCornerShape(22.dp),
            )
            .border(
                1.dp,
                if (isListening) {
                    SoftKraftPalette.Accent.copy(alpha = 0.22f)
                } else {
                    SoftKraftPalette.RuleSoft
                },
                RoundedCornerShape(22.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            bars.forEachIndexed { index, bar ->
                Box(
                    modifier =
                    Modifier
                        .padding(horizontal = 4.dp)
                        .height((bar * 72f).dp)
                        .width(6.dp)
                        .background(
                            if (index == bars.size / 2 || index == (bars.size / 2) - 1) {
                                SoftKraftPalette.AccentDeep.copy(alpha = if (isListening) 1f else 0.72f)
                            } else {
                                SoftKraftPalette.Accent.copy(alpha = if (isListening) 0.78f else 0.42f)
                            },
                            RoundedCornerShape(12.dp),
                        ),
                )
            }
        }
    }
}

@Composable
private fun Header(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Soft Kraft")
            Text(
                text = "Claune",
                style = MaterialTheme.typography.headlineLarge,
                color = SoftKraftPalette.Ink,
            )
            Text(
                text = "A warm command capsule for live phone use. Type for now, voice next.",
                style = MaterialTheme.typography.bodyMedium,
                color = SoftKraftPalette.InkSoft,
            )
        }

        TextButton(onClick = onOpenSettings) {
            Text(
                text = "Settings",
                color = SoftKraftPalette.AccentDeep,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun SetupRunwayCard(
    hasApiKey: Boolean,
    accessibilityConnected: Boolean,
    onOpenSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = SoftKraftPalette.SurfaceMuted),
        modifier =
        Modifier
            .fillMaxWidth()
            .border(1.dp, SoftKraftPalette.Rule, RoundedCornerShape(26.dp)),
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel("Setup")
            Text(
                text = "Two quick things before Claune can drive the phone.",
                style = MaterialTheme.typography.titleMedium,
                color = SoftKraftPalette.Ink,
            )
            SetupStepRow(
                title = "Anthropic API key",
                complete = hasApiKey,
                actionLabel = "Open settings",
                onAction = onOpenSettings,
            )
            SetupStepRow(
                title = "Accessibility access",
                complete = accessibilityConnected,
                actionLabel = "Open accessibility",
                onAction = onOpenAccessibilitySettings,
            )
        }
    }
}

@Composable
private fun ContinueSessionCard(entry: SessionHistoryEntry, onReuseGoal: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SoftKraftPalette.SurfaceMuted),
        modifier =
        Modifier.fillMaxWidth().border(
            1.dp,
            SoftKraftPalette.Rule,
            RoundedCornerShape(24.dp),
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel("Pick up where you left off")
            Text(
                text = entry.goal,
                style = MaterialTheme.typography.titleMedium,
                color = SoftKraftPalette.Ink,
                maxLines = 2,
            )
            Text(
                text = entry.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = SoftKraftPalette.InkSoft,
                maxLines = 2,
            )
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onReuseGoal),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = "→",
                    color = SoftKraftPalette.AccentDeep,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }
}

@Composable
private fun SessionHistoryRow(entry: SessionHistoryEntry, onReuseGoal: () -> Unit) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onReuseGoal)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = entry.goal.ifBlank { entry.summary },
                style = MaterialTheme.typography.bodyLarge,
                color = SoftKraftPalette.Ink,
                maxLines = 2,
            )
        }
        Text(
            text = "→",
            color = SoftKraftPalette.InkSoft,
            style = MaterialTheme.typography.titleMedium,
        )
    }
    Box(
        modifier =
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(SoftKraftPalette.RuleSoft.copy(alpha = 0.7f)),
    ) { }
}

@Composable
private fun BottomCapsule(
    modifier: Modifier = Modifier,
    goal: String,
    onGoalChanged: (String) -> Unit,
    canEdit: Boolean,
    canStart: Boolean,
    canStop: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    sessionState: SessionUiState,
    fullScreen: Boolean = false,
) {
    val capsuleAlpha = if (canEdit || canStop) 1f else 0.62f

    Card(
        modifier =
        modifier
            .fillMaxWidth()
            .shadow(
                elevation = 28.dp,
                shape = RoundedCornerShape(34.dp),
                ambientColor = SoftKraftPalette.AccentDeep.copy(alpha = 0.18f),
                spotColor = SoftKraftPalette.Ink.copy(alpha = 0.12f),
            )
            .alpha(capsuleAlpha),
        shape = RoundedCornerShape(if (fullScreen) 30.dp else 34.dp),
        colors = CardDefaults.cardColors(containerColor = SoftKraftPalette.SurfaceRaised),
        elevation = CardDefaults.cardElevation(defaultElevation = 18.dp),
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CapsuleModePill(label = "Mic", active = false, enabled = false)
                    CapsuleModePill(label = "Type", active = true, enabled = true)
                }
                Text(
                    text =
                    if (sessionState.foregroundServiceRunning) {
                        "working now"
                    } else {
                        "ready for a task"
                    },
                    color = SoftKraftPalette.InkSoft,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
            }

            BasicTextField(
                value = goal,
                onValueChange = onGoalChanged,
                enabled = canEdit,
                textStyle =
                TextStyle(
                    color = SoftKraftPalette.Ink,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                ),
                modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (fullScreen) 240.dp else 0.dp)
                    .testTag("goal_input"),
                decorationBox = { innerTextField ->
                    Box(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(SoftKraftPalette.SurfaceInput, RoundedCornerShape(24.dp))
                            .border(1.5.dp, SoftKraftPalette.Rule, RoundedCornerShape(24.dp))
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        if (goal.isBlank()) {
                            Text(
                                text = "Tell Claune what to do on your phone…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = SoftKraftPalette.InkFaint,
                            )
                        }
                        innerTextField()
                    }
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text =
                    if (fullScreen) {
                        "Write the task clearly, then start when it reads exactly right."
                    } else {
                        "Keyboard-first for now. Voice is the next input slice."
                    },
                    modifier = Modifier.weight(1f),
                    color = SoftKraftPalette.InkSoft,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                )

                if (canStop) {
                    OutlinedButton(
                        modifier = Modifier.testTag("stop_session_button"),
                        onClick = onStop,
                    ) {
                        Text("Stop")
                    }
                } else {
                    Button(
                        modifier = Modifier.testTag("start_session_button"),
                        onClick = onStart,
                        enabled = canStart,
                        colors =
                        ButtonDefaults.buttonColors(
                            containerColor = SoftKraftPalette.Accent,
                            contentColor = SoftKraftPalette.Background,
                        ),
                    ) {
                        Text("Start")
                    }
                }
            }
        }
    }
}

@Composable
private fun CapsuleModePill(label: String, active: Boolean, enabled: Boolean) {
    val background =
        when {
            active -> SoftKraftPalette.AccentSoft
            !enabled -> SoftKraftPalette.Background
            else -> SoftKraftPalette.SurfaceMuted
        }
    val textColor =
        when {
            active -> SoftKraftPalette.AccentDeep
            !enabled -> SoftKraftPalette.InkFaint
            else -> SoftKraftPalette.InkSoft
        }

    Box(
        modifier =
        Modifier
            .background(background, CircleShape)
            .border(1.dp, SoftKraftPalette.Rule, CircleShape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = label.uppercase(),
            color = textColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun SettingsScreen(
    settingsState: SettingsState,
    accessibilityConnected: Boolean,
    onBack: () -> Unit,
    onSaveKey: (String) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    var apiKeyDraft by rememberSaveable { mutableStateOf(settingsState.anthropicApiKey) }
    var showKey by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(settingsState.anthropicApiKey) {
        apiKeyDraft = settingsState.anthropicApiKey
    }

    LazyColumn(
        modifier =
        Modifier
            .fillMaxSize()
            .background(SoftKraftPalette.Background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(
                    onClick = onBack,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text("← Back", color = SoftKraftPalette.AccentDeep)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Settings")
                    Text(
                        text = "Control room",
                        style = MaterialTheme.typography.headlineLarge,
                        color = SoftKraftPalette.Ink,
                    )
                    Text(
                        text = "Keep the agent key on-device and check whether phone control is ready.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SoftKraftPalette.InkSoft,
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = SoftKraftPalette.Surface),
            ) {
                Column(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SectionLabel("Anthropic key")
                    Text(
                        text = "The app now reads the API key from here instead of requiring a rebuild.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SoftKraftPalette.InkSoft,
                    )
                    OutlinedTextField(
                        value = apiKeyDraft,
                        onValueChange = { apiKeyDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        shape = RoundedCornerShape(22.dp),
                        label = { Text("Anthropic API key") },
                        visualTransformation =
                        if (showKey) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions =
                        androidx.compose.foundation.text.KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Password,
                        ),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { onSaveKey(apiKeyDraft) },
                            colors =
                            ButtonDefaults.buttonColors(
                                containerColor = SoftKraftPalette.Accent,
                                contentColor = SoftKraftPalette.Background,
                            ),
                        ) {
                            Text("Save key")
                        }
                        OutlinedButton(onClick = { showKey = !showKey }) {
                            Text(if (showKey) "Hide" else "Show")
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = SoftKraftPalette.Surface),
            ) {
                Column(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SectionLabel("Phone control")
                    Text(
                        text =
                        if (accessibilityConnected) {
                            "Claune accessibility is connected. No action needed."
                        } else {
                            "Claune accessibility is off. Enable it before starting a live run."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = SoftKraftPalette.InkSoft,
                    )
                    OutlinedButton(onClick = onOpenAccessibilitySettings) {
                        Text("Open accessibility settings")
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = SoftKraftPalette.InkFaint,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        letterSpacing = 1.4.sp,
    )
}

private data class VoiceUiState(
    val transcript: String = "",
    val previewTranscript: String = "",
    val elapsedSeconds: Int = 0,
    val level: Float = 0f,
    val isListening: Boolean = false,
    val errorMessage: String? = null,
    val startedAtMillis: Long = 0L,
)

private fun startSpeechRecognition(speechRecognizer: SpeechRecognizer?, context: android.content.Context) {
    if (speechRecognizer == null) {
        Toast.makeText(context, "Speech recognition is not available on this device.", Toast.LENGTH_SHORT).show()
        return
    }
    val intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
    speechRecognizer.cancel()
    speechRecognizer.startListening(intent)
}

private fun extractSpeechTranscript(results: Bundle?): String = results
    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
    ?.firstOrNull()
    .orEmpty()
    .trim()

private fun speechErrorLabel(error: Int): String = when (error) {
    SpeechRecognizer.ERROR_AUDIO -> "Microphone input failed."
    SpeechRecognizer.ERROR_CLIENT -> "Voice capture stopped."
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is missing."
    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
    -> "Speech recognition needs a stable network connection."
    SpeechRecognizer.ERROR_NO_MATCH -> "No speech was recognized."
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognition is already in use."
    SpeechRecognizer.ERROR_SERVER -> "The speech service returned an error."
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech was detected in time."
    else -> "Speech recognition failed."
}

private fun elapsedLabel(seconds: Int): String {
    val minutes = seconds / 60
    val remainder = seconds % 60
    return "%02d:%02d".format(minutes, remainder)
}

private object SoftKraftPalette {
    val Background = Color(0xFFF3EDE1)
    val BackgroundDeep = Color(0xFFE8DFCE)
    val Surface = Color(0xFFFBF7F0)
    val SurfaceMuted = Color(0xFFF0E8DA)
    val SurfaceRaised = Color(0xFFFFFBF5)
    val SurfaceInput = Color(0xFFF8F1E3)
    val Rule = Color(0xFFD4C9B0)
    val RuleSoft = Color(0xFFDDD3C1)
    val Ink = Color(0xFF1D2A22)
    val InkSoft = Color(0xFF4A574D)
    val InkFaint = Color(0xFF8A8777)
    val Accent = Color(0xFF2D5A3F)
    val AccentSoft = Color(0xFFC7D8CA)
    val AccentDeep = Color(0xFF1A3D28)
    val Success = Color(0xFF2F7B55)
    val Warning = Color(0xFF9A5A30)
    val Muted = Color(0xFF726A5F)
}

private data class SessionHistoryEntry(val sessionId: String, val goal: String, val summary: String)

private fun artifactHistoryEntries(metadata: List<RunArtifactMetadata>): List<SessionHistoryEntry> = metadata.map { run ->
    SessionHistoryEntry(
        sessionId = run.runId,
        goal = run.goal,
        summary = run.latestSummary ?: "No summary available yet.",
    )
}

@Composable
private fun SetupStepRow(title: String, complete: Boolean, actionLabel: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = SoftKraftPalette.Ink,
            )
            Text(
                text = if (complete) "Ready" else "Needs attention",
                color = SoftKraftPalette.InkSoft,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
        if (complete) {
            Text(
                text = "done",
                color = SoftKraftPalette.AccentDeep,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        } else {
            TextButton(onClick = onAction) {
                Text(actionLabel, color = SoftKraftPalette.AccentDeep)
            }
        }
    }
}
