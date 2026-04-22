package com.divyanshgolyan.claune.android.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
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
    private var markwon: Markwon? = null
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
        if (!shouldShow(state)) {
            hide()
            return
        }
        if (overlayView == null) {
            show()
        }
        if (debugOverlayVisible) {
            titleView?.text = "Debug overlay"
            renderBodyMarkdown("Test overlay is visible without an agent run.")
        } else {
            titleView?.text = state.activeSessionTitle ?: state.selectedSessionTitle ?: "Current session"
            renderBodyMarkdown(state.lastAssistantText.ifBlank { state.summaryLine })
        }
    }

    private fun shouldShow(state: SessionUiState): Boolean {
        if (service == null) {
            return false
        }
        if (debugOverlayVisible) {
            return true
        }
        return state.foregroundServiceRunning &&
            (state.status == SessionStatus.Running || state.status == SessionStatus.Paused)
    }

    private fun show() {
        val service = service ?: return
        val manager = windowManager ?: return
        val overlay =
            LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(service, 16), dp(service, 14), dp(service, 16), dp(service, 14))
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(service, 20).toFloat()
                    setColor(0xFFF8F1E3.toInt())
                    setStroke(dp(service, 1), 0xFFD4C9B0.toInt())
                }
                elevation = dp(service, 10).toFloat()
            }

        val title =
            TextView(service).apply {
                textSize = 14f
                setTextColor(0xFF1D2A22.toInt())
            }
        val body =
            TextView(service).apply {
                textSize = 13f
                setTextColor(0xFF4A574D.toInt())
                setLinkTextColor(0xFF1A3D28.toInt())
                setPadding(0, dp(service, 6), 0, dp(service, 10))
                maxLines = 5
                ellipsize = TextUtils.TruncateAt.END
            }
        val input =
            EditText(service).apply {
                hint = "Steer Claune"
                setSingleLine()
                imeOptions = EditorInfo.IME_ACTION_SEND
                setTextColor(0xFF1D2A22.toInt())
                setHintTextColor(0xFF8A8777.toInt())
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(service, 14).toFloat()
                    setColor(0xFFFFFBF5.toInt())
                    setStroke(dp(service, 1), 0xFFD4C9B0.toInt())
                }
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
                    if (hasFocus) {
                        overlay.requestApplyInsets()
                    } else {
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
                setOnClickListener {
                    service.startService(ClauneAgentService.stopIntent(service))
                }
            }
        val sendButton =
            Button(service).apply {
                text = "Send"
                setOnClickListener { submitSteerText() }
            }

        buttons.addView(stopButton)
        buttons.addView(sendButton)
        overlay.addView(title)
        overlay.addView(body)
        overlay.addView(input)
        overlay.addView(buttons)

        overlay.setOnApplyWindowInsetsListener { _, insets ->
            val imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom
            updateOverlayBottomOffset(imeBottom)
            insets
        }

        overlayBottomOffsetPx = dp(service, 16)
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
        val nextY = overlayBottomOffsetPx + imeBottom
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

    private fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
