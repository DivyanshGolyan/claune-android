package com.divyanshgolyan.claune.android.llm

import com.divyanshgolyan.claune.android.runtime.ModelTurnInput
import com.divyanshgolyan.claune.android.runtime.ModelTurnOutput

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
                append("The script runtime is wired, but the Koog-backed model loop is still pending. ")
                append("The first visible candidate is ")
                append("'${candidate.label.ifBlank { candidate.role }}'.")
            },
        )
    }
}
