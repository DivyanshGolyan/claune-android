package com.divyanshgolyan.claune.android.service

import android.annotation.SuppressLint
import android.app.ActivityManager
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ClauneAgentService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        val coordinator = applicationContext.clauneContainer().sessionCoordinator
        serviceScope.launch {
            coordinator.uiState.collectLatest { state ->
                if (state.foregroundServiceRunning) {
                    updateNotification(state.summaryLine)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = when (intent?.action) {
        ACTION_STOP -> {
            stopSession()
            START_NOT_STICKY
        }

        else -> {
            val message =
                intent?.getStringExtra(EXTRA_MESSAGE).orEmpty().ifBlank {
                    "Inspect the current screen and explain the next safe action."
                }
            startForeground(NOTIFICATION_ID, buildNotification(message))
            runSessionMessage(message)
            START_STICKY
        }
    }

    override fun onDestroy() {
        applicationContext.clauneContainer().sessionCoordinator.setForegroundServiceRunning(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun runSessionMessage(message: String) {
        val container = applicationContext.clauneContainer()
        container.sessionCoordinator.setForegroundServiceRunning(true)
        serviceScope.launch {
            try {
                container.agentLoop.submitUserMessage(message)
            } finally {
                val status = container.sessionCoordinator.uiState.value.status
                val sessionOpen = status != SessionStatus.Cancelled && status != SessionStatus.Idle
                container.sessionCoordinator.setForegroundServiceRunning(sessionOpen)
                if (!sessionOpen) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun stopSession() {
        val container = applicationContext.clauneContainer()
        serviceScope.launch {
            container.agentLoop.stopActiveSession("Stopped by user.")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
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
        private const val EXTRA_MESSAGE = "extra_message"
        private const val ACTION_SEND_MESSAGE = "com.divyanshgolyan.claune.android.SEND_MESSAGE"
        private const val ACTION_STOP = "com.divyanshgolyan.claune.android.STOP"

        fun startIntent(context: Context, message: String) = Intent(context, ClauneAgentService::class.java)
            .setAction(ACTION_SEND_MESSAGE)
            .putExtra(EXTRA_MESSAGE, message)

        fun stopIntent(context: Context) = Intent(context, ClauneAgentService::class.java)
            .setAction(ACTION_STOP)

        @Suppress("DEPRECATION")
        fun isRunning(context: Context): Boolean {
            val manager = context.getSystemService(ActivityManager::class.java) ?: return false
            return manager.getRunningServices(Int.MAX_VALUE).any { it.service.className == ClauneAgentService::class.java.name }
        }
    }
}
