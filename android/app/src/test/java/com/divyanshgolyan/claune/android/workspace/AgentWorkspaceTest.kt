package com.divyanshgolyan.claune.android.workspace

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AgentWorkspaceTest {
    @Test
    fun `resolve accepts model-root and relative workspace paths`() {
        val dir = Files.createTempDirectory("claune-workspace").toFile()
        try {
            val workspace = AgentWorkspace(dir)

            assertEquals(dir.canonicalFile, workspace.resolve("/work"))
            assertEquals(dir.resolve("scratch/notes.txt").canonicalFile, workspace.resolve("/work/scratch/notes.txt"))
            assertEquals(dir.resolve("scratch/notes.txt").canonicalFile, workspace.resolve("scratch/notes.txt"))
            assertEquals("/work/scratch/notes.txt", workspace.toModelPath(dir.resolve("scratch/notes.txt")))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `resolve rejects paths outside model root`() {
        val dir = Files.createTempDirectory("claune-workspace").toFile()
        try {
            val workspace = AgentWorkspace(dir)

            assertFailsWithMessage("Only /work paths are supported") {
                workspace.resolve("/tmp/notes.txt")
            }
            assertFailsWithMessage("Path escapes /work") {
                workspace.resolve("../notes.txt")
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `initialize creates standard layout`() {
        val root = Files.createTempDirectory("claune-workspace").toFile()
        try {
            val workspace = AgentWorkspace(root)
            workspace.initialize()

            assertTrue(workspace.memoryDir.isDirectory)
            assertTrue(workspace.runsDir.isDirectory)
            assertTrue(workspace.piAgentDir.isDirectory)
            assertTrue(workspace.scriptsDir.isDirectory)
            assertTrue(workspace.scratchDir.isDirectory)
            assertTrue(workspace.outputsDir.isDirectory)
            assertTrue(workspace.bashOutputDir.isDirectory)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `initialize creates empty memory directory`() {
        val root = Files.createTempDirectory("claune-workspace").toFile()
        try {
            val workspace = AgentWorkspace(root)

            workspace.initialize()

            assertEquals("/work/memory/\n", workspace.memoryTree())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `memory tree lists files without file contents`() {
        val dir = Files.createTempDirectory("claune-workspace").toFile()
        try {
            val workspace = AgentWorkspace(dir).also { it.createStandardLayout() }
            workspace.memoryDir.resolve("apps/blinkit.md").also {
                it.parentFile?.mkdirs()
                it.writeText("Durable Blinkit fact")
            }

            val tree = workspace.memoryTree()

            assertTrue(tree.contains("/work/memory/apps/"))
            assertTrue(tree.contains("/work/memory/apps/blinkit.md (20 chars)"))
            assertTrue(!tree.contains("Durable Blinkit fact"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `read text reports character offsets and totals for unicode content`() {
        val dir = Files.createTempDirectory("claune-workspace").toFile()
        try {
            val workspace = AgentWorkspace(dir)
            workspace.resolve("scratch/unicode.txt").also {
                it.parentFile?.mkdirs()
                it.writeText("a₹cd")
            }

            val result = workspace.readText("scratch/unicode.txt", offset = 1, limit = 2)

            assertEquals("₹c", result.content)
            assertEquals(1, result.startOffset)
            assertEquals(3, result.endOffset)
            assertEquals(4, result.totalChars)
            assertEquals(3, result.nextOffset)
        } finally {
            dir.deleteRecursively()
        }
    }
}

private fun assertFailsWithMessage(expectedMessage: String, block: () -> Unit) {
    try {
        block()
        fail("Expected failure containing: $expectedMessage")
    } catch (error: IllegalArgumentException) {
        assertTrue(error.message.orEmpty().contains(expectedMessage))
    } catch (error: IllegalStateException) {
        assertTrue(error.message.orEmpty().contains(expectedMessage))
    }
}
