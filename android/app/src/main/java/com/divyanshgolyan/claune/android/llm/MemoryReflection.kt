package com.divyanshgolyan.claune.android.llm

import com.divyanshgolyan.claune.android.runtime.ModelTurnInput
import com.divyanshgolyan.claune.android.runtime.ModelTurnOutput
import com.divyanshgolyan.claune.android.scripting.ScriptJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

internal object MemoryReflectionPromptBuilder {
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
        appendLine("Rules for this reflection turn:")
        appendLine("- Do not use execute_script.")
        appendLine("- Read memory.md first if you might update it.")
        appendLine("- Use edit_memory for a surgical update, not a full rewrite.")
        appendLine(
            "- Only store durable long-term facts: stable app facts, stable device facts, recurring workflow rules, or user preferences.",
        )
        appendLine(
            "- Do not store time-sensitive, situational, or one-off observations. Memory is for durable facts that are likely to remain useful across many future runs.",
        )
        appendLine(
            "- If a candidate fact depends on temporary state, recent search results, ephemeral availability, current contents, or a single named item/entity from one task, treat it as transient and do not store it.",
        )
        appendLine("- Do not store one-off task outcomes, transient UI state, generic prompting rules, or short-term learnings.")
        appendLine("- If there is no durable long-term learning worth saving, do not update memory.md.")
        appendLine()
        appendLine("Few-shot examples:")
        appendLine("""- Good durable memory: "On this device, App X uses package com.example.realapp instead of the branded name." """)
        appendLine("""- Why good: that is a stable app/device fact that can prevent the same mistake in future runs.""")
        appendLine("""- Bad transient memory: "Today the search results showed oranges unavailable." """)
        appendLine("""- Why bad: that depends on temporary inventory or current results and may be false later.""")
        appendLine(
            """- Good durable memory: "In App Y, tapping the visible search wrapper does not focus an editable field; verify focus before typing." """,
        )
        appendLine("""- Why good: that is a recurring interaction fact about the app surface, not a one-off task result.""")
        appendLine("""- Bad transient memory: "This run succeeded after opening Screen Z and tapping the third visible item." """)
        appendLine("""- Why bad: that is just a one-run tactic, not a durable fact worth injecting into future prompts.""")
        appendLine()
        appendLine("Return a single JSON object with exactly one of these shapes:")
        appendLine("""{"kind":"no_update","summary":"..."}""")
        appendLine("""{"kind":"updated","summary":"..."}""")
        appendLine()
        appendLine("Do not wrap the final JSON in markdown fences.")
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
        val payload =
            runCatching {
                ScriptJson.codec.decodeFromString(MemoryReflectionResponse.serializer(), raw.trim())
            }.getOrElse {
                extractCandidateJsonObjects(raw)
                    .asReversed()
                    .firstNotNullOfOrNull { candidate ->
                        runCatching {
                            ScriptJson.codec.decodeFromString(MemoryReflectionResponse.serializer(), candidate)
                        }.getOrNull()
                    }
            } ?: return MemoryReflectionResult.NoUpdate("Malformed reflection output.")

        return when (payload.kind) {
            "no_update" -> MemoryReflectionResult.NoUpdate(payload.summary)
            "updated" -> MemoryReflectionResult.Updated(payload.summary)
            else -> MemoryReflectionResult.NoUpdate("Unsupported reflection kind '${payload.kind}'.")
        }
    }
}
