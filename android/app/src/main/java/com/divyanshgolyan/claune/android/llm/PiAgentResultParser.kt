package com.divyanshgolyan.claune.android.llm

import com.divyanshgolyan.claune.android.runtime.ModelTurnOutput
import com.divyanshgolyan.claune.android.scripting.ScriptJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

@Serializable
internal data class FinalAgentResponse(
    val kind: String,
    val summary: String? = null,
    val reason: String? = null,
    val messageToUser: String? = null,
)

internal object PiAgentResultParser {
    fun parse(raw: String): ModelTurnOutput {
        val payload = decodeFinalResponse(raw)
            ?: return ModelTurnOutput.Blocked(
                "Model returned malformed final output. Expected JSON with kind/message fields.",
            )

        return when (payload.kind) {
            "completion" -> payload.summary?.takeIf { it.isNotBlank() }?.let(ModelTurnOutput::Completion)
                ?: ModelTurnOutput.Blocked("Model returned completion without a summary.")

            "blocked" -> payload.reason?.takeIf { it.isNotBlank() }?.let(ModelTurnOutput::Blocked)
                ?: ModelTurnOutput.Blocked("Model returned blocked without a reason.")

            "message" -> payload.messageToUser?.takeIf { it.isNotBlank() }?.let(ModelTurnOutput::Message)
                ?: ModelTurnOutput.Blocked("Model returned message without messageToUser.")

            else -> ModelTurnOutput.Blocked("Model returned unsupported final kind '${payload.kind}'.")
        }
    }

    private fun decodeFinalResponse(raw: String): FinalAgentResponse? {
        val trimmed = raw.trim()
        runCatching {
            return ScriptJson.codec.decodeFromString(FinalAgentResponse.serializer(), trimmed)
        }

        return extractCandidateJsonObjects(raw)
            .asReversed()
            .firstNotNullOfOrNull { candidate ->
                runCatching {
                    ScriptJson.codec.decodeFromString(FinalAgentResponse.serializer(), candidate)
                }.getOrNull()
            }
    }
}

internal fun extractCandidateJsonObjects(raw: String): List<String> {
    val candidates = mutableListOf<String>()
    var inString = false
    var escaping = false
    var depth = 0
    var startIndex = -1

    raw.forEachIndexed { index, char ->
        when {
            escaping -> escaping = false
            char == '\\' && inString -> escaping = true
            char == '"' -> inString = !inString
            inString -> Unit
            char == '{' -> {
                if (depth == 0) {
                    startIndex = index
                }
                depth += 1
            }

            char == '}' && depth > 0 -> {
                depth -= 1
                if (depth == 0 && startIndex >= 0) {
                    candidates += raw.substring(startIndex, index + 1)
                    startIndex = -1
                }
            }
        }
    }

    return candidates
}
