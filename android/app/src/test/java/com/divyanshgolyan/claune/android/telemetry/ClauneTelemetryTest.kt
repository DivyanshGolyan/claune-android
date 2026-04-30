package com.divyanshgolyan.claune.android.telemetry

import com.divyanshgolyan.claune.android.runtime.ModelTurnInput
import com.divyanshgolyan.claune.android.runtime.ModelTurnOutput
import com.divyanshgolyan.claune.android.runtime.ScreenDiffStats
import com.divyanshgolyan.claune.android.runtime.ScreenObservation
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class ClauneTelemetryTest {
    @Test
    fun `trace title is derived from user prompt`() {
        assertEquals("Open Wi-Fi settings", deriveTraceTitle("  Open\nWi-Fi   settings  "))
        assertEquals("Claune run", deriveTraceTitle("   "))
        assertEquals(100, deriveTraceTitle("a".repeat(150)).length)
    }

    @Test
    fun `input messages keep full context in LangSmith content format`() {
        val request =
            buildJsonObject {
                put("model", "gpt-5.4-mini")
                put(
                    "input",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("role", "system")
                                put("content", "system prompt")
                            },
                        )
                        add(
                            buildJsonObject {
                                put("role", "user")
                                put(
                                    "content",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("type", "input_text")
                                                put("text", "look at the screen")
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            }

        val messages = inputMessagesForLangSmith(request)

        assertEquals(2, messages.size)
        assertEquals(JsonPrimitive("system"), (messages[0] as JsonObject)["role"])
        val userContent = ((messages[1] as JsonObject)["content"] as JsonArray)[0] as JsonObject
        assertEquals(JsonPrimitive("text"), userContent["type"])
        assertEquals(JsonPrimitive("look at the screen"), userContent["text"])
    }

    @Test
    fun `assistant tool-call output remains structured`() {
        val message =
            buildJsonObject {
                put("role", "assistant")
                put(
                    "content",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("type", "tool_call")
                                put("id", "call-1")
                                put("name", "execute_script")
                                put("arguments", buildJsonObject { put("script", "return 1") })
                            },
                        )
                    },
                )
            }

        val output = outputMessagesForLangSmith(message)
        val content = ((output[0] as JsonObject)["content"] as JsonArray)[0] as JsonObject

        assertEquals(JsonPrimitive("tool_call"), content["type"])
        assertEquals(JsonPrimitive("call-1"), content["id"])
        assertEquals(JsonPrimitive("execute_script"), content["name"])
        assertEquals(JsonPrimitive("return 1"), (content["args"] as JsonObject)["script"])
    }

    @Test
    fun `responses api items normalize to assistant reasoning tool call and tool result messages`() {
        val request =
            buildJsonObject {
                put(
                    "input",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("type", "reasoning")
                                put("summary", buildJsonArray {})
                            },
                        )
                        add(
                            buildJsonObject {
                                put("type", "reasoning")
                                put(
                                    "summary",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("type", "summary_text")
                                                put("text", "Need to inspect the screen.")
                                            },
                                        )
                                    },
                                )
                            },
                        )
                        add(
                            buildJsonObject {
                                put("type", "function_call")
                                put("call_id", "call-1")
                                put("name", "execute_script")
                                put("arguments", "{\"script\":\"return 1\"}")
                            },
                        )
                        add(
                            buildJsonObject {
                                put("type", "function_call_output")
                                put("call_id", "call-1")
                                put("output", "{\"ok\":true}")
                            },
                        )
                    },
                )
            }

        val messages = inputMessagesForLangSmith(request)

        assertEquals(2, messages.size)

        val reasoning = messages[0] as JsonObject
        assertEquals(JsonPrimitive("assistant"), reasoning["role"])
        assertEquals(
            JsonPrimitive("Need to inspect the screen."),
            (((reasoning["content"] as JsonArray)[0] as JsonObject)["text"]),
        )

        val toolCall = messages[0] as JsonObject
        val toolCallContent = (toolCall["content"] as JsonArray)[1] as JsonObject
        assertEquals(JsonPrimitive("assistant"), toolCall["role"])
        assertEquals(JsonPrimitive("tool_call"), toolCallContent["type"])
        assertEquals(JsonPrimitive("execute_script"), toolCallContent["name"])
        assertEquals(JsonPrimitive("return 1"), (toolCallContent["args"] as JsonObject)["script"])

        val toolResult = messages[1] as JsonObject
        val toolResultContent = (toolResult["content"] as JsonArray)[0] as JsonObject
        assertEquals(JsonPrimitive("tool"), toolResult["role"])
        assertEquals(JsonPrimitive("call-1"), toolResult["tool_call_id"])
        assertEquals(JsonPrimitive("{\"ok\":true}"), toolResultContent["text"])
    }

    @Test
    fun `usage metadata maps to LangSmith token fields`() {
        val message =
            buildJsonObject {
                put(
                    "usage",
                    buildJsonObject {
                        put("input", 10)
                        put("output", 3)
                        put("totalTokens", 13)
                        put("cacheRead", 2)
                        put("cacheWrite", 1)
                    },
                )
            }

        val usage = usageMetadataForLangSmith(message)

        assertNotNull(usage)
        assertEquals(JsonPrimitive(10), usage!!["input_tokens"])
        assertEquals(JsonPrimitive(3), usage["output_tokens"])
        assertEquals(JsonPrimitive(13), usage["total_tokens"])
        assertEquals(JsonPrimitive(2), (usage["input_token_details"] as JsonObject)["cache_read"])
    }

    @Test
    fun `native recorder creates root step llm and tool runs with parent ids and metadata`() = runTest {
        val transport = RecordingTransport()
        val ids =
            ArrayDeque(
                listOf(
                    "019d0000-0000-7000-8000-000000000001",
                    "019d0000-0000-7000-8000-000000000002",
                    "019d0000-0000-7000-8000-000000000003",
                    "019d0000-0000-7000-8000-000000000004",
                ),
            )
        val recorder =
            NativeLangSmithClauneTelemetryRecorder(
                projectName = "claune-test",
                transport = transport,
                idGenerator = { ids.removeFirst() },
            )
        val input = modelTurnInput()
        val request =
            buildJsonObject {
                put("model", "gpt-5.4-mini")
                put(
                    "input",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("role", "user")
                                put("content", "open blinkit")
                            },
                        )
                    },
                )
                put(
                    "tools",
                    buildJsonArray {
                        add(buildJsonObject { put("name", "execute_script") })
                    },
                )
            }

        recorder.startRun(input, "openai-codex", "gpt-5.4-mini", "system", "model input")
        val context =
            ClauneTelemetryContext(
                input = input,
                phase = ClauneTelemetryPhase.MAIN,
                provider = "openai-codex",
                model = "gpt-5.4-mini",
            )
        recorder.recordProviderPayload(
            context,
            "JsonObject",
            request,
        )
        recorder.recordProviderMessage(
            context,
            buildJsonObject {
                put("role", "assistant")
                put(
                    "content",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("type", "tool_call")
                                put("id", "tool-1")
                                put("name", "execute_script")
                                put("arguments", buildJsonObject { put("script", "open_app('Blinkit')") })
                            },
                        )
                    },
                )
                put(
                    "usage",
                    buildJsonObject {
                        put("input", 100)
                        put("output", 20)
                        put("totalTokens", 120)
                    },
                )
            },
        )
        recorder.recordToolCall(
            context,
            "tool-1",
            "execute_script",
            buildJsonObject { put("script", "open_app('Blinkit')") },
        )
        recorder.recordToolResult(
            context,
            "tool-1",
            "execute_script",
            false,
            buildJsonObject {
                put(
                    "content",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("type", "text")
                                put("text", "done")
                            },
                        )
                    },
                )
            },
        )
        recorder.endRun(input.runId, ModelTurnOutput.Completion("done"))
        recorder.flushForTests()

        assertEquals(4, transport.posts.size)
        val root = transport.posts[0]
        val step = transport.posts[1]
        val llm = transport.posts[2]
        val tool = transport.posts[3]

        assertEquals(JsonPrimitive("chain"), root["run_type"])
        assertEquals(JsonPrimitive("chain"), step["run_type"])
        assertEquals(JsonPrimitive("llm"), llm["run_type"])
        assertEquals(JsonPrimitive("tool"), tool["run_type"])
        assertEquals(root["id"], step["parent_run_id"])
        assertEquals(step["id"], llm["parent_run_id"])
        assertEquals(step["id"], tool["parent_run_id"])

        val llmInputs = llm["inputs"] as JsonObject
        assertNotNull(llmInputs["messages"])
        assertNotNull(llmInputs["delta_messages"])
        assertNotNull(llmInputs["tools"])
        val llmMetadata = ((llm["extra"] as JsonObject)["metadata"] as JsonObject)
        assertEquals(JsonPrimitive("openai"), llmMetadata["ls_provider"])
        assertEquals(JsonPrimitive("gpt-5.4-mini"), llmMetadata["ls_model_name"])
        assertEquals(JsonPrimitive(input.persistentSessionId), llmMetadata["thread_id"])

        val llmPatch = transport.patches.first { it.first == (llm["id"] as JsonPrimitive).contentOrNull }
        val llmOutputs = llmPatch.second["outputs"] as JsonObject
        assertNotNull(llmOutputs["messages"])
        assertEquals(JsonPrimitive(100), (llmOutputs["usage_metadata"] as JsonObject)["input_tokens"])

        val toolPatch = transport.patches.first { it.first == (tool["id"] as JsonPrimitive).contentOrNull }
        val toolOutputs = toolPatch.second["outputs"] as JsonObject
        assertEquals(JsonPrimitive("tool-1"), toolOutputs["tool_call_id"])
        assertFalse((toolOutputs["is_error"] as JsonPrimitive).contentOrNull.toBoolean())
    }

    private class RecordingTransport : LangSmithRunTransport {
        val posts = mutableListOf<JsonObject>()
        val patches = mutableListOf<Pair<String, JsonObject>>()

        override suspend fun postRun(body: JsonObject) {
            posts += body
        }

        override suspend fun patchRun(runId: String, body: JsonObject) {
            patches += runId to body
        }
    }

    private fun modelTurnInput(): ModelTurnInput = ModelTurnInput(
        runId = "run-1",
        persistentSessionPath = "/sessions/session-1",
        persistentSessionId = "session-1",
        userMessage = "open blinkit",
        screenObservation =
        ScreenObservation(
            mode = "current",
            reason = "test",
            baselineSnapshotId = "snapshot-0",
            currentSnapshotId = "snapshot-1",
            foregroundPackage = "com.test",
            stats =
            ScreenDiffStats(
                additions = 0,
                removals = 0,
                unchanged = 1,
                beforeLineCount = 1,
                afterLineCount = 1,
                changeRatio = 0.0,
            ),
        ),
        recentEvents = emptyList(),
    )
}
