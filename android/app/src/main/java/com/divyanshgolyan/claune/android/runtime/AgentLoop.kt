package com.divyanshgolyan.claune.android.runtime

import com.divyanshgolyan.claune.android.data.local.AgentRunArtifactStore
import com.divyanshgolyan.claune.android.data.local.RunArtifactMetadata
import com.divyanshgolyan.claune.android.data.local.SessionLogStore
import com.divyanshgolyan.claune.android.llm.ModelGateway
import com.divyanshgolyan.claune.android.llm.PiAgentModelGateway
import com.divyanshgolyan.claune.android.runtime.SessionStatus

class AgentLoop(
    private val phoneObserver: PhoneObserver,
    private val modelGateway: ModelGateway,
    private val sessionCoordinator: SessionCoordinator,
    private val logStore: SessionLogStore,
    private val artifactStore: AgentRunArtifactStore,
) {
    suspend fun submitUserText(goal: String) {
        if (sessionCoordinator.uiState.value.status == SessionStatus.Running && modelGateway.steer(goal)) {
            sessionCoordinator.logEvent("Queued steering from the user.")
            return
        }
        runSelectedSessionPrompt(goal)
    }

    suspend fun stopActiveSession(reason: String) {
        modelGateway.abort()
        sessionCoordinator.stopSession(reason)
        sessionCoordinator.refreshSessions()
    }

    private suspend fun runSelectedSessionPrompt(goal: String) {
        val selectedSession = sessionCoordinator.beginRun(goal)
        val sessionId = requireNotNull(sessionCoordinator.uiState.value.sessionId)
        runCatching {
            artifactStore.startRun(
                RunArtifactMetadata(
                    runId = sessionId,
                    persistentSessionPath = selectedSession.path,
                    persistentSessionId = selectedSession.sessionId,
                    goal = goal,
                    startedAt = java.time.Instant.now().toString(),
                    model = PiAgentModelGateway.MODEL_NAME,
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
            modelGateway.nextStep(
                ModelTurnInput(
                    sessionId = sessionId,
                    persistentSessionPath = selectedSession.path,
                    persistentSessionId = selectedSession.sessionId,
                    goal = goal,
                    snapshot = snapshot,
                    recentEvents = sessionCoordinator.uiState.value.timeline,
                ),
            )

        when (modelOutput) {
            is ModelTurnOutput.Message -> {
                sessionCoordinator.pauseSession(modelOutput.messageToUser)
            }

            is ModelTurnOutput.Completion -> {
                sessionCoordinator.completeTurn(modelOutput.summary)
            }

            is ModelTurnOutput.Blocked -> {
                sessionCoordinator.blockTurn(modelOutput.reason)
            }
        }

        sessionCoordinator.refreshSessions()
    }
}
