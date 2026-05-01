package com.divyanshgolyan.claune.android.llm

import com.divyanshgolyan.claune.android.BuildConfig
import com.divyanshgolyan.claune.android.llm.tools.AskUserArguments
import com.divyanshgolyan.claune.android.llm.tools.AskUserToolDefinition
import com.divyanshgolyan.claune.android.llm.tools.FinishRunArguments
import com.divyanshgolyan.claune.android.llm.tools.FinishRunStatus
import com.divyanshgolyan.claune.android.llm.tools.FinishRunToolDefinition
import com.divyanshgolyan.claune.android.llm.tools.TerminalOutcomeRecorder
import com.divyanshgolyan.claune.android.runtime.ModelTurnInput
import com.divyanshgolyan.claune.android.runtime.ModelTurnOutput
import com.divyanshgolyan.claune.android.runtime.QuestionAnswer
import com.divyanshgolyan.claune.android.runtime.QuestionAnswerKind
import com.divyanshgolyan.claune.android.runtime.ScreenNode
import com.divyanshgolyan.claune.android.runtime.ScreenState
import com.divyanshgolyan.claune.android.runtime.UserQuestionPrompter
import com.divyanshgolyan.claune.android.runtime.buildScreenObservation
import java.time.Instant
import kotlinx.coroutines.test.runTest
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
                    screenObservation = screenObservation(),
                    recentEvents = listOf("Run started", "Observed Settings app"),
                ),
            )

        assertTrue(prompt.contains("Open Wi-Fi settings"))
        assertTrue(prompt.contains("Current request:"))
        assertTrue(prompt.contains("Run started"))
        assertTrue(prompt.contains("Last known phone screen before your next action:"))
        assertTrue(prompt.contains("This observation may already be stale. Observe the screen yourself before acting."))
        assertTrue(prompt.contains("foregroundPackage: com.android.settings"))
        assertTrue(prompt.contains("ref=el-1"))
        assertTrue(prompt.contains("label=\"Wi-Fi\""))
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
                    screenObservation = screenObservation(snapshot(packageName = BuildConfig.APPLICATION_ID)),
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
                /work/memory/
                /work/memory/apps/
                /work/memory/apps/settings.md (52 chars)
                """.trimIndent(),
            )

        assertTrue(prompt.contains("Claune JS is available through bash:"))
        assertTrue(prompt.contains("claune-js --help [topic]"))
        assertTrue(prompt.contains("Top-level claune APIs:"))
        assertTrue(prompt.contains("observeScreen"))
        assertTrue(!prompt.contains("interface ClauneHost"))
        assertTrue(!prompt.contains("TypeScript contract for the global `claune` object"))
        assertTrue(prompt.contains("- read:"))
        assertTrue(prompt.contains("- write:"))
        assertTrue(prompt.contains("- edit:"))
        assertTrue(prompt.contains("- bash:"))
        assertTrue(prompt.contains("- finish_run:"))
        assertTrue(prompt.contains("- ask_user:"))
        assertTrue(!prompt.contains("- complete_task:"))
        assertTrue(!prompt.contains("- block_task:"))
        assertTrue(!prompt.contains("- question:"))
        assertTrue(!prompt.contains("- execute_script:"))
        assertTrue(!prompt.contains("- read_memory:"))
        assertTrue(!prompt.contains("- edit_memory:"))
        assertTrue(!prompt.contains("- read_file:"))
        assertTrue(!prompt.contains("- write_file:"))
        assertTrue(!prompt.contains("- edit_file:"))
        assertTrue(prompt.contains("Run outcome contract:"))
        assertTrue(prompt.contains("User decision contract:"))
        assertTrue(prompt.contains("The finish_run message is shown to the user and must be a final statement, not a question."))
        assertTrue(prompt.contains("Use ask_user only when a user decision is needed before continuing the same run."))
        assertTrue(prompt.contains("Workspace and bash contract:"))
        assertTrue(prompt.contains("For short one-off phone probes, run inline Claune JS with bash using `claune-js - <<'JS'`"))
        assertTrue(prompt.contains("A bash command such as `claune-js - <<'JS'` is the normal way to execute a short inline script"))
        assertTrue(prompt.contains("Use `claune-js --help` for the top-level API"))
        assertTrue(prompt.contains("Prefer interaction actions from observeScreen()."))
        assertTrue(prompt.contains("Use scrollScreen for the current page."))
        assertTrue(prompt.contains("For state-changing tasks"))
        assertTrue(!prompt.contains("For mutation requests"))
        assertTrue(prompt.contains("Available tools:"))
        assertTrue(prompt.contains("Memory directory tree:"))
        assertTrue(prompt.contains("/work/memory/apps/settings.md (52 chars)"))
        assertTrue(prompt.contains("The prompt includes only the memory file tree."))
        assertTrue(prompt.contains("Treat memory files as prior evidence, not current proof."))
        assertTrue(prompt.contains("Example wrapper-input Claune JS:"))
        assertTrue(prompt.contains("Example scrolling Claune JS:"))
        assertTrue(prompt.contains("Example interaction-action Claune JS:"))
        assertTrue(prompt.contains("claune.performAction(action.id);"))
        assertTrue(prompt.contains("claune.scrollScreen(\"down\");"))
        assertTrue(prompt.contains("claune.focusSelector({ label: \"Search\" }, 2000);"))
        assertTrue(prompt.contains("you are seeing Claune's control shell"))
    }

    @Test
    fun `memory reflection prompt uses workspace file tools instead of final json`() {
        val prompt =
            MemoryReflectionPromptBuilder.format(
                ModelTurnInput(
                    runId = "run-1",
                    persistentSessionPath = null,
                    persistentSessionId = null,
                    userMessage = "Open Settings",
                    screenObservation = screenObservation(),
                    recentEvents = emptyList(),
                ),
                ModelTurnOutput.Completion("Opened Settings."),
            )

        assertTrue(prompt.contains("If there is no durable learning, do not call tools"))
        assertTrue(prompt.contains("Use write for a new topic file or edit for one surgical update"))
        assertTrue(prompt.contains("After any memory tool call, you may send a short internal note if useful"))
        assertTrue(!prompt.contains("Return final JSON only"))
        assertTrue(!prompt.contains("Your entire final answer must be exactly the JSON object"))
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

    private fun screenObservation(screenState: ScreenState = snapshot()) = buildScreenObservation(null, screenState)

    private fun snapshot(snapshotId: String = "snapshot-1", packageName: String = "com.android.settings"): ScreenState {
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
        val root = ScreenNode(
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
        )
        return ScreenState(
            snapshotId = snapshotId,
            capturedAt = Instant.parse("2026-04-16T00:00:00Z").toString(),
            foregroundPackage = packageName,
            root = root,
        )
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
