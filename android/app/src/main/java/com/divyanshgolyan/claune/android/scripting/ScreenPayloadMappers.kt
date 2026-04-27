package com.divyanshgolyan.claune.android.scripting

import com.divyanshgolyan.claune.android.runtime.CanonicalScreenMode
import com.divyanshgolyan.claune.android.runtime.ScreenDiffStats
import com.divyanshgolyan.claune.android.runtime.ScreenNode
import com.divyanshgolyan.claune.android.runtime.ScreenObservation
import com.divyanshgolyan.claune.android.runtime.ScreenState
import com.divyanshgolyan.claune.android.runtime.ScreenWindow
import com.divyanshgolyan.claune.android.runtime.actionableNodes
import com.divyanshgolyan.claune.android.runtime.boundsArea
import com.divyanshgolyan.claune.android.runtime.centerPoint
import com.divyanshgolyan.claune.android.runtime.elapsedMs
import com.divyanshgolyan.claune.android.runtime.flatten
import com.divyanshgolyan.claune.android.runtime.matchesOwnText
import com.divyanshgolyan.claune.android.runtime.matchesText
import com.divyanshgolyan.claune.android.runtime.normalizedScreenText
import com.divyanshgolyan.claune.android.runtime.toCanonicalScreenText
import com.divyanshgolyan.claune.android.runtime.visibleNodes
import kotlin.math.max
import kotlin.math.min

fun ScreenObservation.toPayload(): ScreenObservationPayload = ScreenObservationPayload(
    mode = mode,
    reason = reason,
    baselineSnapshotId = baselineSnapshotId,
    currentSnapshotId = currentSnapshotId,
    foregroundPackage = foregroundPackage,
    selectedWindowReason = selectedWindowReason,
    baselineMissing = baselineMissing,
    stats = stats.toPayload(),
    canonicalText = canonicalText,
    diff = diff,
)

fun ScreenDiffStats.toPayload(): ScreenDiffStatsPayload = ScreenDiffStatsPayload(
    additions = additions,
    removals = removals,
    unchanged = unchanged,
    beforeLineCount = beforeLineCount,
    afterLineCount = afterLineCount,
    changeRatio = changeRatio,
)

fun ScreenState.toInteractionObservationPayload(profiler: ProjectionProfiler? = null): ScreenObservationPayload {
    val projection = profiler.measure("buildInteractionProjection") { buildInteractionProjection(profiler) }
    val summary = projection.summaryText
    return ScreenObservationPayload(
        mode = SCREEN_MODE_INTERACTIONS,
        reason = "explicit_observe",
        currentSnapshotId = snapshotId,
        snapshotId = snapshotId,
        capturedAt = capturedAt,
        foregroundPackage = foregroundPackage,
        selectedWindowReason = selectedWindowReason,
        stats = ScreenDiffStatsPayload(
            additions = summary.lines().size,
            removals = 0,
            unchanged = 0,
            beforeLineCount = 0,
            afterLineCount = summary.lines().size,
            changeRatio = 1.0,
        ),
        windows = windows.map { it.toPayload() },
        selectedWindow = windows.firstOrNull { it.selected }?.toPayload(),
        summaryText = summary,
        elements = projection.elements,
        groups = projection.groups,
        actions = projection.actions,
        diagnostics = projection.diagnostics,
    )
}

fun ScreenState.toObservationPayload(mode: CanonicalScreenMode): ScreenObservationPayload = ScreenObservationPayload(
    mode = if (mode == CanonicalScreenMode.Full) SCREEN_MODE_FULL_SNAPSHOT else SCREEN_MODE_COMPACT_SNAPSHOT,
    reason = "explicit_observe",
    currentSnapshotId = snapshotId,
    foregroundPackage = foregroundPackage,
    selectedWindowReason = selectedWindowReason,
    stats = ScreenDiffStatsPayload(
        additions = toCanonicalScreenText(mode).lines().size,
        removals = 0,
        unchanged = 0,
        beforeLineCount = 0,
        afterLineCount = toCanonicalScreenText(mode).lines().size,
        changeRatio = 1.0,
    ),
    canonicalText = toCanonicalScreenText(mode),
)

