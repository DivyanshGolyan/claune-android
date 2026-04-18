package com.divyanshgolyan.claune.android.llm.tools

import com.divyanshgolyan.claune.android.data.local.MemoryStore
import com.divyanshgolyan.claune.android.runtime.PhoneObserver
import com.divyanshgolyan.claune.android.scripting.ScriptExecutionRequest
import com.divyanshgolyan.claune.android.scripting.ScriptJson
import com.divyanshgolyan.claune.android.scripting.ScriptRuntime
import com.divyanshgolyan.claune.android.scripting.UiSnapshotPayload
import com.divyanshgolyan.claune.android.scripting.toPayload
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import pi.agent.core.AgentToolResult
import pi.ai.core.TextContent

internal class ExecuteScriptToolDefinition(private val scriptRuntime: ScriptRuntime, private val phoneObserver: PhoneObserver) :
    ToolDefinition<String> {
    override val name: String = "execute_script"
    override val label: String = "Execute Script"
    override val description: String =
        "Execute a JavaScript snippet against the live Claune host runtime. Use this tool for any phone interaction or phone-state recheck. Return value is JSON text with script result and a post-action snapshot, which is the current truth after the script finishes."
    override val promptSnippet: String = "Run one or more phone actions or observations through the JS host runtime."
    override val promptGuidelines: List<String> = listOf(
        "Use execute_script whenever you need to act on the phone or observe fresh UI state.",
        "A single script may do multiple host calls before returning a compact summary object.",
        "After execute_script returns, trust postActionSnapshot as the current screen state for the next step.",
    )
    override val parameters =
        buildJsonObject {
            put("type", JsonPrimitive("object"))
            put(
                "properties",
                buildJsonObject {
                    put(
                        "script",
                        buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put(
                                "description",
                                JsonPrimitive(
                                    "A complete JavaScript snippet that uses the claune host APIs to observe the phone, act on it, and return a compact result object.",
                                ),
                            )
                        },
                    )
                },
            )
            put("required", JsonArray(listOf(JsonPrimitive("script"))))
        }

    override fun validateArguments(arguments: kotlinx.serialization.json.JsonObject): String =
        arguments["script"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: error("Missing script")

    override suspend fun execute(
        toolCallId: String,
        params: String,
        signal: pi.ai.core.AbortSignal?,
        onUpdate: pi.agent.core.AgentToolUpdateCallback<JsonElement>?,
    ): AgentToolResult<JsonElement> {
        val result =
            scriptRuntime.execute(
                ScriptExecutionRequest(
                    script = params,
                    source = "pi_agent",
                ),
            )
        val postActionSnapshot = phoneObserver.captureSnapshot().toPayload()
        val payload =
            ExecuteScriptToolResult(
                ok = result.ok,
                summary = result.summary,
                error = result.error,
                scriptData = result.data,
                postActionSnapshot = postActionSnapshot,
            )
        val encoded = ScriptJson.codec.encodeToString(ExecuteScriptToolResult.serializer(), payload)
        return AgentToolResult(
            content = listOf(TextContent(encoded)),
            details = ScriptJson.codec.encodeToJsonElement(ExecuteScriptToolResult.serializer(), payload),
        )
    }
}

internal class ReadMemoryToolDefinition(private val memoryStore: MemoryStore) : ToolDefinition<Unit> {
    override val name: String = "read_memory"
    override val label: String = "Read Memory"
    override val description: String = "Read the current contents of memory.md."
    override val promptSnippet: String = "Read the current agent memory file before updating or relying on stored facts."
    override val promptGuidelines: List<String> = listOf(
        "Use read_memory before edit_memory so you preserve useful existing memory.",
    )
    override val parameters =
        buildJsonObject {
            put("type", JsonPrimitive("object"))
            put("properties", buildJsonObject {})
        }

    override fun validateArguments(arguments: kotlinx.serialization.json.JsonObject) {
        Unit
    }

    override suspend fun execute(
        toolCallId: String,
        params: Unit,
        signal: pi.ai.core.AbortSignal?,
        onUpdate: pi.agent.core.AgentToolUpdateCallback<JsonElement>?,
    ): AgentToolResult<JsonElement> {
        val content = memoryStore.read()
        val details =
            buildJsonObject {
                put("content", JsonPrimitive(content))
            }
        return AgentToolResult(
            content = listOf(TextContent(content)),
            details = details,
        )
    }
}

internal class EditMemoryToolDefinition(private val memoryStore: MemoryStore) : ToolDefinition<EditMemoryArguments> {
    override val name: String = "edit_memory"
    override val label: String = "Edit Memory"
    override val description: String =
        "Update memory.md by replacing one exact unique string with new Markdown text. Use this for surgical memory updates."
    override val promptSnippet: String = "Edit memory.md by replacing one exact unique string when you learn a durable fact."
    override val promptGuidelines: List<String> = listOf(
        "Only write durable facts to memory.md; avoid transient UI state and one-off results.",
        "Read memory.md first, then replace one exact unique block with edit_memory instead of rewriting the whole file.",
        "To append a new memory bullet, replace the exact header block `# Claune Memory\\n\\n` with that header plus the new bullet below it.",
    )
    override val parameters =
        buildJsonObject {
            put("type", JsonPrimitive("object"))
            put(
                "properties",
                buildJsonObject {
                    put(
                        "oldText",
                        buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put(
                                "description",
                                JsonPrimitive("The exact unique text currently present in memory.md that should be replaced."),
                            )
                        },
                    )
                    put(
                        "newText",
                        buildJsonObject {
                            put("type", JsonPrimitive("string"))
                            put(
                                "description",
                                JsonPrimitive("The replacement Markdown text for the matched memory.md section."),
                            )
                        },
                    )
                },
            )
            put("required", JsonArray(listOf(JsonPrimitive("oldText"), JsonPrimitive("newText"))))
        }

    override fun validateArguments(arguments: kotlinx.serialization.json.JsonObject): EditMemoryArguments = EditMemoryArguments(
        oldText =
        arguments["oldText"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
            ?: error("Missing oldText"),
        newText = arguments["newText"]?.jsonPrimitive?.content ?: error("Missing newText"),
    )

    override suspend fun execute(
        toolCallId: String,
        params: EditMemoryArguments,
        signal: pi.ai.core.AbortSignal?,
        onUpdate: pi.agent.core.AgentToolUpdateCallback<JsonElement>?,
    ): AgentToolResult<JsonElement> {
        memoryStore.edit(params.oldText, params.newText)
        val updated = memoryStore.read()
        val details =
            buildJsonObject {
                put("content", JsonPrimitive(updated))
            }
        return AgentToolResult(
            content = listOf(TextContent("Updated memory.md.")),
            details = details,
        )
    }
}

@Serializable
internal data class EditMemoryArguments(val oldText: String, val newText: String)

@Serializable
internal data class ExecuteScriptToolResult(
    val ok: Boolean,
    val summary: String,
    val error: String? = null,
    val scriptData: JsonElement? = null,
    val postActionSnapshot: UiSnapshotPayload,
)
