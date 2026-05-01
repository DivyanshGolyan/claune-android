package com.divyanshgolyan.claune.android.llm.tools

import com.divyanshgolyan.claune.android.shell.ShellExecutionResult
import com.divyanshgolyan.claune.android.shell.WorkspaceShell
import com.divyanshgolyan.claune.android.workspace.AgentWorkspace
import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pi.ai.core.TextContent

class WorkspaceShellToolTest {
    @Test
    fun `bash tool returns compact output and metadata`() = runTest {
        val dir = Files.createTempDirectory("claune-workspace").toFile()
        try {
            val shell = FakeWorkspaceShell(ShellExecutionResult(exitCode = 0, stdout = "hello\n", stderr = ""))
            val tool =
                BashToolDefinition(
                    shell = shell,
                    workspace = AgentWorkspace(dir),
                )

            val result = tool.execute("tool-call-1", BashArguments("echo hello", timeout = 5), null, null)

            assertEquals("hello\n", (result.content.single() as TextContent).text)
            assertEquals(JsonPrimitive(0), result.details.jsonObject["exitCode"])
            assertEquals(JsonPrimitive(false), result.details.jsonObject["truncated"])
            assertEquals("echo hello", shell.command)
            assertEquals(5, shell.timeoutSeconds)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `bash tool tail-truncates oversized output and spills full output into workspace`() = runTest {
        val dir = Files.createTempDirectory("claune-workspace").toFile()
        try {
            val longOutput = "a".repeat(3_100)
            val workspace = AgentWorkspace(dir).also { it.createStandardLayout() }
            val tool =
                BashToolDefinition(
                    shell = FakeWorkspaceShell(ShellExecutionResult(exitCode = 0, stdout = longOutput, stderr = "")),
                    workspace = workspace,
                    now = { Instant.ofEpochMilli(1234) },
                )

            val result = tool.execute("tool-call-1", BashArguments("generate"), null, null)
            val text = (result.content.single() as TextContent).text

            assertTrue(text.startsWith("a".repeat(3_000)))
            assertTrue(text.contains("Full output: /work/bash-output/1234.txt"))
            assertEquals(longOutput, workspace.resolve("/work/bash-output/1234.txt").readText())
            assertEquals(JsonPrimitive(true), result.details.jsonObject["truncated"])
            assertEquals(
                "/work/bash-output/1234.txt",
                result.details.jsonObject["fullOutputPath"]?.jsonPrimitive?.content,
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `bash tool returns non-zero exits as inspectable output`() = runTest {
        val dir = Files.createTempDirectory("claune-workspace").toFile()
        try {
            val shell = FakeWorkspaceShell(ShellExecutionResult(exitCode = 2, stdout = "", stderr = "missing file\n"))
            val tool =
                BashToolDefinition(
                    shell = shell,
                    workspace = AgentWorkspace(dir),
                )

            val result = tool.execute("tool-call-1", BashArguments("grep needle missing.txt"), null, null)
            val text = (result.content.single() as TextContent).text

            assertTrue(text.contains("missing file"))
            assertTrue(text.contains("Command exited with code 2"))
            assertEquals(JsonPrimitive(2), result.details.jsonObject["exitCode"])
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `bash tool does not call native-capped output full output`() = runTest {
        val dir = Files.createTempDirectory("claune-workspace").toFile()
        try {
            val workspace = AgentWorkspace(dir).also { it.createStandardLayout() }
            val tool =
                BashToolDefinition(
                    shell =
                    FakeWorkspaceShell(
                        ShellExecutionResult(
                            exitCode = 0,
                            stdout = "a".repeat(3_100),
                            stderr = "",
                            stdoutTruncated = true,
                        ),
                    ),
                    workspace = workspace,
                    now = { Instant.ofEpochMilli(5678) },
                )

            val result = tool.execute("tool-call-1", BashArguments("generate"), null, null)
            val text = (result.content.single() as TextContent).text

            assertTrue(text.contains("Captured output: /work/bash-output/5678.txt"))
            assertTrue(text.contains("Native bridge capped stdout/stderr"))
            assertTrue(!text.contains("Full output: /work/bash-output/5678.txt"))
        } finally {
            dir.deleteRecursively()
        }
    }
}

private class FakeWorkspaceShell(private val result: ShellExecutionResult) : WorkspaceShell {
    var command: String? = null
        private set

    var timeoutSeconds: Int? = null
        private set

    override suspend fun execute(command: String, timeoutSeconds: Int?): ShellExecutionResult {
        this.command = command
        this.timeoutSeconds = timeoutSeconds
        return result
    }
}
