package com.divyanshgolyan.claune.android.runtime

import com.divyanshgolyan.claune.android.data.local.AgentRunArtifactStore
import com.divyanshgolyan.claune.android.data.local.InMemorySessionLogStore
import com.divyanshgolyan.claune.android.data.local.RunArtifactMetadata
import com.divyanshgolyan.claune.android.data.local.SerializedAgentEvent
import com.divyanshgolyan.claune.android.llm.ModelGateway
import com.divyanshgolyan.claune.android.scripting.HostCallRecord
import com.divyanshgolyan.claune.android.scripting.ScriptExecutionRecord
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentLoopTest {
    @Test
    fun `runSingleTurn blocks when the model turn times out`() = runTest {
        val sessionCoordinator = SessionCoordinator(InMemorySessionLogStore())
        val loop =
            AgentLoop(
                phoneObserver = FakePhoneObserver,
                modelGateway = SlowModelGateway,
                sessionCoordinator = sessionCoordinator,
                logStore = InMemorySessionLogStore(),
                artifactStore = NoOpArtifactStore,
                modelTurnTimeoutMs = 10,
            )

        loop.runSingleTurn("Open Settings")

        assertEquals(SessionStatus.Blocked, sessionCoordinator.uiState.value.status)
        assertTrue(sessionCoordinator.uiState.value.summaryLine.contains("timed out"))
    }
}

private object FakePhoneObserver : PhoneObserver {
    override suspend fun captureSnapshot(): UiSnapshot = UiSnapshot(
        snapshotId = "snapshot-1",
        capturedAt = Instant.parse("2026-04-18T00:00:00Z"),
        foregroundPackage = "com.android.settings",
        visibleText = listOf("Settings"),
        actionableElements = listOf(
            UiElement(
                id = "id-1",
                ref = "e0",
                role = "button",
                label = "Wi-Fi",
                clickable = true,
                editable = false,
                focused = false,
                bounds = listOf(0, 0, 100, 100),
            ),
        ),
        focusedElementId = null,
    )
}

private object SlowModelGateway : ModelGateway {
    override suspend fun nextStep(input: ModelTurnInput): ModelTurnOutput {
        delay(1_000)
        return ModelTurnOutput.Completion("Done")
    }
}

private object NoOpArtifactStore : AgentRunArtifactStore {
    override fun startRun(metadata: RunArtifactMetadata) = Unit

    override fun recordState(state: SessionUiState) = Unit

    override fun recordSnapshot(runId: String, snapshot: UiSnapshot) = Unit

    override fun recordScriptExecution(runId: String, execution: ScriptExecutionRecord) = Unit

    override fun recordHostCall(runId: String, hostCall: HostCallRecord) = Unit

    override fun writeSystemPrompt(runId: String, systemPrompt: String) = Unit

    override fun writeModelInput(runId: String, modelInput: String) = Unit

    override fun writeFinalOutput(runId: String, finalOutput: String) = Unit

    override fun writeMemoryReflectionPrompt(runId: String, prompt: String) = Unit

    override fun writeMemoryReflectionOutput(runId: String, output: String) = Unit

    override fun writeAgentMessages(runId: String, messages: List<pi.ai.core.Message>) = Unit

    override fun writeAgentEvents(runId: String, events: List<SerializedAgentEvent>) = Unit
}
