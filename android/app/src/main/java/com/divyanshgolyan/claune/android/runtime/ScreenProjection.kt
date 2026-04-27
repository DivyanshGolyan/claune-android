package com.divyanshgolyan.claune.android.runtime

import kotlin.math.max

private const val MAX_DIFF_CHARS = 3500
private const val MAX_COMPACT_CHARS = 4000
private const val MAX_FULL_CHARS = 20_000
private const val LARGE_DIFF_RATIO = 0.35
private const val BOUNDS_QUANTUM = 4
private const val MAX_SALIENT_NODES = 36
private const val MAX_DESCENDANT_DETAIL_CHARS = 220

fun ScreenState.toCanonicalScreenText(mode: CanonicalScreenMode = CanonicalScreenMode.Compact): String = when (mode) {
    CanonicalScreenMode.Compact -> toSalienceScreenText(includeSnapshotId = true).limitChars(MAX_COMPACT_CHARS)
    CanonicalScreenMode.Full -> toCanonicalScreenText(mode = mode, includeSnapshotId = true).limitChars(MAX_FULL_CHARS)
}

fun buildScreenObservation(previous: ScreenState?, current: ScreenState): ScreenObservation {
    val currentText = current.toSalienceScreenText(includeSnapshotId = true).limitChars(MAX_COMPACT_CHARS)
    if (previous == null) {
        return ScreenObservation(
            mode = "compact_snapshot",
            reason = "baseline_missing",
            currentSnapshotId = current.snapshotId,
            foregroundPackage = current.foregroundPackage,
            selectedWindowReason = current.selectedWindowReason,
            baselineMissing = true,
            stats = ScreenDiffStats(
                additions = current.canonicalDiffLines().size,
                removals = 0,
                unchanged = 0,
                beforeLineCount = 0,
                afterLineCount = current.canonicalDiffLines().size,
                changeRatio = 1.0,
            ),
            canonicalText = currentText,
        )
    }

    val diff = diffCanonicalScreens(previous, current)
    val reason = when {
        previous.foregroundPackage != current.foregroundPackage -> "package_changed"
        previous.selectedWindowKey() != current.selectedWindowKey() -> "window_changed"
        diff.stats.changeRatio >= LARGE_DIFF_RATIO -> "large_diff"
        diff.diff.length > MAX_DIFF_CHARS -> "diff_too_large"
        else -> "small_diff"
    }
    val sendSnapshot = reason != "small_diff"
    return ScreenObservation(
        mode = if (sendSnapshot) "compact_snapshot" else "diff",
        reason = reason,
        baselineSnapshotId = previous.snapshotId,
        currentSnapshotId = current.snapshotId,
        foregroundPackage = current.foregroundPackage,
        selectedWindowReason = current.selectedWindowReason,
        stats = diff.stats,
        canonicalText = if (sendSnapshot) currentText else null,
        diff = if (sendSnapshot) null else diff.diff.limitChars(MAX_DIFF_CHARS),
    )
}

data class ScreenDiff(val diff: String, val stats: ScreenDiffStats)

fun diffCanonicalScreens(before: ScreenState, after: ScreenState): ScreenDiff {
    val beforeLines = before.canonicalDiffLines()
    val afterLines = after.canonicalDiffLines()
    val changes = lineChanges(beforeLines, afterLines)
    val additions = changes.count { it.kind == ChangeKind.Insert }
    val removals = changes.count { it.kind == ChangeKind.Delete }
    val unchanged = changes.count { it.kind == ChangeKind.Equal }
    val denominator = max(max(beforeLines.size, afterLines.size), 1)
    val stats = ScreenDiffStats(
        additions = additions,
        removals = removals,
        unchanged = unchanged,
        beforeLineCount = beforeLines.size,
        afterLineCount = afterLines.size,
        changeRatio = (additions + removals).toDouble() / denominator.toDouble(),
    )
    return ScreenDiff(diff = renderUnifiedDiff(changes), stats = stats)
}

fun ScreenState.actionableNodes(): List<ScreenNode> = root?.flatten().orEmpty().filter { it.isActionable() }

fun ScreenState.visibleNodes(): List<ScreenNode> = root?.flatten().orEmpty().filter {
    it.path.isNotEmpty() && it.visibleToUser && it.hasMeaningfulLabel()
}

