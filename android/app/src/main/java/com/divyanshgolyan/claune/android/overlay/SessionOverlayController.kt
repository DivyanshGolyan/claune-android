package com.divyanshgolyan.claune.android.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
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

    fun attach(service: AccessibilityService) {
        this.service = service
        this.windowManager = service.getSystemService(WindowManager::class.java)
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
    }

    private fun render(state: SessionUiState) {
        if (!shouldShow(state)) {
            hide()
            return
        }
        if (overlayView == null) {
            show()
        }
        titleView?.text = state.activeSessionTitle ?: state.selectedSessionTitle ?: "Current session"
        bodyView?.text = state.lastAssistantText.ifBlank { state.summaryLine }.take(220)
    }

    private fun shouldShow(state: SessionUiState): Boolean =
        state.foregroundServiceRunning &&
            state.status == SessionStatus.Running &&
            !state.appInForeground &&
            service != null

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
                setPadding(0, dp(service, 6), 0, dp(service, 10))
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
                y = dp(service, 16)
                horizontalMargin = 0.04f
            }

        manager.addView(overlay, layoutParams)
        overlayView = overlay
        titleView = title
        bodyView = body
        inputView = input
    }

    private fun hide() {
        val manager = windowManager ?: return
        val overlay = overlayView ?: return
        runCatching { manager.removeView(overlay) }
        overlayView = null
        titleView = null
        bodyView = null
        inputView = null
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

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
