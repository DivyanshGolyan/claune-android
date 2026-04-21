package com.divyanshgolyan.claune.android.llm

import com.divyanshgolyan.claune.android.BuildConfig
import com.divyanshgolyan.claune.android.runtime.ModelTurnInput

internal object PiAgentPromptFormatter {
    fun format(input: ModelTurnInput): String = buildString {
        appendLine("Goal:")
        appendLine(input.goal)
        appendLine()
        appendLine("Recent session events:")
        if (input.recentEvents.isEmpty()) {
            appendLine("- none")
        } else {
            input.recentEvents.takeLast(8).forEach { event ->
                append("- ")
                appendLine(event)
            }
        }
        appendLine()
        appendLine("Last known phone snapshot before your next action:")
        appendLine("This snapshot may already be stale. Observe the phone yourself before acting.")
        appendLine("lastKnownForegroundPackage: ${input.snapshot.foregroundPackage}")
        input.snapshot.selectedWindowReason?.let { reason ->
            appendLine("lastKnownSelectedWindowReason: $reason")
        }
        if (input.snapshot.windowCandidates.isNotEmpty()) {
            appendLine("lastKnownWindowCandidates:")
            input.snapshot.windowCandidates.take(8).forEach { candidate ->
                append("- ")
                if (candidate.selected) {
                    append("selected ")
                }
                append(candidate.packageName)
                append(", type=")
                append(candidate.type)
                append(", layer=")
                append(candidate.layer)
                append(", active=")
                append(candidate.active)
                append(", focused=")
                append(candidate.focused)
                append(", actionableCount=")
                append(candidate.actionableElementCount)
                append(", text=")
                appendLine(candidate.visibleText.take(4).joinToString(" | ").ifBlank { "<none>" })
            }
        }
        if (input.snapshot.foregroundPackage == BuildConfig.APPLICATION_ID) {
            appendLine(
                "shellContext: The last known UI was Claune Android's own control shell. Leave Claune before operating the destination app.",
            )
        }
        appendLine("lastKnownFocusedElementId: ${input.snapshot.focusedElementId ?: "none"}")
        appendLine("lastKnownVisibleText:")
        if (input.snapshot.visibleText.isEmpty()) {
            appendLine("- none")
        } else {
            input.snapshot.visibleText.take(20).forEach { line ->
                append("- ")
                appendLine(line)
            }
        }
        appendLine("lastKnownActionableElements:")
        if (input.snapshot.actionableElements.isEmpty()) {
            appendLine("- none")
        } else {
            input.snapshot.actionableElements.take(20).forEach { element ->
                append("- ref=")
                append(element.ref)
                append(", role=")
                append(element.role)
                append(", label=")
                append(if (element.label.isBlank()) "<blank>" else element.label)
                append(", text=")
                append(element.text ?: "<none>")
                append(", contentDescription=")
                append(element.contentDescription ?: "<none>")
                append(", resourceId=")
                append(element.resourceId ?: "<none>")
                append(", idForIdOnlyApis=")
                append(element.id)
                append(", clickable=")
                append(element.clickable)
                append(", editable=")
                append(element.editable)
                append(", enabled=")
                append(element.enabled)
                append(", checked=")
                append(element.checked)
                append(", scrollable=")
                append(element.scrollable)
                append(", focused=")
                appendLine(element.focused)
            }
        }
        appendLine()
        appendLine("When the goal is complete, blocked, or needs the user, call exactly one terminal outcome tool.")
    }
}
