package com.divyanshgolyan.claune.android.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.divyanshgolyan.claune.android.BuildConfig
import com.divyanshgolyan.claune.android.app.clauneContainer
import com.divyanshgolyan.claune.android.runtime.ActionResult
import com.divyanshgolyan.claune.android.runtime.ScreenCaptureMetrics
import com.divyanshgolyan.claune.android.runtime.ScreenState
import com.divyanshgolyan.claune.android.runtime.centerPoint
import com.divyanshgolyan.claune.android.runtime.elapsedMs
import com.divyanshgolyan.claune.android.scripting.HostCallOutcome
import com.divyanshgolyan.claune.android.scripting.ProjectionProfiler
import com.divyanshgolyan.claune.android.scripting.ScriptJson
import com.divyanshgolyan.claune.android.scripting.toHostCallOutcome
import com.divyanshgolyan.claune.android.scripting.toInteractionObservationPayload
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DebugSnapshotReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEBUG) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            runCatching {
                when (intent.action) {
                    ACTION_DUMP_SNAPSHOT -> dumpSnapshot(context)
                    ACTION_TAP_POINT -> tapPoint(context, intent)
                    ACTION_TAP_BOUNDS -> tapBounds(context, intent)
                    ACTION_PRESS_BACK -> writeActionResult(context, context.clauneContainer().accessibilityBridge.pressBack())
                    ACTION_PRESS_HOME -> writeActionResult(context, context.clauneContainer().accessibilityBridge.pressHome())
                    ACTION_PROFILE_PROJECTION -> profileProjection(context)
                }
            }
            pendingResult.finish()
        }
    }

    private suspend fun dumpSnapshot(context: Context) {
        val container = context.clauneContainer()
        val screenState = container.accessibilityBridge.captureScreenState()
        val outputFile = File(context.filesDir, "debug-screen-state.json")
        outputFile.writeText(
            PRETTY_JSON.encodeToString(ScreenState.serializer(), screenState),
        )
        container.accessibilityBridge.captureDebugScreenState()?.let { debugState ->
            File(context.filesDir, "debug-raw-tree.json").writeText(
                PRETTY_JSON.encodeToString(ScreenState.serializer(), debugState),
            )
        }
    }

    private suspend fun tapPoint(context: Context, intent: Intent) {
        val x = intent.getIntExtra(EXTRA_X, -1)
        val y = intent.getIntExtra(EXTRA_Y, -1)
        if (x < 0 || y < 0) {
            writeActionResult(context, ActionResult.Blocked("Missing non-negative $EXTRA_X/$EXTRA_Y extras."))
            return
        }
        writeActionResult(context, context.clauneContainer().accessibilityBridge.tapPoint(x, y))
        dumpSnapshot(context)
    }

    private suspend fun tapBounds(context: Context, intent: Intent) {
        val left = intent.getIntExtra(EXTRA_LEFT, -1)
        val top = intent.getIntExtra(EXTRA_TOP, -1)
        val right = intent.getIntExtra(EXTRA_RIGHT, -1)
        val bottom = intent.getIntExtra(EXTRA_BOTTOM, -1)
        if (left < 0 || top < 0 || right <= left || bottom <= top) {
            writeActionResult(context, ActionResult.Blocked("Missing valid bounds extras."))
            return
        }
        val center = listOf(left, top, right, bottom).centerPoint()
        writeActionResult(context, context.clauneContainer().accessibilityBridge.tapPoint(x = center[0], y = center[1]))
        dumpSnapshot(context)
    }

    private suspend fun profileProjection(context: Context) {
        val captureStarted = System.nanoTime()
        val screenState = context.clauneContainer().accessibilityBridge.captureScreenState()
        val captureMs = elapsedMs(captureStarted)
        val profiler = ProjectionProfiler()
        val projectionStarted = System.nanoTime()
        val payload = screenState.toInteractionObservationPayload(profiler)
        val projectionMs = elapsedMs(projectionStarted)
        File(context.filesDir, "debug-projection-profile.json").writeText(
            PRETTY_JSON.encodeToString(
                DebugProjectionProfile(
                    snapshotId = screenState.snapshotId,
                    foregroundPackage = screenState.foregroundPackage,
                    captureMs = captureMs,
                    captureMetrics = screenState.metrics,
                    projectionMs = projectionMs,
                    phaseTimings = profiler.phases().map { DebugProjectionPhase(name = it.name, durationMs = it.durationMs) },
                    elementCount = payload.elements.size,
                    groupCount = payload.groups.size,
                    actionCount = payload.actions.size,
                    summaryTextLength = payload.summaryText.orEmpty().length,
                ),
            ),
        )
    }

    private fun writeActionResult(context: Context, result: ActionResult) {
        File(context.filesDir, "debug-action-result.json").writeText(
            PRETTY_JSON.encodeToString(HostCallOutcome.serializer(), result.toHostCallOutcome()),
        )
    }

    companion object {
        const val ACTION_DUMP_SNAPSHOT = "com.divyanshgolyan.claune.android.debug.DUMP_SNAPSHOT"
        const val ACTION_TAP_POINT = "com.divyanshgolyan.claune.android.debug.TAP_POINT"
        const val ACTION_TAP_BOUNDS = "com.divyanshgolyan.claune.android.debug.TAP_BOUNDS"
        const val ACTION_PRESS_BACK = "com.divyanshgolyan.claune.android.debug.PRESS_BACK"
        const val ACTION_PRESS_HOME = "com.divyanshgolyan.claune.android.debug.PRESS_HOME"
        const val ACTION_PROFILE_PROJECTION = "com.divyanshgolyan.claune.android.debug.PROFILE_PROJECTION"
        const val EXTRA_X = "x"
        const val EXTRA_Y = "y"
        const val EXTRA_LEFT = "left"
        const val EXTRA_TOP = "top"
        const val EXTRA_RIGHT = "right"
        const val EXTRA_BOTTOM = "bottom"
        private val PRETTY_JSON =
            Json(ScriptJson.codec) {
                prettyPrint = true
            }
    }
}

@Serializable
private data class DebugProjectionProfile(
    val snapshotId: String,
    val foregroundPackage: String,
    val captureMs: Long,
    val captureMetrics: ScreenCaptureMetrics?,
    val projectionMs: Long,
    val phaseTimings: List<DebugProjectionPhase>,
    val elementCount: Int,
    val groupCount: Int,
    val actionCount: Int,
    val summaryTextLength: Int,
)

@Serializable
private data class DebugProjectionPhase(val name: String, val durationMs: Long)
