package com.divyanshgolyan.claune.android.scripting

import com.divyanshgolyan.claune.android.runtime.UiElement
import com.divyanshgolyan.claune.android.runtime.UiSnapshot
import kotlinx.serialization.decodeFromString

internal fun decodeElementSelector(selectorJson: String): ElementSelectorPayload? = runCatching {
    ScriptJson.codec.decodeFromString(ElementSelectorPayload.serializer(), selectorJson)
}.getOrNull()?.takeIf { it.hasCriteria() }

internal fun selectElement(snapshot: UiSnapshot, selector: ElementSelectorPayload): UiElement? {
    val rankedMatches = rankedMatches(snapshot, selector)
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

internal fun selectorFailure(selector: ElementSelectorPayload, snapshot: UiSnapshot): HostCallOutcome {
    val rankedMatches = rankedMatches(snapshot, selector)
    return when {
        rankedMatches.isEmpty() ->
            HostCallOutcome(
                ok = false,
                message = "Selector did not match any actionable element on the current screen.",
            )

        else ->
            HostCallOutcome(
                ok = false,
                message =
                buildString {
                    append("Selector matched multiple elements. Refine the selector or set first=true.")
                    val candidates = rankedMatches.take(3).joinToString(separator = "; ") { (element, _) ->
                        "ref=${element.ref}, label=${element.label.ifBlank { "<blank>" }}, id=${element.id}"
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

internal fun UiElement.matches(selector: ElementSelectorPayload): Boolean = selector.matchScore(this) != null

internal fun UiElement.isSearchLike(): Boolean {
    val candidates = listOfNotNull(label, text, contentDescription, resourceId, className)
    return candidates.any { candidate ->
        candidate.contains("search", ignoreCase = true)
    }
}

private fun rankedMatches(snapshot: UiSnapshot, selector: ElementSelectorPayload): List<Pair<UiElement, Int>> = snapshot.actionableElements
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
    listOf(clickable, editable, focused, enabled, checked, selected, scrollable).any { it != null }

private fun ElementSelectorPayload.matchScore(element: UiElement): Int? {
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

private fun UiElement.textMatchScore(target: String, textExact: Boolean): Int? {
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

private fun UiElement.matchesTextCandidate(target: String, textExact: Boolean, candidate: String?): Boolean {
    val safeCandidate = candidate?.takeIf { it.isNotBlank() } ?: return false
    return if (textExact) {
        safeCandidate == target
    } else {
        safeCandidate.contains(target, ignoreCase = true)
    }
}
