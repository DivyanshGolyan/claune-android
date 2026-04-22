package com.divyanshgolyan.claune.android.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.divyanshgolyan.claune.android.BuildConfig
import com.divyanshgolyan.claune.android.data.local.PersistedSessionDetail
import com.divyanshgolyan.claune.android.data.local.PersistedSessionDetailEntry
import com.divyanshgolyan.claune.android.data.local.PersistedSessionDetailKind
import com.divyanshgolyan.claune.android.data.local.SettingsState
import com.divyanshgolyan.claune.android.runtime.SessionStatus
import com.divyanshgolyan.claune.android.runtime.SessionUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
private data object HomeRoute

@Serializable
private data object SessionRoute

@Serializable
private data object SettingsRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClauneApp(
    uiState: ClauneUiState,
    effects: Flow<ClauneUiEffect>,
    onEvent: (ClauneUiEvent) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onStartSession: (String) -> Unit,
    onStopSession: () -> Unit,
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    var goal by rememberSaveable { mutableStateOf("") }
    var composerRequestsKeyboard by rememberSaveable { mutableStateOf(false) }
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

                    override fun onRmsChanged(rmsdB: Float) = Unit

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
                        pendingVoiceSend = false
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
                        } else {
                            pendingVoiceSend = false
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
                voiceUiState = VoiceUiState(startedAtMillis = SystemClock.elapsedRealtime())
                startSpeechRecognition(speechRecognizer, context)
            } else {
                Toast.makeText(context, "Microphone access is required for voice input.", Toast.LENGTH_SHORT).show()
            }
        }

    LaunchedEffect(voiceUiState.isListening) {
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

    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                ClauneUiEffect.OpenAccessibilitySettings -> onOpenAccessibilitySettings()
                is ClauneUiEffect.NavigateToSession -> navController.navigate(SessionRoute)
                is ClauneUiEffect.StartSession -> onStartSession(effect.goal)
                ClauneUiEffect.StopSession -> onStopSession()
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ClaunePalette.Background,
    ) {
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
        ) {
            composable<HomeRoute> {
                SessionChooserHomeScreen(
                    sessionState = uiState.sessionState,
                    settingsState = uiState.settingsState,
                    historyEntries = uiState.historyEntries,
                    onCreateSession = {
                        onEvent(ClauneUiEvent.CreateSession)
                        goal = ""
                        composerRequestsKeyboard = true
                    },
                    onOpenSession = { path ->
                        onEvent(ClauneUiEvent.SelectSession(path))
                        goal = ""
                        composerRequestsKeyboard = false
                    },
                    onOpenAccessibilitySettings = { onEvent(ClauneUiEvent.OpenAccessibilitySettings) },
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                )
            }

            composable<SessionRoute> {
                SessionDetailScreen(
                    sessionState = uiState.sessionState,
                    sessionDetail = uiState.sessionDetail,
                    settingsState = uiState.settingsState,
                    goal = goal,
                    previewGoal = voiceUiState.previewTranscript,
                    isListening = voiceUiState.isListening,
                    listeningElapsedSeconds = voiceUiState.elapsedSeconds,
                    listeningError = voiceUiState.errorMessage,
                    autoFocusInput = composerRequestsKeyboard,
                    onGoalChanged = { goal = it },
                    onSendMessage = {
                        composerRequestsKeyboard = false
                        onEvent(ClauneUiEvent.SendGoal(goal))
                        goal = ""
                        voiceUiState = VoiceUiState()
                    },
                    onStartVoiceCapture = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            composerRequestsKeyboard = false
                            voiceUiState = VoiceUiState(startedAtMillis = SystemClock.elapsedRealtime())
                            startSpeechRecognition(speechRecognizer, context)
                        } else {
                            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onStopVoiceCapture = {
                        pendingVoiceSend = true
                        speechRecognizer?.stopListening()
                    },
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                    onOpenSessions = { navController.navigate(HomeRoute) },
                    onStopSession = { onEvent(ClauneUiEvent.StopSession) },
                )
            }

            composable<SettingsRoute> {
                SettingsScreen(
                    settingsState = uiState.settingsState,
                    accessibilityConnected = uiState.sessionState.accessibilityConnected,
                    onSaveKey = { onEvent(ClauneUiEvent.UpdateAnthropicKey(it)) },
                    onOpenAccessibilitySettings = { onEvent(ClauneUiEvent.OpenAccessibilitySettings) },
                    onDebugOverlayVisibleChanged = { onEvent(ClauneUiEvent.SetDebugOverlayVisible(it)) },
                    onNavigateBack = { navController.navigateUp() },
                )
            }
        }
    }
}