fun ScreenState.toInspectionPayload(options: ScreenInspectOptionsPayload): ScreenInspectionPayload {
    val query = options.text?.trim()?.takeIf { it.isNotBlank() }
    val limit = options.limit.coerceIn(1, 80)
    val visibleMatches =
        visibleNodes()
            .asSequence()
            .filter { node -> query == null || node.matchesText(query) }
            .sortedWith(inspectionComparator(query))
            .take(limit)
            .map { it.toPayload() }
            .toList()
    val actionableMatches =
        actionableNodes()
            .asSequence()
            .filter { node -> query == null || node.matchesText(query) }
            .sortedWith(inspectionComparator(query))
            .take(20)
            .map { it.toPayload() }
            .toList()
    return ScreenInspectionPayload(
        snapshotId = snapshotId,
        capturedAt = capturedAt,
        foregroundPackage = foregroundPackage,
        query = query,
        visibleElements = visibleMatches,
        actionableElements = actionableMatches,
        selectedWindowReason = selectedWindowReason,
    )
}

fun ScreenState.toRawNodeSearchResult(options: RawNodeSearchOptionsPayload): RawNodeSearchResultPayload {
    val pattern = options.pattern.trim()
    if (pattern.isBlank()) {
        return rawSearchError(pattern = pattern, message = "pattern must not be blank")
    }
    val unsupportedFlags = options.flags.filterNot { it == 'i' }
    if (unsupportedFlags.isNotEmpty()) {
        return rawSearchError(pattern = pattern, message = "unsupported regex flags '$unsupportedFlags'; v1 supports only 'i'")
    }
    val regex =
        runCatching {
            Regex(
                pattern = pattern,
                options = if (options.flags.contains('i')) setOf(RegexOption.IGNORE_CASE) else emptySet(),
            )
        }.getOrElse { throwable ->
            return rawSearchError(
                pattern = pattern,
                message = throwable.message?.let { "invalid regex: $it" } ?: "invalid regex",
            )
        }
    val limit = options.limit.coerceIn(1, 50)
    val fields = options.fields.normalizedSearchFields()
    val visibleNodes = visibleNodes()
    val visibleByPath = visibleNodes.associateBy { it.path }
    val matches =
        visibleNodes
            .asSequence()
            .mapNotNull { node -> node.toRawMatch(regex, fields, visibleByPath, includeContext = options.includeContext) }
            .sortedWith(
                compareByDescending<RawNodeMatchPayload> { it.nearestActionable != null }
                    .thenByDescending { it.node.clickable || it.node.tapFallbackEligible || it.node.actions.isNotEmpty() }
                    .thenBy { it.node.bounds.boundsArea() },
            )
            .take(limit)
            .toList()
    return RawNodeSearchResultPayload(
        snapshotId = snapshotId,
        foregroundPackage = foregroundPackage,
        pattern = pattern,
        matches = matches,
    )
}

fun ScreenWindow.toPayload(): ScreenWindowPayload = ScreenWindowPayload(
    packageName = packageName,
    className = className,
    type = type,
    layer = layer,
    active = active,
    focused = focused,
    bounds = bounds,
    visibleText = visibleText,
    actionableElementCount = actionableElementCount,
    selected = selected,
    selectionReason = selectionReason,
)

fun ScreenNode.toPayload(): ScreenNodePayload = ScreenNodePayload(
    elementId = elementId,
    ref = ref,
    role = role,
    label = label,
    text = text,
    contentDescription = contentDescription,
    resourceId = resourceId,
    className = className,
    clickable = clickable,
    focusable = focusable,
    editable = editable,
    focused = focused,
    enabled = enabled,
    checked = checked,
    selected = selected,
    scrollable = scrollable,
    bounds = bounds,
    center = bounds.centerPoint(),
    actions = actions,
    tapFallbackEligible = tapFallbackEligible,
    clickabilityReason = clickabilityReason,
    clickableParentDepth = clickableParentDepth,
    clickableParentClassName = clickableParentClassName,
    clickableDescendantPath = clickableDescendantPath,
    clickableDescendantClassName = clickableDescendantClassName,
)

fun ScreenState.findInteractionAction(actionId: String): ActionAffordancePayload? =
    buildInteractionActionsOnly().firstOrNull { it.id == actionId }

fun ScreenState.interactionActionTargetElementId(actionId: String): String? = findInteractionAction(actionId)?.targetElementId

fun String.toCanonicalScreenMode(): CanonicalScreenMode = when (lowercase()) {
    "full" -> CanonicalScreenMode.Full
    else -> CanonicalScreenMode.Compact
}

