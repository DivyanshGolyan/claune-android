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
    suspend fun submitUserMessage(message: String) {
        if (sessionCoordinator.uiState.value.status == SessionStatus.Running && modelGateway.steer(message)) {
            sessionCoordinator.logEvent("Added instruction to current run.")
            return
        }
        runSelectedSessionPrompt(message)
    }

    suspend fun stopActiveSession(reason: String) {
        modelGateway.abort()
        sessionCoordinator.stopSession(reason)
        sessionCoordinator.refreshSessions()
    }

    private suspend fun runSelectedSessionPrompt(userMessage: String) {
        val selectedSession = sessionCoordinator.beginRun(userMessage)
        val runId = requireNotNull(sessionCoordinator.uiState.value.activeRunId)
        runCatching {
            artifactStore.startRun(
                RunArtifactMetadata(
                    runId = runId,
                    persistentSessionPath = selectedSession.path,
                    persistentSessionId = selectedSession.sessionId,
                    userMessage = userMessage,
                    startedAt = java.time.Instant.now().toString(),
                    model = modelGateway.currentModelName(),
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
                    runId = runId,
                    persistentSessionPath = selectedSession.path,
                    persistentSessionId = selectedSession.sessionId,
                    userMessage = userMessage,
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
