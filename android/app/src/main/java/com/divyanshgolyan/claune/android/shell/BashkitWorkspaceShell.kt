package com.divyanshgolyan.claune.android.shell

import com.divyanshgolyan.claune.android.workspace.AgentWorkspace
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BashkitWorkspaceShell(
    private val workspace: AgentWorkspace,
    clauneJsRunner: ClauneJsRunner? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WorkspaceShell {
    private val clauneJsBridge = clauneJsRunner?.let { BashkitClauneJsBridge(workspace, it) }

    override suspend fun execute(command: String, timeoutSeconds: Int?): ShellExecutionResult = withContext(dispatcher) {
        NativeLibrary.load()
        workspace.createStandardLayout()
        val timeoutMs = timeoutSeconds?.coerceAtLeast(1)?.times(1_000L) ?: 0L
        val output =
            nativeJson.decodeFromString<NativeShellOutput>(
                nativeExecute(
                    workspace.rootDir.absolutePath,
                    command,
                    timeoutMs,
                    clauneJsBridge,
                ),
            )
        ShellExecutionResult(
            exitCode = output.exitCode,
            stdout = output.stdout,
            stderr = output.stderr,
            durationMs = output.durationMs,
            error = output.error,
            stdoutTruncated = output.stdoutTruncated,
            stderrTruncated = output.stderrTruncated,
        )
    }

    private external fun nativeExecute(
        workspaceRoot: String,
        command: String,
        timeoutMs: Long,
        clauneJsBridge: BashkitClauneJsBridge?,
    ): String

    private object NativeLibrary {
        @Volatile
        private var loaded = false

        fun load() {
            if (loaded) return
            synchronized(this) {
                if (!loaded) {
                    System.loadLibrary("bashkit_bridge")
                    loaded = true
                }
            }
        }
    }
}

internal class BashkitClauneJsBridge(private val workspace: AgentWorkspace, private val runner: ClauneJsRunner) {
    fun run(scriptPath: String, argv: Array<String>, stdin: String): String = runBlocking {
        val result =
            when (scriptPath) {
                "--help", "-h", "help" -> runner.help(argv.firstOrNull())
                "-" -> runner.runInline(script = stdin, argv = argv.toList(), stdin = "")
                else -> {
                    val resolvedScriptPath = workspace.resolve(scriptPath).absolutePath
                    runner.run(scriptPath = resolvedScriptPath, argv = argv.toList(), stdin = stdin)
                }
            }
        nativeJson.encodeToString(
            result.toNativeOutput(),
        )
    }
}

@Serializable
private data class NativeShellOutput(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long? = null,
    val error: String? = null,
    val stdoutTruncated: Boolean = false,
    val stderrTruncated: Boolean = false,
)

@Serializable
private data class NativeClauneJsOutput(val exitCode: Int, val stdout: String, val stderr: String)

private val nativeJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private fun ClauneJsResult.toNativeOutput(): NativeClauneJsOutput = NativeClauneJsOutput(
    exitCode = exitCode,
    stdout = stdout,
    stderr = stderr,
)
