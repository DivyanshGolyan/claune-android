package com.divyanshgolyan.claune.android.runtime

import com.divyanshgolyan.claune.android.data.local.SessionLogStore
import com.divyanshgolyan.claune.android.llm.ModelGateway

class AgentLoop(
    private val phoneObserver: PhoneObserver,
    private val modelGateway: ModelGateway,
    private val sessionCoordinator: SessionCoordinator,
    private val logStore: SessionLogStore,
) {
    suspend fun runSingleTurn(goal: String) {
        sessionCoordinator.startSession(goal)
        val snapshot = phoneObserver.captureSnapshot()
        sessionCoordinator.setLastKnownApp(snapshot.foregroundPackage)
        sessionCoordinator.logEvent(
            "Observed ${snapshot.actionableElements.size} actionable elements from ${snapshot.foregroundPackage}.",
        )

        val modelOutput =
            modelGateway.nextStep(
                ModelTurnInput(
                    goal = goal,
                    snapshot = snapshot,
                    recentEvents = sessionCoordinator.uiState.value.timeline,
                ),
            )

        when (modelOutput) {
            is ModelTurnOutput.Message -> {
                val line =
                    buildString {
                        append(modelOutput.messageToUser)
                        modelOutput.toolCall?.let { call ->
                            append(" Next tool candidate: ${call.toolName}.")
                        }
                    }
                sessionCoordinator.blockSession(line)
            }

            is ModelTurnOutput.Completion -> {
                sessionCoordinator.finishSession(modelOutput.summary)
            }

            is ModelTurnOutput.Blocked -> {
                sessionCoordinator.blockSession(modelOutput.reason)
            }
        }

        logStore.recordSnapshot(snapshot)
    }
}
