package com.divyanshgolyan.claune.android.runtime

import com.divyanshgolyan.claune.android.data.local.AgentRunArtifactStore
import com.divyanshgolyan.claune.android.data.local.RunArtifactMetadata
import com.divyanshgolyan.claune.android.data.local.SessionLogStore
import com.divyanshgolyan.claune.android.llm.KoogModelGateway
import com.divyanshgolyan.claune.android.llm.ModelGateway

class AgentLoop(
    private val phoneObserver: PhoneObserver,
    private val modelGateway: ModelGateway,
    private val sessionCoordinator: SessionCoordinator,
    private val logStore: SessionLogStore,
    private val artifactStore: AgentRunArtifactStore,
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
                    model = KoogModelGateway.MODEL_NAME,
                    maxIterations = KoogModelGateway.MAX_ITERATIONS,
                    promptVersion = KoogModelGateway.PROMPT_VERSION,
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
            modelGateway.nextStep(
                ModelTurnInput(
                    sessionId = sessionId,
                    goal = goal,
                    snapshot = snapshot,
                    recentEvents = sessionCoordinator.uiState.value.timeline,
                ),
            )

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
}
