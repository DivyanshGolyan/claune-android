package com.divyanshgolyan.claune.android.runtime

import com.divyanshgolyan.claune.android.data.local.AgentRunArtifactStore
import com.divyanshgolyan.claune.android.data.local.CodingSessionStore
import com.divyanshgolyan.claune.android.data.local.InMemorySessionLogStore
import com.divyanshgolyan.claune.android.data.local.RunArtifactMetadata
import com.divyanshgolyan.claune.android.data.local.SerializedAgentEvent
import com.divyanshgolyan.claune.android.llm.ModelGateway
import com.divyanshgolyan.claune.android.scripting.HostCallRecord
import com.divyanshgolyan.claune.android.scripting.ScriptExecutionRecord
import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentLoopTest {
    @Test
    fun `submitUserMessage completes without a product timeout`() = runTest {
        val (sessionCoordinator, _) = testCoordinator()
        val loop =
            AgentLoop(
                phoneObserver = FakePhoneObserver,
                modelGateway = SlowModelGateway,
                sessionCoordinator = sessionCoordinator,
                logStore = InMemorySessionLogStore(),
                artifactStore = NoOpArtifactStore,
            )

        loop.submitUserMessage("Open Settings")

        assertEquals(SessionStatus.Completed, sessionCoordinator.uiState.value.status)
        assertEquals("Done", sessionCoordinator.uiState.value.summaryLine)
    }
}

private object FakePhoneObserver : PhoneObserver {
    override suspend fun captureScreenState(): ScreenState {
        val wifi = ScreenNode(
            path = listOf(0),
            ref = "e0",
            elementId = "id-1",
            role = "button",
            label = "Wi-Fi",
            visibleToUser = true,
            clickable = true,
            editable = false,
            focused = false,
            bounds = listOf(0, 0, 100, 100),
        )
        return ScreenState(
            snapshotId = "snapshot-1",
            capturedAt = Instant.parse("2026-04-18T00:00:00Z").toString(),
            foregroundPackage = "com.android.settings",
            root = ScreenNode(
                path = emptyList(),
                ref = "root",
                elementId = "root",
                role = "root",
                label = "Settings",
                visibleToUser = true,
                clickable = false,
                editable = false,
                focused = false,
                bounds = listOf(0, 0, 1080, 2400),
                children = listOf(wifi),
            ),
        )
    }
}

private object SlowModelGateway : ModelGateway {
    override fun currentModelName(): String = "test-model"

    override suspend fun nextStep(input: ModelTurnInput): ModelTurnOutput {
        delay(50)
        return ModelTurnOutput.Completion("Done")
    }

    override suspend fun steer(message: String): Boolean = false

    override suspend fun abort() = Unit
}

private object NoOpArtifactStore : AgentRunArtifactStore {
    override fun startRun(metadata: RunArtifactMetadata) = Unit

    override fun recordState(state: SessionUiState) = Unit

    override fun recordScreenState(runId: String, screenState: ScreenState) = Unit

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

private fun testCoordinator(): Pair<SessionCoordinator, CodingSessionStore> {
    val root = Files.createTempDirectory("claune-agent-loop").toFile()
    val store = CodingSessionStore(cwd = root.absolutePath, agentDir = root.resolve("agent"))
    return SessionCoordinator(InMemorySessionLogStore(), store) to store
}
