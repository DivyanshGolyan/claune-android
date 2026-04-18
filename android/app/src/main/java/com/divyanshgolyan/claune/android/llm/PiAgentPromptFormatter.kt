package com.divyanshgolyan.claune.android.llm

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
        appendLine("Current phone snapshot:")
        appendLine("foregroundPackage: ${input.snapshot.foregroundPackage}")
        appendLine("focusedElementId: ${input.snapshot.focusedElementId ?: "none"}")
        appendLine("visibleText:")
        if (input.snapshot.visibleText.isEmpty()) {
            appendLine("- none")
        } else {
            input.snapshot.visibleText.take(20).forEach { line ->
                append("- ")
                appendLine(line)
            }
        }
        appendLine("actionableElements:")
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
        appendLine("Return final JSON only when the goal is complete or clearly blocked.")
    }
}