private data class InteractionProjection(
    val elements: List<VisibleElementPayload>,
    val groups: List<VisibleGroupPayload>,
    val actions: List<ActionAffordancePayload>,
    val diagnostics: InteractionDiagnosticsPayload,
    val summaryText: String,
)

data class ProjectionPhaseTiming(val name: String, val durationMs: Long)

class ProjectionProfiler {
    private val phases = mutableListOf<ProjectionPhaseTiming>()

    fun <T> measure(name: String, block: () -> T): T {
        val started = System.nanoTime()
        try {
            return block()
        } finally {
            phases += ProjectionPhaseTiming(name = name, durationMs = elapsedMs(started))
        }
    }

    fun phases(): List<ProjectionPhaseTiming> = phases.toList()
}

private fun <T> ProjectionProfiler?.measure(name: String, block: () -> T): T = this?.measure(name, block) ?: block()

private data class VisibleElementDraft(val node: ScreenNode, val groupIds: List<String>)

private data class GroupDraft(val id: String, val node: ScreenNode, val elementIds: List<String>, val evidence: List<String>)

private data class ActionCluster(val nodes: MutableList<ScreenNode>)

private data class ActionCandidate(val node: ScreenNode, val label: String, val kind: String) {
    val clusterKey: String = "${kind.lowercase()}|${label.lowercase()}"
}

private fun ScreenState.buildInteractionProjection(profiler: ProjectionProfiler?): InteractionProjection {
    val selectedWindow = profiler.measure("selectedWindow") { windows.firstOrNull { it.selected } }
    val allNodes = profiler.measure("flattenRoot") { root?.flatten().orEmpty() }
    val visibleNodes = profiler.measure("filterVisibleNodes") {
        allNodes.filter { node -> node.path.isNotEmpty() && node.visibleToUser && node.bounds.boundsArea() > 0 }
    }
    val elementNodes = profiler.measure("buildElementNodes") {
        visibleNodes
            .filter { node -> node.normalizedVisibleLabel().isNotBlank() || node.isInteractionActionCandidate() }
            .sortedWith(compareBy<ScreenNode> { it.boundsTop() }.thenBy { it.boundsLeft() }.thenBy { it.pathKey() })
    }

    val groups = profiler.measure("buildGroups") { buildGroups(visibleNodes = visibleNodes, elementNodes = elementNodes) }
    val groupByElementId = profiler.measure("indexGroupsByElement") {
        groups
            .flatMap { group -> group.elementIds.map { elementId -> elementId to group.id } }
            .groupBy({ it.first }, { it.second })
    }
    val actions = profiler.measure("buildActions") { buildActions(elementNodes = elementNodes, groups = groups) }
    val actionIdsByGroup = profiler.measure("indexActionsByGroup") {
        actions
            .mapNotNull { action -> action.scope.groupId?.let { groupId -> groupId to action.id } }
            .groupBy({ it.first }, { it.second })
    }
    val childGroupsByParent = profiler.measure("indexChildGroups") {
        groups
            .mapNotNull { group -> group.parentGroupId?.let { parent -> parent to group.id } }
            .groupBy({ it.first }, { it.second })
    }
    val labelSummaryByGroup = profiler.measure("indexGroupLabels") { buildLabelSummaryByGroup(groups, elementNodes) }

    val elements = profiler.measure("buildElementPayloads") {
        elementNodes.map { node ->
            VisibleElementDraft(node = node, groupIds = groupByElementId[node.elementId].orEmpty())
                .toPayload(selectedWindow)
        }
    }
    val visibleGroups = profiler.measure("buildGroupPayloads") {
        groups.map { group ->
            val actionIds = actionIdsByGroup[group.id].orEmpty().distinct()
            VisibleGroupPayload(
                id = group.id,
                role = group.role(),
                labelSummary = labelSummaryByGroup[group.id].orEmpty(),
                bounds = group.node.bounds,
                elementIds = group.elementIds,
                actionIds = actionIds,
                parentGroupId = group.parentGroupId,
                childGroupIds = childGroupsByParent[group.id].orEmpty().distinct(),
                confidence = group.confidence(actionIds),
                evidence = group.evidence,
            )
        }
    }
    val summaryText = profiler.measure("buildSummaryText") {
        buildInteractionSummary(
            elements = elements,
            groups = visibleGroups,
            actions = actions,
            foregroundPackage = foregroundPackage,
        )
    }

    return InteractionProjection(
        elements = elements,
        groups = visibleGroups,
        actions = actions,
        diagnostics = InteractionDiagnosticsPayload(
            visibleElementCount = elements.size,
            actionCount = actions.size,
            groupCount = visibleGroups.size,
            rawVisibleNodeCount = visibleNodes.size,
        ),
        summaryText = summaryText,
    )
}

