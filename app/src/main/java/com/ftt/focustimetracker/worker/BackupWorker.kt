package com.ftt.focustimetracker.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ftt.focustimetracker.data.BackupManager
import com.ftt.focustimetracker.data.StorageManager
import java.io.File
import java.time.LocalDate

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val history = StorageManager.loadHistory(applicationContext)
            val csvContent = BackupManager.createCsvContent(history)

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
