package com.ftt.focustimetracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class TimerService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentSeconds = 0
    private var taskName = "Focus Task"
    private var isRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        taskName = intent?.getStringExtra(EXTRA_TASK_NAME) ?: "Focus Task"
        currentSeconds = intent?.getIntExtra(EXTRA_SECONDS, 0) ?: 0
        val action = intent?.action

        if (action == ACTION_STOP) {
            stopTimerAndService()
            return START_NOT_STICKY
        }

        if (!isRunning) {
            isRunning = true
            acquireWakeLock()
            startTicking()
        }

        showNotification(taskName, currentSeconds)
        return START_STICKY
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "FocusTimeTracker::TimerWakeLock"
            ).apply {
                acquire(4 * 60 * 60 * 1000L) // 4 hours max safety timeout
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        wakeLock = null
    }

    private fun startTicking() {
        serviceScope.launch {
            while (isRunning) {
                delay(1000)
                currentSeconds++

                // Update notification
                showNotification(taskName, currentSeconds)

                // Broadcast tick to MainActivity
                val tickIntent = Intent(ACTION_TICK_BROADCAST).apply {
                    putExtra(EXTRA_SECONDS, currentSeconds)
                }
                sendBroadcast(tickIntent)
            }
        }
    }

    private fun stopTimerAndService() {
        isRunning = false
        serviceJob.cancelChildren()
        releaseWakeLock()

        val stopBroadcast = Intent(ACTION_STOP_BROADCAST)
        sendBroadcast(stopBroadcast)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceJob.cancel()
        releaseWakeLock()
    }

    private fun showNotification(taskName: String, totalSeconds: Int) {
        val channelId = "timer_channel"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId,
            "Focus Timer",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active focus timer tracking"
        }
        notificationManager.createNotificationChannel(channel)

        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, TimerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeFormatted = formatTime(totalSeconds)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Focusing: $taskName")
            .setContentText("Elapsed: $timeFormatted")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()

        startForeground(1, notification)
    }

    companion object {
        const val EXTRA_TASK_NAME = "extra_task_name"
        const val EXTRA_SECONDS = "extra_seconds"
        const val ACTION_STOP = "action_stop"
        const val ACTION_STOP_BROADCAST = "com.ftt.focustimetracker.ACTION_STOP_TIMER"
        const val ACTION_TICK_BROADCAST = "com.ftt.focustimetracker.ACTION_TICK_TIMER"

        fun start(context: Context, taskName: String, seconds: Int) {
            val intent = Intent(context, TimerService::class.java).apply {
                putExtra(EXTRA_TASK_NAME, taskName)
                putExtra(EXTRA_SECONDS, seconds)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
