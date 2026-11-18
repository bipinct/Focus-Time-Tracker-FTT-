package com.ftt.focustimetracker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.File
import java.time.LocalDate

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val tasks = loadTasks(applicationContext)
            val history = loadHistory(applicationContext)
            val csvContent = createCsvContent(history, tasks)

            val backupDir = File(applicationContext.getExternalFilesDir(null), "backups")
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }
            val currentYear = LocalDate.now().year
            val backupFile = File(backupDir, "focus_time_tracker_${currentYear}.csv")
            backupFile.writeText(csvContent)

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
