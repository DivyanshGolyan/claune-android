package com.divyanshgolyan.claune.android.data.local

import java.io.File
import java.time.Instant
import kotlinx.serialization.json.JsonObject
import pi.ai.core.AssistantMessage
import pi.ai.core.ImageContent
import pi.ai.core.TextContent
import pi.ai.core.ToolResultMessage
import pi.ai.core.UserContentPart
import pi.ai.core.UserMessage
import pi.ai.core.UserMessageContent
import pi.coding.agent.core.BranchSummaryEntry
import pi.coding.agent.core.CompactionEntry
import pi.coding.agent.core.CustomMessageEntry
import pi.coding.agent.core.ModelChangeEntry
import pi.coding.agent.core.SessionEntry
import pi.coding.agent.core.SessionInfo
import pi.coding.agent.core.SessionManager
import pi.coding.agent.core.SessionMessageEntry
import pi.coding.agent.core.ThinkingLevelChangeEntry
import pi.coding.agent.core.getDefaultSessionDir

data class PersistedSessionSummary(
    val path: String,
    val sessionId: String,
    val title: String,
    val preview: String,
    val modifiedAt: Instant,
)

data class PersistedSessionDetail(val summary: PersistedSessionSummary, val entries: List<PersistedSessionDetailEntry>)

data class PersistedSessionDetailEntry(
    val id: String,
    val timestamp: Instant,
    val kind: PersistedSessionDetailKind,
    val title: String,
    val body: String,
    val details: JsonObject? = null,
)

enum class PersistedSessionDetailKind {
    User,
    Assistant,
    Tool,
    Custom,
    Compaction,
    BranchSummary,
    System,
}

class CodingSessionStore(private val cwd: String, private val agentDir: File) {
    private val sessionDir: String = getDefaultSessionDir(cwd, agentDir.absolutePath)

    fun listSessions(limit: Int = 20): List<PersistedSessionSummary> = SessionManager
        .list(cwd, sessionDir)
        .take(limit)
        .map(::toSummary)

    fun createSession(name: String): PersistedSessionSummary {
        val manager = SessionManager.create(cwd, sessionDir)
        val trimmedName = name.trim()
        if (trimmedName.isNotBlank()) {
            manager.appendSessionInfo(trimmedName)
        }
        manager.ensureSessionFileWritten()
        return manager.toSummary()
    }

    fun loadSession(path: String?): PersistedSessionSummary? {
        val sessionFile = path?.existingSessionFile() ?: return null
        return runCatching {
            SessionManager.open(sessionFile.absolutePath, sessionDir = sessionDir, cwdOverride = cwd).toSummary()
        }.getOrNull()
    }

    fun sessionManager(path: String?): SessionManager {
        val sessionFile = path?.existingSessionFile()
        return when (sessionFile) {
            null -> SessionManager.create(cwd, sessionDir)
            else -> SessionManager.open(sessionFile.absolutePath, sessionDir = sessionDir, cwdOverride = cwd)
        }
    }

    fun loadSessionDetail(path: String?): PersistedSessionDetail? {
        val summary = loadSession(path) ?: return null
        val manager = sessionManager(summary.path)
        val entries =
            manager.getEntries().mapNotNull { entry ->
                entry.toDetailEntry()
            }
        return PersistedSessionDetail(summary = summary, entries = entries)
    }

    private fun toSummary(info: SessionInfo): PersistedSessionSummary {
        val firstMessage =
            if (info.messageCount == 0) {
                ""
            } else {
                userFacingText(info.firstMessage)
            }
        val fallbackTitle = info.name?.trim().orEmpty().ifBlank { firstMessage }
        val title = sessionTitle(fallbackTitle)
        val preview = firstMessage.ifBlank { title }
        return PersistedSessionSummary(
            path = info.path,
            sessionId = info.id,
            title = title,
            preview = preview,
            modifiedAt = info.modified,
        )
    }

    private fun SessionManager.toSummary(): PersistedSessionSummary {
        val sessionFile = requireNotNull(getSessionFile()) { "Persistent session file is required for Claune sessions." }
        val firstUserText =
            getEntries()
                .asSequence()
                .filterIsInstance<SessionMessageEntry>()
                .mapNotNull { entry ->
                    val userMessage = entry.message as? UserMessage ?: return@mapNotNull null
                    userMessage.content.asDisplayText()
                }.firstOrNull()
                .orEmpty()
        val title = sessionTitle(getSessionName()?.trim().orEmpty().ifBlank { firstUserText })
        return PersistedSessionSummary(
            path = sessionFile,
            sessionId = getSessionId(),
            title = title,
            preview = firstUserText.ifBlank { title },
            modifiedAt = Instant.ofEpochMilli(File(sessionFile).lastModified()),
        )
    }

