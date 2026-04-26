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
import com.divyanshgolyan.claune.android.runtime.matchesOwnText
import com.divyanshgolyan.claune.android.runtime.matchesText
import com.divyanshgolyan.claune.android.runtime.toCanonicalScreenText
import com.divyanshgolyan.claune.android.runtime.visibleNodes

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

fun ScreenState.toObservationPayload(mode: CanonicalScreenMode): ScreenObservationPayload = ScreenObservationPayload(
    mode = if (mode == CanonicalScreenMode.Full) "full_snapshot" else "compact_snapshot",
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

fun String.toCanonicalScreenMode(): CanonicalScreenMode = when (lowercase()) {
    "full" -> CanonicalScreenMode.Full
    else -> CanonicalScreenMode.Compact
}

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

private fun ScreenNode.flatten(): List<ScreenNode> = buildList {
    fun visit(node: ScreenNode) {
        add(node)
        node.children.forEach(::visit)
    }
    visit(this@flatten)
}

private fun List<Int>.boundsArea(): Int = if (size == 4) {
    ((this[2] - this[0]).coerceAtLeast(0)) * ((this[3] - this[1]).coerceAtLeast(0))
} else {
    0
}

private fun listOfNotBlank(value: Pair<String, String>): List<Pair<String, String>> =
    value.second.takeIf { it.isNotBlank() }?.let { listOf(value) } ?: emptyList()
