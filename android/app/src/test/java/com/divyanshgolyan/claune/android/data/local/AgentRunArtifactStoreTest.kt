package com.divyanshgolyan.claune.android.data.local

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import com.divyanshgolyan.claune.android.runtime.SessionStatus
import com.divyanshgolyan.claune.android.runtime.SessionUiState
import com.divyanshgolyan.claune.android.runtime.UiElement
import com.divyanshgolyan.claune.android.runtime.UiSnapshot
import com.divyanshgolyan.claune.android.scripting.ScriptJson
import java.nio.file.Files
import java.time.Instant as JavaInstant
import kotlin.time.Instant as KotlinInstant
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRunArtifactStoreTest {
    @Test
    fun `file store writes metadata history and snapshots`() {
        val root = Files.createTempDirectory("claune-artifacts").toFile()
        try {
            val store = FileAgentRunArtifactStore(root) { JavaInstant.parse("2026-04-17T10:00:00Z") }

            store.startRun(
                RunArtifactMetadata(
                    runId = "session-1",
                    goal = "Open Wi-Fi settings",
                    startedAt = "2026-04-17T09:59:00Z",
                    model = "claude-haiku-4-5",
                    maxIterations = 100,
                    promptVersion = "koog-anthropic-v1",
                ),
            )
            store.recordState(
                SessionUiState(
                    sessionId = "session-1",
                    status = SessionStatus.Blocked,
                    summaryLine = "Timed out reaching Wi-Fi.",
                    lastKnownApp = "com.android.settings",
                    accessibilityConnected = true,
                    foregroundServiceRunning = true,
                    timeline = listOf("Session started", "Timed out reaching Wi-Fi."),
                ),
            )
            store.recordSnapshot("session-1", snapshot())
            store.writeSystemPrompt("session-1", "system prompt")
            store.writeModelInput("session-1", "formatted prompt")
            store.writeFinalOutput("session-1", """{"kind":"blocked","reason":"Timed out"}""")
            store.writeKoogHistory("session-1", history())

            val metadata =
                ScriptJson.codec.decodeFromString(
                    RunArtifactMetadata.serializer(),
                    root.resolve("session-1/metadata.json").readText(),
                )
            val serializedHistory =
                ScriptJson.codec.decodeFromString(
                    ListSerializer(SerializedKoogMessage.serializer()),
                    root.resolve("session-1/koog-history.json").readText(),
                )

            assertEquals(SessionStatus.Blocked.name, metadata.status)
            assertEquals("Timed out reaching Wi-Fi.", metadata.latestSummary)
            assertTrue(metadata.finishedAt != null)
            assertEquals(4, serializedHistory.size)
            assertEquals("user", serializedHistory.first().type)
            assertTrue(root.resolve("session-1/snapshots.json").exists())
            assertEquals("formatted prompt", root.resolve("session-1/model-input.txt").readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `koog history serializer preserves tool call payloads`() {
        val serialized = history().map(KoogHistorySerializer::serialize)

        assertEquals("tool_call", serialized[1].type)
        val payload = serialized[1].payload
        assertEquals("execute_script", payload["tool"]?.toString()?.trim('"'))
        assertTrue(payload.toString().contains("claune.observePhone"))
    }

    private fun snapshot(): UiSnapshot = UiSnapshot(
        snapshotId = "snapshot-1",
        capturedAt = JavaInstant.parse("2026-04-17T10:00:00Z"),
        foregroundPackage = "com.android.settings",
        visibleText = listOf("Settings", "Wi-Fi"),
        actionableElements = listOf(
            UiElement(
                id = "el-1",
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

    private fun history(): List<Message> {
        val requestMeta = RequestMetaInfo(KotlinInstant.parse("2026-04-17T10:00:00Z"))
        val responseMeta = ResponseMetaInfo(KotlinInstant.parse("2026-04-17T10:00:01Z"))
        return listOf(
            Message.User("Open Wi-Fi settings", requestMeta),
            Message.Tool.Call(
                "tool-call-1",
                "execute_script",
                """{"script":"const screen = claune.observePhone(); return screen;"}""",
                responseMeta,
            ),
            Message.Tool.Result(
                "tool-call-1",
                "execute_script",
                """{"ok":true,"summary":"Observed settings","postActionSnapshot":{"snapshotId":"snapshot-1"}}""",
                requestMeta,
            ),
            Message.Assistant("""{"kind":"blocked","reason":"Timed out"}""", responseMeta),
        )
    }
}