fun ScreenState.visibleText(): List<String> = root
    ?.flatten()
    .orEmpty()
    .asSequence()
    .filter { it.visibleToUser }
    .flatMap { node -> sequenceOf(node.text, node.contentDescription, node.label).filterNotNull() }
    .map { it.normalizedText() }
    .filter { it.isNotBlank() }
    .distinct()
    .toList()

fun ScreenState.focusedElementId(): String? = actionableNodes().firstOrNull { it.focused }?.elementId

fun ScreenNode.boundsArea(): Int {
    if (bounds.size < 4) return 0
    val width = (bounds[2] - bounds[0]).coerceAtLeast(0)
    val height = (bounds[3] - bounds[1]).coerceAtLeast(0)
    return width * height
}

fun ScreenNode.matchesText(query: String?): Boolean {
    if (query == null) return true
    return listOf(label, text, contentDescription, resourceId, className).any { value ->
        value?.contains(query, ignoreCase = true) == true
    }
}

fun ScreenNode.matchesOwnText(query: String?): Boolean {
    if (query == null) return true
    return listOf(text, contentDescription).any { value ->
        value?.contains(query, ignoreCase = true) == true
    }
}

fun ScreenNode.isSearchLike(): Boolean {
    val candidates = listOfNotNull(label, text, contentDescription, resourceId, className)
    return candidates.any { candidate ->
        candidate.contains("search", ignoreCase = true)
    }
}

fun List<Int>.centerPoint(): List<Int> {
    if (size < 4) return emptyList()
    return listOf((this[0] + this[2]) / 2, (this[1] + this[3]) / 2)
}

private fun ScreenState.canonicalDiffLines(): List<String> = toSalienceScreenText(includeSnapshotId = false).lines()

private fun ScreenState.toSalienceScreenText(includeSnapshotId: Boolean): String = buildString {
    append("screen-v1-salience")
    if (includeSnapshotId) {
        append(" snapshot=")
        append(snapshotId)
    }
    append(" package=")
    append(foregroundPackage)
    appendLine()
    appendSelectedWindow(this@toSalienceScreenText)
    appendLine("priority:")
    root
        ?.flatten()
        .orEmpty()
        .asSequence()
        .filter { node -> node.visibleToUser && (node.isActionable() || node.hasMeaningfulLabel()) }
        .map { node -> SalientNode(node = node, score = node.salienceScore()) }
        .filter { salient -> salient.score >= 0 }
        .sortedWith(
            compareByDescending<SalientNode> { it.score }
                .thenBy { it.node.boundsTop() }
                .thenBy { it.node.boundsLeft() }
                .thenBy { it.node.pathKey() },
        )
        .distinctBy { salient -> salient.node.salienceIdentity() }
        .take(MAX_SALIENT_NODES)
        .forEach { salient -> salient.node.renderSalienceLine(score = salient.score, output = this) }
}

private fun ScreenState.toCanonicalScreenText(mode: CanonicalScreenMode, includeSnapshotId: Boolean): String = buildString {
    append("screen-v1")
    if (includeSnapshotId) {
        append(" snapshot=")
        append(snapshotId)
    }
    append(" package=")
    append(foregroundPackage)
    appendLine()
    appendSelectedWindow(this@toCanonicalScreenText)
    selectedWindowReason?.let { reason ->
        append("windowReason=")
        appendLine(reason.normalizedText().quoted())
    }
    appendLine("tree:")
    val rootNode = root ?: return@buildString
    val keepPaths = if (mode == CanonicalScreenMode.Full) {
        rootNode.flatten().filter { it.visibleToUser }.map { it.pathKey() }.toSet()
    } else {
        compactKeepPaths(rootNode)
    }
    rootNode.renderCanonicalLines(keepPaths = keepPaths, output = this)
}

private fun compactKeepPaths(root: ScreenNode): Set<String> {
    val keep = linkedSetOf<String>()
    root.flatten().forEach { node ->
        if (node.visibleToUser && (node.isActionable() || node.hasMeaningfulLabel())) {
            node.path.runningFold(emptyList<Int>()) { acc, value -> acc + value }
                .forEach { path -> keep += path.pathKey() }
        }
    }
    return keep
}