private fun ScreenState.buildInteractionActionsOnly(): List<ActionAffordancePayload> {
    val elementNodes = root
        ?.flatten()
        .orEmpty()
        .filter { node -> node.path.isNotEmpty() && node.visibleToUser && node.bounds.boundsArea() > 0 }
        .filter { node -> node.normalizedVisibleLabel().isNotBlank() || node.isInteractionActionCandidate() }
    return buildActions(elementNodes = elementNodes, groups = emptyList())
}

private fun buildGroups(visibleNodes: List<ScreenNode>, elementNodes: List<ScreenNode>): List<GroupDraftWithParent> {
    val visibleByPath = visibleNodes.associateBy { it.path }
    val ancestorStatsByPath = buildAncestorStats(elementNodes)
    val selectedPaths = linkedSetOf<List<Int>>()
    elementNodes
        .filter { it.isInteractionActionCandidate() }
        .forEach { actionNode ->
            actionNode.path.indices.reversed().forEach { depth ->
                val path = actionNode.path.take(depth)
                val ancestor = visibleByPath[path] ?: return@forEach
                if (path.isEmpty()) return@forEach
                val stats = ancestorStatsByPath[path] ?: return@forEach
                if (
                    stats.elementCount >= 2 &&
                    stats.usefulTextCount > 0 &&
                    stats.actionCount > 0 &&
                    ancestor.boundsArea() > actionNode.boundsArea()
                ) {
                    selectedPaths += ancestor.path
                    return@forEach
                }
            }
        }

    val groupPaths = selectedPaths.take(MAX_INTERACTION_GROUPS).toList()
    val elementIdsByGroupPath = groupPaths.associateWith { mutableListOf<String>() }
    elementNodes.forEach { candidate ->
        groupPaths.forEach { groupPath ->
            if (candidate.path == groupPath || candidate.path.startsWith(groupPath)) {
                elementIdsByGroupPath.getValue(groupPath) += candidate.elementId
            }
        }
    }

    return selectedPaths
        .take(MAX_INTERACTION_GROUPS)
        .mapIndexedNotNull { index, path ->
            val node = visibleByPath[path] ?: return@mapIndexedNotNull null
            val elementIds = elementIdsByGroupPath[path].orEmpty()
                .distinct()
            if (elementIds.size < 2) return@mapIndexedNotNull null
            GroupDraft(
                id = "g${index + 1}",
                node = node,
                elementIds = elementIds,
                evidence = listOf("shared_accessibility_ancestor:${node.pathKey()}", "contains_visible_text_and_action"),
            )
        }
        .withParentIds()
}

private data class AncestorGroupStats(var elementCount: Int = 0, var usefulTextCount: Int = 0, var actionCount: Int = 0)

private fun buildAncestorStats(elementNodes: List<ScreenNode>): Map<List<Int>, AncestorGroupStats> {
    val statsByPath = mutableMapOf<List<Int>, AncestorGroupStats>()
    elementNodes.forEach { node ->
        val isAction = node.isInteractionActionCandidate()
        val hasUsefulText = node.normalizedVisibleLabel().isNotBlank() && !isAction
        for (depth in 1 until node.path.size) {
            val path = node.path.take(depth)
            val stats = statsByPath.getOrPut(path) { AncestorGroupStats() }
            stats.elementCount += 1
            if (hasUsefulText) {
                stats.usefulTextCount += 1
            }
            if (isAction) {
                stats.actionCount += 1
            }
        }
    }
    return statsByPath
}

private fun List<GroupDraft>.withParentIds(): List<GroupDraftWithParent> {
    val groups = this
    return groups.map { group ->
        val parent = groups
            .filter { candidate -> candidate.node.path != group.node.path && group.node.path.startsWith(candidate.node.path) }
            .maxByOrNull { it.node.path.size }
        group.copyWithParent(parent?.id)
    }
}

