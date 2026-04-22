package com.divyanshgolyan.claune.android.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.graphics.Typeface
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SessionOverlayController(
    private val appContext: Context,
    private val sessionStateProvider: kotlinx.coroutines.flow.StateFlow<SessionUiState>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var service: AccessibilityService? = null
    private var collectionJob: Job? = null
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var titleView: TextView? = null
    private var bodyView: TextView? = null
    private var inputView: EditText? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null
    private var overlayBottomOffsetPx = 0
    private var stableImeBottomPx = 0
    private var overlayInputFocused = false
    private var markwon: Markwon? = null
    private var renderedState: RenderedOverlayState? = null
    private var debugOverlayVisible = false

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

    private fun render(state: SessionUiState) {
        val show = shouldShow(state)
        if (!show) {
            hide()
            return
        }
        if (overlayView == null) {
            show()
        }
        val nextRenderedState =
            if (debugOverlayVisible) {
                RenderedOverlayState(
                    title = "Debug overlay",
                    bodyMarkdown = "Test overlay is visible without an agent run.",
                    inputHint = "Steer Claune",
                )
            } else {
                RenderedOverlayState(
                    title = state.activeSessionTitle ?: state.selectedSessionTitle ?: "Current session",
                    bodyMarkdown = state.lastAssistantText.ifBlank { state.summaryLine },
                    inputHint = state.overlayInputHint(),
                )
            }
        renderOverlayState(nextRenderedState)
    }

    private fun renderOverlayState(nextState: RenderedOverlayState) {
        val previousState = renderedState
        if (previousState?.title != nextState.title) {
            titleView?.text = nextState.title
        }
        if (previousState?.inputHint != nextState.inputHint) {
            inputView?.hint = nextState.inputHint
        }
        if (previousState?.bodyMarkdown != nextState.bodyMarkdown) {
            renderBodyMarkdown(nextState.bodyMarkdown)
        }
        renderedState = nextState
    }

    private fun SessionUiState.overlayInputHint(): String = when (status) {
        SessionStatus.Running -> "Steer Claune"
        SessionStatus.Paused -> "Reply to Claune"
        SessionStatus.Completed,
        SessionStatus.Blocked,
        -> "Tell Claune what next"
        else -> "Tell Claune what next"
    }

    private fun shouldShow(state: SessionUiState): Boolean {
        if (service == null) {
            return false
        }
        if (debugOverlayVisible) {
            return true
        }
        return state.foregroundServiceRunning && state.status in OPEN_OVERLAY_STATUSES
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

        val title =
            TextView(service).apply {
                includeFontPadding = false
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ClaunePalette.InkArgb)
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
        val input =
            EditText(service).apply {
                hint = "Steer Claune"
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
                        submitSteerText()
                        true
                    } else {
                        false
                    }
                }
                setOnFocusChangeListener { _, hasFocus ->
                    overlayInputFocused = hasFocus
                    if (hasFocus) {
                        overlay.requestApplyInsets()
                    } else {
                        stableImeBottomPx = 0
                        updateOverlayBottomOffset(0)
                    }
                }
            }
        val buttons =
            LinearLayout(service).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                setPadding(0, dp(service, 10), 0, 0)
            }
        val stopButton =
            Button(service).apply {
                text = "Stop"
                setAllCaps(false)
                setTextColor(ClaunePalette.AccentDeepArgb)
                backgroundTintList = ColorStateList.valueOf(ClaunePalette.BackgroundArgb)
                setOnClickListener {
                    service.startService(ClauneAgentService.stopIntent(service))
                }
            }
        val sendButton =
            Button(service).apply {
                text = "Send"
                setAllCaps(false)
                setTextColor(ClaunePalette.BackgroundArgb)
                backgroundTintList = ColorStateList.valueOf(ClaunePalette.AccentArgb)
                setOnClickListener { submitSteerText() }
            }

        buttons.addView(
            stopButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginEnd = dp(service, 8)
            },
        )
        buttons.addView(
            sendButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        overlay.addView(title)
        overlay.addView(body)
        overlay.addView(input)
        overlay.addView(buttons)

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
        titleView = title
        bodyView = body
        inputView = input
        overlay.requestApplyInsets()
    }

    private fun hide() {
        val manager = windowManager ?: return
        val overlay = overlayView ?: return
        runCatching { manager.removeView(overlay) }
        overlayView = null
        titleView = null
        bodyView = null
        inputView = null
        overlayLayoutParams = null
        overlayBottomOffsetPx = 0
        stableImeBottomPx = 0
        overlayInputFocused = false
        renderedState = null
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

    private fun submitSteerText() {
        val service = service ?: return
        val text = inputView?.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) {
            return
        }
        ContextCompat.startForegroundService(service, ClauneAgentService.startIntent(service, text))
        inputView?.text?.clear()
        val imm = service.getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(inputView?.windowToken, 0)
    }

    private fun roundedRect(context: Context, radiusDp: Int, fillColor: Int, strokeColor: Int): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(context, radiusDp).toFloat()
            setColor(fillColor)
            setStroke(dp(context, 1), strokeColor)
        }

    private fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private data class RenderedOverlayState(val title: String, val bodyMarkdown: String, val inputHint: String)

    private companion object {
        private val OPEN_OVERLAY_STATUSES =
            setOf(
                SessionStatus.Running,
                SessionStatus.Paused,
                SessionStatus.Completed,
                SessionStatus.Blocked,
            )
    }
}
