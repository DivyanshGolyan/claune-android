package com.divyanshgolyan.claune.android.shell

import com.divyanshgolyan.claune.android.workspace.AgentWorkspace
import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class BashkitClauneJsBridgeTest {
    @Test
    fun `dash script path runs inline source from stdin`() {
        val dir = Files.createTempDirectory("claune-workspace").toFile()
        try {
            val runner = RecordingClauneJsRunner()
            val bridge = BashkitClauneJsBridge(AgentWorkspace(dir), runner)

            val result = Json.parseToJsonElement(
                bridge.run("-", arrayOf("one", "two"), "console.log(argv.join(','));\n"),
            ).jsonObject

            assertEquals(0, result["exitCode"]?.jsonPrimitive?.int)
            assertEquals("inline", runner.mode)
            assertEquals("console.log(argv.join(','));\n", runner.script)
            assertEquals(listOf("one", "two"), runner.argv)
            assertEquals("", runner.stdin)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `help flag returns runtime help without resolving a workspace path`() {
        val dir = Files.createTempDirectory("claune-workspace").toFile()
        try {
            val runner = RecordingClauneJsRunner()
            val bridge = BashkitClauneJsBridge(AgentWorkspace(dir), runner)

            val result = Json.parseToJsonElement(
                bridge.run("--help", arrayOf("observeScreen"), ""),
            ).jsonObject

            assertEquals(0, result["exitCode"]?.jsonPrimitive?.int)
            assertEquals("help", runner.mode)
            assertEquals("observeScreen", runner.topic)
        } finally {
            dir.deleteRecursively()
        }
    }
}

private class RecordingClauneJsRunner : ClauneJsRunner {
    var mode: String? = null
        private set

    var script: String? = null
        private set

    var argv: List<String>? = null
        private set

    var stdin: String? = null
        private set

    var topic: String? = null
        private set

    override suspend fun run(scriptPath: String, argv: List<String>, stdin: String): ClauneJsResult {
        mode = "file"
        script = scriptPath
        this.argv = argv
        this.stdin = stdin
        return ClauneJsResult(exitCode = 0, stdout = "", stderr = "")
    }

    override suspend fun runInline(script: String, argv: List<String>, stdin: String): ClauneJsResult {
        mode = "inline"
        this.script = script
        this.argv = argv
        this.stdin = stdin
        return ClauneJsResult(exitCode = 0, stdout = "", stderr = "")
    }

    override suspend fun help(topic: String?): ClauneJsResult {
        mode = "help"
        this.topic = topic
        return ClauneJsResult(exitCode = 0, stdout = "help", stderr = "")
    }
}