private fun GroupDraft.copyWithParent(parentGroupId: String?): GroupDraftWithParent = GroupDraftWithParent(
    id = id,
    node = node,
    elementIds = elementIds,
    evidence = evidence,
    parentGroupId = parentGroupId,
)

private data class GroupDraftWithParent(
    val id: String,
    val node: ScreenNode,
    val elementIds: List<String>,
    val evidence: List<String>,
    val parentGroupId: String?,
)

private fun buildActions(elementNodes: List<ScreenNode>, groups: List<GroupDraftWithParent>): List<ActionAffordancePayload> {
    val clusters = mutableListOf<ActionCluster>()
    val clustersByKey = mutableMapOf<String, MutableList<ActionCluster>>()
    elementNodes
        .filter { it.isInteractionActionCandidate() }
        .sortedWith(compareBy<ScreenNode> { it.boundsTop() }.thenBy { it.boundsLeft() }.thenBy { it.pathKey() })
        .map { node -> ActionCandidate(node = node, label = node.actionLabel(), kind = node.actionKind()) }
        .forEach { candidate ->
            val keyClusters = clustersByKey.getOrPut(candidate.clusterKey) { mutableListOf() }
            val match = keyClusters.firstOrNull { cluster -> cluster.nodes.any { it.sameVisualTargetAs(candidate.node) } }
            if (match == null) {
                val cluster = ActionCluster(mutableListOf(candidate.node))
                clusters += cluster
                keyClusters += cluster
            } else {
                match.nodes += candidate.node
            }
        }

    return clusters.map { cluster ->
        val preferred = cluster.nodes.preferredActionTarget()
        val group = groups
            .filter { preferred.path.startsWith(it.node.path) }
            .maxByOrNull { it.node.path.size }
        val bounds = cluster.nodes.map { it.bounds }.unionBounds()
        val semanticAction = preferred.clickable || preferred.actions.any { it.contains("CLICK", ignoreCase = true) }
        ActionAffordancePayload(
            id = stableActionId(preferred = preferred, label = preferred.actionLabel(), kind = preferred.actionKind(), bounds = bounds),
            label = preferred.actionLabel(),
            kind = preferred.actionKind(),
            bounds = bounds,
            center = bounds.centerPoint(),
            enabled = preferred.enabled,
            targetRef = preferred.ref,
            targetElementId = preferred.elementId,
            equivalentRefs = cluster.nodes.map { it.ref }.distinct(),
            fallbackMethod = when {
                semanticAction -> FALLBACK_METHOD_PERFORM_ACTION
                preferred.clickableParentDepth != null -> FALLBACK_METHOD_CLICKABLE_PARENT
                preferred.editable -> FALLBACK_METHOD_TYPE_FOCUSED
                preferred.scrollable -> FALLBACK_METHOD_SCROLL
                else -> FALLBACK_METHOD_TAP_CENTER
            },
            scope = ActionScopePayload(groupId = group?.id, elementId = preferred.elementId),
            confidence = actionConfidence(preferred, group),
            evidence = buildList {
                if (semanticAction) add("semantic_action")
                if (cluster.nodes.size > 1) add("deduplicated_equivalent_refs")
                if (group != null) add("inside_visible_group:${group.id}")
                if (preferred.tapFallbackEligible) add("tap_fallback_eligible")
            },
        )
    }
}

private fun stableActionId(preferred: ScreenNode, label: String, kind: String, bounds: List<Int>): String {
    val readable = listOf(kind, preferred.ref, label)
        .joinToString("_")
        .stableToken()
        .ifBlank { "action" }
        .take(MAX_ACTION_ID_READABLE_CHARS)
    val hash = "${preferred.elementId}|$label|$kind|${bounds.joinToString(",")}".hashCode().toUInt().toString(radix = 36)
    return "a_${readable}_$hash"
}

private fun VisibleElementDraft.toPayload(selectedWindow: ScreenWindow?): VisibleElementPayload {
    val node = node
    return VisibleElementPayload(
        id = node.elementId,
        ref = node.ref,
        elementId = node.elementId,
        normalizedLabel = node.normalizedVisibleLabel(),
        textFields = ElementTextFieldsPayload(
            label = node.label.takeIf { it.isNotBlank() },
            text = node.text,
            contentDescription = node.contentDescription,
            resourceId = node.resourceId,
            className = node.className,
        ),
        role = node.role,
        className = node.className,
        resourceId = node.resourceId,
        bounds = node.bounds,
        center = node.bounds.centerPoint(),
        state = ElementStatePayload(
            enabled = node.enabled,
            checked = node.checked,
            selected = node.selected,
            focused = node.focused,
            editable = node.editable,
            scrollable = node.scrollable,
            clickable = node.clickable,
            focusable = node.focusable,
        ),
        visibility = node.visibilityEvidence(selectedWindow),
        rawRefs = listOf(node.ref),
        groupIds = groupIds,
    )
}

