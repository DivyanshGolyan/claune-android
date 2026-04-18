package com.divyanshgolyan.claune.android.runtime

import com.divyanshgolyan.claune.android.data.local.AgentRunArtifactStore
import com.divyanshgolyan.claune.android.data.local.RunArtifactMetadata
import com.divyanshgolyan.claune.android.data.local.SessionLogStore
import com.divyanshgolyan.claune.android.llm.ModelGateway
import com.divyanshgolyan.claune.android.llm.PiAgentModelGateway
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

class AgentLoop(
    private val phoneObserver: PhoneObserver,
    private val modelGateway: ModelGateway,
    private val sessionCoordinator: SessionCoordinator,
    private val logStore: SessionLogStore,
    private val artifactStore: AgentRunArtifactStore,
    private val modelTurnTimeoutMs: Long = DEFAULT_MODEL_TURN_TIMEOUT_MS,
) {
    suspend fun runSingleTurn(goal: String) {
        sessionCoordinator.startSession(goal)
        val sessionId = requireNotNull(sessionCoordinator.uiState.value.sessionId)
        runCatching {
            artifactStore.startRun(
                RunArtifactMetadata(
                    runId = sessionId,
                    goal = goal,
                    startedAt = java.time.Instant.now().toString(),
                    model = PiAgentModelGateway.MODEL_NAME,
                    maxIterations = PiAgentModelGateway.MAX_ITERATIONS,
                    promptVersion = PiAgentModelGateway.PROMPT_VERSION,
                ),
            )
            artifactStore.recordState(sessionCoordinator.uiState.value)
        }
        val snapshot = phoneObserver.captureSnapshot()
        logStore.recordSnapshot(snapshot)
        sessionCoordinator.setAccessibilityConnected(snapshot.foregroundPackage != "unavailable")
        sessionCoordinator.setLastKnownApp(snapshot.foregroundPackage)
        sessionCoordinator.logEvent(
            "Observed ${snapshot.actionableElements.size} actionable elements from ${snapshot.foregroundPackage}.",
        )

        val modelOutput =
            try {
                withTimeout(modelTurnTimeoutMs) {
                    modelGateway.nextStep(
                        ModelTurnInput(
                            sessionId = sessionId,
                            goal = goal,
                            snapshot = snapshot,
                            recentEvents = sessionCoordinator.uiState.value.timeline,
                        ),
                    )
                }
            } catch (_: TimeoutCancellationException) {
                val reason = "Model turn timed out after ${modelTurnTimeoutMs}ms."
                sessionCoordinator.logEvent(reason)
                ModelTurnOutput.Blocked(reason)
            }

        when (modelOutput) {
            is ModelTurnOutput.Message -> {
                sessionCoordinator.blockSession(modelOutput.messageToUser)
            }

            is ModelTurnOutput.Completion -> {
                sessionCoordinator.finishSession(modelOutput.summary)
            }

            is ModelTurnOutput.Blocked -> {
                sessionCoordinator.blockSession(modelOutput.reason)
            }
        }
    }

    companion object {
        const val DEFAULT_MODEL_TURN_TIMEOUT_MS: Long = 120_000
    }
}
