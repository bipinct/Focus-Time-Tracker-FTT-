package com.ftt.focustimetracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class TimerService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskName = intent?.getStringExtra(EXTRA_TASK_NAME) ?: "Focus Task"
        val totalSeconds = intent?.getIntExtra(EXTRA_SECONDS, 0) ?: 0
        val action = intent?.action

        if (action == ACTION_STOP) {
            val stopBroadcast = Intent(ACTION_STOP_BROADCAST)
            sendBroadcast(stopBroadcast)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        showNotification(taskName, totalSeconds)
        return START_STICKY
    }

    private fun showNotification(taskName: String, totalSeconds: Int) {
        val channelId = "timer_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Focus Timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active focus timer tracking"
            }
            notificationManager.createNotificationChannel(channel)
        }

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
