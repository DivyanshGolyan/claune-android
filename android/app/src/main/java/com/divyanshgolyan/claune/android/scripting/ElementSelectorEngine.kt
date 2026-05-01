package com.divyanshgolyan.claune.android.scripting

import com.divyanshgolyan.claune.android.runtime.ScreenNode
import com.divyanshgolyan.claune.android.runtime.ScreenState
import com.divyanshgolyan.claune.android.runtime.boundsArea
import com.divyanshgolyan.claune.android.runtime.normalizedScreenText
import com.divyanshgolyan.claune.android.runtime.visibleNodes
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun decodeLocatorSpec(specJson: String): LocatorSpecPayload? = runCatching {
    ScriptJson.codec.decodeFromString(LocatorSpecPayload.serializer(), specJson)
}.getOrNull()?.takeIf { it.hasCriteria() }

internal fun decodeLocatorOptions(optionsJson: String): LocatorOptionsPayload = runCatching {
    ScriptJson.codec.decodeFromString(LocatorOptionsPayload.serializer(), optionsJson)
}.getOrDefault(LocatorOptionsPayload())

internal fun decodeLocatorDescribeOptions(optionsJson: String): LocatorDescribeOptionsPayload = runCatching {
    ScriptJson.codec.decodeFromString(LocatorDescribeOptionsPayload.serializer(), optionsJson)
}.getOrDefault(LocatorDescribeOptionsPayload())

internal fun decodeLocatorAssertion(assertionJson: String): LocatorAssertionPayload? = runCatching {
    ScriptJson.codec.decodeFromString(LocatorAssertionPayload.serializer(), assertionJson)
}.getOrNull()

internal fun invalidLocatorFailure(): HostCallOutcome = HostCallOutcome(
    ok = false,
    message = "Invalid locator.",
    errorCode = "invalid_selector",
)

internal data class LocatorCandidate(val node: ScreenNode, val actionNode: ScreenNode, val reasons: List<String>, val score: Int)

internal fun queryLocator(screenState: ScreenState, spec: LocatorSpecPayload): List<LocatorCandidate> {
    val matchers = spec.queryMatchers()
    val visibleNodes = screenState.visibleNodes()
    val visibleByPath = visibleNodes.associateBy { it.path.pathKey() }
    val scopedNodes = spec.scope?.let { scopeSpec ->
        val scopeCandidates = queryLocator(screenState, scopeSpec)
        if (scopeCandidates.isEmpty()) {
            emptyList()
        } else {
            visibleNodes.filter { node ->
                scopeCandidates.any { scopeCandidate ->
                    node.path.isDescendantOf(scopeCandidate.node.path)
                }
            }
        }
    } ?: visibleNodes
    return scopedNodes
        .mapNotNull { node ->
            node.locatorMatch(spec, matchers)?.let { (reasons, score) ->
                LocatorCandidate(
                    node = node,
                    actionNode = node.activationTarget(visibleByPath),
                    reasons = reasons,
                    score = score,
                )
            }
        }
        .filter { candidate -> spec.filters.all { filter -> candidate.node.matchesFilter(filter) } }
        .sortedWith(
            compareByDescending<LocatorCandidate> { it.score }
                .thenBy { it.node.path.joinToString(".") },
        )
        .dedupeByActionTarget()
}

internal fun locatorFailure(spec: LocatorSpecPayload, screenState: ScreenState): HostCallOutcome {
    val candidates = queryLocator(screenState, spec)
    return locatorFailure(spec, candidates)
}

internal fun locatorFailure(spec: LocatorSpecPayload, candidates: List<LocatorCandidate>): HostCallOutcome = if (candidates.isEmpty()) {
    HostCallOutcome(
        ok = false,
        errorCode = "no_match",
        message = "Locator did not match any visible element on the current screen.",
        data = locatorQueryData(spec, candidates),
    )
} else {
    HostCallOutcome(
        ok = false,
        errorCode = "ambiguous_match",
        message = "Locator matched ${candidates.size} elements. Refine it or use first()/nth() deliberately.",
        data = locatorQueryData(spec, candidates),
    )
}

internal fun locatorCountData(spec: LocatorSpecPayload, count: Int): JsonObject = buildJsonObject {
    put("kind", spec.kind)
    put("count", count)
}

internal fun locatorQueryData(spec: LocatorSpecPayload, candidates: List<LocatorCandidate>): JsonObject = buildJsonObject {
    putLocatorCandidates(spec, candidates, 10)
}

internal fun locatorDescribeData(spec: LocatorSpecPayload, candidates: List<LocatorCandidate>, limit: Int): JsonObject = buildJsonObject {
    putLocatorCandidates(spec, candidates, limit.coerceIn(1, 100))
}

