@file:Suppress("ktlint:standard:function-signature")

package com.divyanshgolyan.claune.android.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.divyanshgolyan.claune.android.runtime.PendingQuestionUiState
import com.divyanshgolyan.claune.android.runtime.QuestionAnswer
import com.divyanshgolyan.claune.android.runtime.QuestionAnswerKind
import com.divyanshgolyan.claune.android.runtime.QuestionPromptCoordinator
import com.divyanshgolyan.claune.android.runtime.SessionStatus
import com.divyanshgolyan.claune.android.runtime.SessionUiState
import com.divyanshgolyan.claune.android.service.ClauneAgentService
import com.divyanshgolyan.claune.android.ui.ClaunePalette
import com.divyanshgolyan.claune.android.ui.MarkdownRenderer
import io.noties.markwon.Markwon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SessionOverlayController(
    private val appContext: Context,
    private val sessionStateProvider: StateFlow<SessionUiState>,
    private val questionPromptCoordinator: QuestionPromptCoordinator,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var service: AccessibilityService? = null
    private var collectionJob: Job? = null
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var bodyView: TextView? = null
    private var actionContainer: LinearLayout? = null
    private var inputView: EditText? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null
    private var overlayBottomOffsetPx = 0
    private var stableImeBottomPx = 0
    private var overlayInputFocused = false
    private var markwon: Markwon? = null
    private var renderedState: RenderedOverlayState? = null
    private var renderedActionsKey: String? = null
    private var inputMode: InputMode? = null
    private var debugOverlayVisible = false
    private var coordinateTapSuppressionActive = false

    fun attach(service: AccessibilityService) {
        this.service = service
        this.windowManager = service.getSystemService(WindowManager::class.java)
        this.markwon = MarkdownRenderer.create(service)
        collectionJob?.cancel()
        collectionJob =
            scope.launch {
                sessionStateProvider.collectLatest { state ->
                    render(state)
                }
            }
    }

    fun detach() {
        collectionJob?.cancel()
        collectionJob = null
        hide()
        service = null
        windowManager = null
        markwon = null
    }

    fun setDebugOverlayVisible(visible: Boolean) {
        debugOverlayVisible = visible
        render(sessionStateProvider.value)
    }

    suspend fun <T> withoutOverlayForCoordinateTap(block: suspend () -> T): T {
        val shouldRestore =
            withContext(Dispatchers.Main.immediate) {
                coordinateTapSuppressionActive = true
                val wasVisible = overlayView != null
                if (wasVisible) {
                    hide()
                }
                wasVisible
            }
        if (shouldRestore) {
            delay(OVERLAY_TAP_SUPPRESSION_SETTLE_MS)
        }
        return try {
            block()
        } finally {
            if (shouldRestore) {
                delay(OVERLAY_TAP_RESTORE_DELAY_MS)
            }
            withContext(Dispatchers.Main.immediate) {
                coordinateTapSuppressionActive = false
                render(sessionStateProvider.value)
            }
        }
    }

    private fun render(state: SessionUiState) {
        if (coordinateTapSuppressionActive) {
            hide()
            return
        }
        if (!shouldShow(state)) {
            hide()
            return
        }
        if (overlayView == null) {
            show()
        }
        if (inputMode is InputMode.QuestionCustom && state.pendingQuestion?.id != inputMode?.questionId) {
            inputMode = null
        }
        val nextRenderedState =
            if (debugOverlayVisible) {
                RenderedOverlayState(
                    bodyMarkdown = "Test overlay is visible without an agent run.",
                )
            } else {
                RenderedOverlayState(
                    bodyMarkdown = state.pendingQuestion?.prompt ?: state.lastAssistantText.ifBlank { state.summaryLine },
                )
            }
        renderOverlayState(nextRenderedState)
        renderActions(state)
    }

    private fun renderOverlayState(nextState: RenderedOverlayState) {
        val previousState = renderedState
        if (previousState?.bodyMarkdown != nextState.bodyMarkdown) {
            renderBodyMarkdown(nextState.bodyMarkdown)
        }
        renderedState = nextState
    }

    private fun renderActions(state: SessionUiState) {
        val pendingQuestion = state.pendingQuestion
        val key = actionKey(state, pendingQuestion)
        if (renderedActionsKey == key) {
            return
        }
        val service = service ?: return
        val container = actionContainer ?: return
        container.removeAllViews()
        inputView = null
        overlayInputFocused = false
        stableImeBottomPx = 0
        renderedActionsKey = key

        when {
            debugOverlayVisible -> addDebugActions(container, service)
            pendingQuestion != null -> addQuestionActions(container, service, pendingQuestion)
            inputMode is InputMode.Text -> addTextInputActions(
                container = container,
                service = service,
                hint = "Message Claune",
            ) {
                submitInput()
            }
            state.status == SessionStatus.Running -> addRunningActions(container, service)
            state.status == SessionStatus.Paused ||
                state.status == SessionStatus.Completed ||
                state.status == SessionStatus.Blocked -> addIdleActions(container, service)
            else -> addTerminalActions(container, service)
        }
    }

    private fun actionKey(state: SessionUiState, pendingQuestion: PendingQuestionUiState?): String {
        if (debugOverlayVisible) {
            return "debug"
        }
        val mode = inputMode
        return buildString {
            append(state.status.name)
            append("|")
            append(pendingQuestion?.id.orEmpty())
            append("|")
            append(pendingQuestion?.options?.joinToString(separator = "\u001F").orEmpty())
            append("|")
            append(mode?.javaClass?.simpleName.orEmpty())
            append("|")
            append(mode?.questionId.orEmpty())
        }
    }

    private fun addDebugActions(container: LinearLayout, service: AccessibilityService) {
        val note =
            TextView(service).apply {
                includeFontPadding = false
                textSize = 12f
                setTextColor(ClaunePalette.InkFaintArgb)
                text = "Overlay controls are disabled in debug preview."
            }
        container.addView(note)
    }

    private fun addRunningActions(container: LinearLayout, service: AccessibilityService) {
        val row = horizontalRow(service)
        row.addView(
            secondaryButton(service, "Steer") {
                inputMode = InputMode.Text
                render(sessionStateProvider.value)
            },
            buttonLayout(service, marginEnd = 8),
        )
        row.addView(stopButton(service), buttonLayout(service))
        container.addView(row)
    }

    private fun addIdleActions(container: LinearLayout, service: AccessibilityService) {
        val row = horizontalRow(service)
        row.addView(
            secondaryButton(service, "Message") {
                inputMode = InputMode.Text
                render(sessionStateProvider.value)
            },
            buttonLayout(service, marginEnd = 8),
        )
        row.addView(stopButton(service), buttonLayout(service))
        container.addView(row)
    }

    private fun addTerminalActions(container: LinearLayout, service: AccessibilityService) {
        val row = horizontalRow(service)
        row.addView(
            secondaryButton(service, "Message") {
                inputMode = InputMode.Text
                render(sessionStateProvider.value)
            },
            buttonLayout(service, marginEnd = 8),
        )
        row.addView(stopButton(service), buttonLayout(service))
        container.addView(row)
    }

    private fun addQuestionActions(
        container: LinearLayout,
        service: AccessibilityService,
        question: PendingQuestionUiState,
    ) {
        if (inputMode is InputMode.QuestionCustom) {
            addTextInputActions(container, service, "Custom response") {
                submitQuestionCustomInput(question.id)
            }
            return
        }

        question.options.forEachIndexed { index, option ->
            container.addView(
                primaryButton(service, option) {
                    answerQuestion(
                        questionId = question.id,
                        answer =
                        QuestionAnswer(
                            text = option,
                            kind = QuestionAnswerKind.Option,
                            optionIndex = index,
                        ),
                    )
                },
                fullWidthLayout(service, topMargin = if (index == 0) 0 else 8),
            )
        }
        val row = horizontalRow(service)
        row.addView(
            secondaryButton(service, "Custom response") {
                inputMode = InputMode.QuestionCustom(question.id)
                render(sessionStateProvider.value)
            },
            buttonLayout(service, marginEnd = 8),
        )
        row.addView(stopButton(service), buttonLayout(service))
        container.addView(row, fullWidthLayout(service, topMargin = 8))
    }

    private fun addTextInputActions(
        container: LinearLayout,
        service: AccessibilityService,
        hint: String,
        submit: () -> Unit,
    ) {
        val input =
            EditText(service).apply {
                this.hint = hint
                setSingleLine()
                imeOptions = EditorInfo.IME_ACTION_SEND
                includeFontPadding = false
                textSize = 14f
                setTextColor(ClaunePalette.InkArgb)
                setHintTextColor(ClaunePalette.InkFaintArgb)
                background =
                    roundedRect(
                        context = service,
                        radiusDp = 12,
                        fillColor = ClaunePalette.BackgroundArgb,
                        strokeColor = ClaunePalette.RuleArgb,
                    )
                setPadding(dp(service, 12), dp(service, 10), dp(service, 12), dp(service, 10))
                setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_SEND) {
                        submit()
                        true
                    } else {
                        false
                    }
                }
                setOnFocusChangeListener { _, hasFocus ->
                    overlayInputFocused = hasFocus
                    if (hasFocus) {
                        overlayView?.requestApplyInsets()
                    } else {
                        stableImeBottomPx = 0
                        updateOverlayBottomOffset(0)
                    }
                }
            }
        inputView = input
        container.addView(input, fullWidthLayout(service))

        val row = horizontalRow(service)
        row.addView(stopButton(service), buttonLayout(service, marginEnd = 8))
        row.addView(primaryButton(service, "Send") { submit() }, buttonLayout(service))
        container.addView(row, fullWidthLayout(service, topMargin = 8))

        input.requestFocus()
        overlayView?.requestApplyInsets()
        service.getSystemService(InputMethodManager::class.java)?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun shouldShow(state: SessionUiState): Boolean {
        if (service == null) {
            return false
        }
        if (debugOverlayVisible) {
            return true
        }
        return state.foregroundServiceRunning
    }

    private fun show() {
        val service = service ?: return
        val manager = windowManager ?: return
        val overlay =
            LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(service, 16), dp(service, 14), dp(service, 16), dp(service, 14))
                background =
                    roundedRect(
                        context = service,
                        radiusDp = 16,
                        fillColor = ClaunePalette.BackgroundArgb,
                        strokeColor = ClaunePalette.RuleArgb,
                    )
                elevation = dp(service, 6).toFloat()
            }

        val body =
            TextView(service).apply {
                includeFontPadding = false
                textSize = 13f
                setTextColor(ClaunePalette.InkSoftArgb)
                setLinkTextColor(ClaunePalette.AccentDeepArgb)
                setPadding(0, dp(service, 6), 0, dp(service, 10))
                maxLines = 5
                ellipsize = TextUtils.TruncateAt.END
            }
        val actions =
            LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL
            }

        overlay.addView(body)
        overlay.addView(actions)

        overlay.setOnApplyWindowInsetsListener { _, insets ->
            val imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom
            updateOverlayBottomOffset(imeBottom)
            insets
        }

        overlayBottomOffsetPx = dp(service, 12)
        val layoutParams =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                x = 0
                y = overlayBottomOffsetPx
                horizontalMargin = 0.04f
                softInputMode =
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
            }

        manager.addView(overlay, layoutParams)
        overlayView = overlay
        overlayLayoutParams = layoutParams
        bodyView = body
        actionContainer = actions
        overlay.requestApplyInsets()
    }

    private fun hide() {
        val manager = windowManager ?: return
        val overlay = overlayView ?: return
        runCatching { manager.removeView(overlay) }
        overlayView = null
        bodyView = null
        actionContainer = null
        inputView = null
        overlayLayoutParams = null
        overlayBottomOffsetPx = 0
        stableImeBottomPx = 0
        overlayInputFocused = false
        renderedState = null
        renderedActionsKey = null
        inputMode = null
    }

    private fun renderBodyMarkdown(markdown: String) {
        val renderer = markwon ?: return
        val body = bodyView ?: return
        MarkdownRenderer.render(renderer, body, markdown)
    }

    private fun updateOverlayBottomOffset(imeBottom: Int) {
        val manager = windowManager ?: return
        val overlay = overlayView ?: return
        val params = overlayLayoutParams ?: return
        val effectiveImeBottom =
            when {
                imeBottom <= 0 -> {
                    stableImeBottomPx = 0
                    0
                }

                overlayInputFocused -> {
                    stableImeBottomPx = maxOf(stableImeBottomPx, imeBottom)
                    stableImeBottomPx
                }

                else -> imeBottom
            }
        val nextY = overlayBottomOffsetPx + effectiveImeBottom
        if (params.y == nextY) {
            return
        }
        params.y = nextY
        runCatching {
            manager.updateViewLayout(overlay, params)
        }
    }

    private fun submitInput() {
        val service = service ?: return
        val text = inputView?.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) {
            return
        }
        ContextCompat.startForegroundService(service, ClauneAgentService.startIntent(service, text))
        inputMode = null
        inputView?.text?.clear()
        hideKeyboard()
        render(sessionStateProvider.value)
    }

    private fun submitQuestionCustomInput(questionId: String) {
        val text = inputView?.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) {
            return
        }
        answerQuestion(
            questionId = questionId,
            answer = QuestionAnswer(text = text, kind = QuestionAnswerKind.Custom),
        )
    }

    private fun answerQuestion(questionId: String, answer: QuestionAnswer) {
        if (questionPromptCoordinator.answerPendingQuestion(questionId, answer)) {
            inputMode = null
            hideKeyboard()
            render(sessionStateProvider.value)
        }
    }

    private fun hideKeyboard() {
        val service = service ?: return
        val input = inputView ?: return
        service.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(input.windowToken, 0)
        input.clearFocus()
    }

    private fun stopButton(service: AccessibilityService): Button =
        secondaryButton(service, "Stop") {
            service.startService(ClauneAgentService.stopIntent(service))
        }

    private fun primaryButton(service: AccessibilityService, label: String, onClick: () -> Unit): Button =
        Button(service).apply {
            text = label
            setAllCaps(false)
            setTextColor(ClaunePalette.BackgroundArgb)
            backgroundTintList = ColorStateList.valueOf(ClaunePalette.AccentArgb)
            setOnClickListener { onClick() }
        }

    private fun secondaryButton(service: AccessibilityService, label: String, onClick: () -> Unit): Button =
        Button(service).apply {
            text = label
            setAllCaps(false)
            setTextColor(ClaunePalette.AccentDeepArgb)
            backgroundTintList = ColorStateList.valueOf(ClaunePalette.BackgroundArgb)
            setOnClickListener { onClick() }
        }

    private fun horizontalRow(service: AccessibilityService): LinearLayout =
        LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

    private fun fullWidthLayout(
        context: Context,
        topMargin: Int = 0,
    ): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            if (topMargin > 0) {
                this.topMargin = dp(context, topMargin)
            }
        }

    private fun buttonLayout(context: Context, marginEnd: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            if (marginEnd > 0) {
                this.marginEnd = dp(context, marginEnd)
            }
        }

    private fun roundedRect(context: Context, radiusDp: Int, fillColor: Int, strokeColor: Int): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(context, radiusDp).toFloat()
            setColor(fillColor)
            setStroke(dp(context, 1), strokeColor)
        }

    private fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private data class RenderedOverlayState(val bodyMarkdown: String)

    private sealed interface InputMode {
        val questionId: String?

        data object Text : InputMode {
            override val questionId: String? = null
        }

        data class QuestionCustom(override val questionId: String) : InputMode
    }
}

private const val OVERLAY_TAP_SUPPRESSION_SETTLE_MS = 120L
private const val OVERLAY_TAP_RESTORE_DELAY_MS = 250L
