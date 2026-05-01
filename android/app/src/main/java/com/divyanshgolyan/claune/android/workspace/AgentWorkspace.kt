package com.divyanshgolyan.claune.android.workspace

import com.divyanshgolyan.claune.android.data.local.FileMutationQueue
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

private const val MAX_TREE_ENTRIES = 200
private const val MAX_TREE_CHARS = 10_000

class AgentWorkspace(private val root: File) {
    val rootDir: File = root.canonicalFile
    val modelRoot: String = MODEL_ROOT
    val memoryDir: File = rootDir.resolve("memory")
    val runsDir: File = rootDir.resolve("runs")
    val piAgentDir: File = rootDir.resolve("pi-agent")
    val scriptsDir: File = rootDir.resolve("scripts")
    val scratchDir: File = rootDir.resolve("scratch")
    val outputsDir: File = rootDir.resolve("outputs")
    val bashOutputDir: File = rootDir.resolve("bash-output")

    fun initialize() {
        createStandardLayout()
    }

    fun createStandardLayout() {
        rootDir.mkdirs()
        memoryDir.mkdirs()
        runsDir.mkdirs()
        piAgentDir.mkdirs()
        scriptsDir.mkdirs()
        scratchDir.mkdirs()
        outputsDir.mkdirs()
        bashOutputDir.mkdirs()
    }

    fun resolve(path: String): File {
        val relative = normalizeModelPath(path)
        val resolved = rootDir.resolve(relative).canonicalFile
        require(resolved == rootDir || resolved.path.startsWith(rootDir.path + File.separator)) {
            "Path escapes /work: $path"
        }
        return resolved
    }

    fun readText(path: String, offset: Int = 0, limit: Int = DEFAULT_OUTPUT_LIMIT_CHARS): WorkspaceReadResult {
        require(offset >= 0) { "Offset must be non-negative." }
        require(limit > 0) { "Limit must be positive." }
        val file = resolve(path)
        require(file.isFile) { "File does not exist: ${toModelPath(file)}" }
        val cappedLimit = minOf(limit, DEFAULT_OUTPUT_LIMIT_CHARS)
        val readWindow = file.readWindow(offset, cappedLimit)
        val startOffset = offset - readWindow.missingOffsetChars
        val value = readWindow.content
        val endOffset = startOffset + value.length
        return WorkspaceReadResult(
            path = toModelPath(file),
            content = value,
            startOffset = startOffset,
            endOffset = endOffset,
            totalChars = readWindow.totalChars,
            nextOffset = if (readWindow.hasMore) endOffset else null,
        )
    }

    suspend fun writeText(path: String, content: String): WorkspaceWriteResult {
        val file = resolve(path)
        return FileMutationQueue.withFileLock(file) {
            file.parentFile?.mkdirs()
            file.writeText(content)
            WorkspaceWriteResult(
                path = toModelPath(file),
                charsWritten = content.length,
            )
        }
    }

    suspend fun editText(path: String, edits: List<WorkspaceTextEdit>): WorkspaceEditResult {
        require(edits.isNotEmpty()) { "At least one edit is required." }
        edits.forEach { edit ->
            require(edit.oldText.isNotEmpty()) { "Edit oldText must not be empty." }
        }
        val file = resolve(path)
        return FileMutationQueue.withFileLock(file) {
            require(file.isFile) { "File does not exist: ${toModelPath(file)}" }
            val content = file.readText()
            val replacements =
                edits.map { edit ->
                    val firstIndex = content.indexOf(edit.oldText)
                    require(firstIndex >= 0) { "oldText not found for ${toModelPath(file)}." }
                    val secondIndex = content.indexOf(edit.oldText, startIndex = firstIndex + edit.oldText.length)
                    require(secondIndex < 0) { "oldText must match exactly once for ${toModelPath(file)}." }
                    WorkspaceReplacement(
                        start = firstIndex,
                        end = firstIndex + edit.oldText.length,
                        newText = edit.newText,
                    )
                }.sortedBy { it.start }

            replacements.zipWithNext().forEach { (left, right) ->
                require(left.end <= right.start) { "Edits must not overlap." }
            }

            val updated =
                replacements.asReversed().fold(content) { value, replacement ->
                    value.replaceRange(replacement.start, replacement.end, replacement.newText)
                }
            file.writeText(updated)
            WorkspaceEditResult(
                path = toModelPath(file),
                replacements = replacements.size,
                charsWritten = updated.length,
            )
        }
    }

    fun memoryTree(): String = directoryTree(memoryDir, "/work/memory")

