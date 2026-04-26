@file:Suppress("ktlint:standard:function-signature")

package com.divyanshgolyan.claune.android.runtime

import com.divyanshgolyan.claune.android.data.local.PersistedSessionSummary
import kotlinx.serialization.Serializable

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

@Serializable
data class ScreenState(
    val snapshotId: String,
    val capturedAt: String,
    val foregroundPackage: String,
    val root: ScreenNode? = null,
    val windows: List<ScreenWindow> = emptyList(),
    val selectedWindowReason: String? = null,
)

@Serializable
data class ScreenWindow(
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

@Serializable
data class ScreenNode(
    val path: List<Int>,
    val ref: String,
    val elementId: String,
    val role: String,
    val label: String,
    val text: String? = null,
    val contentDescription: String? = null,
    val resourceId: String? = null,
    val className: String? = null,
    val packageName: String? = null,
    val visibleToUser: Boolean = false,
    val clickable: Boolean,
    val focusable: Boolean = false,
    val editable: Boolean,
    val focused: Boolean,
    val enabled: Boolean = true,
    val checked: Boolean = false,
    val selected: Boolean = false,
    val scrollable: Boolean = false,
    val importantForAccessibility: Boolean = false,
    val bounds: List<Int>,
    val actions: List<String> = emptyList(),
    val tapFallbackEligible: Boolean = false,
    val clickabilityReason: String = "",
    val clickableParentDepth: Int? = null,
    val clickableParentClassName: String? = null,
    val clickableDescendantPath: String? = null,
    val clickableDescendantClassName: String? = null,
    val children: List<ScreenNode> = emptyList(),
)

data class ElementRef(val elementId: String)

enum class CanonicalScreenMode {
    Compact,
    Full,
}

@Serializable
data class ScreenDiffStats(
    val additions: Int,
    val removals: Int,
    val unchanged: Int,
    val beforeLineCount: Int,
    val afterLineCount: Int,
    val changeRatio: Double,
)

@Serializable
data class ScreenObservation(
    val mode: String,
    val reason: String,
    val baselineSnapshotId: String? = null,
    val currentSnapshotId: String,
    val foregroundPackage: String,
    val selectedWindowReason: String? = null,
    val baselineMissing: Boolean = false,
    val stats: ScreenDiffStats,
    val canonicalText: String? = null,
    val diff: String? = null,
)

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
    val screenObservation: ScreenObservation,
    val recentEvents: List<String>,
)

sealed interface ModelTurnOutput {
    data class Message(val messageToUser: String) : ModelTurnOutput

    data class Completion(val summary: String) : ModelTurnOutput

    data class Blocked(val reason: String) : ModelTurnOutput
}

interface PhoneObserver {
    suspend fun captureScreenState(): ScreenState
}

interface PhoneActuator {
    suspend fun tap(target: ElementRef): ActionResult

    suspend fun tapPoint(x: Int, y: Int): ActionResult

    suspend fun type(target: ElementRef, text: String): ActionResult

    suspend fun typeFocused(text: String): ActionResult

    suspend fun scroll(target: ElementRef, direction: ScrollDirection): ActionResult

    suspend fun pressBack(): ActionResult

    suspend fun pressHome(): ActionResult
}
