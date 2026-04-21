package com.divyanshgolyan.claune.android.llm

import com.divyanshgolyan.claune.android.BuildConfig
import com.divyanshgolyan.claune.android.data.local.MemoryStore
import com.divyanshgolyan.claune.android.llm.tools.CompleteTaskArguments
import com.divyanshgolyan.claune.android.llm.tools.CompleteTaskToolDefinition
import com.divyanshgolyan.claune.android.llm.tools.EditMemoryArguments
import com.divyanshgolyan.claune.android.llm.tools.EditMemoryToolDefinition
import com.divyanshgolyan.claune.android.llm.tools.ExecuteScriptToolDefinition
import com.divyanshgolyan.claune.android.llm.tools.ExecuteScriptToolResult
import com.divyanshgolyan.claune.android.llm.tools.ReadMemoryToolDefinition
import com.divyanshgolyan.claune.android.llm.tools.TerminalOutcomeRecorder
import com.divyanshgolyan.claune.android.runtime.ModelTurnInput
import com.divyanshgolyan.claune.android.runtime.ModelTurnOutput
import com.divyanshgolyan.claune.android.runtime.PhoneObserver
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
import kotlinx.serialization.json.jsonPrimitive
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
                    persistentSessionPath = null,
                    persistentSessionId = null,
                    goal = "Open Wi-Fi settings",
                    snapshot = snapshot(),
                    recentEvents = listOf("Session started", "Observed Settings app"),
                ),
            )

        assertTrue(prompt.contains("Open Wi-Fi settings"))
        assertTrue(prompt.contains("Session started"))
        assertTrue(prompt.contains("Last known phone snapshot before your next action:"))
        assertTrue(prompt.contains("This snapshot may already be stale. Observe the phone yourself before acting."))
        assertTrue(prompt.contains("lastKnownForegroundPackage: com.android.settings"))
        assertTrue(prompt.contains("ref=el-1, role=button, label=Wi-Fi"))
        assertTrue(prompt.contains("idForIdOnlyApis=el-1"))
    }

    @Test
    fun `prompt formatter warns when still inside claune shell`() {
        val prompt =
            PiAgentPromptFormatter.format(
                ModelTurnInput(
                    sessionId = "session-1",
                    persistentSessionPath = null,
                    persistentSessionId = null,
                    goal = "Find the current weather",
                    snapshot = snapshot(packageName = BuildConfig.APPLICATION_ID),
                    recentEvents = emptyList(),
                ),
            )

        assertTrue(prompt.contains("shellContext: The last known UI was Claune Android's own control shell."))
    }

    @Test
    fun `system prompt embeds stable contract and memory context`() {
        val prompt =
            PiAgentModelGateway.systemPromptForTests(
                """
                # Claune Memory

                - The user prefers Wi-Fi tasks to start from Settings.
                """.trimIndent(),
            )

        assertTrue(prompt.contains("TypeScript contract for the global `claune` object"))
        assertTrue(prompt.contains("interface ClauneHost"))
        assertTrue(prompt.contains("tapText(text: string, exact: boolean): HostSuccessOutcome;"))
        assertTrue(prompt.contains("tapSelector(selector: ElementSelector): HostSuccessOutcome;"))
        assertTrue(prompt.contains("focusSelector(selector: ElementSelector, timeoutMs: number): HostSuccessOutcome;"))
        assertTrue(prompt.contains("scrollScreen(direction: \"up\" | \"down\"): HostSuccessOutcome;"))
        assertTrue(prompt.contains("typeIntoFocused(text: string): HostSuccessOutcome;"))
        assertTrue(prompt.contains("- execute_script:"))
        assertTrue(prompt.contains("- complete_task:"))
        assertTrue(prompt.contains("- block_task:"))
        assertTrue(prompt.contains("- ask_user:"))
        assertTrue(prompt.contains("- read_memory:"))
        assertTrue(prompt.contains("- edit_memory:"))
        assertTrue(prompt.contains("Terminal outcome contract:"))
        assertTrue(prompt.contains("After calling a terminal outcome tool, do not call more tools"))
        assertTrue(prompt.contains("The TypeScript contract below is the source of truth"))
        assertTrue(prompt.contains("Prefer visible direct controls by text or label."))
        assertTrue(prompt.contains("Use scrollScreen for the current page."))
        assertTrue(prompt.contains("For state-changing tasks"))
        assertTrue(!prompt.contains("For mutation goals"))
        assertTrue(prompt.contains("Available tools:"))
        assertTrue(prompt.contains("Current memory.md:"))
        assertTrue(prompt.contains("The user prefers Wi-Fi tasks to start from Settings."))
        assertTrue(prompt.contains("Do not edit memory during the main task."))
        assertTrue(prompt.contains("Example wrapper-input script:"))
        assertTrue(prompt.contains("Example scrolling script:"))
        assertTrue(prompt.contains("Example observe-and-wait script:"))
        assertTrue(prompt.contains("claune.scrollScreen(\"down\");"))
        assertTrue(prompt.contains("claune.focusSelector({ label: \"Search\" }, 2000);"))
        assertTrue(prompt.contains("you are seeing Claune's control shell"))
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
        val toolSet =
            ExecuteScriptToolDefinition(
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

    @Test
    fun `complete task tool records terminal completion`() = runTest {
        val recorder = TerminalOutcomeRecorder()
        val tool = CompleteTaskToolDefinition(recorder)

        val result =
            tool.execute(
                "tool-call-1",
                CompleteTaskArguments("Added the requested items."),
                null,
                null,
            )

        assertEquals(ModelTurnOutput.Completion("Added the requested items."), recorder.outcome)
        assertEquals("Recorded task completion.", (result.content.single() as pi.ai.core.TextContent).text)
    }

    @Test
    fun `read memory tool returns current markdown`() = runTest {
        val tool = ReadMemoryToolDefinition(FakeMemoryStore("# Claune Memory\n\n- Use Settings first.\n"))

        val result = tool.execute("tool-call-1", Unit, null, null)
        val text = (result.content.single() as pi.ai.core.TextContent).text

        assertTrue(text.contains("Use Settings first."))
        assertEquals(
            "# Claune Memory\n\n- Use Settings first.\n",
            result.details.jsonObject["content"]?.jsonPrimitive?.content,
        )
    }

    @Test
    fun `edit memory tool updates the markdown file by exact match`() = runTest {
        val store = FakeMemoryStore("# Claune Memory\n\n- Old fact.\n")
        val tool = EditMemoryToolDefinition(store)

        val result =
            tool.execute(
                "tool-call-1",
                EditMemoryArguments(
                    oldText = "- Old fact.\n",
                    newText = "- New durable fact.\n",
                ),
                null,
                null,
            )

        assertEquals("# Claune Memory\n\n- New durable fact.\n", store.read())
        assertEquals("Updated memory.md.", (result.content.single() as pi.ai.core.TextContent).text)
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

private class FakeMemoryStore(private var content: String) : MemoryStore {
    override suspend fun read(): String = content

    override suspend fun edit(oldText: String, newText: String) {
        val occurrences = content.split(oldText).size - 1
        check(occurrences == 1)
        content = content.replace(oldText, newText).trimEnd() + "\n"
    }

    override suspend fun overwrite(content: String) {
        this.content = content.trimEnd() + "\n"
    }
}
