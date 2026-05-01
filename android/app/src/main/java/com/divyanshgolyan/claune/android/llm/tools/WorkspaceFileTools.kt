package com.divyanshgolyan.claune.android.llm.tools

import com.divyanshgolyan.claune.android.workspace.AgentWorkspace
import com.divyanshgolyan.claune.android.workspace.WorkspaceTextEdit
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import pi.agent.core.AgentToolResult
import pi.ai.core.TextContent

internal data class ReadFileArguments(val path: String, val offset: Int = 0, val limit: Int = AgentWorkspace.DEFAULT_OUTPUT_LIMIT_CHARS)

internal data class WriteFileArguments(val path: String, val content: String)

internal data class EditFileArguments(val path: String, val edits: List<WorkspaceTextEdit>)

internal class ReadFileToolDefinition(private val workspace: AgentWorkspace, private val validatePath: (String) -> Unit = {}) :
    ToolDefinition<ReadFileArguments> {
    override val name: String = "read"
    override val label: String = "Read File"
    override val description: String =
        "Read a UTF-8 text file from /work. Results are capped at 3000 characters; use offset and limit to continue."
    override val promptSnippet: String = "Read a text file from the /work workspace."
    override val promptGuidelines: List<String> = listOf(
        "Use /work paths or relative paths within the workspace.",
        "For large files, continue from nextOffset instead of rereading the same content.",
    )
    override val parameters: JsonObject =
        objectParameters(
            properties =
            buildJsonObject {
                put("path", stringProperty("Workspace file path, for example /work/memory/apps.md or scratch/notes.txt."))
                put("offset", integerProperty("Zero-based character offset to start reading from. Defaults to 0."))
                put("limit", integerProperty("Maximum characters to return. Capped at 3000."))
            },
            required = listOf("path"),
        )

    override fun validateArguments(arguments: JsonObject): ReadFileArguments = ReadFileArguments(
        path = arguments.requiredString("path"),
        offset = arguments.optionalNonNegativeInt("offset") ?: 0,
        limit = arguments.optionalPositiveInt("limit") ?: AgentWorkspace.DEFAULT_OUTPUT_LIMIT_CHARS,
    )

    override suspend fun execute(
        toolCallId: String,
        params: ReadFileArguments,
        signal: pi.ai.core.AbortSignal?,
        onUpdate: pi.agent.core.AgentToolUpdateCallback<JsonElement>?,
    ): AgentToolResult<JsonElement> {
        validatePath(params.path)
        val result = workspace.readText(params.path, params.offset, params.limit)
        return AgentToolResult(
            content = listOf(TextContent(result.content)),
            details =
            buildJsonObject {
                put("path", result.path)
                put("content", result.content)
                put("startOffset", result.startOffset)
                put("endOffset", result.endOffset)
                put("totalChars", result.totalChars)
                result.nextOffset?.let { put("nextOffset", it) }
                put("headTruncated", result.headTruncated)
                put("tailTruncated", result.tailTruncated)
            },
        )
    }
}

internal class WriteFileToolDefinition(private val workspace: AgentWorkspace, private val validatePath: (String) -> Unit = {}) :
    ToolDefinition<WriteFileArguments> {
    override val name: String = "write"
    override val label: String = "Write File"
    override val description: String = "Write a UTF-8 text file under /work, creating parent directories and overwriting existing content."
    override val promptSnippet: String = "Write or overwrite a text file in the /work workspace."
    override val promptGuidelines: List<String> = listOf(
        "Use write only when replacing the whole file is intended.",
        "Parent directories are created automatically.",
    )
    override val parameters: JsonObject =
        objectParameters(
            properties =
            buildJsonObject {
                put("path", stringProperty("Workspace file path, for example /work/scratch/notes.txt."))
                put("content", stringProperty("Complete file content to write."))
            },
            required = listOf("path", "content"),
        )

    override fun validateArguments(arguments: JsonObject): WriteFileArguments = WriteFileArguments(
        path = arguments.requiredString("path"),
        content = arguments.requiredContentString("content"),
    )

    override suspend fun execute(
        toolCallId: String,
        params: WriteFileArguments,
        signal: pi.ai.core.AbortSignal?,
        onUpdate: pi.agent.core.AgentToolUpdateCallback<JsonElement>?,
    ): AgentToolResult<JsonElement> {
        validatePath(params.path)
        val result = workspace.writeText(params.path, params.content)
        val message = "Wrote ${result.path} (${result.charsWritten} chars)."
        return AgentToolResult(
            content = listOf(TextContent(message)),
            details =
            buildJsonObject {
                put("path", result.path)
                put("charsWritten", result.charsWritten)
            },
        )
    }
}

