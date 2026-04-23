package com.divyanshgolyan.claune.android.runtime

import com.divyanshgolyan.claune.android.data.local.CodingSessionStore
import com.divyanshgolyan.claune.android.data.local.InMemorySessionLogStore
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionPromptCoordinatorTest {
    @Test
    fun `ask question publishes pending state and returns selected option`() = runTest {
        val sessionCoordinator = coordinator()
        val questionCoordinator = QuestionPromptCoordinator(sessionCoordinator)

        val answer =
            async {
                questionCoordinator.askQuestion(
                    toolCallId = "tool-call-1",
                    prompt = "Which account should I use?",
                    options = listOf("Personal", "Work"),
                    signal = null,
                )
            }
        testScheduler.runCurrent()

        assertEquals(
            PendingQuestionUiState(
                id = "tool-call-1",
                prompt = "Which account should I use?",
                options = listOf("Personal", "Work"),
            ),
            sessionCoordinator.uiState.value.pendingQuestion,
        )
        assertTrue(
            questionCoordinator.answerPendingQuestion(
                "tool-call-1",
                QuestionAnswer("Work", QuestionAnswerKind.Option, optionIndex = 1),
            ),
        )

        assertEquals(QuestionAnswer("Work", QuestionAnswerKind.Option, optionIndex = 1), answer.await())
        assertNull(sessionCoordinator.uiState.value.pendingQuestion)
    }

    @Test
    fun `cancel active question clears pending state`() = runTest {
        val sessionCoordinator = coordinator()
        val questionCoordinator = QuestionPromptCoordinator(sessionCoordinator)

        val answer =
            async {
                questionCoordinator.askQuestion(
                    toolCallId = "tool-call-1",
                    prompt = "Continue?",
                    options = listOf("Yes"),
                    signal = null,
                )
            }
        testScheduler.runCurrent()

        questionCoordinator.cancelActiveQuestion("Stopped by user.")

        runCatching { answer.await() }
        assertTrue(answer.isCancelled)
        assertNull(sessionCoordinator.uiState.value.pendingQuestion)
    }

    private fun coordinator(): SessionCoordinator {
        val root = Files.createTempDirectory("claune-question").toFile()
        val store = CodingSessionStore(cwd = root.absolutePath, agentDir = File(root, "agent"))
        return SessionCoordinator(InMemorySessionLogStore(), store)
    }
}
