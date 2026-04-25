package com.divyanshgolyan.claune.android.scripting

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

interface ScriptRuntime {
    suspend fun execute(request: ScriptExecutionRequest): ScriptExecutionResult
}

data class ScriptExecutionRequest(val script: String, val source: String = "script_lab")

data class ScriptExecutionResult(
    val ok: Boolean,
    val summary: String,
    val data: JsonElement? = null,
    val hostCalls: List<HostCallRecord> = emptyList(),
    val error: String? = null,
)

@Serializable
data class ScriptExecutionRecord(
    val scriptExecutionId: String,
    val runId: String? = null,
    val source: String,
    val script: String,
    val ok: Boolean,
    val summary: String,
    val dataJson: String? = null,
    val hostCallCount: Int,
    val error: String? = null,
    val startedAt: String,
    val finishedAt: String,
)

@Serializable
data class HostCallRecord(
    val hostCallId: String,
    val scriptExecutionId: String,
    val runId: String? = null,
    val name: String,
    val argumentsJson: String,
    val resultJson: String,
    val startedAt: String,
    val finishedAt: String,
)

@Serializable
data class HostCallOutcome(val ok: Boolean, val message: String, val data: JsonElement? = null)

@Serializable
data class UiSnapshotPayload(
    val snapshotId: String,
    val capturedAt: String,
    val foregroundPackage: String,
    val visibleText: List<String>,
    val actionableElements: List<UiElementPayload>,
    val focusedElementId: String? = null,
    val windowCandidates: List<WindowCandidatePayload> = emptyList(),
    val selectedWindowReason: String? = null,
)

@Serializable
data class InstalledAppPayload(val label: String, val packageName: String, val activityName: String? = null)

@Serializable
data class WindowCandidatePayload(
    val packageName: String,
    val className: String? = null,
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
data class UiElementPayload(
    val id: String,
    val ref: String,
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

@Serializable
data class ElementSelectorPayload(
    val ref: String? = null,
    val label: String? = null,
    val text: String? = null,
    val textExact: Boolean = false,
    val contentDescription: String? = null,
    val resourceId: String? = null,
    val role: String? = null,
    val clickable: Boolean? = null,
    val focusable: Boolean? = null,
    val editable: Boolean? = null,
    val focused: Boolean? = null,
    val enabled: Boolean? = null,
    val checked: Boolean? = null,
    val selected: Boolean? = null,
    val scrollable: Boolean? = null,
    val first: Boolean = false,
)
