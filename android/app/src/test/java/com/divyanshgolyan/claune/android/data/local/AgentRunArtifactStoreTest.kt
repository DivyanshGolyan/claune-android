package com.divyanshgolyan.claune.android.data.local

import com.divyanshgolyan.claune.android.runtime.ScreenNode
import com.divyanshgolyan.claune.android.runtime.ScreenState
import com.divyanshgolyan.claune.android.runtime.SessionStatus
import com.divyanshgolyan.claune.android.runtime.SessionUiState
import com.divyanshgolyan.claune.android.scripting.ScriptJson
import java.nio.file.Files
import java.time.Instant as JavaInstant
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pi.agent.core.AgentEvent
import pi.agent.core.AgentToolResult
import pi.ai.core.AssistantMessage
import pi.ai.core.StopReason
import pi.ai.core.TextContent
import pi.ai.core.ToolCall
import pi.ai.core.ToolResultMessage
import pi.ai.core.Usage
import pi.ai.core.UsageCost
import pi.ai.core.UserMessage
import pi.ai.core.UserMessageContent

class AgentRunArtifactStoreTest {
    @Test
    fun `file store writes metadata history and snapshots`() {
        val root = Files.createTempDirectory("claune-artifacts").toFile()
        try {
            val store = FileAgentRunArtifactStore(root) { JavaInstant.parse("2026-04-17T10:00:00Z") }

            store.startRun(
                RunArtifactMetadata(
                    runId = "run-1",
                    userMessage = "Open Wi-Fi settings",
                    startedAt = "2026-04-17T09:59:00Z",
                    model = "claude-haiku-4-5",
                    maxIterations = 100,
                    promptVersion = "pi-agent-anthropic-v1",
                ),
            )
            store.recordState(
                SessionUiState(
                    activeRunId = "run-1",
                    status = SessionStatus.Blocked,
                    summaryLine = "Timed out reaching Wi-Fi.",
                    lastKnownApp = "com.android.settings",
                    accessibilityConnected = true,
                    foregroundServiceRunning = true,
                    timeline = listOf("Run started", "Timed out reaching Wi-Fi."),
                ),
            )
            store.recordScreenState("run-1", snapshot())
            store.writeSystemPrompt("run-1", "system prompt")
            store.writeModelInput("run-1", "formatted prompt")
            store.writeFinalOutput("run-1", """{"status":"blocked","message":"Timed out"}""")
            store.writeAgentMessages("run-1", history())
            store.writeAgentEvents("run-1", events())

            val metadata =
                ScriptJson.codec.decodeFromString(
                    RunArtifactMetadata.serializer(),
                    root.resolve("run-1/metadata.json").readText(),
                )
            val serializedMessages =
                ScriptJson.codec.decodeFromString(
                    ListSerializer(SerializedAgentMessage.serializer()),
                    root.resolve("run-1/agent-messages.json").readText(),
                )
            val serializedEvents =
                ScriptJson.codec.decodeFromString(
                    ListSerializer(SerializedAgentEvent.serializer()),
                    root.resolve("run-1/agent-events.json").readText(),
                )
            val screenStates =
                ScriptJson.codec.decodeFromString(
                    ListSerializer(ScreenStateArtifactRecord.serializer()),
                    root.resolve("run-1/screen-states.json").readText(),
                )
            val latestScreenState =
                ScriptJson.codec.decodeFromString(
                    ScreenState.serializer(),
                    root.resolve("run-1/latest-screen-state.json").readText(),
                )

            assertEquals(SessionStatus.Blocked.name, metadata.status)
            assertEquals("Timed out reaching Wi-Fi.", metadata.latestSummary)
            assertTrue(metadata.finishedAt != null)
            assertEquals(4, serializedMessages.size)
            assertEquals("user", serializedMessages.first().type)
            assertEquals(3, serializedEvents.size)
            assertEquals("agent_start", serializedEvents.first().type)
            assertEquals("run-1", metadata.runId)
            assertEquals("Open Wi-Fi settings", metadata.userMessage)
            assertEquals("snapshot-1", screenStates.single().snapshotId)
            assertEquals("com.android.settings", screenStates.single().foregroundPackage)
            assertEquals(1, screenStates.single().visibleNodeCount)
            assertTrue(screenStates.single().canonicalText.contains("Wi-Fi"))
            assertEquals("Settings", latestScreenState.root?.label)
            assertEquals("formatted prompt", root.resolve("run-1/model-input.txt").readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `file store caps compact screen history and overwrites latest raw screen state`() {
        val root = Files.createTempDirectory("claune-artifacts").toFile()
        try {
            val store = FileAgentRunArtifactStore(root) { JavaInstant.parse("2026-04-17T10:00:00Z") }
            store.startRun(
                RunArtifactMetadata(
                    runId = "run-1",
                    userMessage = "Inspect screen",
                    startedAt = "2026-04-17T09:59:00Z",
                    model = "claude-haiku-4-5",
                    maxIterations = 100,
                    promptVersion = "pi-agent-anthropic-v1",
                ),
            )

            repeat(85) { index ->
                store.recordScreenState("run-1", snapshot("snapshot-$index", label = "Item $index"))
            }

            val screenStates =
                ScriptJson.codec.decodeFromString(
                    ListSerializer(ScreenStateArtifactRecord.serializer()),
                    root.resolve("run-1/screen-states.json").readText(),
                )
            val latestScreenState =
                ScriptJson.codec.decodeFromString(
                    ScreenState.serializer(),
                    root.resolve("run-1/latest-screen-state.json").readText(),
                )

            assertEquals(80, screenStates.size)
            assertEquals("snapshot-5", screenStates.first().snapshotId)
            assertEquals("snapshot-84", screenStates.last().snapshotId)
            assertEquals("Item 84", latestScreenState.root?.label)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `file store rotates oversized legacy screen history before appending compact records`() {
        val root = Files.createTempDirectory("claune-artifacts").toFile()
        try {
            val store = FileAgentRunArtifactStore(root) { JavaInstant.parse("2026-04-17T10:00:00Z") }
            store.startRun(
                RunArtifactMetadata(
                    runId = "run-1",
                    userMessage = "Inspect screen",
                    startedAt = "2026-04-17T09:59:00Z",
                    model = "claude-haiku-4-5",
                    maxIterations = 100,
                    promptVersion = "pi-agent-anthropic-v1",
                ),
            )
            root.resolve("run-1/screen-states.json").writeText(" ".repeat(1_000_001))

            store.recordScreenState("run-1", snapshot("snapshot-new"))

            val screenStates =
                ScriptJson.codec.decodeFromString(
                    ListSerializer(ScreenStateArtifactRecord.serializer()),
                    root.resolve("run-1/screen-states.json").readText(),
                )

            assertEquals("snapshot-new", screenStates.single().snapshotId)
            assertTrue(root.resolve("run-1").listFiles().orEmpty().any { it.name.startsWith("screen-states.json.legacy-") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `file store appends perf events as json lines`() {
        val root = Files.createTempDirectory("claune-artifacts").toFile()
        try {
            val store = FileAgentRunArtifactStore(root) { JavaInstant.parse("2026-04-17T10:00:00Z") }
            store.startRun(
                RunArtifactMetadata(
                    runId = "run-1",
                    userMessage = "Inspect screen",
                    startedAt = "2026-04-17T09:59:00Z",
                    model = "claude-haiku-4-5",
                    maxIterations = 100,
                    promptVersion = "pi-agent-anthropic-v1",
                ),
            )

            repeat(1_005) { index ->
                store.recordPerfEvent(
                    "run-1",
                    PerfEventRecord(
                        recordedAt = "2026-04-17T10:00:00Z",
                        scope = "projection",
                        name = "observeScreen.project",
                        durationMs = index.toLong(),
                        attrs = mapOf("snapshotId" to "snapshot-$index"),
                    ),
                )
            }

            val perfEvents = root.resolve("run-1/perf-events.jsonl")
                .readLines()
                .map { line -> ScriptJson.codec.decodeFromString(PerfEventRecord.serializer(), line) }

            assertEquals(1_005, perfEvents.size)
            assertEquals("snapshot-0", perfEvents.first().attrs["snapshotId"])
            assertEquals("snapshot-1004", perfEvents.last().attrs["snapshotId"])
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `file store rotates oversized perf events before appending`() {
        val root = Files.createTempDirectory("claune-artifacts").toFile()
        try {
            val store = FileAgentRunArtifactStore(root) { JavaInstant.parse("2026-04-17T10:00:00Z") }
            store.startRun(
                RunArtifactMetadata(
                    runId = "run-1",
                    userMessage = "Inspect screen",
                    startedAt = "2026-04-17T09:59:00Z",
                    model = "claude-haiku-4-5",
                    maxIterations = 100,
                    promptVersion = "pi-agent-anthropic-v1",
                ),
            )
            root.resolve("run-1/perf-events.jsonl").writeText(" ".repeat(1_000_001))

            store.recordPerfEvent(
                "run-1",
                PerfEventRecord(
                    recordedAt = "2026-04-17T10:00:00Z",
                    scope = "projection",
                    name = "observeScreen.project",
                    durationMs = 12,
                ),
            )

            val perfEvents = root.resolve("run-1/perf-events.jsonl")
                .readLines()
                .map { line -> ScriptJson.codec.decodeFromString(PerfEventRecord.serializer(), line) }

            assertEquals(1, perfEvents.size)
            assertEquals("run-1", perfEvents.single().runId)
            assertTrue(root.resolve("run-1").listFiles().orEmpty().any { it.name.startsWith("perf-events.jsonl.legacy-") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `agent transcript serializer preserves tool call payloads`() {
        val serialized = history().map(AgentTranscriptSerializer::serializeMessage)

        assertEquals("assistant", serialized[1].type)
        val payload = serialized[1].payload
        val firstBlock = payload["content"].toString()
        assertTrue(firstBlock.contains("tool_call"))
        assertTrue(firstBlock.contains("execute_script"))
        assertTrue(firstBlock.contains("claune.observeScreen"))
    }

    @Test
    fun `file store recovers orphaned running metadata`() {
        val root = Files.createTempDirectory("claune-artifacts").toFile()
        try {
            val store = FileAgentRunArtifactStore(root) { JavaInstant.parse("2026-04-18T10:20:00Z") }

            store.startRun(
                RunArtifactMetadata(
                    runId = "run-stuck",
                    userMessage = "Add apples and oranges",
                    startedAt = "2026-04-18T10:10:00Z",
                    model = "claude-haiku-4-5",
                    maxIterations = 100,
                    promptVersion = "pi-agent-anthropic-v5",
                ),
            )

            store.recoverOrphanedRuns("Previous session ended unexpectedly. Resetting stale running state.")

            val metadata =
                ScriptJson.codec.decodeFromString(
                    RunArtifactMetadata.serializer(),
                    root.resolve("run-stuck/metadata.json").readText(),
                )

            assertEquals(SessionStatus.Cancelled.name, metadata.status)
            assertEquals("Previous session ended unexpectedly. Resetting stale running state.", metadata.latestSummary)
            assertEquals(false, metadata.foregroundServiceRunning)
            assertTrue(metadata.finishedAt != null)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun snapshot(snapshotId: String = "snapshot-1", label: String = "Settings"): ScreenState {
        val wifi = ScreenNode(
            path = listOf(0),
            ref = "el-1",
            elementId = "el-1",
            role = "button",
            label = "Wi-Fi",
            visibleToUser = true,
            clickable = true,
            editable = false,
            focused = false,
            bounds = listOf(0, 0, 100, 100),
        )
        return ScreenState(
            snapshotId = snapshotId,
            capturedAt = JavaInstant.parse("2026-04-17T10:00:00Z").toString(),
            foregroundPackage = "com.android.settings",
            root = ScreenNode(
                path = emptyList(),
                ref = "root",
                elementId = "root",
                role = "root",
                label = label,
                visibleToUser = true,
                clickable = false,
                editable = false,
                focused = false,
                bounds = listOf(0, 0, 1080, 2400),
                children = listOf(wifi),
            ),
        )
    }

    private fun history(): List<pi.ai.core.Message> = listOf(
        UserMessage(
            content = UserMessageContent.Text("Open Wi-Fi settings"),
            timestamp = JavaInstant.parse("2026-04-17T10:00:00Z").toEpochMilli(),
        ),
        AssistantMessage(
            content =
            mutableListOf(
                ToolCall(
                    id = "tool-call-1",
                    name = "execute_script",
                    arguments =
                    buildJsonObject {
                        put("script", "const screen = claune.observeScreen(); return screen;")
                    },
                ),
            ),
            api = "anthropic-messages",
            provider = "anthropic",
            model = "claude-haiku-4-5",
            usage = Usage(1, 2, 3, 4, 10, UsageCost()),
            stopReason = StopReason.TOOL_USE,
            timestamp = JavaInstant.parse("2026-04-17T10:00:01Z").toEpochMilli(),
        ),
        ToolResultMessage(
            toolCallId = "tool-call-1",
            toolName = "execute_script",
            content =
            listOf(
                TextContent(
                    """{"ok":true,"summary":"Observed settings","postActionObservation":{"currentSnapshotId":"snapshot-1"}}""",
                ),
            ),
            isError = false,
            timestamp = JavaInstant.parse("2026-04-17T10:00:02Z").toEpochMilli(),
        ),
        AssistantMessage(
            content = mutableListOf(TextContent("""{"kind":"blocked","reason":"Timed out"}""")),
            api = "anthropic-messages",
            provider = "anthropic",
            model = "claude-haiku-4-5",
            usage = Usage(5, 6, 0, 0, 11, UsageCost()),
            stopReason = StopReason.STOP,
            timestamp = JavaInstant.parse("2026-04-17T10:00:03Z").toEpochMilli(),
        ),
    )

    private fun events(): List<SerializedAgentEvent> = listOf(
        AgentTranscriptSerializer.serializeEvent(AgentEvent.AgentStart),
        AgentTranscriptSerializer.serializeEvent(
            AgentEvent.ToolExecutionStart(
                toolCallId = "tool-call-1",
                toolName = "execute_script",
                args = buildJsonObject { put("script", "const screen = claune.observeScreen();") },
            ),
        ),
        AgentTranscriptSerializer.serializeEvent(
            AgentEvent.ToolExecutionEnd(
                toolCallId = "tool-call-1",
                toolName = "execute_script",
                result =
                AgentToolResult(
                    content = listOf(TextContent("""{"ok":true}""")),
                    details = buildJsonObject { put("ok", true) },
                ),
                isError = false,
            ),
        ),
    )
}