private fun JsonObjectBuilder.putLocatorCandidates(spec: LocatorSpecPayload, candidates: List<LocatorCandidate>, limit: Int) {
    put("kind", spec.kind)
    put("count", candidates.size)
    put("truncated", candidates.size > limit)
    put(
        "candidates",
        buildJsonArray {
            candidates.take(limit).forEach { candidate ->
                add(
                    buildJsonObject {
                        put("ref", candidate.node.ref)
                        put("elementId", candidate.node.elementId)
                        put("label", candidate.node.label)
                        candidate.node.text?.let { put("text", it) }
                        candidate.node.contentDescription?.let { put("contentDescription", it) }
                        candidate.node.resourceId?.let { put("resourceId", it) }
                        put("role", candidate.node.role)
                        put("bounds", buildJsonArray { candidate.node.bounds.forEach { add(JsonPrimitive(it)) } })
                        put("enabled", candidate.node.enabled)
                        put("clickable", candidate.node.clickable)
                        put("editable", candidate.node.editable)
                        put("visible", candidate.node.visibleToUser && candidate.node.boundsArea() > 0)
                        put("actionElementId", candidate.actionNode.elementId)
                        put("actionRef", candidate.actionNode.ref)
                        put("actionLabel", candidate.actionNode.label)
                        put("actionable", candidate.actionNode.isLocatorActionable())
                        put("reasons", buildJsonArray { candidate.reasons.forEach { add(JsonPrimitive(it)) } })
                        put("score", candidate.score)
                    },
                )
            }
        },
    )
}

internal fun ScreenNode.isLocatorActionable(force: Boolean = false): Boolean = visibleToUser &&
    boundsArea() > 0 &&
    enabled &&
    (force || clickable || tapFallbackEligible || actions.any { it.contains("click", ignoreCase = true) })

internal fun ScreenNode.locatorVisible(): Boolean = visibleToUser && boundsArea() > 0

private fun ScreenNode.isDirectLocatorActionable(): Boolean = visibleToUser &&
    boundsArea() > 0 &&
    enabled &&
    (clickable || actions.any { it.contains("click", ignoreCase = true) })

private fun LocatorSpecPayload.hasCriteria(): Boolean = when (kind) {
    LOCATOR_KIND_TEXT, LOCATOR_KIND_LABEL, LOCATOR_KIND_PLACEHOLDER -> text?.isNotBlank() == true || pattern?.isNotBlank() == true
    LOCATOR_KIND_ROLE -> role?.isNotBlank() == true
    LOCATOR_KIND_TEST_ID -> testId?.isNotBlank() == true
    LOCATOR_KIND_ALL -> true
    else -> false
}

private data class LocatorQueryMatchers(val text: LocatorTextMatcher?, val roleName: LocatorTextMatcher?)

private fun LocatorSpecPayload.queryMatchers(): LocatorQueryMatchers = LocatorQueryMatchers(
    text = LocatorTextMatcher.from(text = text, exact = exact, pattern = pattern, flags = flags),
    roleName = if (name != null || pattern != null) {
        LocatorTextMatcher.from(text = name, exact = exact, pattern = pattern, flags = flags)
    } else {
        null
    },
)

private fun ScreenNode.locatorMatch(spec: LocatorSpecPayload, matchers: LocatorQueryMatchers): Pair<List<String>, Int>? {
    val reasons = mutableListOf<String>()
    var score = 0
    when (spec.kind) {
        LOCATOR_KIND_ALL -> {
            if (role == "root") return null
            reasons += LOCATOR_KIND_ALL
            score += 10
        }
        LOCATOR_KIND_TEXT -> {
            if (matchers.text?.matches(listOf(label, text, contentDescription)) != true) return null
            reasons += LOCATOR_KIND_TEXT
            score += 40
        }
        LOCATOR_KIND_LABEL -> {
            if (matchers.text?.matches(listOf(contentDescription, text, label)) != true) return null
            reasons += LOCATOR_KIND_LABEL
            score += 45
            if (editable) score += 25
            if (isSearchLike()) score += 15
            if (clickable || tapFallbackEligible) score += 5
        }
        LOCATOR_KIND_PLACEHOLDER -> {
            if (matchers.text?.matches(listOf(contentDescription, text, label)) != true) return null
            reasons += LOCATOR_KIND_PLACEHOLDER
            score += 45
            if (editable) score += 30
            if (isSearchLike()) score += 20
        }
        LOCATOR_KIND_ROLE -> {
            val roleName = spec.role ?: return null
            if (!matchesRole(roleName)) return null
            reasons += "$LOCATOR_KIND_ROLE:$roleName"
            score += 35
            matchers.roleName?.let { nameMatcher ->
                if (!nameMatcher.matches(listOf(label, text, contentDescription))) {
                    return null
                }
                reasons += "name"
                score += 30
            }
        }
        LOCATOR_KIND_TEST_ID -> {
            val testId = spec.testId ?: return null
            if (!(
                    resourceId?.equals(
                        testId,
                        ignoreCase = true,
                    ) == true ||
                        resourceId?.endsWith(testId, ignoreCase = true) == true
                    )
            ) {
                return null
            }
            reasons += LOCATOR_KIND_TEST_ID
            score += 60
        }
        else -> return null
    }
    if (enabled) score += 5
    if (clickable) score += 5
    if (editable) score += 5
    if (boundsArea() > 0) score += 5
    return reasons to score
}

