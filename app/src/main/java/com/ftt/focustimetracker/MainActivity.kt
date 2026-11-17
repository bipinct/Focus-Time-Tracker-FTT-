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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

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

// --- Persistence Helpers ---
fun saveTasks(context: Context, tasks: List<Task>) {
    val prefs = context.getSharedPreferences("ftt_prefs", Context.MODE_PRIVATE)
    val jsonArray = JSONArray()
    for (task in tasks) {
        val obj = JSONObject().apply {
            put("id", task.id)
            put("name", task.name)
            put("totalSeconds", task.totalSeconds)
            put("isRunning", false) // pause timers on relaunch
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

    // State initialized from persistent storage
    var tasks by remember { mutableStateOf(loadTasks(context)) }
    var history by remember { mutableStateOf(loadHistory(context)) }
    var currentDate by remember { mutableStateOf(getTodayString()) }
    var activeTab by remember { mutableStateOf(0) } // 0 for Tracker, 1 for History

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

    val runningTask = tasks.find { it.isRunning }

    // Start / Stop Foreground Service for Lock Screen Timer
    LaunchedEffect(runningTask?.id, runningTask?.totalSeconds) {
        if (runningTask != null) {
            TimerService.start(context, runningTask.name, runningTask.totalSeconds)
        } else {
            TimerService.stop(context)
        }
    }

    // Listen to notification stop action broadcast
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == TimerService.ACTION_STOP_BROADCAST) {
                    tasks = tasks.map { it.copy(isRunning = false) }
                }
            }
        }
        val filter = IntentFilter(TimerService.ACTION_STOP_BROADCAST)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    // Timer Loop & Midnight Crossover Logic
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
                // Normal second tick
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(16.dp))
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

                LazyColumn(modifier = Modifier.padding(16.dp)) {
                    items(tasks) { task ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
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
                            IconButton(onClick = { taskToDelete = task }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Focus Tracker", fontWeight = FontWeight.Bold)
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
                                items(sortedTasks) { task ->
                                    val taskIcon = availableIcons[task.iconName] ?: Icons.Default.Star
                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clip(CircleShape)
                                            .background(if (task.isRunning) task.color else Color.White)
                                            .border(
                                                width = 4.dp,
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
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = task.name,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (task.isRunning) Color.White else Color.Gray,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = formatTime(task.totalSeconds),
                                                fontSize = 20.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = if (task.isRunning) Color.White else Color.DarkGray
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Icon(
                                                imageVector = if (task.isRunning) Icons.Default.Close else Icons.Default.PlayArrow,
                                                contentDescription = if (task.isRunning) "Pause" else "Play",
                                                tint = if (task.isRunning) Color.White else Color.Gray,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // HISTORY TAB
                    Column(Modifier.padding(16.dp).fillMaxSize()) {
                        Button(
                            onClick = { /* Export to CSV requires Android File Provider logic not available in a single UI file */ },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Export")
                            Spacer(Modifier.width(8.dp))
                            Text("Export feature requires FileProvider configuration")
                        }

                        Spacer(Modifier.height(16.dp))

                        if (history.isEmpty()) {
                            Text("No history recorded yet.", color = Color.Gray, modifier = Modifier.align(Alignment.CenterHorizontally))
                        } else {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                LazyColumn(modifier = Modifier.padding(8.dp)) {
                                    items(history.reversed()) { record ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text(record.taskName, fontWeight = FontWeight.Bold)
                                                Text(record.date, fontSize = 12.sp, color = Color.Gray)
                                            }
                                            Text(formatTime(record.totalSeconds), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                        }
                                        Divider()
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