@Composable
private fun SessionChooserHomeScreen(
    sessionState: SessionUiState,
    settingsState: SettingsState,
    historyEntries: List<SessionHistoryEntry>,
    onCreateSession: () -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val isReady = settingsState.anthropicApiKey.isNotBlank() && sessionState.accessibilityConnected
    Scaffold(
        topBar = {
            ClauneTopAppBar(
                title = "Claune",
                primaryActionLabel = "Settings",
                onPrimaryAction = onOpenSettings,
            )
        },
        containerColor = ClaunePalette.Background,
    ) { innerPadding ->
        LazyColumn(
            modifier =
            Modifier
                .fillMaxSize()
                .background(ClaunePalette.Background),
            contentPadding =
            PaddingValues(
                start = ClauneLayout.ScreenPadding,
                top = innerPadding.calculateTopPadding() + 16.dp,
                end = ClauneLayout.ScreenPadding,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(ClauneLayout.SectionGap),
        ) {
            item {
                IdleHeader(isReady = isReady)
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
            } else {
                item {
                    NewSessionCard(onCreateSession = onCreateSession)
                }

                if (historyEntries.isNotEmpty()) {
                    item {
                        SectionLabel("Existing sessions")
                    }
                }

                items(historyEntries.take(8)) { entry ->
                    SessionHistoryRow(
                        entry = entry,
                        onReuseGoal = { onOpenSession(entry.sessionPath) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionDetailScreen(
    sessionState: SessionUiState,
    sessionDetail: PersistedSessionDetail?,
    settingsState: SettingsState,
    goal: String,
    previewGoal: String,
    isListening: Boolean,
    listeningElapsedSeconds: Int,
    listeningError: String?,
    autoFocusInput: Boolean,
    onGoalChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onStartVoiceCapture: () -> Unit,
    onStopVoiceCapture: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSessions: () -> Unit,
    onStopSession: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val canEdit = settingsState.anthropicApiKey.isNotBlank() && sessionState.accessibilityConnected
    val canSend = goal.isNotBlank() && settingsState.anthropicApiKey.isNotBlank() && sessionState.accessibilityConnected
    val inputHelpText =
        when {
            settingsState.anthropicApiKey.isBlank() -> "Add an Anthropic API key before starting."
            !sessionState.accessibilityConnected -> "Turn on accessibility access before starting."
            else -> "Claune starts only after you tap Start on phone."
        }
    val statusBannerText = sessionStatusBannerText(sessionState.status, sessionState.lastKnownApp)
    val visibleSessionDetail = sessionDetail?.takeIf { it.summary.path == sessionState.selectedSessionPath }
    val visibleChatEntries = visibleSessionDetail?.entries.orEmpty().filter(::isChatTranscriptEntry)

    LaunchedEffect(autoFocusInput, isListening) {
        if (autoFocusInput && !isListening) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Scaffold(
        topBar = {
            ClauneTopAppBar(
                title = sessionState.selectedSessionTitle ?: "Session",
                primaryActionLabel = "Settings",
                onPrimaryAction = onOpenSettings,
                secondaryActionLabel = "Sessions",
                onSecondaryAction = onOpenSessions,
            )
        },
        bottomBar = {
            Surface(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding(),
                color = ClaunePalette.Background,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Box(modifier = Modifier.padding(horizontal = ClauneLayout.SurfacePadding, vertical = 12.dp)) {
                    SessionComposerCard(
                        goal = goal,
                        previewGoal = previewGoal,
                        isListening = isListening,
                        listeningElapsedSeconds = listeningElapsedSeconds,
                        listeningError = listeningError,
                        canEdit = canEdit && !isListening,
                        canSend = canSend && !isListening,
                        canStop = sessionState.status == SessionStatus.Running,
                        inputHelpText = inputHelpText,
                        focusRequester = focusRequester,
                        onGoalChanged = onGoalChanged,
                        onSendMessage = onSendMessage,
                        onStartVoiceCapture = onStartVoiceCapture,
                        onStopVoiceCapture = onStopVoiceCapture,
                        onStopSession = onStopSession,
                    )
                }
            }
        },
        containerColor = ClaunePalette.Background,
    ) { innerPadding ->
        LazyColumn(
            modifier =
            Modifier
                .fillMaxSize()
                .background(ClaunePalette.Background),
            contentPadding =
            PaddingValues(
                start = ClauneLayout.ScreenPadding,
                top = innerPadding.calculateTopPadding() + 16.dp,
                end = ClauneLayout.ScreenPadding,
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(ClauneLayout.ControlGap),
        ) {
            if (statusBannerText != null) {
                item {
                    SessionStatusBanner(statusBannerText)
                }
            }
            if (sessionState.isCompacting) {
                item {
                    SessionSystemChip("Compacting context…")
                }
            }
            if (sessionState.pendingSteeringCount > 0) {
                item {
                    SessionSystemChip("Queued steering: ${sessionState.pendingSteeringCount}")
                }
            }
            if (visibleSessionDetail == null || visibleChatEntries.isEmpty()) {
                item {
                    SessionEmptyState()
                }
            } else {
                items(visibleChatEntries) { entry ->
                    SessionTranscriptRow(entry = entry)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClauneTopAppBar(
    title: String,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                color = ClaunePalette.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        actions = {
            if (secondaryActionLabel != null && onSecondaryAction != null) {
                ClauneTextAction(secondaryActionLabel, onSecondaryAction)
            }
            if (primaryActionLabel != null && onPrimaryAction != null) {
                ClauneTextAction(primaryActionLabel, onPrimaryAction)
            }
        },
        colors =
        TopAppBarDefaults.topAppBarColors(
            containerColor = ClaunePalette.Background,
            titleContentColor = ClaunePalette.Ink,
            actionIconContentColor = ClaunePalette.AccentDeep,
        ),
    )
}

@Composable
private fun ClaunePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ClauneShapes.Control,
        colors =
        ButtonDefaults.buttonColors(
            containerColor = ClaunePalette.Accent,
            contentColor = ClaunePalette.Background,
        ),
        modifier = modifier,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text)
    }
}

@Composable
private fun ClauneSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = ClauneShapes.Control,
        modifier = modifier,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text)
    }
}

@Composable
private fun ClauneTextAction(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, shape = ClauneShapes.Control) {
        Text(text, color = ClaunePalette.AccentDeep)
    }
}

@Composable
private fun ClauneRowChevron(contentDescription: String? = null) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
        contentDescription = contentDescription,
        tint = ClaunePalette.AccentDeep,
    )
}

@Composable
private fun SessionStatusBanner(text: String) {
    Card(
        shape = ClauneShapes.Card,
        colors = CardDefaults.cardColors(containerColor = ClaunePalette.SurfaceMuted),
        modifier =
        Modifier
            .fillMaxWidth()
            .border(1.dp, ClaunePalette.RuleSoft, ClauneShapes.Card),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = ClaunePalette.InkSoft,
            modifier = Modifier.padding(horizontal = ClauneLayout.SurfacePadding, vertical = 10.dp),
        )
    }
}

@Composable
private fun IdleHeader(isReady: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!isReady) {
            SectionLabel("Setup")
        }
        Text(
            text =
            if (isReady) {
                "What needs\ndoing?"
            } else {
                "Before you\nstart"
            },
            style = MaterialTheme.typography.headlineLarge,
            color = ClaunePalette.Ink,
        )
        if (!isReady) {
            Text(
                text = "Finish setup and Claune will be ready to use your phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = ClaunePalette.InkSoft,
            )
        }
    }
}

@Composable
private fun ComposerTopBar(statusText: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (statusText != null) {
            Text(
                text = statusText,
                color = ClaunePalette.AccentDeep,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.05.em,
                ),
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
        shape = ClauneShapes.Card,
        colors = CardDefaults.cardColors(containerColor = ClaunePalette.BackgroundDeep),
        modifier =
        Modifier
            .fillMaxWidth()
            .border(1.dp, ClaunePalette.Rule, ClauneShapes.Card),
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(ClauneLayout.CardPadding),
            verticalArrangement = Arrangement.spacedBy(ClauneLayout.ControlGap),
        ) {
            SectionLabel("Setup")
            Text(
                text = "Two quick things before Claune can use the phone.",
                style = MaterialTheme.typography.titleMedium,
                color = ClaunePalette.Ink,
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
private fun SessionHistoryRow(entry: SessionHistoryEntry, onReuseGoal: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(
                text = entry.goal.ifBlank { entry.summary },
                style = MaterialTheme.typography.bodyLarge,
                color = ClaunePalette.Ink,
                maxLines = 2,
            )
        },
        trailingContent = {
            ClauneRowChevron(contentDescription = "Open session")
        },
        colors = ListItemDefaults.colors(containerColor = ClaunePalette.Background),
        modifier =
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onReuseGoal),
    )
    HorizontalDivider(color = ClaunePalette.RuleSoft)
}

@Composable
private fun SettingsScreen(
    settingsState: SettingsState,
    accessibilityConnected: Boolean,
    onSaveKey: (String) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onDebugOverlayVisibleChanged: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
) {
    var apiKeyDraft by rememberSaveable { mutableStateOf(settingsState.anthropicApiKey) }
    var showKey by rememberSaveable { mutableStateOf(false) }
    var debugOverlayVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(settingsState.anthropicApiKey) {
        apiKeyDraft = settingsState.anthropicApiKey
    }

    Scaffold(
        topBar = {
            ClauneTopAppBar(
                title = "Settings",
                primaryActionLabel = "Done",
                onPrimaryAction = onNavigateBack,
            )
        },
        containerColor = ClaunePalette.Background,
    ) { innerPadding ->
        LazyColumn(
            modifier =
            Modifier
                .fillMaxSize()
                .background(ClaunePalette.Background)
                .imePadding(),
            contentPadding =
            PaddingValues(
                start = ClauneLayout.ScreenPadding,
                top = innerPadding.calculateTopPadding() + 16.dp,
                end = ClauneLayout.ScreenPadding,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(ClauneLayout.SectionGap),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Control room",
                        style = MaterialTheme.typography.headlineLarge,
                        color = ClaunePalette.Ink,
                    )
                    Text(
                        text = "Keep the agent key on-device and check whether phone control is ready.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ClaunePalette.InkSoft,
                    )
                }
            }

            item {
                Card(
                    shape = ClauneShapes.Card,
                    colors = CardDefaults.cardColors(containerColor = ClaunePalette.BackgroundDeep),
                    modifier = Modifier.border(1.dp, ClaunePalette.Rule, ClauneShapes.Card),
                ) {
                    Column(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(ClauneLayout.CardPadding),
                        verticalArrangement = Arrangement.spacedBy(ClauneLayout.ControlGap),
                    ) {
                        SectionLabel("Anthropic key")
                        Text(
                            text = "The app now reads the API key from here instead of requiring a rebuild.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ClaunePalette.InkSoft,
                        )
                        OutlinedTextField(
                            value = apiKeyDraft,
                            onValueChange = { apiKeyDraft = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            shape = ClauneShapes.Control,
                            label = { Text("Anthropic API key") },
                            visualTransformation =
                            if (showKey) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions =
                            KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                keyboardType = KeyboardType.Password,
                            ),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(ClauneLayout.ControlGap)) {
                            ClaunePrimaryButton(
                                text = "Save key",
                                onClick = { onSaveKey(apiKeyDraft) },
                            )
                            ClauneSecondaryButton(
                                text = if (showKey) "Hide" else "Show",
                                onClick = { showKey = !showKey },
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    shape = ClauneShapes.Card,
                    colors = CardDefaults.cardColors(containerColor = ClaunePalette.BackgroundDeep),
                    modifier = Modifier.border(1.dp, ClaunePalette.Rule, ClauneShapes.Card),
                ) {
                    Column(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(ClauneLayout.CardPadding),
                        verticalArrangement = Arrangement.spacedBy(ClauneLayout.ControlGap),
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
                            color = ClaunePalette.InkSoft,
                        )
                        ClauneSecondaryButton(
                            text = "Open accessibility settings",
                            onClick = onOpenAccessibilitySettings,
                        )
                    }
                }
            }

            if (BuildConfig.DEBUG) {
                item {
                    Card(
                        shape = ClauneShapes.Card,
                        colors = CardDefaults.cardColors(containerColor = ClaunePalette.BackgroundDeep),
                        modifier = Modifier.border(1.dp, ClaunePalette.Rule, ClauneShapes.Card),
                    ) {
                        Column(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(ClauneLayout.CardPadding),
                            verticalArrangement = Arrangement.spacedBy(ClauneLayout.ControlGap),
                        ) {
                            SectionLabel("Debug")
                            Text(
                                text = "Show the same accessibility overlay without starting an agent run.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ClaunePalette.InkSoft,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Test overlay",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = ClaunePalette.Ink,
                                )
                                Switch(
                                    checked = debugOverlayVisible,
                                    onCheckedChange = { visible ->
                                        debugOverlayVisible = visible
                                        onDebugOverlayVisibleChanged(visible)
                                    },
                                    enabled = accessibilityConnected,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewSessionCard(onCreateSession: () -> Unit) {
    Card(
        shape = ClauneShapes.Card,
        colors = CardDefaults.cardColors(containerColor = ClaunePalette.Background),
        modifier =
        Modifier
            .fillMaxWidth()
            .border(1.dp, ClaunePalette.Rule, ClauneShapes.Card),
    ) {
        ListItem(
            overlineContent = {
                SectionLabel("New session")
            },
            headlineContent = {
                Text(
                    text = "Start something new",
                    style = MaterialTheme.typography.titleMedium,
                    color = ClaunePalette.Ink,
                )
            },
            trailingContent = {
                ClauneRowChevron(contentDescription = "Start new session")
            },
            colors = ListItemDefaults.colors(containerColor = ClaunePalette.Background),
            modifier = Modifier.clickable(onClick = onCreateSession),
        )
    }
}

@Composable
private fun SessionSystemChip(text: String) {
    Box(
        modifier =
        Modifier
            .background(ClaunePalette.SurfaceMuted, ClauneShapes.Control)
            .border(1.dp, ClaunePalette.RuleSoft, ClauneShapes.Control)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = ClaunePalette.InkSoft,
        )
    }
}

@Composable
private fun SessionEmptyState() {
    Card(
        shape = ClauneShapes.Card,
        colors = CardDefaults.cardColors(containerColor = ClaunePalette.Background),
        modifier =
        Modifier
            .fillMaxWidth()
            .border(1.dp, ClaunePalette.Rule, ClauneShapes.Card),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(ClauneLayout.CardPadding),
            verticalArrangement = Arrangement.spacedBy(ClauneLayout.TightGap),
        ) {
            Text(
                text = "This session is empty.",
                style = MaterialTheme.typography.titleMedium,
                color = ClaunePalette.Ink,
            )
            Text(
                text = "Type or dictate the first instruction below.",
                style = MaterialTheme.typography.bodyMedium,
                color = ClaunePalette.InkSoft,
            )
        }
    }
}

@Composable
private fun SessionTranscriptRow(entry: PersistedSessionDetailEntry) {
    val isUser = entry.kind == PersistedSessionDetailKind.User
    val background =
        if (isUser) {
            ClaunePalette.AccentSoft
        } else {
            ClaunePalette.BackgroundDeep
        }
    val shape =
        if (isUser) {
            ClauneShapes.UserBubble
        } else {
            ClauneShapes.AssistantBubble
        }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = background),
            modifier =
            Modifier
                .fillMaxWidth(if (isUser) 0.84f else 0.94f)
                .border(1.dp, ClaunePalette.RuleSoft, shape),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SectionLabel(
                    text = entry.title,
                    modifier =
                    if (isUser) {
                        Modifier.align(Alignment.End)
                    } else {
                        Modifier
                    },
                )
                MarkdownText(
                    markdown = entry.body,
                    color = ClaunePalette.Ink,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun isChatTranscriptEntry(entry: PersistedSessionDetailEntry): Boolean =
    entry.kind == PersistedSessionDetailKind.User || entry.kind == PersistedSessionDetailKind.Assistant

@Composable
private fun MarkdownText(markdown: String, color: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val markwon = remember(context) { MarkdownRenderer.create(context) }
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            TextView(viewContext).apply {
                includeFontPadding = false
                textSize = 16f
                setLineSpacing(0f, 1.08f)
                setTextColor(color.toArgb())
                setLinkTextColor(ClaunePalette.AccentDeep.toArgb())
            }
        },
        update = { textView ->
            textView.setTextColor(color.toArgb())
            textView.setLinkTextColor(ClaunePalette.AccentDeep.toArgb())
            MarkdownRenderer.render(markwon, textView, markdown)
        },
    )
}

@Composable
private fun SessionComposerCard(
    goal: String,
    previewGoal: String,
    isListening: Boolean,
    listeningElapsedSeconds: Int,
    listeningError: String?,
    canEdit: Boolean,
    canSend: Boolean,
    canStop: Boolean,
    inputHelpText: String,
    focusRequester: FocusRequester,
    onGoalChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onStartVoiceCapture: () -> Unit,
    onStopVoiceCapture: () -> Unit,
    onStopSession: () -> Unit,
) {
    val visibleGoal = if (isListening) previewGoal.ifBlank { goal } else goal

    Card(
        shape = ClauneShapes.Card,
        colors = CardDefaults.cardColors(containerColor = ClaunePalette.Background),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier =
        Modifier
            .fillMaxWidth()
            .border(1.dp, ClaunePalette.Rule, ClauneShapes.Card),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(ClauneLayout.SurfacePadding),
            verticalArrangement = Arrangement.spacedBy(ClauneLayout.ControlGap),
        ) {
            if (isListening) {
                ComposerTopBar("Listening · ${elapsedLabel(listeningElapsedSeconds)}")
            }
            OutlinedTextField(
                value = visibleGoal,
                onValueChange = onGoalChanged,
                enabled = canEdit || isListening,
                readOnly = isListening,
                label = { Text("Instruction") },
                placeholder = { Text("What should Claune do on your phone?") },
                supportingText = {
                    Text(
                        text =
                        listeningError
                            ?: if (isListening) {
                                "Tap Done to review the text before Claune starts using the phone."
                            } else {
                                inputHelpText
                            },
                    )
                },
                isError = listeningError != null,
                minLines = 3,
                maxLines = 6,
                shape = ClauneShapes.Control,
                textStyle = MaterialTheme.typography.bodyLarge,
                colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ClaunePalette.Accent,
                    unfocusedBorderColor = ClaunePalette.RuleSoft,
                    focusedContainerColor = ClaunePalette.Background,
                    unfocusedContainerColor = ClaunePalette.Background,
                    focusedTextColor = ClaunePalette.Ink,
                    unfocusedTextColor = ClaunePalette.Ink,
                    cursorColor = ClaunePalette.AccentDeep,
                ),
                keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions =
                KeyboardActions(
                    onSend = {
                        if (canSend) {
                            onSendMessage()
                        }
                    },
                ),
                modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag("goal_input"),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClauneLayout.ControlGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ClauneSecondaryButton(
                    text = if (isListening) "Done" else "Voice",
                    onClick = if (isListening) onStopVoiceCapture else onStartVoiceCapture,
                    icon = if (isListening) Icons.Filled.Check else Icons.Filled.Mic,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (canStop) {
                    ClauneSecondaryButton(
                        text = "Stop",
                        onClick = onStopSession,
                        icon = Icons.Filled.Stop,
                    )
                }
                ClaunePrimaryButton(
                    text = "Start on phone",
                    onClick = onSendMessage,
                    enabled = canSend,
                    icon = Icons.AutoMirrored.Filled.Send,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = ClaunePalette.InkFaint,
        style = MaterialTheme.typography.bodySmall.copy(
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.08.em,
        ),
        modifier = modifier,
    )
}

private data class VoiceUiState(
    val transcript: String = "",
    val previewTranscript: String = "",
    val elapsedSeconds: Int = 0,
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

private fun sessionStatusBannerText(status: SessionStatus, lastKnownApp: String?): String? = when (status) {
    SessionStatus.Running -> "Working${lastKnownApp?.let { " in $it" } ?: ""}"
    SessionStatus.Blocked -> "Blocked"
    SessionStatus.Paused -> "Paused"
    SessionStatus.Completed,
    SessionStatus.Cancelled,
    SessionStatus.Idle,
    -> null
}

@Composable
private fun SetupStepRow(title: String, complete: Boolean, actionLabel: String, onAction: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = ClaunePalette.Ink,
            )
        },
        supportingContent = {
            Text(
                text = if (complete) "Ready" else "Needs attention",
                color = ClaunePalette.InkSoft,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.05.em,
                ),
            )
        },
        trailingContent = {
            if (complete) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Ready",
                        tint = ClaunePalette.AccentDeep,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "done",
                        color = ClaunePalette.AccentDeep,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.05.em,
                        ),
                    )
                }
            } else {
                ClauneTextAction(actionLabel, onAction)
            }
        },
        colors = ListItemDefaults.colors(containerColor = ClaunePalette.BackgroundDeep),
        modifier = Modifier.fillMaxWidth(),
    )
}
