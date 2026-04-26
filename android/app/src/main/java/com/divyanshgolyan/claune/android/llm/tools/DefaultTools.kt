package com.divyanshgolyan.claune.android.llm.tools

import com.divyanshgolyan.claune.android.data.local.MemoryStore
import com.divyanshgolyan.claune.android.data.local.SessionLogStore
import com.divyanshgolyan.claune.android.runtime.PhoneObserver
import com.divyanshgolyan.claune.android.runtime.ScreenState
import com.divyanshgolyan.claune.android.runtime.buildScreenObservation
import com.divyanshgolyan.claune.android.scripting.ScriptExecutionRequest
import com.divyanshgolyan.claune.android.scripting.ScriptJson
import com.divyanshgolyan.claune.android.scripting.ScriptRuntime
import com.divyanshgolyan.claune.android.scripting.toPayload
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import pi.agent.core.AgentToolResult
import pi.ai.core.TextContent

internal class ExecuteScriptToolDefinition(
    private val scriptRuntime: ScriptRuntime,
    private val phoneObserver: PhoneObserver,
    private val logStore: SessionLogStore? = null,
) : ToolDefinition<String> {
    private var lastPostActionScreenState: ScreenState? = null

    override val name: String = "execute_script"
    override val label: String = "Execute Script"
    override val description: String =
        "Execute a JavaScript snippet against the live Claune host runtime. Use this tool for any phone interaction or phone-state recheck. Return value is compact JSON with the script result and a post-action screen observation."
    override val promptSnippet: String = "Run one or more phone actions or observations through the JS host runtime."
    override val promptGuidelines: List<String> = listOf(
        "Use execute_script whenever you need to act on the phone or observe fresh UI state.",
        "A single script may do multiple host calls before returning a compact summary object.",
        "After execute_script returns, use postActionObservation as the current screen summary or diff for the next step.",
    )
    override val parameters =
        objectParameters(
            properties =
            buildJsonObject {
                put(
                    "script",
                    stringProperty(
                        "A complete JavaScript snippet that uses the claune host APIs to observe the phone, act on it, and return a compact result object.",
                    ),
                )
            },
            required = listOf("script"),
        )

    override fun validateArguments(arguments: kotlinx.serialization.json.JsonObject): String = arguments.requiredString("script")

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
        val previousScreenState = lastPostActionScreenState
        val postActionScreenState = phoneObserver.captureScreenState()
        logStore?.recordScreenState(postActionScreenState)
        lastPostActionScreenState = postActionScreenState
        val postActionObservation = buildScreenObservation(previousScreenState, postActionScreenState).toPayload()
        val payload =
            ExecuteScriptToolResult(
                ok = result.ok,
                summary = result.summary,
                error = result.error,
                scriptData = result.data,
                postActionObservation = postActionObservation,
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
        objectParameters()

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
        objectParameters(
            properties =
            buildJsonObject {
                put("oldText", stringProperty("The exact unique text currently present in memory.md that should be replaced."))
                put("newText", stringProperty("The replacement Markdown text for the matched memory.md section."))
            },
            required = listOf("oldText", "newText"),
        )

    override fun validateArguments(arguments: kotlinx.serialization.json.JsonObject): EditMemoryArguments = EditMemoryArguments(
        oldText = arguments.requiredString("oldText"),
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
    val postActionObservation: com.divyanshgolyan.claune.android.scripting.ScreenObservationPayload,
)