private fun GroupDraftWithParent.role(): String = when {
    evidence.any { it.startsWith("shared_accessibility_ancestor") } -> "container"
    else -> "group"
}

private fun buildLabelSummaryByGroup(groups: List<GroupDraftWithParent>, elementNodes: List<ScreenNode>): Map<String, String> {
    val labelByElementId = elementNodes.associate { it.elementId to it.normalizedVisibleLabel() }
    return groups.associate { group ->
        val labels = group.elementIds
            .asSequence()
            .mapNotNull(labelByElementId::get)
            .filter { it.isNotBlank() }
            .distinct()
            .take(8)
            .joinToString(" | ")
        group.id to labels
    }
}

private fun GroupDraftWithParent.confidence(actionIds: List<String>): Double {
    var confidence = 0.68
    if (actionIds.isNotEmpty()) confidence += 0.12
    if (elementIds.size >= 3) confidence += 0.08
    if (parentGroupId != null) confidence += 0.04
    return confidence.coerceAtMost(0.92)
}

private fun ScreenNode.visibilityEvidence(selectedWindow: ScreenWindow?): VisibilityEvidencePayload {
    val area = bounds.boundsArea()
    val windowBounds = selectedWindow?.bounds
    val visibleRatio = if (windowBounds != null && area > 0) {
        bounds.intersectionArea(windowBounds).toDouble() / area.toDouble()
    } else if (area > 0) {
        1.0
    } else {
        0.0
    }
    val intersectsWindow = windowBounds?.let { bounds.intersectionArea(it) > 0 } ?: true
    var confidence = 0.0
    if (visibleToUser) confidence += 0.45
    if (area > 0) confidence += 0.2
    if (intersectsWindow) confidence += 0.2
    confidence += min(visibleRatio, 1.0) * 0.15
    return VisibilityEvidencePayload(
        a11yVisibleToUser = visibleToUser,
        hasNonEmptyBounds = area > 0,
        intersectsSelectedWindow = intersectsWindow,
        visibleAreaRatio = visibleRatio.coerceIn(0.0, 1.0),
        selectedWindow = selectedWindow?.packageName == packageName || selectedWindow == null,
        confidence = confidence.coerceIn(0.0, 1.0),
    )
}

private fun buildInteractionSummary(
    elements: List<VisibleElementPayload>,
    groups: List<VisibleGroupPayload>,
    actions: List<ActionAffordancePayload>,
    foregroundPackage: String,
): String = buildString {
    val actionIdsByElementId = actions
        .mapNotNull { action -> action.scope.elementId?.let { elementId -> elementId to action.id } }
        .groupBy({ it.first }, { it.second })
    val actionIdsByGroupId = actions
        .mapNotNull { action -> action.scope.groupId?.let { groupId -> groupId to action.id } }
        .groupBy({ it.first }, { it.second })
    appendLine("screen-v2-interactions package=$foregroundPackage elements=${elements.size} groups=${groups.size} actions=${actions.size}")
    groups.take(12).forEach { group ->
        appendLine("group ${group.id} ${group.role} ${group.labelSummary.quotedForSummary()} actions=${group.actionIds.joinToString(",")}")
    }
    elements
        .filter { it.normalizedLabel.isNotBlank() }
        .take(MAX_SUMMARY_ELEMENTS)
        .forEach { element ->
            val scopedGroupActionIds = element.groupIds.flatMap { groupId -> actionIdsByGroupId[groupId].orEmpty() }
            val actionIds = (actionIdsByElementId[element.id].orEmpty() + scopedGroupActionIds)
                .distinct()
                .take(4)
            appendLine(
                "element ${element.id.summaryId()} ${element.role} ${element.normalizedLabel.quotedForSummary()} " +
                    "bounds=${element.bounds} actions=${actionIds.joinToString(",")}",
            )
        }
    actions.filter { it.scope.groupId == null }.take(12).forEach { action ->
        appendLine("action ${action.id} ${action.kind} ${action.label.quotedForSummary()} ref=${action.targetRef}")
    }
}

