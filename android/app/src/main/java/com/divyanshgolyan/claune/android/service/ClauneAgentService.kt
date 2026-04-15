package com.divyanshgolyan.claune.android.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.divyanshgolyan.claune.android.R
import com.divyanshgolyan.claune.android.app.clauneContainer
import com.divyanshgolyan.claune.android.runtime.SessionStatus
import com.divyanshgolyan.claune.android.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ClauneAgentService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = when (intent?.action) {
        ACTION_STOP -> {
            stopSession()
            START_NOT_STICKY
        }

        else -> {
            val goal =
                intent?.getStringExtra(EXTRA_GOAL).orEmpty().ifBlank {
                    "Inspect the current screen and explain the next safe action."
                }
            startForeground(NOTIFICATION_ID, buildNotification(goal))
            runPrototypeTurn(goal)
            START_STICKY
        }
    }

    override fun onDestroy() {
        clauneContainer().sessionCoordinator.setForegroundServiceRunning(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun runPrototypeTurn(goal: String) {
        val container = clauneContainer()
        if (container.sessionCoordinator.uiState.value.status == SessionStatus.Running) {
            container.sessionCoordinator.logEvent("Ignored start request while a session is already running.")
            return
        }

        container.sessionCoordinator.setForegroundServiceRunning(true)
        serviceScope.launch {
            container.agentLoop.runSingleTurn(goal)
            updateNotification(container.sessionCoordinator.uiState.value.summaryLine)
        }
    }

    private fun stopSession() {
        val coordinator = clauneContainer().sessionCoordinator
        coordinator.stopSession("Stopped from the foreground notification.")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(summary: String) = NotificationCompat
        .Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.notification_title))
        .setContentText(summary)
        .setSmallIcon(android.R.drawable.stat_notify_sync)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        ).addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            getString(R.string.stop_session),
            PendingIntent.getService(
                this,
                1,
                stopIntent(this),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        ).build()

    @SuppressLint("MissingPermission")
    private fun updateNotification(summary: String) {
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(summary))
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_description)
            }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "claune-agent-session"
        private const val NOTIFICATION_ID = 42
        private const val EXTRA_GOAL = "extra_goal"
        private const val ACTION_START = "com.divyanshgolyan.claune.android.START"
        private const val ACTION_STOP = "com.divyanshgolyan.claune.android.STOP"

        fun startIntent(context: Context, goal: String) = Intent(context, ClauneAgentService::class.java)
            .setAction(ACTION_START)
            .putExtra(EXTRA_GOAL, goal)

        fun stopIntent(context: Context) = Intent(context, ClauneAgentService::class.java)
            .setAction(ACTION_STOP)
    }
}
