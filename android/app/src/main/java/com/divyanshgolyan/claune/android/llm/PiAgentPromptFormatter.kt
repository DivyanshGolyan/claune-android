package com.divyanshgolyan.claune.android.llm

import com.divyanshgolyan.claune.android.BuildConfig
import com.divyanshgolyan.claune.android.runtime.ModelTurnInput

internal object PiAgentPromptFormatter {
    fun format(input: ModelTurnInput): String = buildString {
        appendLine("Current request:")
        appendLine(input.userMessage)
        val recentEvents = input.recentEvents
            .filterNot { event ->
                event.startsWith("Run started:", ignoreCase = true) ||
                    event.startsWith("Observed ", ignoreCase = true)
            }
            .takeLast(3)
        if (recentEvents.isNotEmpty()) {
            appendLine()
            appendLine("Recent session events:")
            recentEvents.forEach { event ->
                append("- ").appendLine(event)
            }
        }
        appendLine()
        appendLine("Phone state hint:")
        appendLine("- lastForegroundPackage: ${input.screenObservation.foregroundPackage}")
        appendLine("- staleHint: observe the phone yourself with claune-js before acting.")
        if (input.screenObservation.foregroundPackage == BuildConfig.APPLICATION_ID) {
            appendLine("- shellContext: last known UI was Claune Android; leave it before operating another app.")
        }
        appendLine()
        appendLine("Use finish_run exactly once when complete or blocked; use ask_user only when a user decision is needed.")
    }
}