private fun ScreenNode.sameVisualTargetAs(other: ScreenNode): Boolean = path.startsWith(other.path) ||
    other.path.startsWith(path) ||
    bounds.overlapRatio(other.bounds) >= 0.72

private fun List<ScreenNode>.preferredActionTarget(): ScreenNode = maxWithOrNull(
    compareBy<ScreenNode> { it.enabled }
        .thenBy { it.clickable || it.actions.any { action -> action.contains("CLICK", ignoreCase = true) } }
        .thenBy { it.editable }
        .thenByDescending { it.boundsArea() * -1 },
) ?: first()

private fun ScreenNode.isInteractionActionCandidate(): Boolean = visibleToUser &&
    enabled &&
    (clickable || editable || scrollable || focusable || actions.isNotEmpty() || tapFallbackEligible)

private fun ScreenNode.actionKind(): String = when {
    editable -> ACTION_KIND_TYPE
    scrollable -> ACTION_KIND_SCROLL
    else -> ACTION_KIND_CLICK
}

private fun ScreenNode.actionLabel(): String = normalizedVisibleLabel().ifBlank {
    when {
        editable -> "Input"
        scrollable -> "Scroll"
        else -> role.ifBlank { "Action" }
    }
}

private fun actionConfidence(node: ScreenNode, group: GroupDraftWithParent?): Double {
    var confidence = 0.64
    if (node.clickable || node.actions.any { it.contains("CLICK", ignoreCase = true) }) confidence += 0.18
    if (node.tapFallbackEligible) confidence += 0.08
    if (group != null) confidence += 0.08
    return confidence.coerceAtMost(0.96)
}

private fun ScreenNode.normalizedVisibleLabel(): String = listOf(
    label,
    text.orEmpty(),
    contentDescription.orEmpty(),
    resourceId?.substringAfterLast('/').orEmpty(),
)
    .firstOrNull { it.isNotBlank() }
    ?.normalizedScreenText()
    ?: ""

private fun List<Int>.intersectionArea(other: List<Int>): Int {
    if (size != 4 || other.size != 4) return 0
    val left = max(this[0], other[0])
    val top = max(this[1], other[1])
    val right = min(this[2], other[2])
    val bottom = min(this[3], other[3])
    return ((right - left).coerceAtLeast(0)) * ((bottom - top).coerceAtLeast(0))
}

private fun List<Int>.overlapRatio(other: List<Int>): Double {
    val smaller = min(boundsArea(), other.boundsArea()).coerceAtLeast(1)
    return intersectionArea(other).toDouble() / smaller.toDouble()
}

private fun List<List<Int>>.unionBounds(): List<Int> {
    val valid = filter { it.size == 4 }
    if (valid.isEmpty()) return emptyList()
    return listOf(
        valid.minOf { it[0] },
        valid.minOf { it[1] },
        valid.maxOf { it[2] },
        valid.maxOf { it[3] },
    )
}

private fun List<Int>.startsWith(prefix: List<Int>): Boolean = size >= prefix.size && take(prefix.size) == prefix

private fun ScreenNode.boundsTop(): Int = bounds.getOrNull(1) ?: 0

private fun ScreenNode.boundsLeft(): Int = bounds.getOrNull(0) ?: 0

private fun ScreenNode.pathKey(): String = path.joinToString(".")

private fun String.quotedForSummary(): String = "\"${replace("\"", "\\\"")}\""

private fun String.summaryId(): String = substringAfterLast('|').ifBlank { takeLast(24) }

private fun String.stableToken(): String = lowercase()
    .replace(Regex("[^a-z0-9]+"), "_")
    .trim('_')

private const val MAX_ACTION_ID_READABLE_CHARS = 48
private const val MAX_INTERACTION_GROUPS = 80
private const val MAX_SUMMARY_ELEMENTS = 24

private fun inspectionComparator(query: String?): Comparator<ScreenNode> =
    compareByDescending<ScreenNode> { query != null && it.matchesOwnText(query) }
        .thenByDescending { it.isActivationCandidate() }
        .thenByDescending { it.hasOwnMeaningfulText() }
        .thenByDescending { it.selected }
        .thenBy { it.boundsArea() }
        .thenBy { it.path.size }

