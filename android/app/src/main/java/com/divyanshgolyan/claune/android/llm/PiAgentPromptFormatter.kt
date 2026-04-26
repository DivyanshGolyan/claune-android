package com.divyanshgolyan.claune.android.llm

import com.divyanshgolyan.claune.android.BuildConfig
import com.divyanshgolyan.claune.android.runtime.ModelTurnInput

internal object PiAgentPromptFormatter {
    fun format(input: ModelTurnInput): String = buildString {
        appendLine("Current request:")
        appendLine(input.userMessage)
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
        appendLine("Last known phone screen before your next action:")
        appendLine("This observation may already be stale. Observe the screen yourself before acting.")
        appendLine("observationMode: ${input.screenObservation.mode}")
        appendLine("observationReason: ${input.screenObservation.reason}")
        appendLine("currentSnapshotId: ${input.screenObservation.currentSnapshotId}")
        input.screenObservation.baselineSnapshotId?.let { appendLine("baselineSnapshotId: $it") }
        appendLine("foregroundPackage: ${input.screenObservation.foregroundPackage}")
        input.screenObservation.selectedWindowReason?.let { reason ->
            appendLine("selectedWindowReason: $reason")
        }
        appendLine(
            "diffStats: additions=${input.screenObservation.stats.additions}, " +
                "removals=${input.screenObservation.stats.removals}, " +
                "unchanged=${input.screenObservation.stats.unchanged}, " +
                "changeRatio=${"%.2f".format(input.screenObservation.stats.changeRatio)}",
        )
        if (input.screenObservation.foregroundPackage == BuildConfig.APPLICATION_ID) {
            appendLine(
                "shellContext: The last known UI was Claune Android's own control shell. Leave Claune before operating the destination app.",
            )
        }
        appendLine("screenObservation:")
        appendLine(input.screenObservation.canonicalText ?: input.screenObservation.diff ?: "<empty>")
        appendLine()
        appendLine("When the current request is complete or blocked, call finish_run exactly once.")
        appendLine("When you need a user decision before continuing, call ask_user.")
    }
}
