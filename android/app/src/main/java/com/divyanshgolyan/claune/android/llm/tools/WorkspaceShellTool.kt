package com.divyanshgolyan.claune.android.llm.tools

import com.divyanshgolyan.claune.android.shell.WorkspaceShell
import com.divyanshgolyan.claune.android.workspace.AgentWorkspace
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import pi.agent.core.AgentToolResult
import pi.ai.core.TextContent

internal class BashToolDefinition(
    private val shell: WorkspaceShell,
    private val workspace: AgentWorkspace,
    private val now: () -> Instant = { Instant.now() },
) : ToolDefinition<BashArguments> {
    override val name: String = "bash"
    override val label: String = "bash"
    override val description: String =
        "Execute a Bashkit command in /work. Returns bounded stdout/stderr. Oversized output is saved under /work/bash-output."
    override val promptSnippet: String = "Run Bashkit shell commands in /work, including claune-js scripts."
    override val promptGuidelines: List<String> = listOf(
        "Use bash to run scripts, compose command output, grep files, and transform artifacts in /work.",
        "Use claune-js from bash when you need to observe or act on the phone; `claune-js - <<'JS'` runs short inline scripts from stdin.",
        "Bash output is bounded; read the full-output path if truncation occurs.",
    )
    override val parameters: JsonObject =
        objectParameters(
            properties =
            buildJsonObject {
                put("command", stringProperty("Bash command to execute in /work."))
                put("timeout", integerProperty("Optional timeout in seconds."))
            },
            required = listOf("command"),
        )

    override fun validateArguments(arguments: JsonObject): BashArguments = BashArguments(
        command = arguments.requiredString("command"),
        timeout = arguments["timeout"]?.jsonPrimitive?.intOrNull?.also {
            require(it > 0) { "timeout must be positive." }
        },
    )

    override suspend fun execute(
        toolCallId: String,
        params: BashArguments,
        signal: pi.ai.core.AbortSignal?,
        onUpdate: pi.agent.core.AgentToolUpdateCallback<JsonElement>?,
    ): AgentToolResult<JsonElement> {
        val result = shell.execute(params.command, params.timeout)
        val combined = buildString {
            append(result.stdout)
            if (result.stderr.isNotBlank()) {
                if (isNotEmpty() && !endsWith("\n")) appendLine()
                append(result.stderr)
            }
        }
        val rendered = combined.ifBlank { "(no output)" }
        val bounded = boundBashOutput(rendered)
        val spillPath =
            if (bounded.truncated) {
                writeFullOutput(rendered)
            } else {
                null
            }
        val text =
            buildString {
                append(bounded.text)
                if (spillPath != null) {
                    appendLine()
                    appendLine()
                    val label =
                        if (result.stdoutTruncated || result.stderrTruncated) {
                            "Captured output"
                        } else {
                            "Full output"
                        }
                    append("[Output truncated to ${AgentWorkspace.DEFAULT_OUTPUT_LIMIT_CHARS} characters. $label: $spillPath]")
                    if (result.stdoutTruncated || result.stderrTruncated) {
                        appendLine()
                        append("[Native bridge capped stdout/stderr before Kotlin received it.]")
                    }
                }
                if (result.exitCode != 0) {
                    appendLine()
                    appendLine()
                    append("Command exited with code ${result.exitCode}")
                }
            }
        val details =
            buildJsonObject {
                put("exitCode", result.exitCode)
                put("truncated", bounded.truncated)
                spillPath?.let { put("fullOutputPath", it) }
                result.durationMs?.let { put("durationMs", it) }
                result.error?.let { put("error", it) }
                put("nativeStdoutTruncated", result.stdoutTruncated)
                put("nativeStderrTruncated", result.stderrTruncated)
            }
        return AgentToolResult(
            content = listOf(TextContent(text)),
            details = details,
        )
    }

    private suspend fun writeFullOutput(output: String): String = withContext(Dispatchers.IO) {
        val path = "/work/bash-output/${now().toEpochMilli()}.txt"
        workspace.writeText(path, output)
        path
    }

    private fun boundBashOutput(output: String): BoundedOutput {
        if (output.length <= AgentWorkspace.DEFAULT_OUTPUT_LIMIT_CHARS) {
            return BoundedOutput(output, truncated = false)
        }
        return BoundedOutput(output.takeLast(AgentWorkspace.DEFAULT_OUTPUT_LIMIT_CHARS), truncated = true)
    }
}

@Serializable
internal data class BashArguments(val command: String, val timeout: Int? = null)

private data class BoundedOutput(val text: String, val truncated: Boolean)
