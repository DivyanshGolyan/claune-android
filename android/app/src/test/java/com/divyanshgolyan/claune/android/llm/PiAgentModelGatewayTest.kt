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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PiAgentModelGatewayTest {
    @Test
    fun `prompt formatter includes request and compact phone hint`() {
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
        assertTrue(prompt.contains("Phone state hint:"))
        assertTrue(prompt.contains("lastForegroundPackage: com.android.settings"))
        assertTrue(prompt.contains("observe the phone yourself with claune-js before acting"))
        assertTrue(!prompt.contains("screenObservation:"))
        assertTrue(!prompt.contains("ref=el-1"))
        assertTrue(!prompt.contains("label=\"Wi-Fi\""))
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

        assertTrue(prompt.contains("shellContext: last known UI was Claune Android"))
    }

    @Test
    fun `system prompt includes tools memory tree and no removed tools`() {
        val prompt =
            PiAgentModelGateway.systemPromptForTests(
                """
                /work/memory/
                /work/memory/apps/
                /work/memory/apps/settings.md (52 chars)
                """.trimIndent(),
            )

        assertTrue(prompt.contains("- read:"))
        assertTrue(prompt.contains("- write:"))
        assertTrue(prompt.contains("- edit:"))
        assertTrue(prompt.contains("- bash:"))
        assertTrue(prompt.contains("- finish_run:"))
        assertTrue(prompt.contains("- ask_user:"))
        assertTrue(prompt.contains("/work/memory/apps/settings.md (52 chars)"))
        assertTrue(prompt.contains("claune.apps"))
        assertTrue(prompt.contains("claune.device.current()"))
        assertTrue(prompt.contains("claune.locator(\"*\").describe"))
        assertTrue(prompt.contains("getByPlaceholder"))
        assertTrue(prompt.contains("/work/memory/apps/com.example.app.md"))
        assertFalse(prompt.contains("Example visible-bounds fallback Claune JS:"))
        assertFalse(prompt.contains("Example raw-tree search fallback Claune JS:"))
        assertFalse(prompt.contains("claune.debug.inspectScreen({ text: \"Exact visible target\", limit: 5 });"))
        assertTrue(!prompt.contains("- complete_task:"))
        assertTrue(!prompt.contains("- block_task:"))
        assertTrue(!prompt.contains("- question:"))
        assertTrue(!prompt.contains("- execute_script:"))
        assertTrue(!prompt.contains("- read_memory:"))
        assertTrue(!prompt.contains("- edit_memory:"))
        assertTrue(!prompt.contains("- read_file:"))
        assertTrue(!prompt.contains("- write_file:"))
        assertTrue(!prompt.contains("- edit_file:"))
        assertTrue(!prompt.contains("For mutation requests"))
        assertTrue(!prompt.contains("execute_script"))
        assertTrue(!prompt.contains("read_memory"))
        assertTrue(!prompt.contains("edit_memory"))
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

        assertTrue(prompt.contains("Reply exactly `NO_MEMORY_UPDATE: <brief reason>`"))
        assertTrue(prompt.contains("/work/memory/apps/<package>.md"))
        assertTrue(prompt.contains("Known Working Patterns"))
        assertTrue(prompt.contains("Use write for a new topic file or edit for one surgical update"))
        assertTrue(prompt.contains("call write or edit before replying"))
        assertTrue(prompt.contains("MEMORY_UPDATED: <path>"))
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
