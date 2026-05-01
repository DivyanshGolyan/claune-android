package com.divyanshgolyan.claune.android.shell

import com.divyanshgolyan.claune.android.scripting.HostCallRecord

interface WorkspaceShell {
    suspend fun execute(command: String, timeoutSeconds: Int? = null): ShellExecutionResult
}

data class ShellExecutionResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long? = null,
    val error: String? = null,
    val stdoutTruncated: Boolean = false,
    val stderrTruncated: Boolean = false,
)

interface ClauneJsRunner {
    suspend fun run(scriptPath: String, argv: List<String>, stdin: String): ClauneJsResult

    suspend fun runInline(script: String, argv: List<String>, stdin: String = ""): ClauneJsResult

    suspend fun help(topic: String? = null): ClauneJsResult
}

data class ClauneJsResult(val exitCode: Int, val stdout: String, val stderr: String, val hostCalls: List<HostCallRecord> = emptyList())