private fun ScreenNode.renderCanonicalLines(keepPaths: Set<String>, output: StringBuilder) {
    if (pathKey() in keepPaths) {
        output.append("  ".repeat(path.size))
        output.append("- path=")
        output.append(pathKey())
        output.append(" role=")
        output.append(role)
        output.append(" ref=")
        output.append(ref)
        appendField(output, "label", label.takeIf { it.isNotBlank() })
        appendField(output, "text", text)
        appendField(output, "desc", contentDescription)
        appendField(output, "rid", resourceId)
        appendField(output, "class", className?.substringAfterLast('.'))
        val flags = compactFlags()
        if (flags.isNotEmpty()) {
            output.append(" flags=")
            output.append(flags.joinToString(","))
        }
        if (bounds.size == 4) {
            output.append(" bounds=")
            output.append(bounds.quantizedBounds())
        }
        output.appendLine()
    }
    children.forEach { child -> child.renderCanonicalLines(keepPaths, output) }
}

private fun StringBuilder.appendSelectedWindow(screenState: ScreenState) {
    screenState.windows.firstOrNull { it.selected }?.let { window ->
        append("window selected package=")
        append(window.packageName)
        append(" type=")
        append(window.type)
        append(" layer=")
        append(window.layer)
        window.visibleText.take(3).joinToString(" | ").takeIf { it.isNotBlank() }?.let { text ->
            append(" text=")
            append(text.quoted())
        }
        appendLine()
    }
}

private fun appendField(output: StringBuilder, name: String, value: String?) {
    val safe = value?.normalizedText()?.takeIf { it.isNotBlank() } ?: return
    output.append(' ')
    output.append(name)
    output.append('=')
    output.append(safe.quoted())
}

private fun ScreenNode.compactFlags(): List<String> = buildList {
    if (clickable) add("click")
    if (editable) add("edit")
    if (scrollable) add("scroll")
    if (focusable) add("focusable")
    if (!enabled) add("disabled")
    if (checked) add("checked")
    if (selected) add("selected")
    if (tapFallbackEligible && !clickable) add("tapFallback")
}

private data class SalientNode(val node: ScreenNode, val score: Int)

private fun ScreenNode.salienceScore(): Int {
    if (!visibleToUser) return -1
    var score = 0
    if (focused) score += 120
    if (editable) score += 90
    if (clickable) score += 70
    if (tapFallbackEligible && !clickable) score += 55
    if (focusable) score += 45
    if (selected || checked) score += 35
    if (scrollable) score += 25
    if (importantForAccessibility) score += 20
    score += actions.size.coerceAtMost(5) * 5
    if (!contentDescription.isNullOrBlank()) score += 18
    if (!text.isNullOrBlank()) score += 14
    if (label.isNotBlank()) score += 10
    if (!resourceId.isNullOrBlank()) score += 6
    val simpleClassName = className?.substringAfterLast('.')?.lowercase().orEmpty()
    score += when {
        "button" in simpleClassName -> 18
        "edittext" in simpleClassName -> 18
        "textview" in simpleClassName -> 6
        else -> 0
    }
    score += (boundsArea() / 50_000).coerceAtMost(20)
    if (!enabled) score -= 25
    return score
}

private fun ScreenNode.renderSalienceLine(score: Int, output: StringBuilder) {
    output.append("- score=")
    output.append(score)
    output.append(" path=")
    output.append(pathKey())
    output.append(" role=")
    output.append(role)
    output.append(" ref=")
    output.append(ref)
    appendField(output, "label", label.takeIf { it.isNotBlank() })
    appendField(output, "text", text)
    appendField(output, "desc", contentDescription)
    appendField(output, "rid", resourceId)
    appendField(output, "class", className?.substringAfterLast('.'))
    appendField(output, "details", descendantDetail())
    val flags = compactFlags()
    if (flags.isNotEmpty()) {
        output.append(" flags=")
        output.append(flags.joinToString(","))
    }
    if (bounds.size == 4) {
        output.append(" bounds=")
        output.append(bounds.quantizedBounds())
    }
    output.appendLine()
}

private fun ScreenNode.salienceIdentity(): String = listOf(
    label.normalizedText(),
    text?.normalizedText().orEmpty(),
    contentDescription?.normalizedText().orEmpty(),
    resourceId?.normalizedText().orEmpty(),
    className?.normalizedText().orEmpty(),
).joinToString(separator = "|")

