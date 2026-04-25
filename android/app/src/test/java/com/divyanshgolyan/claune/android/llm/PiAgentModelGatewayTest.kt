package com.divyanshgolyan.claune.android.llm

import com.divyanshgolyan.claune.android.BuildConfig
import com.divyanshgolyan.claune.android.data.local.MemoryStore
import com.divyanshgolyan.claune.android.llm.tools.AskUserArguments
import com.divyanshgolyan.claune.android.llm.tools.AskUserToolDefinition
import com.divyanshgolyan.claune.android.llm.tools.EditMemoryArguments
import com.divyanshgolyan.claune.android.llm.tools.EditMemoryToolDefinition
import com.divyanshgolyan.claune.android.llm.tools.ExecuteScriptToolDefinition
import com.divyanshgolyan.claune.android.llm.tools.ExecuteScriptToolResult
import com.divyanshgolyan.claune.android.llm.tools.FinishRunArguments
import com.divyanshgolyan.claune.android.llm.tools.FinishRunStatus
import com.divyanshgolyan.claune.android.llm.tools.FinishRunToolDefinition
import com.divyanshgolyan.claune.android.llm.tools.ReadMemoryToolDefinition
import com.divyanshgolyan.claune.android.llm.tools.TerminalOutcomeRecorder
import com.divyanshgolyan.claune.android.runtime.ModelTurnInput
import com.divyanshgolyan.claune.android.runtime.ModelTurnOutput
import com.divyanshgolyan.claune.android.runtime.PhoneObserver
import com.divyanshgolyan.claune.android.runtime.QuestionAnswer
import com.divyanshgolyan.claune.android.runtime.QuestionAnswerKind
import com.divyanshgolyan.claune.android.runtime.UiElement
import com.divyanshgolyan.claune.android.runtime.UiSnapshot
import com.divyanshgolyan.claune.android.runtime.UserQuestionPrompter
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
    fun `prompt formatter includes current request events and actionable elements`() {
        val prompt =
            PiAgentPromptFormatter.format(
                ModelTurnInput(
                    runId = "run-1",
                    persistentSessionPath = null,
                    persistentSessionId = null,
                    userMessage = "Open Wi-Fi settings",
                    snapshot = snapshot(),
                    recentEvents = listOf("Run started", "Observed Settings app"),
                ),
            )

        assertTrue(prompt.contains("Open Wi-Fi settings"))
        assertTrue(prompt.contains("Current request:"))
        assertTrue(prompt.contains("Run started"))
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
                    runId = "run-1",
                    persistentSessionPath = null,
                    persistentSessionId = null,
                    userMessage = "Find the current weather",
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
        assertTrue(prompt.contains("- finish_run:"))
        assertTrue(prompt.contains("- ask_user:"))
        assertTrue(!prompt.contains("- complete_task:"))
        assertTrue(!prompt.contains("- block_task:"))
        assertTrue(!prompt.contains("- question:"))
        assertTrue(prompt.contains("- read_memory:"))
        assertTrue(prompt.contains("- edit_memory:"))
        assertTrue(prompt.contains("Run outcome contract:"))
        assertTrue(prompt.contains("User decision contract:"))
        assertTrue(prompt.contains("The finish_run message is shown to the user and must be a final statement, not a question."))
        assertTrue(prompt.contains("Use ask_user only when a user decision is needed before continuing the same run."))
        assertTrue(prompt.contains("The TypeScript contract below is the source of truth"))
        assertTrue(prompt.contains("Prefer visible direct controls by text or label."))
        assertTrue(prompt.contains("Use scrollScreen for the current page."))
        assertTrue(prompt.contains("For state-changing tasks"))
        assertTrue(!prompt.contains("For mutation requests"))
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
    fun `memory reflection prompt uses memory tools instead of final json`() {
        val prompt =
            MemoryReflectionPromptBuilder.format(
                ModelTurnInput(
                    runId = "run-1",
                    persistentSessionPath = null,
                    persistentSessionId = null,
                    userMessage = "Open Settings",
                    snapshot = snapshot(),
                    recentEvents = emptyList(),
                ),
                ModelTurnOutput.Completion("Opened Settings."),
            )

        assertTrue(prompt.contains("If there is no durable learning, do not call memory tools"))
        assertTrue(prompt.contains("Use edit_memory for one surgical update"))
        assertTrue(prompt.contains("After any memory tool call, you may send a short internal note if useful"))
        assertTrue(!prompt.contains("Return final JSON only"))
        assertTrue(!prompt.contains("Your entire final answer must be exactly the JSON object"))
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
    fun `finish run tool records verified completion`() = runTest {
        val recorder = TerminalOutcomeRecorder()
        val tool = FinishRunToolDefinition(recorder)

        val result =
            tool.execute(
                "tool-call-1",
                FinishRunArguments(
                    status = FinishRunStatus.Completed,
                    message = "Added the requested items.",
                    evidence = "Cart shows apples and oranges.",
                ),
                null,
                null,
            )

        assertEquals(ModelTurnOutput.Completion("Added the requested items."), recorder.outcome)
        assertEquals("Recorded run completed.", (result.content.single() as pi.ai.core.TextContent).text)
        assertEquals("run_outcome", result.details.jsonObject["kind"]?.jsonPrimitive?.content)
        assertEquals("completed", result.details.jsonObject["status"]?.jsonPrimitive?.content)
        assertEquals("Added the requested items.", result.details.jsonObject["value"]?.jsonPrimitive?.content)
        assertEquals("Cart shows apples and oranges.", result.details.jsonObject["evidence"]?.jsonPrimitive?.content)
        assertTrue(result.terminal)
    }

    @Test
    fun `finish run requires evidence for completed status`() {
        val tool = FinishRunToolDefinition(TerminalOutcomeRecorder())

        assertFailsWithIllegalArgument {
            tool.validateArguments(
                buildJsonObject {
                    put("status", "completed")
                    put("message", "Done.")
                },
            )
        }
    }

    @Test
    fun `ask user tool validates strict option contract`() {
        val tool = AskUserToolDefinition(FakeQuestionPrompter())

        val valid =
            tool.validateArguments(
                buildJsonObject {
                    put("prompt", "Which account should I use?")
                    put(
                        "options",
                        kotlinx.serialization.json.JsonArray(
                            listOf(
                                kotlinx.serialization.json.JsonPrimitive("Personal"),
                                kotlinx.serialization.json.JsonPrimitive("Work"),
                            ),
                        ),
                    )
                },
            )

        assertEquals("Which account should I use?", valid.prompt)
        assertEquals(listOf("Personal", "Work"), valid.options)
        assertFailsWithIllegalArgument {
            tool.validateArguments(
                buildJsonObject {
                    put("prompt", "Pick one")
                    put(
                        "options",
                        kotlinx.serialization.json.JsonArray(
                            listOf(
                                kotlinx.serialization.json.JsonPrimitive("One"),
                                kotlinx.serialization.json.JsonPrimitive("Two"),
                                kotlinx.serialization.json.JsonPrimitive("Three"),
                                kotlinx.serialization.json.JsonPrimitive("Four"),
                            ),
                        ),
                    )
                },
            )
        }
    }

    @Test
    fun `ask user tool returns selected answer as tool result`() = runTest {
        val tool =
            AskUserToolDefinition(
                FakeQuestionPrompter(
                    QuestionAnswer(
                        text = "Work",
                        kind = QuestionAnswerKind.Option,
                        optionIndex = 1,
                    ),
                ),
            )

        val result =
            tool.execute(
                "tool-call-1",
                AskUserArguments(
                    prompt = "Which account?",
                    options = listOf("Personal", "Work"),
                ),
                null,
                null,
            )

        assertEquals("User selected: Work", (result.content.single() as pi.ai.core.TextContent).text)
        assertEquals("option", result.details.jsonObject["answerType"]?.jsonPrimitive?.content)
        assertEquals("Work", result.details.jsonObject["answer"]?.jsonPrimitive?.content)
        assertEquals("1", result.details.jsonObject["optionIndex"]?.jsonPrimitive?.content)
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

private class FakeQuestionPrompter(
    private val answer: QuestionAnswer = QuestionAnswer("Personal", QuestionAnswerKind.Option, optionIndex = 0),
) : UserQuestionPrompter {
    override suspend fun askQuestion(
        toolCallId: String,
        prompt: String,
        options: List<String>,
        signal: pi.ai.core.AbortSignal?,
    ): QuestionAnswer = answer
}

private fun assertFailsWithIllegalArgument(block: () -> Unit) {
    try {
        block()
    } catch (expected: IllegalArgumentException) {
        return
    }
    error("Expected IllegalArgumentException")
}
