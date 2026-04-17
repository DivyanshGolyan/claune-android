package com.divyanshgolyan.claune.android.runtime

import java.time.Instant

data class SessionUiState(
    val sessionId: String? = null,
    val status: SessionStatus = SessionStatus.Idle,
    val summaryLine: String = "Idle and waiting for a goal.",
    val lastKnownApp: String? = null,
    val accessibilityConnected: Boolean = false,
    val foregroundServiceRunning: Boolean = false,
    val timeline: List<String> = listOf("Prototype scaffold ready. Start a session to exercise the loop skeleton."),
)

enum class SessionStatus {
    Idle,
    Running,
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

data class ModelTurnInput(val sessionId: String, val goal: String, val snapshot: UiSnapshot, val recentEvents: List<String>)

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