private fun ScreenNode.descendantDetail(): String? {
    if (!isActionable()) return null
    val ownText = setOf(label.normalizedText(), text?.normalizedText().orEmpty(), contentDescription?.normalizedText().orEmpty())
    val detail = children
        .flatMap { child -> child.flatten() }
        .asSequence()
        .filter { node -> node.visibleToUser }
        .flatMap { node -> sequenceOf(node.label, node.text, node.contentDescription).filterNotNull() }
        .map { value -> value.normalizedText() }
        .filter { value -> value.isNotBlank() && value !in ownText }
        .distinct()
        .take(6)
        .joinToString(separator = " | ")
        .take(MAX_DESCENDANT_DETAIL_CHARS)
        .trim()
    return detail.takeIf { it.isNotBlank() }
}

private fun ScreenNode.boundsTop(): Int = bounds.getOrNull(1) ?: 0

private fun ScreenNode.boundsLeft(): Int = bounds.getOrNull(0) ?: 0

private fun ScreenNode.isActionable(): Boolean = visibleToUser &&
    (clickable || focusable || editable || scrollable || actions.isNotEmpty())

private fun ScreenNode.hasMeaningfulLabel(): Boolean = listOf(label, text, contentDescription, resourceId).any { !it.isNullOrBlank() }

fun ScreenNode.flatten(): List<ScreenNode> = buildList {
    fun visit(node: ScreenNode) {
        add(node)
        node.children.forEach(::visit)
    }
    visit(this@flatten)
}

private fun ScreenState.selectedWindowKey(): String? =
    windows.firstOrNull { it.selected }?.let { "${it.packageName}|${it.type}|${it.layer}|${it.bounds}" }

private fun List<Int>.pathKey(): String = joinToString(separator = "_").ifBlank { "root" }

private fun ScreenNode.pathKey(): String = path.pathKey()

private fun List<Int>.quantizedBounds(): String = joinToString(prefix = "[", postfix = "]", separator = ",") { value ->
    ((value + BOUNDS_QUANTUM / 2) / BOUNDS_QUANTUM * BOUNDS_QUANTUM).toString()
}

fun String.normalizedScreenText(): String = replace(Regex("\\s+"), " ")
    .replace("\uFEFF", "")
    .replace("\u200B", "")
    .replace("\u200C", "")
    .replace("\u200D", "")
    .replace("\u2060", "")
    .trim()

private fun String.normalizedText(): String = normalizedScreenText()

private fun String.quoted(): String = buildString {
    append('"')
    this@quoted.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
    append('"')
}

private fun String.limitChars(maxChars: Int): String {
    if (length <= maxChars) return this
    val suffix = "\n[truncated at $maxChars chars]"
    return take((maxChars - suffix.length).coerceAtLeast(0)).trimEnd() + suffix
}

private enum class ChangeKind {
    Equal,
    Insert,
    Delete,
}

private data class LineChange(val kind: ChangeKind, val value: String)

private fun lineChanges(before: List<String>, after: List<String>): List<LineChange> {
    val lcs = Array(before.size + 1) { IntArray(after.size + 1) }
    for (i in before.indices.reversed()) {
        for (j in after.indices.reversed()) {
            lcs[i][j] = if (before[i] == after[j]) {
                lcs[i + 1][j + 1] + 1
            } else {
                max(lcs[i + 1][j], lcs[i][j + 1])
            }
        }
    }
    val changes = mutableListOf<LineChange>()
    var i = 0
    var j = 0
    while (i < before.size && j < after.size) {
        when {
            before[i] == after[j] -> {
                changes += LineChange(ChangeKind.Equal, before[i])
                i += 1
                j += 1
            }
            lcs[i + 1][j] >= lcs[i][j + 1] -> {
                changes += LineChange(ChangeKind.Delete, before[i])
                i += 1
            }
            else -> {
                changes += LineChange(ChangeKind.Insert, after[j])
                j += 1
            }
        }
    }
    while (i < before.size) {
        changes += LineChange(ChangeKind.Delete, before[i])
        i += 1
    }
    while (j < after.size) {
        changes += LineChange(ChangeKind.Insert, after[j])
        j += 1
    }
    return changes
}

private fun renderUnifiedDiff(changes: List<LineChange>): String {
    if (changes.none { it.kind != ChangeKind.Equal }) return ""
    return buildString {
        appendLine("--- before")
        appendLine("+++ after")
        changes.forEach { change ->
            when (change.kind) {
                ChangeKind.Equal -> append(" ")
                ChangeKind.Insert -> append("+")
                ChangeKind.Delete -> append("-")
            }
            appendLine(change.value)
        }
    }
}
