@file:Suppress("ktlint:standard:function-signature")

package com.divyanshgolyan.claune.android.runtime

import com.divyanshgolyan.claune.android.data.local.PersistedSessionSummary
import java.time.Instant

data class SessionUiState(
    val activeRunId: String? = null,
    val selectedSessionPath: String? = null,
    val selectedPersistentSessionId: String? = null,
    val selectedSessionTitle: String? = null,
    val activeSessionPath: String? = null,
    val activePersistentSessionId: String? = null,
    val activeSessionTitle: String? = null,
    val recentSessions: List<PersistedSessionSummary> = emptyList(),
    val status: SessionStatus = SessionStatus.Idle,
    val summaryLine: String = "Idle and waiting for a message.",
    val lastKnownApp: String? = null,
    val appInForeground: Boolean = false,
    val accessibilityConnected: Boolean = false,
    val foregroundServiceRunning: Boolean = false,
    val isStreaming: Boolean = false,
    val isCompacting: Boolean = false,
    val pendingSteeringCount: Int = 0,
    val pendingQuestion: PendingQuestionUiState? = null,
    val lastAssistantText: String = "",
    val timeline: List<String> = listOf("Prototype scaffold ready. Start a session and send a message."),
)

data class PendingQuestionUiState(val id: String, val prompt: String, val options: List<String>)

data class QuestionAnswer(val text: String, val kind: QuestionAnswerKind, val optionIndex: Int? = null)

enum class QuestionAnswerKind {
    Option,
    Custom,
}

enum class SessionStatus {
    Idle,
    Running,
    Paused,
    Blocked,
    Completed,
    Cancelled,
}

data class UiSnapshot(
    val snapshotId: String,
    val capturedAt: Instant,
    val foregroundPackage: String,
    val visibleText: List<String>,
    val actionableElements: List<UiElement>,
    val focusedElementId: String?,
    val windowCandidates: List<WindowCandidate> = emptyList(),
    val selectedWindowReason: String? = null,
)

data class WindowCandidate(
    val packageName: String,
    val className: String?,
    val type: String,
    val layer: Int,
    val active: Boolean,
    val focused: Boolean,
    val bounds: List<Int>,
    val visibleText: List<String>,
    val actionableElementCount: Int,
    val selected: Boolean = false,
    val selectionReason: String? = null,
)

data class UiElement(
    val id: String,
    val ref: String = id,
    val role: String,
    val label: String,
    val text: String? = null,
    val contentDescription: String? = null,
    val resourceId: String? = null,
    val className: String? = null,
    val clickable: Boolean,
    val focusable: Boolean = false,
    val editable: Boolean,
    val focused: Boolean,
    val enabled: Boolean = true,
    val checked: Boolean = false,
    val selected: Boolean = false,
    val scrollable: Boolean = false,
    val bounds: List<Int>,
)

data class ElementRef(val elementId: String)

enum class ScrollDirection {
    Up,
    Down,
    Left,
    Right,
}

sealed interface ActionResult {
    data class Success(val message: String) : ActionResult

    data class Blocked(val reason: String) : ActionResult
}

data class ModelTurnInput(
    val runId: String,
    val persistentSessionPath: String?,
    val persistentSessionId: String?,
    val userMessage: String,
    val snapshot: UiSnapshot,
    val recentEvents: List<String>,
)

sealed interface ModelTurnOutput {
    data class Message(val messageToUser: String) : ModelTurnOutput

    data class Completion(val summary: String) : ModelTurnOutput

    data class Blocked(val reason: String) : ModelTurnOutput
}

interface PhoneObserver {
    suspend fun captureSnapshot(): UiSnapshot
}

interface PhoneActuator {
    suspend fun tap(target: ElementRef): ActionResult

    suspend fun type(target: ElementRef, text: String): ActionResult

    suspend fun scroll(target: ElementRef, direction: ScrollDirection): ActionResult

    suspend fun pressBack(): ActionResult

    suspend fun pressHome(): ActionResult
}
