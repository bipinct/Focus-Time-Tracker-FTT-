package com.ftt.focustimetracker.data

import android.content.Context
import androidx.work.*
import com.ftt.focustimetracker.model.HistoryRecord
import com.ftt.focustimetracker.model.RestoreData
import com.ftt.focustimetracker.model.Task
import com.ftt.focustimetracker.ui.theme.presetColors
import com.ftt.focustimetracker.ui.theme.formatTime
import com.ftt.focustimetracker.worker.BackupWorker
import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit

object BackupManager {
    fun createBackupJson(tasks: List<Task>, history: List<HistoryRecord>): String {
        val rootObj = JSONObject().apply {
            put("version", 1)
            put("timestamp", System.currentTimeMillis())

            val tasksArray = JSONArray()
            for (task in tasks) {
                tasksArray.put(JSONObject().apply {
                    put("id", task.id)
                    put("name", task.name)
                    put("colorInt", task.colorInt)
                    put("iconName", task.iconName)
                })
            }
            put("tasks", tasksArray)

            val historyArray = JSONArray()
            for (record in history) {
                historyArray.put(JSONObject().apply {
                    put("id", record.id)
                    put("date", record.date)
                    put("taskName", record.taskName)
                    put("totalSeconds", record.totalSeconds)
                })
            }
            put("history", historyArray)
        }
        return rootObj.toString(2)
    }

    fun parseBackupJson(jsonString: String): RestoreData? {
        try {
            val rootObj = JSONObject(jsonString)
            val tasksList = mutableListOf<Task>()
            val tasksArray = rootObj.optJSONArray("tasks")
            if (tasksArray != null) {
                for (i in 0 until tasksArray.length()) {
                    val obj = tasksArray.getJSONObject(i)
                    tasksList.add(
                        Task(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            name = obj.optString("name", "Task"),
                            totalSeconds = 0,
                            isRunning = false,
                            colorInt = obj.optInt("colorInt", 0xFF4F46E5.toInt()),
                            iconName = obj.optString("iconName", "Star")
                        )
                    )
                }
            }

            val historyList = mutableListOf<HistoryRecord>()
            val historyArray = rootObj.optJSONArray("history")
            if (historyArray != null) {
                for (i in 0 until historyArray.length()) {
                    val obj = historyArray.getJSONObject(i)
                    historyList.add(
                        HistoryRecord(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            date = obj.optString("date", StorageManager.getTodayString()),
                            taskName = obj.optString("taskName", "Task"),
                            totalSeconds = obj.optInt("totalSeconds", 0)
                        )
                    )
                }
            }
            return RestoreData(tasksList, historyList)
        } catch (e: Exception) {
            return null
        }
    }

    fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var inQuotes = false
        val sb = StringBuilder()
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    result.add(sb.toString())
                    sb.clear()
                }
                else -> {
                    sb.append(c)
                }
            }
            i++
        }
        result.add(sb.toString())
        return result
    }

    fun parseDurationToSeconds(durationStr: String): Int {
        try {
            val parts = durationStr.split(":")
            if (parts.size == 3) {
                val h = parts[0].toIntOrNull() ?: 0
                val m = parts[1].toIntOrNull() ?: 0
                val s = parts[2].toIntOrNull() ?: 0
                return h * 3600 + m * 60 + s
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return durationStr.toIntOrNull() ?: 0
    }

    fun parseBackupCsv(csvString: String): RestoreData {
        val lines = csvString.lines()
        val tasksMap = mutableMapOf<String, Task>()
        val historyList = mutableListOf<HistoryRecord>()

        val presetColorArray = presetColors.map { it.toArgb() }
        var colorIndex = 0

        for (i in 1 until lines.size) { // skip header
            val line = lines[i].trim()
            if (line.isEmpty()) continue

            val tokens = parseCsvLine(line)
            if (tokens.size >= 3) {
                val date = tokens[0].trim()
                val taskName = tokens[1].trim()
                val durationStr = tokens[2].trim()

                val totalSeconds = parseDurationToSeconds(durationStr)

                historyList.add(
                    HistoryRecord(
                        date = date,
                        taskName = taskName,
                        totalSeconds = totalSeconds
                    )
                )

                if (!tasksMap.containsKey(taskName)) {
                    val color = presetColorArray[colorIndex % presetColorArray.size]
                    colorIndex++
                    tasksMap[taskName] = Task(
                        name = taskName,
                        totalSeconds = 0,
                        colorInt = color,
                        iconName = "Star"
                    )
                }
            }
        }

        return RestoreData(tasksMap.values.toList(), historyList)
    }

    fun createCsvContent(history: List<HistoryRecord>): String {
        val sb = StringBuilder()
        sb.append("date,task name,duration\n")
        for (record in history) {
            val safeName = "\"${record.taskName.replace("\"", "\"\"")}\""
            sb.append("${record.date},${safeName},${formatTime(record.totalSeconds)}\n")
        }
        return sb.toString()
    }

    fun restoreAutoBackup(context: Context): Boolean {
        try {
            val backupDir = File(context.getExternalFilesDir(null), "backups")
            if (!backupDir.exists()) return false
            val currentYear = LocalDate.now().year
            val backupFile = File(backupDir, "focus_time_tracker_${currentYear}.csv")
            if (!backupFile.exists()) return false

            val csvString = backupFile.readText()
            val restored = parseBackupCsv(csvString)
            if (restored.history.isNotEmpty()) {
                StorageManager.saveHistory(context, restored.history)
                StorageManager.saveTasks(context, restored.tasks)
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    fun scheduleAutoBackup(context: Context, enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (enabled) {
            val constraints = Constraints.Builder()
                .setRequiresDeviceIdle(true)
                .setRequiresCharging(true)
                .build()

            val periodicWork = PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                "AutoBackupWork",
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicWork
            )
        } else {
            workManager.cancelUniqueWork("AutoBackupWork")
        }
    }

    fun saveAutoBackupPref(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("ftt_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("auto_backup_enabled", enabled).apply()
        scheduleAutoBackup(context, enabled)
    }

    fun loadAutoBackupPref(context: Context): Boolean {
        val prefs = context.getSharedPreferences("ftt_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("auto_backup_enabled", false)
    }
}