internal class EditFileToolDefinition(private val workspace: AgentWorkspace, private val validatePath: (String) -> Unit = {}) :
    ToolDefinition<EditFileArguments> {
    override val name: String = "edit"
    override val label: String = "Edit File"
    override val description: String =
        "Apply exact unique non-overlapping text replacements to a UTF-8 text file under /work."
    override val promptSnippet: String = "Edit a workspace text file using exact oldText/newText replacements."
    override val promptGuidelines: List<String> = listOf(
        "Read the file first, then use exact oldText strings from the current file.",
        "Each oldText must occur exactly once, and replacements must not overlap.",
    )
    override val parameters: JsonObject =
        objectParameters(
            properties =
            buildJsonObject {
                put("path", stringProperty("Workspace file path, for example /work/scratch/notes.txt."))
                put(
                    "edits",
                    buildJsonObject {
                        put("type", JsonPrimitive("array"))
                        put("description", JsonPrimitive("Exact text replacements to apply atomically."))
                        put(
                            "items",
                            objectParameters(
                                properties =
                                buildJsonObject {
                                    put("oldText", stringProperty("Exact text currently present in the file."))
                                    put("newText", stringProperty("Replacement text."))
                                },
                                required = listOf("oldText", "newText"),
                            ),
                        )
                    },
                )
            },
            required = listOf("path", "edits"),
        )

    override fun validateArguments(arguments: JsonObject): EditFileArguments = EditFileArguments(
        path = arguments.requiredString("path"),
        edits = arguments.requiredEdits("edits"),
    )

    override suspend fun execute(
        toolCallId: String,
        params: EditFileArguments,
        signal: pi.ai.core.AbortSignal?,
        onUpdate: pi.agent.core.AgentToolUpdateCallback<JsonElement>?,
    ): AgentToolResult<JsonElement> {
        validatePath(params.path)
        val result = workspace.editText(params.path, params.edits)
        val message = "Edited ${result.path} (${result.replacements} replacement(s))."
        return AgentToolResult(
            content = listOf(TextContent(message)),
            details =
            buildJsonObject {
                put("path", result.path)
                put("replacements", result.replacements)
                put("charsWritten", result.charsWritten)
            },
        )
    }
}

private fun JsonObject.requiredContentString(name: String): String = this[name]?.jsonPrimitive?.contentOrNull ?: error("Missing $name")

private fun JsonObject.optionalNonNegativeInt(name: String): Int? =
    optionalInt(name)?.also { require(it >= 0) { "$name must be non-negative." } }

private fun JsonObject.optionalPositiveInt(name: String): Int? = optionalInt(name)?.also { require(it > 0) { "$name must be positive." } }

private fun JsonObject.optionalInt(name: String): Int? {
    val value = this[name] ?: return null
    return value.jsonPrimitive.contentOrNull?.toIntOrNull() ?: error("$name must be an integer.")
}

private fun JsonObject.requiredEdits(name: String): List<WorkspaceTextEdit> {
    val edits =
        this[name]
            ?.jsonArray
            ?.map { element ->
                val edit = element.jsonObject
                WorkspaceTextEdit(
                    oldText = edit.requiredContentString("oldText"),
                    newText = edit.requiredContentString("newText"),
                )
            }
            ?: error("Missing $name")
    require(edits.isNotEmpty()) { "$name must contain at least one edit." }
    return edits
}
