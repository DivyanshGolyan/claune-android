@file:Suppress("ktlint:standard:function-signature")

package com.divyanshgolyan.claune.android.runtime

import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import pi.ai.core.AbortSignal

interface UserQuestionPrompter {
    suspend fun askQuestion(toolCallId: String, prompt: String, options: List<String>, signal: AbortSignal?): QuestionAnswer
}

class QuestionPromptCoordinator(private val sessionCoordinator: SessionCoordinator) : UserQuestionPrompter {
    private val lock = Any()
    private var activeQuestion: ActiveQuestion? = null

    override suspend fun askQuestion(
        toolCallId: String,
        prompt: String,
        options: List<String>,
        signal: AbortSignal?,
    ): QuestionAnswer {
        val questionId = toolCallId.ifBlank { "question-${UUID.randomUUID()}" }
        val deferred = CompletableDeferred<QuestionAnswer>()
        val question =
            ActiveQuestion(
                id = questionId,
                deferred = deferred,
            )
        signal?.throwIfAborted()
        val superseded =
            synchronized(lock) {
                val previous = activeQuestion
                activeQuestion = question
                previous
            }
        superseded?.deferred?.cancel(CancellationException("Question was superseded."))
        sessionCoordinator.setPendingQuestion(PendingQuestionUiState(questionId, prompt, options))
        sessionCoordinator.logEvent("Waiting for user response.")

        val removeAbortListener =
            signal?.addListener {
                cancelActiveQuestion("Question was cancelled.")
            }
        return try {
            deferred.await()
        } finally {
            removeAbortListener?.invoke()
            clearIfActive(questionId)
        }
    }

    fun answerPendingQuestion(questionId: String, answer: QuestionAnswer): Boolean {
        val question =
            synchronized(lock) {
                activeQuestion.takeIf { it?.id == questionId }
            } ?: return false
        return question.deferred.complete(answer)
    }

    fun cancelActiveQuestion(reason: String) {
        val question =
            synchronized(lock) {
                activeQuestion.also { activeQuestion = null }
            }
        question?.deferred?.cancel(CancellationException(reason))
        sessionCoordinator.clearPendingQuestion()
    }

    private fun clearIfActive(questionId: String) {
        val shouldClear =
            synchronized(lock) {
                if (activeQuestion?.id == questionId) {
                    activeQuestion = null
                    true
                } else {
                    false
                }
            }
        if (shouldClear) {
            sessionCoordinator.clearPendingQuestion(questionId)
        }
    }

    private data class ActiveQuestion(val id: String, val deferred: CompletableDeferred<QuestionAnswer>)
}

private fun AbortSignal.throwIfAborted() {
    if (aborted) {
        throw CancellationException("Question was cancelled.")
    }
}
