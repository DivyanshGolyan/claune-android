package com.divyanshgolyan.claune.android.scripting

import com.divyanshgolyan.claune.android.runtime.ScreenNode
import com.divyanshgolyan.claune.android.runtime.ScreenState
import com.divyanshgolyan.claune.android.runtime.actionableNodes
import com.divyanshgolyan.claune.android.runtime.visibleNodes
import kotlinx.serialization.decodeFromString

internal fun decodeElementSelector(selectorJson: String): ElementSelectorPayload? = runCatching {
    ScriptJson.codec.decodeFromString(ElementSelectorPayload.serializer(), selectorJson)
}.getOrNull()?.takeIf { it.hasCriteria() }

internal fun selectElement(screenState: ScreenState, selector: ElementSelectorPayload): ScreenNode? {
    val rankedMatches = rankedMatches(screenState, selector)
    if (rankedMatches.isEmpty()) {
        return null
    }
    if (selector.first || rankedMatches.size == 1) {
        return rankedMatches.first().first
    }

    val bestScore = rankedMatches.first().second
    val topMatches = rankedMatches.takeWhile { (_, score) -> score == bestScore }
    return topMatches.singleOrNull()?.first
}

internal fun selectorFailure(selector: ElementSelectorPayload, screenState: ScreenState): HostCallOutcome {
    val rankedMatches = rankedMatches(screenState, selector)
    val visibleMatches = rankedVisibleMatches(screenState, selector)
    return when {
        rankedMatches.isEmpty() ->
            HostCallOutcome(
                ok = false,
                message =
                buildString {
                    append("Selector did not match any actionable element on the current screen.")
                    if (visibleMatches.isNotEmpty()) {
                        append(
                            " Visible non-actionable matches exist; call inspectScreen with the same text, verify bounds, then use tapBounds only if the requested target is visually present.",
                        )
                        val candidates = visibleMatches.take(3).joinToString(separator = "; ") { (element, _) ->
                            "ref=${element.ref}, label=${element.label.ifBlank {
                                "<blank>"
                            }}, bounds=${element.bounds}, reason=${element.clickabilityReason}"
                        }
                        append(" Visible candidates: ")
                        append(candidates)
                        append('.')
                    }
                },
            )

        else ->
            HostCallOutcome(
                ok = false,
                message =
                buildString {
                    append("Selector matched multiple elements. Refine the selector or set first=true.")
                    val candidates = rankedMatches.take(3).joinToString(separator = "; ") { (element, _) ->
                        "ref=${element.ref}, label=${element.label.ifBlank { "<blank>" }}, id=${element.elementId}"
                    }
                    if (candidates.isNotBlank()) {
                        append(" Top candidates: ")
                        append(candidates)
                        append('.')
                    }
                },
            )
    }
}

internal fun ScreenNode.matches(selector: ElementSelectorPayload): Boolean = selector.matchScore(this) != null

private fun rankedMatches(screenState: ScreenState, selector: ElementSelectorPayload): List<Pair<ScreenNode, Int>> =
    screenState.actionableNodes()
        .mapNotNull { element ->
            selector.matchScore(element)?.let { score -> element to score }
        }
        .sortedByDescending { (_, score) -> score }

private fun rankedVisibleMatches(screenState: ScreenState, selector: ElementSelectorPayload): List<Pair<ScreenNode, Int>> =
    screenState.visibleNodes()
        .filterNot { visible -> screenState.actionableNodes().any { it.elementId == visible.elementId } }
        .mapNotNull { element ->
            selector.matchScore(element)?.let { score -> element to score }
        }
        .sortedByDescending { (_, score) -> score }

private fun ElementSelectorPayload.hasCriteria(): Boolean = listOf(
    ref,
    label,
    text,
    contentDescription,
    resourceId,
    role,
).any { !it.isNullOrBlank() } ||
    listOf(clickable, focusable, editable, focused, enabled, checked, selected, scrollable).any { it != null }

private fun ElementSelectorPayload.matchScore(element: ScreenNode): Int? {
    if (ref != null && ref != element.ref) {
        return null
    }

    if (label != null && !element.matchesTextCandidate(label, textExact, candidate = element.label)) {
        return null
    }

    var score = 0
    if (ref != null) {
        score += 100
    }
    if (label != null) {
        score += if (textExact) 60 else 40
        if (element.clickable) {
            score += 10
        }
    }

    if (text != null) {
        val textScore = element.textMatchScore(text, textExact) ?: return null
        score += textScore
    }

    if (contentDescription != null) {
        if (!(element.contentDescription?.contains(contentDescription, ignoreCase = true) == true)) {
            return null
        }
        score += 30
    }

    if (resourceId != null) {
        if (!(element.resourceId?.contains(resourceId, ignoreCase = true) == true)) {
            return null
        }
        score += 35
    }

    if (role != null && role != element.role) {
        return null
    }
    if (role != null) {
        score += 15
    }

    val stateFilters =
        listOf(
            clickable to element.clickable,
            focusable to element.focusable,
            editable to element.editable,
            focused to element.focused,
            enabled to element.enabled,
            checked to element.checked,
            selected to element.selected,
            scrollable to element.scrollable,
        )
    if (stateFilters.any { (expected, actual) -> expected != null && expected != actual }) {
        return null
    }
    score += stateFilters.count { (expected, _) -> expected != null } * 5

    return score
}

private fun ScreenNode.textMatchScore(target: String, textExact: Boolean): Int? {
    val exactCandidates = listOfNotNull(label.takeIf { it.isNotBlank() }, text, contentDescription)
    val hasExact = exactCandidates.any { it.equals(target, ignoreCase = false) }
    if (textExact) {
        return if (hasExact) {
            if (clickable) {
                90
            } else {
                80
            }
        } else {
            null
        }
    }

    val hasPartial = exactCandidates.any { it.contains(target, ignoreCase = true) }
    if (!hasPartial) {
        return null
    }

    return when {
        hasExact && clickable -> 90
        hasExact -> 80
        clickable -> 70
        else -> 60
    }
}

private fun ScreenNode.matchesTextCandidate(target: String, textExact: Boolean, candidate: String?): Boolean {
    val safeCandidate = candidate?.takeIf { it.isNotBlank() } ?: return false
    return if (textExact) {
        safeCandidate == target
    } else {
        safeCandidate.contains(target, ignoreCase = true)
    }
}