    fun requireMemoryPath(path: String) {
        val file = resolve(path)
        val memoryRoot = memoryDir.canonicalFile
        require(file == memoryRoot || file.path.startsWith(memoryRoot.path + File.separator)) {
            "Memory reflection may only access /work/memory: $path"
        }
    }

    fun memorySignature(): String {
        if (!memoryDir.exists()) return ""
        return memoryDir
            .walkTopDown()
            .filter { it.isFile }
            .map { file ->
                val relative = file.relativeTo(memoryDir).invariantSeparatorsPath
                "$relative:${file.length()}:${file.lastModified()}"
            }
            .sorted()
            .joinToString("\n")
    }

    fun toModelPath(file: File): String {
        val canonical = file.canonicalFile
        require(canonical == rootDir || canonical.path.startsWith(rootDir.path + File.separator)) {
            "File is outside /work: ${file.path}"
        }
        if (canonical == rootDir) return MODEL_ROOT
        return MODEL_ROOT + "/" + canonical.relativeTo(rootDir).invariantSeparatorsPath
    }

    private fun normalizeModelPath(path: String): String {
        val trimmed = path.trim()
        require(trimmed.isNotBlank()) { "Path must not be blank." }
        val withoutRoot =
            when {
                trimmed == MODEL_ROOT -> ""
                trimmed.startsWith("$MODEL_ROOT/") -> trimmed.removePrefix("$MODEL_ROOT/")
                trimmed.startsWith("/") -> error("Only /work paths are supported: $path")
                else -> trimmed
            }
        return withoutRoot.ifBlank { "." }
    }

    companion object {
        const val MODEL_ROOT = "/work"
        const val DEFAULT_OUTPUT_LIMIT_CHARS = 3_000
    }
}

private fun directoryTree(root: File, modelRoot: String): String {
    if (!root.exists()) return "$modelRoot/\n"
    val allEntries =
        root.walkTopDown()
            .drop(1)
            .filter { file -> file.isDirectory || file.isFile }
            .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.relativeTo(root).invariantSeparatorsPath })
            .take(MAX_TREE_ENTRIES + 1)
            .map { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                val suffix = if (file.isDirectory) "/" else " (${file.length()} chars)"
                "$modelRoot/$relative$suffix"
            }
            .toList()
    val entries = allEntries.take(MAX_TREE_ENTRIES)
    return buildString {
        appendLine("$modelRoot/")
        entries.forEach { appendLine(it) }
        if (allEntries.size > MAX_TREE_ENTRIES) {
            appendLine("... truncated after $MAX_TREE_ENTRIES entries")
        }
    }.let { value ->
        if (value.length <= MAX_TREE_CHARS) value else value.take(MAX_TREE_CHARS).trimEnd() + "\n... truncated\n"
    }
}

data class WorkspaceReadResult(
    val path: String,
    val content: String,
    val startOffset: Int,
    val endOffset: Int,
    val totalChars: Int,
    val nextOffset: Int?,
) {
    val headTruncated: Boolean = startOffset > 0
    val tailTruncated: Boolean = nextOffset != null
}

data class WorkspaceWriteResult(val path: String, val charsWritten: Int)

data class WorkspaceTextEdit(val oldText: String, val newText: String)

data class WorkspaceEditResult(val path: String, val replacements: Int, val charsWritten: Int)

private data class WorkspaceReplacement(val start: Int, val end: Int, val newText: String)

private data class WorkspaceReadWindow(val content: String, val totalChars: Int, val hasMore: Boolean, val missingOffsetChars: Int)

private fun File.readWindow(offset: Int, limit: Int): WorkspaceReadWindow {
    InputStreamReader(inputStream(), StandardCharsets.UTF_8).use { reader ->
        var remainingOffset = offset.toLong()
        while (remainingOffset > 0) {
            val skipped = reader.skip(remainingOffset)
            if (skipped <= 0) break
            remainingOffset -= skipped
        }

        val buffer = CharArray(limit)
        val read = reader.read(buffer).coerceAtLeast(0)
        val firstCharAfterWindow = reader.read()
        val hasMore = firstCharAfterWindow != -1
        val totalChars =
            offset - remainingOffset.toInt() +
                read +
                if (hasMore) 1 + reader.countRemainingChars() else 0
        return WorkspaceReadWindow(
            content = String(buffer, 0, read),
            totalChars = totalChars,
            hasMore = hasMore,
            missingOffsetChars = remainingOffset.toInt(),
        )
    }
}

private fun InputStreamReader.countRemainingChars(): Int {
    val buffer = CharArray(DEFAULT_COUNT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read <= 0) return total
        total += read
    }
}

private const val DEFAULT_COUNT_BUFFER_SIZE = 8 * 1024
