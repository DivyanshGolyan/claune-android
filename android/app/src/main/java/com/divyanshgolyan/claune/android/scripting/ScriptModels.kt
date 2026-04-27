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

const val SCREEN_MODE_INTERACTIONS = "interactions"
const val SCREEN_MODE_FULL_SNAPSHOT = "full_snapshot"
const val SCREEN_MODE_COMPACT_SNAPSHOT = "compact_snapshot"
const val ACTION_KIND_CLICK = "click"
const val ACTION_KIND_TYPE = "type"
const val ACTION_KIND_SCROLL = "scroll"
const val FALLBACK_METHOD_PERFORM_ACTION = "performAction"
const val FALLBACK_METHOD_CLICKABLE_PARENT = "clickableParent"
const val FALLBACK_METHOD_TYPE_FOCUSED = "typeFocused"
const val FALLBACK_METHOD_SCROLL = "scroll"
const val FALLBACK_METHOD_TAP_CENTER = "tapCenter"

@Serializable
data class ScreenObservationPayload(
    val mode: String,
    val reason: String,
    val baselineSnapshotId: String? = null,
    val currentSnapshotId: String,
    val snapshotId: String = currentSnapshotId,
    val capturedAt: String? = null,
    val foregroundPackage: String,
    val selectedWindowReason: String? = null,
    val baselineMissing: Boolean = false,
    val stats: ScreenDiffStatsPayload,
    val canonicalText: String? = null,
    val diff: String? = null,
    val windows: List<ScreenWindowPayload> = emptyList(),
    val selectedWindow: ScreenWindowPayload? = null,
    val summaryText: String? = null,
    val elements: List<VisibleElementPayload> = emptyList(),
    val groups: List<VisibleGroupPayload> = emptyList(),
    val actions: List<ActionAffordancePayload> = emptyList(),
    val diagnostics: InteractionDiagnosticsPayload? = null,
)

@Serializable
data class ScreenDiffStatsPayload(
    val additions: Int,
    val removals: Int,
    val unchanged: Int,
    val beforeLineCount: Int,
    val afterLineCount: Int,
    val changeRatio: Double,
)

@Serializable
data class ScreenObserveOptionsPayload(val mode: String = SCREEN_MODE_INTERACTIONS, val includeDiff: Boolean = true)

@Serializable
data class ScreenDiffOptionsPayload(val baselineSnapshotId: String? = null)

@Serializable
data class ScreenInspectOptionsPayload(val text: String? = null, val includeAll: Boolean = false, val limit: Int = 20)

@Serializable
data class RawNodeSearchOptionsPayload(
    val pattern: String,
    val flags: String = "i",
    val fields: List<String> = emptyList(),
    val limit: Int = 20,
    val includeContext: Boolean = true,
)

@Serializable
data class ScreenInspectionPayload(
    val snapshotId: String,
    val capturedAt: String,
    val foregroundPackage: String,
    val query: String? = null,
    val visibleElements: List<ScreenNodePayload>,
    val actionableElements: List<ScreenNodePayload>,
    val selectedWindowReason: String? = null,
)

@Serializable
data class RawNodeSearchResultPayload(
    val snapshotId: String,
    val foregroundPackage: String,
    val pattern: String,
    val error: String? = null,
    val matches: List<RawNodeMatchPayload> = emptyList(),
)

@Serializable
data class RawNodeMatchPayload(
    val node: ScreenNodePayload,
    val matchedFields: List<String>,
    val matchedText: String,
    val nearestActionable: ScreenNodePayload? = null,
    val ancestorLabels: List<String> = emptyList(),
    val childLabels: List<String> = emptyList(),
)

@Serializable
data class InstalledAppPayload(val label: String, val packageName: String, val activityName: String? = null)

@Serializable
data class ScreenWindowPayload(
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
data class ScreenNodePayload(
    val elementId: String,
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
    val center: List<Int>,
    val actions: List<String> = emptyList(),
    val tapFallbackEligible: Boolean = false,
    val clickabilityReason: String = "",
    val clickableParentDepth: Int? = null,
    val clickableParentClassName: String? = null,
    val clickableDescendantPath: String? = null,
    val clickableDescendantClassName: String? = null,
)

@Serializable
data class VisibleElementPayload(
    val id: String,
    val ref: String,
    val elementId: String,
    val normalizedLabel: String,
    val textFields: ElementTextFieldsPayload,
    val role: String,
    val className: String? = null,
    val resourceId: String? = null,
    val bounds: List<Int>,
    val center: List<Int>,
    val state: ElementStatePayload,
    val visibility: VisibilityEvidencePayload,
    val rawRefs: List<String>,
    val groupIds: List<String> = emptyList(),
)

@Serializable
data class ElementTextFieldsPayload(
    val label: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val resourceId: String? = null,
    val className: String? = null,
)

@Serializable
data class ElementStatePayload(
    val enabled: Boolean = true,
    val checked: Boolean = false,
    val selected: Boolean = false,
    val focused: Boolean = false,
    val editable: Boolean = false,
    val scrollable: Boolean = false,
    val clickable: Boolean = false,
    val focusable: Boolean = false,
)

@Serializable
data class VisibilityEvidencePayload(
    val a11yVisibleToUser: Boolean,
    val hasNonEmptyBounds: Boolean,
    val intersectsSelectedWindow: Boolean,
    val visibleAreaRatio: Double,
    val selectedWindow: Boolean,
    val occlusion: String = "unknown",
    val confidence: Double,
)

@Serializable
data class VisibleGroupPayload(
    val id: String,
    val role: String,
    val labelSummary: String,
    val bounds: List<Int>,
    val elementIds: List<String>,
    val actionIds: List<String>,
    val parentGroupId: String? = null,
    val childGroupIds: List<String> = emptyList(),
    val confidence: Double,
    val evidence: List<String>,
)

@Serializable
data class ActionAffordancePayload(
    val id: String,
    val label: String,
    val kind: String,
    val bounds: List<Int>,
    val center: List<Int>,
    val enabled: Boolean,
    val targetRef: String,
    val targetElementId: String,
    val equivalentRefs: List<String>,
    val fallbackMethod: String,
    val scope: ActionScopePayload,
    val confidence: Double,
    val evidence: List<String>,
)

@Serializable
data class ActionScopePayload(val groupId: String? = null, val elementId: String? = null)

@Serializable
data class InteractionDiagnosticsPayload(
    val visibleElementCount: Int,
    val actionCount: Int,
    val groupCount: Int,
    val rawVisibleNodeCount: Int,
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
