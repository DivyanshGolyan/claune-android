package com.divyanshgolyan.claune.android.scripting

import com.divyanshgolyan.claune.android.runtime.UiElement
import com.divyanshgolyan.claune.android.runtime.UiSnapshot
import com.divyanshgolyan.claune.android.runtime.WindowCandidate

fun UiSnapshot.toPayload(): UiSnapshotPayload = UiSnapshotPayload(
    snapshotId = snapshotId,
    capturedAt = capturedAt.toString(),
    foregroundPackage = foregroundPackage,
    visibleText = visibleText,
    actionableElements = actionableElements.map { it.toPayload() },
    focusedElementId = focusedElementId,
    windowCandidates = windowCandidates.map { it.toPayload() },
    selectedWindowReason = selectedWindowReason,
)

fun UiSnapshot.toInspectionPayload(options: ScreenInspectOptionsPayload): ScreenInspectionPayload {
    val query = options.text?.trim()?.takeIf { it.isNotBlank() }
    val limit = options.limit.coerceIn(1, 80)
    val visibleMatches =
        visibleElements
            .asSequence()
            .filter { element -> query == null || element.containsText(query) }
            .sortedWith(compareByDescending<UiElement> { it.containsOwnText(query) }.thenByDescending { it.boundsArea() })
            .take(if (options.includeAll) limit else limit.coerceAtMost(20))
            .map { it.toPayload() }
            .toList()
    val actionableMatches =
        actionableElements
            .asSequence()
            .filter { element -> query == null || element.containsText(query) }
            .take(20)
            .map { it.toPayload() }
            .toList()
    return ScreenInspectionPayload(
        snapshotId = snapshotId,
        capturedAt = capturedAt.toString(),
        foregroundPackage = foregroundPackage,
        query = query,
        visibleElements = visibleMatches,
        actionableElements = actionableMatches,
        selectedWindowReason = selectedWindowReason,
    )
}

fun WindowCandidate.toPayload(): WindowCandidatePayload = WindowCandidatePayload(
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

fun UiElement.toPayload(): UiElementPayload = UiElementPayload(
    id = id,
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
)

private fun UiElement.containsText(query: String?): Boolean {
    if (query == null) return true
    return listOf(label, text, contentDescription, resourceId, className).any { value ->
        value?.contains(query, ignoreCase = true) == true
    }
}

private fun UiElement.containsOwnText(query: String?): Boolean {
    if (query == null) return true
    return listOf(text, contentDescription).any { value ->
        value?.contains(query, ignoreCase = true) == true
    }
}

private fun UiElement.boundsArea(): Int {
    if (bounds.size < 4) return 0
    val width = (bounds[2] - bounds[0]).coerceAtLeast(0)
    val height = (bounds[3] - bounds[1]).coerceAtLeast(0)
    return width * height
}

private fun List<Int>.centerPoint(): List<Int> {
    if (size < 4) return emptyList()
    return listOf((this[0] + this[2]) / 2, (this[1] + this[3]) / 2)
}