private fun ScreenNode.matchesRole(expectedRole: String): Boolean {
    val normalized = expectedRole.lowercase()
    return when (normalized) {
        "button" -> role == "button" || (clickable && role == "control")
        "textbox", "text", "input" -> editable || role == "input"
        "checkbox" -> role == "checkbox" || actions.any { it.contains("check", ignoreCase = true) }
        "switch" -> role == "switch"
        "listitem", "list item" -> role == "listitem" || role == "item"
        "image", "img" -> role == "image"
        "heading" -> role == "heading"
        else -> role.equals(normalized, ignoreCase = true)
    }
}

private fun ScreenNode.matchesFilter(filter: LocatorFilterPayload): Boolean {
    filter.visible?.let { expected ->
        if (locatorVisible() != expected) return false
    }
    if (filter.hasText == null && filter.hasPattern == null) return true
    val matcher = LocatorTextMatcher.from(
        text = filter.hasText,
        exact = false,
        pattern = filter.hasPattern,
        flags = filter.hasFlags,
    ) ?: return false
    return matcher.matches(subtreeTextValues())
}

private fun ScreenNode.subtreeTextValues(): List<String?> = buildList {
    fun visit(node: ScreenNode) {
        add(node.label)
        add(node.text)
        add(node.contentDescription)
        node.children.forEach(::visit)
    }
    visit(this@subtreeTextValues)
}

private fun ScreenNode.isSearchLike(): Boolean = subtreeTextValues()
    .filterNotNull()
    .any { value -> value.contains("search", ignoreCase = true) }

private fun List<LocatorCandidate>.dedupeByActionTarget(): List<LocatorCandidate> {
    val byTarget = linkedMapOf<String, LocatorCandidate>()
    forEach { candidate ->
        val key = candidate.actionNode.elementId
        val existing = byTarget[key]
        if (existing == null || candidate.score > existing.score) {
            byTarget[key] = candidate
        }
    }
    return byTarget.values.toList()
}

private fun ScreenNode.activationTarget(visibleByPath: Map<String, ScreenNode>): ScreenNode {
    if (isDirectLocatorActionable()) return this
    path.ancestorPaths()
        .asReversed()
        .forEach { ancestorPath ->
            val ancestor = visibleByPath[ancestorPath.pathKey()]
            if (ancestor != null && ancestor.isDirectLocatorActionable()) return ancestor
        }
    if (isLocatorActionable()) return this
    path.ancestorPaths()
        .asReversed()
        .forEach { ancestorPath ->
            val ancestor = visibleByPath[ancestorPath.pathKey()]
            if (ancestor != null && ancestor.isLocatorActionable()) return ancestor
        }
    return this
}

private fun List<Int>.ancestorPaths(): List<List<Int>> = indices.map { endExclusive -> take(endExclusive) }

private fun List<Int>.isDescendantOf(parent: List<Int>): Boolean = size > parent.size && take(parent.size) == parent

private fun List<Int>.pathKey(): String = joinToString(separator = "_").ifBlank { "root" }

internal class LocatorTextMatcher private constructor(
    private val normalizedTarget: String?,
    private val regex: Regex?,
    private val exact: Boolean,
) {
    fun matches(values: List<String?>): Boolean {
        val candidates = values.filterNotNull().filter { it.isNotBlank() }
        regex?.let { matcher ->
            return candidates.any { value -> matcher.containsMatchIn(value.normalizedScreenText()) }
        }
        val target = normalizedTarget ?: return false
        return candidates.any { value ->
            val normalizedValue = value.normalizedScreenText().lowercase()
            if (exact) normalizedValue == target else normalizedValue.contains(target)
        }
    }

    companion object {
        fun from(text: String?, exact: Boolean, pattern: String?, flags: String): LocatorTextMatcher? {
            pattern?.let { rawPattern ->
                val options = if (flags.contains("i")) setOf(RegexOption.IGNORE_CASE) else emptySet()
                return runCatching { Regex(rawPattern, options) }
                    .getOrNull()
                    ?.let { regex -> LocatorTextMatcher(normalizedTarget = null, regex = regex, exact = exact) }
            }
            val target = text ?: return null
            return LocatorTextMatcher(
                normalizedTarget = target.normalizedScreenText().lowercase(),
                regex = null,
                exact = exact,
            )
        }
    }
}
