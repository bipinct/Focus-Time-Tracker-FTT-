package com.ftt.focustimetracker

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit

// --- Data Models ---
data class Task(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val totalSeconds: Int = 0,
    val isRunning: Boolean = false,
    val colorInt: Int = 0xFF4F46E5.toInt(),
    val iconName: String = "Star"
) {
    val color: Color get() = Color(colorInt)
}

data class HistoryRecord(
    val id: String = UUID.randomUUID().toString(),
    val date: String,
    val taskName: String,
    val totalSeconds: Int
)

data class RestoreData(val tasks: List<Task>, val history: List<HistoryRecord>)

// --- Persistence, Backup & WorkManager Helpers ---
fun saveTasks(context: Context, tasks: List<Task>) {
    val prefs = context.getSharedPreferences("ftt_prefs", Context.MODE_PRIVATE)
    val jsonArray = JSONArray()
    for (task in tasks) {
        val obj = JSONObject().apply {
            put("id", task.id)
            put("name", task.name)
            put("totalSeconds", task.totalSeconds)
            put("isRunning", task.isRunning)
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
                    totalSeconds = obj.optInt("totalSeconds", 0),
                    isRunning = obj.optBoolean("isRunning", false),
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

fun createBackupJson(tasks: List<Task>, history: List<HistoryRecord>): String {
    val rootObj = JSONObject().apply {
        put("version", 1)
        put("timestamp", System.currentTimeMillis())

        val tasksArray = JSONArray()
        for (task in tasks) {
            tasksArray.put(JSONObject().apply {
                put("id", task.id)
                put("name", task.name)
                put("totalSeconds", task.totalSeconds)
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
                        totalSeconds = obj.optInt("totalSeconds", 0),
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
                        date = obj.optString("date", getTodayString()),
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
    val today = getTodayString()

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

            val existing = tasksMap[taskName]
            val taskSeconds = if (date == today) totalSeconds else (existing?.totalSeconds ?: 0)
            if (existing == null) {
                val color = presetColorArray[colorIndex % presetColorArray.size]
                colorIndex++
                tasksMap[taskName] = Task(
                    name = taskName,
                    totalSeconds = taskSeconds,
                    colorInt = color,
                    iconName = "Star"
                )
            } else if (date == today) {
                tasksMap[taskName] = existing.copy(totalSeconds = taskSeconds)
            }
        }
    }

    return RestoreData(tasksMap.values.toList(), historyList)
}

fun createCsvContent(history: List<HistoryRecord>, tasks: List<Task>): String {
    val sb = StringBuilder()
    sb.append("date,task name,duration\n")
    for (record in history) {
        val safeName = "\"${record.taskName.replace("\"", "\"\"")}\""
        sb.append("${record.date},${safeName},${formatTime(record.totalSeconds)}\n")
    }
    val today = getTodayString()
    for (task in tasks) {
        if (task.totalSeconds > 0) {
            val safeName = "\"${task.name.replace("\"", "\"\"")}\""
            sb.append("${today},${safeName},${formatTime(task.totalSeconds)}\n")
        }
    }
    return sb.toString()
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

// --- Constants ---
val presetColors = listOf(
    Color(0xFF4F46E5), // Indigo
    Color(0xFFEF4444), // Red
    Color(0xFF10B981), // Emerald
    Color(0xFF3B82F6), // Blue
    Color(0xFFF59E0B), // Amber
    Color(0xFF8B5CF6), // Purple
    Color(0xFFEC4899), // Pink
    Color(0xFF14B8A6)  // Teal
)

val availableIcons = mapOf(
    "Star" to Icons.Default.Star,
    "Work" to Icons.Default.Work,
    "Home" to Icons.Default.Home,
    "Favorite" to Icons.Default.Favorite,
    "School" to Icons.Default.School,
    "Code" to Icons.Default.Code,
    "Book" to Icons.AutoMirrored.Filled.MenuBook,
    "Fitness" to Icons.Default.FitnessCenter,
    "Fire" to Icons.Default.LocalFireDepartment,
    "Coffee" to Icons.Default.Coffee,
    "Flag" to Icons.Default.Flag,
    "Timer" to Icons.Default.Timer
)

// --- Utility Functions ---
fun formatTime(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return String.format("%02d:%02d:%02d", h, m, s)
}

fun getTodayString(): String {
    return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                TimeTrackerApp()
            }
        }
    }
}

