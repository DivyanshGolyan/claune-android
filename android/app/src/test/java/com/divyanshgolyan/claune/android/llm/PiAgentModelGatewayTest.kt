package com.divyanshgolyan.claune.android.llm

import com.divyanshgolyan.claune.android.data.local.InMemorySessionLogStore
import com.divyanshgolyan.claune.android.runtime.ModelTurnInput
import com.divyanshgolyan.claune.android.runtime.PhoneObserver
import com.divyanshgolyan.claune.android.runtime.SessionCoordinator
import com.divyanshgolyan.claune.android.runtime.UiElement
import com.divyanshgolyan.claune.android.runtime.UiSnapshot
import com.divyanshgolyan.claune.android.scripting.ScriptExecutionRequest
import com.divyanshgolyan.claune.android.scripting.ScriptExecutionResult
import com.divyanshgolyan.claune.android.scripting.ScriptJson
import com.divyanshgolyan.claune.android.scripting.ScriptRuntime
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PiAgentModelGatewayTest {
    @Test
    fun `prompt formatter includes goal events and actionable elements`() {
        val prompt =
            PiAgentPromptFormatter.format(
                ModelTurnInput(
                    sessionId = "session-1",
                    goal = "Open Wi-Fi settings",
                    snapshot = snapshot(),
                    recentEvents = listOf("Session started", "Observed Settings app"),
                ),
            )

        assertTrue(prompt.contains("Open Wi-Fi settings"))
        assertTrue(prompt.contains("Session started"))
        assertTrue(prompt.contains("foregroundPackage: com.android.settings"))
        assertTrue(prompt.contains("id=el-1, role=button, label=Wi-Fi"))
    }

    @Test
    fun `system prompt embeds generated claune host contract`() {
        val prompt = PiAgentModelGateway.systemPromptForTests()

        assertTrue(prompt.contains("TypeScript contract for the global `claune` object"))
        assertTrue(prompt.contains("interface ClauneHost"))
        assertTrue(prompt.contains("tapSelector(selector: ElementSelector): HostSuccessOutcome;"))
        assertTrue(prompt.contains("typeIntoFocused(text: string): HostSuccessOutcome;"))
        assertTrue(prompt.contains("The TypeScript contract above is the source of truth"))
        assertTrue(prompt.contains("For any task that changes app or device state"))
        assertTrue(!prompt.contains("For mutation goals"))
    }

    @Test
    fun `result parser maps completion blocked and message responses`() {
        val completion = PiAgentResultParser.parse("""{"kind":"completion","summary":"Done."}""")
        val blocked = PiAgentResultParser.parse("""{"kind":"blocked","reason":"Could not proceed."}""")
        val message = PiAgentResultParser.parse("""{"kind":"message","messageToUser":"Need input."}""")

        assertEquals(com.divyanshgolyan.claune.android.runtime.ModelTurnOutput.Completion("Done."), completion)
        assertEquals(com.divyanshgolyan.claune.android.runtime.ModelTurnOutput.Blocked("Could not proceed."), blocked)
        assertEquals(com.divyanshgolyan.claune.android.runtime.ModelTurnOutput.Message("Need input."), message)
    }

    @Test
    fun `result parser blocks malformed output`() {
        val malformed = PiAgentResultParser.parse("""not-json""")
        val unsupported = PiAgentResultParser.parse("""{"kind":"tool","summary":"nope"}""")

        assertEquals(
            com.divyanshgolyan.claune.android.runtime.ModelTurnOutput.Blocked(
                "Model returned malformed final output. Expected JSON with kind/message fields.",
            ),
            malformed,
        )
        assertEquals(
            com.divyanshgolyan.claune.android.runtime.ModelTurnOutput.Blocked(
                "Model returned unsupported final kind 'tool'.",
            ),
            unsupported,
        )
    }

    @Test
    fun `result parser extracts trailing json object from prose response`() {
        val parsed =
            PiAgentResultParser.parse(
                """
                Perfect, I finished the task successfully.

                {"kind":"completion","summary":"Opened Settings and reached the Wi-Fi page."}
                """.trimIndent(),
            )

        assertEquals(
            com.divyanshgolyan.claune.android.runtime.ModelTurnOutput.Completion(
                "Opened Settings and reached the Wi-Fi page.",
            ),
            parsed,
        )
    }

    @Test
    fun `execute script tool returns runtime result plus post action snapshot`() = runTest {
        val coordinator = SessionCoordinator(InMemorySessionLogStore()).apply {
            startSession("Inspect Wi-Fi")
        }
        val toolSet =
            ExecuteScriptAgentTool(
                scriptRuntime = FakeScriptRuntime(
                    ScriptExecutionResult(
                        ok = true,
                        summary = "Script completed with 1 host call.",
                        data = buildJsonObject { put("step", "opened_settings") },
                    ),
                ),
                phoneObserver = FakePhoneObserver(snapshot(snapshotId = "after-script")),
            )

        val encoded =
            toolSet.execute(
                toolCallId = "tool-call-1",
                params = "return { ok: true };",
                signal = null,
                onUpdate = null,
            ).content.single().let { textBlock ->
                (textBlock as pi.ai.core.TextContent).text
            }
        val payload = ScriptJson.codec.decodeFromString<ExecuteScriptToolResult>(encoded)

        assertTrue(payload.ok)
        assertEquals("Script completed with 1 host call.", payload.summary)
        assertEquals("after-script", payload.postActionSnapshot.snapshotId)
        assertEquals("opened_settings", payload.scriptData?.jsonObject?.get("step")?.toString()?.trim('"'))
    }

    private fun snapshot(snapshotId: String = "snapshot-1", packageName: String = "com.android.settings"): UiSnapshot = UiSnapshot(
        snapshotId = snapshotId,
        capturedAt = Instant.parse("2026-04-16T00:00:00Z"),
        foregroundPackage = packageName,
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
}

private class FakeScriptRuntime(private val result: ScriptExecutionResult) : ScriptRuntime {
    override suspend fun execute(request: ScriptExecutionRequest): ScriptExecutionResult = result
}

private class FakePhoneObserver(private val snapshot: UiSnapshot) : PhoneObserver {
    override suspend fun captureSnapshot(): UiSnapshot = snapshot
}