    private fun String.existingSessionFile(): File? = takeIf(String::isNotBlank)
        ?.let(::File)
        ?.takeIf(File::isFile)

    private fun SessionEntry.toDetailEntry(): PersistedSessionDetailEntry? = when (this) {
        is SessionMessageEntry -> messageEntry()
        is CustomMessageEntry -> {
            if (!display) {
                null
            } else {
                PersistedSessionDetailEntry(
                    id = id,
                    timestamp = Instant.parse(timestamp),
                    kind = PersistedSessionDetailKind.Custom,
                    title = customType,
                    body = content.asDisplayText(),
                    details = details as? JsonObject,
                )
            }
        }
        is CompactionEntry ->
            PersistedSessionDetailEntry(
                id = id,
                timestamp = Instant.parse(timestamp),
                kind = PersistedSessionDetailKind.Compaction,
                title = "Context compacted",
                body = summary,
                details = details as? JsonObject,
            )
        is BranchSummaryEntry ->
            PersistedSessionDetailEntry(
                id = id,
                timestamp = Instant.parse(timestamp),
                kind = PersistedSessionDetailKind.BranchSummary,
                title = "Branch summary",
                body = summary,
                details = details as? JsonObject,
            )
        is ThinkingLevelChangeEntry ->
            PersistedSessionDetailEntry(
                id = id,
                timestamp = Instant.parse(timestamp),
                kind = PersistedSessionDetailKind.System,
                title = "Thinking level",
                body = thinkingLevel,
            )
        is ModelChangeEntry ->
            PersistedSessionDetailEntry(
                id = id,
                timestamp = Instant.parse(timestamp),
                kind = PersistedSessionDetailKind.System,
                title = "Model",
                body = "$provider / $modelId",
            )
        else -> null
    }

    private fun SessionMessageEntry.messageEntry(): PersistedSessionDetailEntry? = when (val message = message) {
        is UserMessage ->
            PersistedSessionDetailEntry(
                id = id,
                timestamp = Instant.parse(timestamp),
                kind = PersistedSessionDetailKind.User,
                title = "You",
                body = userFacingText(message.content.asDisplayText()),
            )
        is AssistantMessage ->
            PersistedSessionDetailEntry(
                id = id,
                timestamp = Instant.parse(timestamp),
                kind = PersistedSessionDetailKind.Assistant,
                title = "Claune",
                body = message.content.filterIsInstance<TextContent>().joinToString(separator = "\n") { it.text }.ifBlank {
                    message.errorMessage ?: ""
                },
            ).takeIf { it.body.isNotBlank() }
        is ToolResultMessage ->
            PersistedSessionDetailEntry(
                id = id,
                timestamp = Instant.parse(timestamp),
                kind = PersistedSessionDetailKind.Tool,
                title = "Tool result",
                body = message.content.joinToString(separator = "\n") { content ->
                    when (content) {
                        is TextContent -> content.text
                        else -> content.toString()
                    }
                },
            ).takeIf { it.body.isNotBlank() }
        else -> null
    }

    private fun UserMessageContent.asDisplayText(): String = when (this) {
        is UserMessageContent.Text -> value.trim()
        is UserMessageContent.Structured -> parts.joinToString(separator = "\n") { it.asDisplayText() }.trim()
    }

    private fun UserContentPart.asDisplayText(): String = when (this) {
        is TextContent -> text.trim()
        is ImageContent -> "[image]"
    }

    private fun sessionTitle(text: String): String = userFacingText(text)
        .lineSequence()
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        ?.take(MAX_SESSION_TITLE_CHARS)
        ?.ifBlank { null }
        ?: "Untitled session"

    private fun userFacingText(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("Goal:")) {
            return trimmed
        }
        return trimmed
            .removePrefix("Goal:")
            .substringBefore("\n\nRecent session events:")
            .substringBefore("\n\nLast known phone snapshot")
            .trim()
    }

    private companion object {
        const val MAX_SESSION_TITLE_CHARS = 96
    }
}
