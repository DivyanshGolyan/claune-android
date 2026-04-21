package com.divyanshgolyan.claune.android.llm

import com.divyanshgolyan.claune.android.runtime.ModelTurnInput
import com.divyanshgolyan.claune.android.runtime.ModelTurnOutput
import kotlinx.serialization.Serializable

internal object MemoryReflectionPromptBuilder {
    fun systemPrompt(memoryContent: String): String = buildString {
        appendLine("You are Claune Android's memory reflection step.")
        appendLine("Your only job is deciding whether to update long-term memory after a completed phone-control run.")
        appendLine()
        appendLine("Available memory tools:")
        appendLine("- read_memory: read memory.md before updating it.")
        appendLine("- edit_memory: replace one exact unique memory.md string with new Markdown.")
        appendLine()
        appendLine("Current memory.md:")
        appendLine("```md")
        appendLine(memoryContent.ifBlank { "# Claune Memory" }.trimEnd())
        appendLine("```")
    }.trim()

    fun format(input: ModelTurnInput, result: ModelTurnOutput): String = buildString {
        appendLine("System follow-up: reflect on the run for long-term memory only.")
        appendLine()
        appendLine("Original user goal:")
        appendLine(input.goal)
        appendLine()
        appendLine("Run outcome:")
        when (result) {
            is ModelTurnOutput.Blocked -> appendLine("blocked: ${result.reason}")
            is ModelTurnOutput.Completion -> appendLine("completion: ${result.summary}")
            is ModelTurnOutput.Message -> appendLine("message: ${result.messageToUser}")
        }
        appendLine()
        appendLine("Task:")
        appendLine("Decide whether this run produced one durable learning useful for future phone-control runs.")
        appendLine()
        appendLine("Do not use execute_script.")
        appendLine()
        appendLine("Durable memory includes only:")
        appendLine("- Stable user preferences.")
        appendLine("- Recurring workflow rules.")
        appendLine("- Stable device facts.")
        appendLine("- Stable app interaction facts.")
        appendLine()
        appendLine("Do not store:")
        appendLine("- One-off task outcomes.")
        appendLine("- Transient UI state.")
        appendLine("- Current search results, availability, inventory, or content.")
        appendLine("- Generic prompting rules.")
        appendLine("- A single named item/entity from one task unless it reveals a stable app/device fact.")
        appendLine()
        appendLine("Procedure:")
        appendLine("1. If there is no durable learning, return no_update without reading memory.md.")
        appendLine("2. If there is a durable learning, read memory.md.")
        appendLine("3. Use edit_memory for one surgical update. Do not rewrite the whole file.")
        appendLine("4. Return final JSON only.")
        appendLine()
        appendLine("Examples:")
        appendLine("""Good: "On this device, a branded app launches as package com.example.realapp." """)
        appendLine("Why: stable device/app fact.")
        appendLine()
        appendLine("""Good: "In this app, the search wrapper does not focus text input; verify focus before typing." """)
        appendLine("Why: recurring interaction fact.")
        appendLine()
        appendLine("""Bad: "Today item X was unavailable." """)
        appendLine("Why: transient availability.")
        appendLine()
        appendLine("""Bad: "This run succeeded after tapping the third result." """)
        appendLine("Why: one-off tactic.")
        appendLine()
        appendLine("Return a single JSON object with exactly one of these shapes:")
        appendLine("""{"kind":"no_update","summary":"..."}""")
        appendLine("""{"kind":"updated","summary":"..."}""")
        appendLine()
        appendLine("Your entire final answer must be exactly the JSON object. No prose. No markdown fences.")
    }.trim()
}

@Serializable
private data class MemoryReflectionResponse(val kind: String, val summary: String)

internal sealed interface MemoryReflectionResult {
    val summary: String

    data class NoUpdate(override val summary: String) : MemoryReflectionResult

    data class Updated(override val summary: String) : MemoryReflectionResult
}

internal object MemoryReflectionResultParser {
    fun parse(raw: String): MemoryReflectionResult {
        val payload = decodeModelJson(raw, MemoryReflectionResponse.serializer())
            ?: return MemoryReflectionResult.NoUpdate("Malformed reflection output.")

        return when (payload.kind) {
            "no_update" -> MemoryReflectionResult.NoUpdate(payload.summary)
            "updated" -> MemoryReflectionResult.Updated(payload.summary)
            else -> MemoryReflectionResult.NoUpdate("Unsupported reflection kind '${payload.kind}'.")
        }
    }
}
