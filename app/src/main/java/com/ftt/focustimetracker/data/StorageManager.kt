package com.ftt.focustimetracker.data

import android.content.Context
import com.ftt.focustimetracker.model.HistoryRecord
import com.ftt.focustimetracker.model.Task
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

object StorageManager {
    fun saveTasks(context: Context, tasks: List<Task>) {
        val prefs = context.getSharedPreferences("ftt_prefs", Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        for (task in tasks) {
            val obj = JSONObject().apply {
                put("id", task.id)
                put("name", task.name)
                put("colorInt", task.colorInt)
                put("iconName", task.iconName)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString("saved_tasks", jsonArray.toString()).apply()
    }

    fun loadTasks(context: Context): List<Task> {
        val prefs = context.getSharedPreferences("ftt_prefs", Context.MODE_PRIVATE)
        val jsonString = prefs.getString("saved_tasks", null) ?: return emptyList()
        val list = mutableListOf<Task>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveHistory(context: Context, history: List<HistoryRecord>) {
        val prefs = context.getSharedPreferences("ftt_prefs", Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        for (record in history) {
            val obj = JSONObject().apply {
                put("id", record.id)
                put("date", record.date)
                put("taskName", record.taskName)
                put("totalSeconds", record.totalSeconds)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString("saved_history", jsonArray.toString()).apply()
    }

    fun loadHistory(context: Context): List<HistoryRecord> {
        val prefs = context.getSharedPreferences("ftt_prefs", Context.MODE_PRIVATE)
        val jsonString = prefs.getString("saved_history", null) ?: return emptyList()
        val list = mutableListOf<HistoryRecord>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    HistoryRecord(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        date = obj.optString("date", getTodayString()),
                        taskName = obj.optString("taskName", "Task"),
                        totalSeconds = obj.optInt("totalSeconds", 0)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun getTodayString(): String {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    fun updateTasksWithDate(tasks: List<Task>, history: List<HistoryRecord>, date: String): List<Task> {
        return tasks.map { task ->
            val record = history.find { it.date == date && it.taskName == task.name }
            task.copy(totalSeconds = record?.totalSeconds ?: 0)
        }
    }
}
