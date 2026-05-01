package com.divyanshgolyan.claune.android.llm.tools

import com.divyanshgolyan.claune.android.workspace.AgentWorkspace
import com.divyanshgolyan.claune.android.workspace.WorkspaceTextEdit
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import pi.ai.core.TextContent

class WorkspaceFileToolsTest {
    @Test
    fun `write file creates parents and overwrites content`() = runTest {
        val dir = Files.createTempDirectory("claune-workspace").toFile()
        try {
            val workspace = AgentWorkspace(dir)
            val tool = WriteFileToolDefinition(workspace)

            val firstResult =
                tool.execute(
                    "tool-call-1",
                    WriteFileArguments("/work/scratch/nested/notes.txt", "first"),
                    null,
                    null,
                )
            tool.execute(
                "tool-call-2",
                WriteFileArguments("scratch/nested/notes.txt", "second"),
                null,
                null,
            )

            assertEquals("second", dir.resolve("scratch/nested/notes.txt").readText())
            assertEquals("Wrote /work/scratch/nested/notes.txt (5 chars).", (firstResult.content.single() as TextContent).text)
            assertEquals(
                "/work/scratch/nested/notes.txt",
                firstResult.details.jsonObject["path"]?.jsonPrimitive?.content,
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `read file caps output and returns continuation offsets`() = runTest {
        val dir = Files.createTempDirectory("claune-workspace").toFile()
        try {
            val workspace = AgentWorkspace(dir)
            workspace.writeText("scratch/large.txt", "a".repeat(3_500))
            val tool = ReadFileToolDefinition(workspace)

            val result = tool.execute("tool-call-1", ReadFileArguments("/work/scratch/large.txt"), null, null)

            assertEquals(3_000, (result.content.single() as TextContent).text.length)
            assertEquals(JsonPrimitive(3_000), result.details.jsonObject["endOffset"])
            assertEquals(JsonPrimitive(3_500), result.details.jsonObject["totalChars"])
            assertEquals(JsonPrimitive(3_000), result.details.jsonObject["nextOffset"])
            assertEquals(JsonPrimitive(false), result.details.jsonObject["headTruncated"])
            assertEquals(JsonPrimitive(true), result.details.jsonObject["tailTruncated"])
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `read file continues from offset with requested limit`() = runTest {
        val dir = Files.createTempDirectory("claune-workspace").toFile()
        try {
            val workspace = AgentWorkspace(dir)
            workspace.writeText("scratch/notes.txt", "0123456789")
            val tool = ReadFileToolDefinition(workspace)

            val result = tool.execute("tool-call-1", ReadFileArguments("scratch/notes.txt", offset = 4, limit = 3), null, null)

            assertEquals("456", (result.content.single() as TextContent).text)
            assertEquals(JsonPrimitive(4), result.details.jsonObject["startOffset"])
            assertEquals(JsonPrimitive(7), result.details.jsonObject["endOffset"])
            assertEquals(JsonPrimitive(7), result.details.jsonObject["nextOffset"])
            assertEquals(JsonPrimitive(true), result.details.jsonObject["headTruncated"])
            assertEquals(JsonPrimitive(true), result.details.jsonObject["tailTruncated"])
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `edit file applies exact unique non-overlapping replacements`() = runTest {
        val dir = Files.createTempDirectory("claune-workspace").toFile()
        try {
            val workspace = AgentWorkspace(dir)
            workspace.writeText("scratch/notes.txt", "alpha\nbeta\ngamma\n")
            val tool = EditFileToolDefinition(workspace)

            val result =
                tool.execute(
                    "tool-call-1",
                    EditFileArguments(
                        path = "/work/scratch/notes.txt",
                        edits =
                        listOf(
                            WorkspaceTextEdit("alpha", "one"),
                            WorkspaceTextEdit("gamma", "three"),
                        ),
                    ),
                    null,
                    null,
                )

            assertEquals("one\nbeta\nthree\n", dir.resolve("scratch/notes.txt").readText())
            assertEquals("Edited /work/scratch/notes.txt (2 replacement(s)).", (result.content.single() as TextContent).text)
            assertEquals(JsonPrimitive(2), result.details.jsonObject["replacements"])
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `edit file rejects duplicate and overlapping matches`() = runTest {
        val dir = Files.createTempDirectory("claune-workspace").toFile()
        try {
            val workspace = AgentWorkspace(dir)
            workspace.writeText("scratch/notes.txt", "alpha alpha beta")

            assertFailsWithMessage("exactly once") {
                workspace.editText("scratch/notes.txt", listOf(WorkspaceTextEdit("alpha", "one")))
            }

            workspace.writeText("scratch/notes.txt", "abcdef")
            assertFailsWithMessage("must not overlap") {
                workspace.editText(
                    "scratch/notes.txt",
                    listOf(
                        WorkspaceTextEdit("abc", "one"),
                        WorkspaceTextEdit("bcd", "two"),
                    ),
                )
            }
            assertEquals("abcdef", workspace.resolve("scratch/notes.txt").readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `tool validation accepts empty write content but rejects invalid read limit`() = runTest {
        val dir = Files.createTempDirectory("claune-workspace").toFile()
        try {
            val writeTool = WriteFileToolDefinition(AgentWorkspace(dir))
            val writeArguments =
                writeTool.validateArguments(
                    kotlinx.serialization.json.buildJsonObject {
                        put("path", JsonPrimitive("/work/empty.txt"))
                        put("content", JsonPrimitive(""))
                    },
                )

            assertEquals("", writeArguments.content)

            val readTool = ReadFileToolDefinition(AgentWorkspace(dir))
            assertFailsWithMessage("limit must be positive") {
                readTool.validateArguments(
                    kotlinx.serialization.json.buildJsonObject {
                        put("path", JsonPrimitive("/work/empty.txt"))
                        put("limit", JsonPrimitive(0))
                    },
                )
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}

private suspend fun assertFailsWithMessage(expectedMessage: String, block: suspend () -> Unit) {
    try {
        block()
        fail("Expected failure containing: $expectedMessage")
    } catch (error: IllegalArgumentException) {
        assertTrue(error.message.orEmpty().contains(expectedMessage))
    } catch (error: IllegalStateException) {
        assertTrue(error.message.orEmpty().contains(expectedMessage))
    }
}
