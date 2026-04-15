package com.divyanshgolyan.claune.android.llm

import com.divyanshgolyan.claune.android.runtime.ModelTurnInput
import com.divyanshgolyan.claune.android.runtime.ModelTurnOutput
import com.divyanshgolyan.claune.android.runtime.PrototypeToolCall

interface ModelGateway {
    suspend fun nextStep(input: ModelTurnInput): ModelTurnOutput
}

class StubModelGateway : ModelGateway {
    override suspend fun nextStep(input: ModelTurnInput): ModelTurnOutput = if (input.snapshot.actionableElements.isEmpty()) {
        ModelTurnOutput.Blocked(
            "No actionable elements were captured. Enable the accessibility service, reopen the target app, and retry.",
        )
    } else {
        val candidate = input.snapshot.actionableElements.first()
        ModelTurnOutput.Message(
            messageToUser =
            buildString {
                append("The runtime is wired, but model-backed execution is still stubbed. ")
                append("The first visible candidate is ")
                append("'${candidate.label.ifBlank { candidate.role }}'.")
            },
            toolCall =
            PrototypeToolCall(
                toolName = "tap_element",
                arguments = mapOf("elementId" to candidate.id),
            ),
        )
    }
}