private fun ScreenState.rawSearchError(pattern: String, message: String): RawNodeSearchResultPayload = RawNodeSearchResultPayload(
    snapshotId = snapshotId,
    foregroundPackage = foregroundPackage,
    pattern = pattern,
    error = message,
)

private fun ScreenNode.toRawMatch(
    regex: Regex,
    fields: List<String>,
    visibleByPath: Map<List<Int>, ScreenNode>,
    includeContext: Boolean,
): RawNodeMatchPayload? {
    val fieldValues = rawSearchFieldValues(fields)
    val matchedFields = fieldValues.filter { (_, value) -> regex.containsMatchIn(value) }
    if (matchedFields.isEmpty()) {
        return null
    }
    return RawNodeMatchPayload(
        node = toPayload(),
        matchedFields = matchedFields.map { it.first }.distinct(),
        matchedText = matchedFields.first().second,
        nearestActionable = nearestActivationCandidate(visibleByPath)?.toPayload(),
        ancestorLabels = if (includeContext) ancestorLabels(visibleByPath) else emptyList(),
        childLabels = if (includeContext) childLabels() else emptyList(),
    )
}

private fun ScreenNode.rawSearchFieldValues(fields: List<String>): List<Pair<String, String>> = fields.flatMap { field ->
    when (field) {
        "label" -> listOfNotBlank("label" to label)
        "text" -> listOfNotBlank("text" to text.orEmpty())
        "contentDescription" -> listOfNotBlank("contentDescription" to contentDescription.orEmpty())
        "resourceId" -> listOfNotBlank("resourceId" to resourceId.orEmpty())
        "className" -> listOfNotBlank("className" to className.orEmpty())
        "role" -> listOfNotBlank("role" to role)
        "actions" -> actions.mapNotNull { action -> action.takeIf { it.isNotBlank() }?.let { "actions" to it } }
        else -> emptyList()
    }
}

private fun List<String>.normalizedSearchFields(): List<String> {
    val allowed = setOf("label", "text", "contentDescription", "resourceId", "className", "role", "actions")
    val requested = mapNotNull { field -> field.takeIf { it in allowed } }.distinct()
    return requested.ifEmpty { listOf("label", "text", "contentDescription", "resourceId") }
}

private fun ScreenNode.nearestActivationCandidate(visibleByPath: Map<List<Int>, ScreenNode>): ScreenNode? {
    if (isActivationCandidate()) {
        return this
    }
    path.indices.reversed().forEach { depth ->
        val ancestor = visibleByPath[path.take(depth)]
        if (ancestor?.isActivationCandidate() == true) {
            return ancestor
        }
    }
    return children
        .flatMap { it.flatten() }
        .filter { it.visibleToUser && it.isActivationCandidate() }
        .minWithOrNull(compareBy<ScreenNode> { it.path.size }.thenBy { it.boundsArea() })
}

private fun ScreenNode.ancestorLabels(visibleByPath: Map<List<Int>, ScreenNode>): List<String> = path.indices
    .mapNotNull { depth -> visibleByPath[path.take(depth)]?.contextLabel() }
    .filter { it.isNotBlank() }
    .distinct()
    .takeLast(4)

private fun ScreenNode.childLabels(): List<String> = children
    .flatMap { it.flatten() }
    .asSequence()
    .filter { it.visibleToUser }
    .mapNotNull { it.contextLabel() }
    .filter { it.isNotBlank() }
    .distinct()
    .take(6)
    .toList()

private fun ScreenNode.contextLabel(): String? = listOf(label, text, contentDescription, resourceId).firstOrNull { !it.isNullOrBlank() }

private fun ScreenNode.isActivationCandidate(): Boolean = visibleToUser &&
    (clickable || focusable || editable || scrollable || actions.isNotEmpty() || tapFallbackEligible)

private fun ScreenNode.hasOwnMeaningfulText(): Boolean = listOf(label, text, contentDescription, resourceId).any { !it.isNullOrBlank() }

private fun List<Int>.boundsArea(): Int = if (size == 4) {
    ((this[2] - this[0]).coerceAtLeast(0)) * ((this[3] - this[1]).coerceAtLeast(0))
} else {
    0
}

private fun listOfNotBlank(value: Pair<String, String>): List<Pair<String, String>> =
    value.second.takeIf { it.isNotBlank() }?.let { listOf(value) } ?: emptyList()
