package com.divyanshgolyan.claune.android.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.divyanshgolyan.claune.android.BuildConfig
import com.divyanshgolyan.claune.android.app.clauneContainer
import com.divyanshgolyan.claune.android.scripting.ScriptJson
import com.divyanshgolyan.claune.android.scripting.UiSnapshotPayload
import com.divyanshgolyan.claune.android.scripting.toPayload
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

class DebugSnapshotReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEBUG || intent.action != ACTION_DUMP_SNAPSHOT) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            runCatching {
                val snapshot = context.clauneContainer().accessibilityBridge.captureSnapshot()
                val outputFile = File(context.filesDir, "debug-snapshot.json")
                outputFile.writeText(
                    ScriptJson.codec.encodeToString(
                        UiSnapshotPayload.serializer(),
                        snapshot.toPayload(),
                    ),
                )
            }
            pendingResult.finish()
        }
    }

    companion object {
        const val ACTION_DUMP_SNAPSHOT = "com.divyanshgolyan.claune.android.debug.DUMP_SNAPSHOT"
    }
}
