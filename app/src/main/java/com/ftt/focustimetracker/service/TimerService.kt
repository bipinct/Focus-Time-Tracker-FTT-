package com.ftt.focustimetracker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.ftt.focustimetracker.MainActivity
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class TimerService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var currentSeconds = 0
    private var taskName = "Focus Task"
    private var isRunning = false
    private var startTimeMillis = 0L
    private var scheduler: ScheduledExecutorService? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopTimerAndService()
            return START_NOT_STICKY
        }

        taskName = intent?.getStringExtra(EXTRA_TASK_NAME) ?: taskName
        val requestedSeconds = intent?.getIntExtra(EXTRA_SECONDS, -1) ?: -1

        if (!isRunning) {
            isRunning = true
            currentSeconds = if (requestedSeconds >= 0) requestedSeconds else 0
            startTimeMillis = SystemClock.elapsedRealtime() - (currentSeconds * 1000L)
            acquireWakeLock()
            saveTimerState(true, startTimeMillis, taskName)
            startTicking()
        } else {
            if (requestedSeconds >= 0 && kotlin.math.abs(requestedSeconds - currentSeconds) > 2) {
                currentSeconds = requestedSeconds
                startTimeMillis = SystemClock.elapsedRealtime() - (currentSeconds * 1000L)
                saveTimerState(true, startTimeMillis, taskName)
            }
        }

        showNotification(taskName, currentSeconds)
        return START_STICKY
    }

    private fun saveTimerState(running: Boolean, startRealtime: Long, name: String) {
        val prefs = getSharedPreferences("ftt_timer_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("is_timer_running", running)
            .putLong("start_elapsed_realtime", startRealtime)
            .putString("running_task_name", name)
            .apply()
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "FocusTimeTracker::TimerWakeLock"
            ).apply {
                acquire(8 * 60 * 60 * 1000L)
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
        scheduler?.shutdownNow()
        scheduler = Executors.newSingleThreadScheduledExecutor().apply {
            scheduleWithFixedDelay({
                if (!isRunning) {
                    shutdown()
                    return@scheduleWithFixedDelay
                }
                val elapsedMillis = SystemClock.elapsedRealtime() - startTimeMillis
                currentSeconds = (elapsedMillis / 1000L).toInt()

                showNotification(taskName, currentSeconds)

                val tickIntent = Intent(ACTION_TICK_BROADCAST).apply {
                    putExtra(EXTRA_SECONDS, currentSeconds)
                }
                sendBroadcast(tickIntent)
            }, 0, 1, TimeUnit.SECONDS)
        }
    }

    private fun stopTimerAndService() {
        isRunning = false
        scheduler?.shutdownNow()
        scheduler = null
        releaseWakeLock()
        saveTimerState(false, 0L, "")

        val stopBroadcast = Intent(ACTION_STOP_BROADCAST)
        sendBroadcast(stopBroadcast)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        scheduler?.shutdownNow()
        scheduler = null
        releaseWakeLock()
        saveTimerState(false, 0L, "")
    }

    private fun formatServiceTime(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return String.format("%02d:%02d:%02d", h, m, s)
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

        val timeFormatted = formatServiceTime(totalSeconds)

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
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