@SuppressLint("UnspecifiedRegisterReceiverFlag")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeTrackerApp() {
    val context = LocalContext.current

    // Request notification permission for lock screen timer
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // State initialized from persistent storage (declared BEFORE launchers)
    var tasks by remember { mutableStateOf(loadTasks(context)) }
    var history by remember { mutableStateOf(loadHistory(context)) }
    var currentDate by remember { mutableStateOf(getTodayString()) }
    var activeTab by remember { mutableStateOf(0) } // 0 for Tracker, 1 for History

    // Backup Launcher (CSV format named focus_time_tracker_<year>.csv)
    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            try {
                val csvContent = createCsvContent(history, tasks)
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(csvContent.toByteArray())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Restore Launcher for JSON backup files
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val jsonString = inputStream.bufferedReader().use { reader -> reader.readText() }
                    val restored = parseBackupJson(jsonString)
                    if (restored != null) {
                        tasks = restored.tasks
                        history = restored.history
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Auto-save tasks and history whenever they change
    LaunchedEffect(tasks) {
        saveTasks(context, tasks)
    }

    LaunchedEffect(history) {
        saveHistory(context, history)
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    var newTaskName by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(presetColors[0]) }
    var selectedIcon by remember { mutableStateOf("Star") }
    var taskToDelete by remember { mutableStateOf<Task?>(null) }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }
    var editedTaskName by remember { mutableStateOf("") }

    val runningTask = tasks.find { it.isRunning }

    // Start / Stop Foreground Service for Lock Screen Timer & Screen-Off Ticking
    LaunchedEffect(runningTask?.id) {
        if (runningTask != null) {
            TimerService.start(context, runningTask.name, runningTask.totalSeconds)
        } else {
            TimerService.stop(context)
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    // Listen to notification stop and tick broadcasts from TimerService and lifecycle resume
    DisposableEffect(lifecycleOwner, context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    TimerService.ACTION_STOP_BROADCAST -> {
                        tasks = tasks.map { it.copy(isRunning = false) }
                    }
                    TimerService.ACTION_TICK_BROADCAST -> {
                        val newSeconds = intent.getIntExtra(TimerService.EXTRA_SECONDS, 0)
                        tasks = tasks.map {
                            if (it.isRunning) it.copy(totalSeconds = newSeconds) else it
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(TimerService.ACTION_STOP_BROADCAST)
            addAction(TimerService.ACTION_TICK_BROADCAST)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        val lifecycleObserver = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val prefs = context.getSharedPreferences("ftt_timer_prefs", Context.MODE_PRIVATE)
                if (prefs.getBoolean("is_timer_running", false)) {
                    val startRealtime = prefs.getLong("start_elapsed_realtime", 0L)
                    if (startRealtime > 0L) {
                        val elapsedSecs = ((android.os.SystemClock.elapsedRealtime() - startRealtime) / 1000L).toInt()
                        if (elapsedSecs >= 0) {
                            tasks = tasks.map {
                                if (it.isRunning) it.copy(totalSeconds = elapsedSecs) else it
                            }
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            context.unregisterReceiver(receiver)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }

    // Timer Loop for Foreground Ticking
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val today = getTodayString()

            if (today != currentDate) {
                // Midnight crossover
                val newHistory = tasks.filter { it.totalSeconds > 0 }.map {
                    HistoryRecord(date = currentDate, taskName = it.name, totalSeconds = it.totalSeconds)
                }
                history = history + newHistory
                tasks = tasks.map { it.copy(totalSeconds = if (it.isRunning) 1 else 0) }
                currentDate = today
            } else {
                tasks = tasks.map {
                    if (it.isRunning) it.copy(totalSeconds = it.totalSeconds + 1) else it
                }
            }
        }
    }

    // Deletion Confirmation Dialog
    if (taskToDelete != null) {
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { Text("Delete Task") },
            text = { Text("Are you sure you want to delete \"${taskToDelete?.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        taskToDelete?.let { t ->
                            tasks = tasks.filter { it.id != t.id }
                        }
                        taskToDelete = null
                    }
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Task Name Dialog
    if (taskToEdit != null) {
        AlertDialog(
            onDismissRequest = { taskToEdit = null },
            title = { Text("Edit Task Name") },
            text = {
                OutlinedTextField(
                    value = editedTaskName,
                    onValueChange = { editedTaskName = it },
                    label = { Text("Task Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editedTaskName.isNotBlank() && taskToEdit != null) {
                            val targetId = taskToEdit!!.id
                            tasks = tasks.map {
                                if (it.id == targetId) it.copy(name = editedTaskName.trim()) else it
                            }
                        }
                        taskToEdit = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToEdit = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 16.dp)
                ) {
                    // Sidebar Header with Logo and "Focus Time Tracker" text
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Focus Time Tracker",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Divider(Modifier.padding(vertical = 12.dp))

                    Text("Add New Task", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = newTaskName,
                        onValueChange = { newTaskName = it },
                        label = { Text("Task Name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )

                    Spacer(Modifier.height(12.dp))

                    Text("Pick Color", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presetColors.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { selectedColor = color }
                                    .border(
                                        width = if (selectedColor == color) 3.dp else 0.dp,
                                        color = Color.Black,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Text("Pick Icon", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableIcons.forEach { (name, icon) ->
                            IconButton(
                                onClick = { selectedIcon = name },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = if (selectedIcon == name) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        shape = CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = name,
                                    tint = if (selectedIcon == name) MaterialTheme.colorScheme.primary else Color.Gray
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (newTaskName.isNotBlank()) {
                                tasks = tasks + Task(
                                    name = newTaskName.trim(),
                                    colorInt = selectedColor.toArgb(),
                                    iconName = selectedIcon
                                )
                                newTaskName = ""
                                selectedColor = presetColors[0]
                                selectedIcon = "Star"
                                coroutineScope.launch { drawerState.close() }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        enabled = newTaskName.isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                        Spacer(Modifier.width(8.dp))
                        Text("Create Task")
                    }

                    Divider(Modifier.padding(vertical = 16.dp))
                    Text("Your Tasks", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp))

                    Spacer(Modifier.height(4.dp))

                    tasks.forEach { task ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(task.color),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = availableIcons[task.iconName] ?: Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(task.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Row {
                                IconButton(onClick = {
                                    taskToEdit = task
                                    editedTaskName = task.name
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray)
                                }
                                IconButton(onClick = { taskToDelete = task }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                                }
                            }
                        }
                    }

                    // --- BELOW YOUR TASKS: Backup & Restore & Daily Auto-Backup ---
                    Divider(Modifier.padding(vertical = 16.dp))
                    Text("Cloud Backup & Restore", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val currentYear = LocalDate.now().year
                                val filename = "focus_time_tracker_${currentYear}.csv"
                                backupLauncher.launch(filename)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = "Backup", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Backup", fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                restoreLauncher.launch(arrayOf("application/json", "text/csv", "*/*"))
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = "Restore", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Restore", fontSize = 12.sp)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    var autoBackupEnabled by remember { mutableStateOf(loadAutoBackupPref(context)) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                autoBackupEnabled = !autoBackupEnabled
                                saveAutoBackupPref(context, autoBackupEnabled)
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = autoBackupEnabled,
                            onCheckedChange = { checked ->
                                autoBackupEnabled = checked
                                saveAutoBackupPref(context, checked)
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Daily Auto-Backup", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text("Sync CSV file for current year daily", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Focus Time Tracker", fontWeight = FontWeight.Bold)
                            Text(currentDate, fontSize = 12.sp, color = Color.LightGray)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.CheckCircle, contentDescription = "Today") },
                        label = { Text("Today") },
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.DateRange, contentDescription = "History") },
                        label = { Text("History") },
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 }
                    )
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(Color(0xFFF3F4F6))) {

                if (activeTab == 0) {
                    // TRACKER TAB
                    Column(Modifier.padding(16.dp)) {
                        val totalToday = tasks.sumOf { it.totalSeconds }

                        // Total Summary
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE0E7FF))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("TOTAL FOCUS TODAY", fontSize = 12.sp, color = Color(0xFF4F46E5), fontWeight = FontWeight.Bold)
                                Text(formatTime(totalToday), fontSize = 36.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color(0xFF4338CA))
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        if (tasks.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No tasks yet. Open menu to add one.", color = Color.Gray)
                            }
                        } else {
                            val sortedTasks = tasks.sortedByDescending { it.isRunning }
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(
                                    items = sortedTasks,
                                    span = { task ->
                                        GridItemSpan(if (task == sortedTasks.firstOrNull()) 2 else 1)
                                    }
                                ) { task ->
                                    val isFirst = (task == sortedTasks.firstOrNull())
                                    val taskIcon = availableIcons[task.iconName] ?: Icons.Default.Star
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(CircleShape)
                                            .background(if (task.isRunning) task.color else Color.White)
                                            .border(
                                                width = if (isFirst) 6.dp else 4.dp,
                                                color = if (task.isRunning) Color.White else Color.LightGray,
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                // Toggle Timer logic
                                                tasks = tasks.map {
                                                    if (it.id == task.id) it.copy(isRunning = !it.isRunning)
                                                    else it.copy(isRunning = false)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier.padding(8.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = taskIcon,
                                                    contentDescription = null,
                                                    tint = if (task.isRunning) Color.White else Color.Gray,
                                                    modifier = Modifier.size(if (isFirst) 22.dp else 16.dp)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = task.name,
                                                    fontSize = if (isFirst) 18.sp else 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (task.isRunning) Color.White else Color.Gray,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = formatTime(task.totalSeconds),
                                                fontSize = if (isFirst) 32.sp else 20.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = if (task.isRunning) Color.White else Color.DarkGray
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Icon(
                                                imageVector = if (task.isRunning) Icons.Default.Close else Icons.Default.PlayArrow,
                                                contentDescription = if (task.isRunning) "Pause" else "Play",
                                                tint = if (task.isRunning) Color.White else Color.Gray,
                                                modifier = Modifier.size(if (isFirst) 28.dp else 20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // HISTORY TAB (Grouped by date, date title with total sum, alternate color blocks)
                    Column(Modifier.padding(16.dp).fillMaxSize()) {
                        if (history.isEmpty()) {
                            Spacer(Modifier.height(32.dp))
                            Text("No history recorded yet.", color = Color.Gray, modifier = Modifier.align(Alignment.CenterHorizontally))
                        } else {
                            data class DateGroup(val date: String, val totalSeconds: Int, val records: List<HistoryRecord>)
                            val dateGroups = remember(history) {
                                history.groupBy { it.date }
                                    .entries
                                    .sortedByDescending { it.key }
                                    .map { (date, records) ->
                                        DateGroup(
                                            date = date,
                                            totalSeconds = records.sumOf { it.totalSeconds },
                                            records = records
                                        )
                                    }
                            }

                            val palette = listOf(
                                Color(0xFFFFFFFF), // White
                                Color(0xFFF8FAFC), // Slate 50
                                Color(0xFFF0FDF4), // Emerald 50
                                Color(0xFFEFF6FF), // Blue 50
                                Color(0xFFFAF5FF), // Purple 50
                                Color(0xFFFFFBEB)  // Amber 50
                            )

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(dateGroups.indices.toList()) { index ->
                                    val group = dateGroups[index]
                                    val groupBgColor = palette[index % palette.size]

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = groupBgColor),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp)
                                        ) {
                                            // Date Title : Total Focus Hour for the day
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = group.date,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp,
                                                    color = Color(0xFF1E293B)
                                                )
                                                Text(
                                                    text = "Total: ${formatTime(group.totalSeconds)}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = Color(0xFF4F46E5)
                                                )
                                            }

                                            Spacer(Modifier.height(8.dp))
                                            Divider(color = Color(0xFFE2E8F0))
                                            Spacer(Modifier.height(8.dp))

                                            // Each task inside the block
                                            group.records.forEach { record ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = Icons.Default.CheckCircle,
                                                            contentDescription = null,
                                                            tint = Color(0xFF10B981),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(Modifier.width(8.dp))
                                                        Text(
                                                            text = record.taskName,
                                                            fontSize = 14.sp,
                                                            color = Color(0xFF334155),
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }
                                                    Text(
                                                        text = formatTime(record.totalSeconds),
                                                        fontSize = 14.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = Color(0xFF475569)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
