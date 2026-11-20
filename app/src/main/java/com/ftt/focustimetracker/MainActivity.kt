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
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.ftt.focustimetracker.data.BackupManager
import com.ftt.focustimetracker.data.StorageManager
import com.ftt.focustimetracker.model.HistoryRecord
import com.ftt.focustimetracker.model.Task
import com.ftt.focustimetracker.service.TimerService
import com.ftt.focustimetracker.ui.components.*
import java.time.LocalDate

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

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var currentDate by remember { mutableStateOf(StorageManager.getTodayString()) }
    var history by remember { mutableStateOf(StorageManager.loadHistory(context)) }
    var tasks by remember {
        mutableStateOf(
            StorageManager.loadTasks(context).let { list ->
                val updated = StorageManager.updateTasksWithDate(list, history, currentDate)
                val prefs = context.getSharedPreferences("ftt_timer_prefs", Context.MODE_PRIVATE)
                val isTimerRunning = prefs.getBoolean("is_timer_running", false)
                val runningName = prefs.getString("running_task_name", null)
                if (isTimerRunning && runningName != null) {
                    val startRealtime = prefs.getLong("start_elapsed_realtime", 0L)
                    val elapsedSecs = if (startRealtime > 0L) ((android.os.SystemClock.elapsedRealtime() - startRealtime) / 1000L).toInt() else 0
                    updated.map { task ->
                        if (task.name == runningName) task.copy(isRunning = true, totalSeconds = elapsedSecs.coerceAtLeast(task.totalSeconds))
                        else task.copy(isRunning = false)
                    }
                } else {
                    updated
                }
            }
        )
    }

    var activeTab by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Helper to commit today's task seconds into history
    fun commitTodayToHistory() {
        val todayRecords = tasks.filter { it.totalSeconds > 0 }.map { task ->
            HistoryRecord(date = currentDate, taskName = task.name, totalSeconds = task.totalSeconds)
        }
        val otherHistory = history.filter { it.date != currentDate }
        history = otherHistory + todayRecords
    }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            try {
                commitTodayToHistory()
                val csvContent = BackupManager.createCsvContent(history)
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(csvContent.toByteArray())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val jsonString = inputStream.bufferedReader().use { reader -> reader.readText() }
                    val restored = BackupManager.parseBackupJson(jsonString)
                    if (restored != null) {
                        tasks = StorageManager.updateTasksWithDate(restored.tasks, restored.history, currentDate)
                        history = restored.history
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(history) {
        StorageManager.saveHistory(context, history)
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var taskToDelete by remember { mutableStateOf<Task?>(null) }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }
    var historyRecordToEdit by remember { mutableStateOf<HistoryRecord?>(null) }

    val runningTask = tasks.find { it.isRunning }

    // Start/Stop Timer Service and local smooth ticking loop
    LaunchedEffect(runningTask?.id) {
        if (runningTask != null) {
            TimerService.start(context, runningTask.name, runningTask.totalSeconds)
            while (true) {
                delay(1000)
                val today = StorageManager.getTodayString()
                if (today != currentDate) {
                    commitTodayToHistory()
                    currentDate = today
                    tasks = StorageManager.updateTasksWithDate(tasks, history, currentDate)
                }
                tasks = tasks.map { task ->
                    if (task.isRunning) {
                        task.copy(totalSeconds = task.totalSeconds + 1)
                    } else {
                        task
                    }
                }
                val running = tasks.find { it.isRunning }
                if (running != null) {
                    history = history.map { record ->
                        if (record.date == currentDate && record.taskName == running.name) {
                            record.copy(totalSeconds = running.totalSeconds)
                        } else {
                            record
                        }
                    }
                    val exists = history.any { it.date == currentDate && it.taskName == running.name }
                    if (!exists) {
                        history = history + HistoryRecord(date = currentDate, taskName = running.name, totalSeconds = running.totalSeconds)
                    }
                }
            }
        } else {
            commitTodayToHistory()
            StorageManager.saveTasks(context, tasks)
            TimerService.stop(context)
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    TimerService.ACTION_STOP_BROADCAST -> {
                        commitTodayToHistory()
                        tasks = tasks.map { it.copy(isRunning = false) }
                        StorageManager.saveTasks(context, tasks)
                    }
                    TimerService.ACTION_TICK_BROADCAST -> {
                        val newSeconds = intent.getIntExtra(TimerService.EXTRA_SECONDS, 0)
                        val today = StorageManager.getTodayString()
                        if (today != currentDate) {
                            commitTodayToHistory()
                            currentDate = today
                            tasks = StorageManager.updateTasksWithDate(tasks, history, currentDate)
                        }
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
                val today = StorageManager.getTodayString()
                if (today != currentDate) {
                    commitTodayToHistory()
                    currentDate = today
                    tasks = StorageManager.updateTasksWithDate(tasks, history, currentDate)
                }

                val prefs = context.getSharedPreferences("ftt_timer_prefs", Context.MODE_PRIVATE)
                if (prefs.getBoolean("is_timer_running", false)) {
                    val startRealtime = prefs.getLong("start_elapsed_realtime", 0L)
                    val runningName = prefs.getString("running_task_name", null)
                    if (startRealtime > 0L) {
                        val elapsedSecs = ((android.os.SystemClock.elapsedRealtime() - startRealtime) / 1000L).toInt()
                        if (elapsedSecs >= 0) {
                            tasks = tasks.map {
                                if (it.name == runningName) it.copy(isRunning = true, totalSeconds = elapsedSecs)
                                else it.copy(isRunning = false)
                            }
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            commitTodayToHistory()
            StorageManager.saveTasks(context, tasks)
            context.unregisterReceiver(receiver)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }

    if (taskToDelete != null) {
        DeleteConfirmationDialog(
            task = taskToDelete!!,
            onConfirm = {
                taskToDelete?.let { t ->
                    tasks = tasks.filter { it.id != t.id }
                    StorageManager.saveTasks(context, tasks)
                }
                taskToDelete = null
            },
            onDismiss = { taskToDelete = null }
        )
    }

    if (taskToEdit != null) {
        EditTaskDialog(
            task = taskToEdit!!,
            onSave = { newName ->
                val targetId = taskToEdit!!.id
                tasks = tasks.map {
                    if (it.id == targetId) it.copy(name = newName) else it
                }
                StorageManager.saveTasks(context, tasks)
                taskToEdit = null
            },
            onDismiss = { taskToEdit = null }
        )
    }

    if (historyRecordToEdit != null) {
        EditHistoryDialog(
            record = historyRecordToEdit!!,
            onSave = { newSeconds ->
                val targetId = historyRecordToEdit!!.id
                history = history.map {
                    if (it.id == targetId) it.copy(totalSeconds = newSeconds) else it
                }
                tasks = StorageManager.updateTasksWithDate(tasks, history, currentDate)
                StorageManager.saveTasks(context, tasks)
                historyRecordToEdit = null
            },
            onDelete = {
                val targetId = historyRecordToEdit!!.id
                history = history.filter { it.id != targetId }
                tasks = StorageManager.updateTasksWithDate(tasks, history, currentDate)
                StorageManager.saveTasks(context, tasks)
                historyRecordToEdit = null
            },
            onDismiss = { historyRecordToEdit = null }
        )
    }

    var autoBackupEnabled by remember { mutableStateOf(BackupManager.loadAutoBackupPref(context)) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SidebarDrawer(
                tasks = tasks,
                onAddTask = { name, color, icon ->
                    tasks = tasks + Task(name = name, colorInt = color.toArgb(), iconName = icon)
                    StorageManager.saveTasks(context, tasks)
                    coroutineScope.launch { drawerState.close() }
                },
                onEditTask = { task -> taskToEdit = task },
                onDeleteTask = { task -> taskToDelete = task },
                onBackupClick = {
                    commitTodayToHistory()
                    val currentYear = LocalDate.now().year
                    val filename = "focus_time_tracker_${currentYear}.csv"
                    backupLauncher.launch(filename)
                },
                onRestoreClick = {
                    restoreLauncher.launch(arrayOf("application/json", "text/csv", "*/*"))
                },
                onRestoreAutoBackupClick = {
                    val success = BackupManager.restoreAutoBackup(context)
                    if (success) {
                        history = StorageManager.loadHistory(context)
                        tasks = StorageManager.updateTasksWithDate(StorageManager.loadTasks(context), history, currentDate)
                        StorageManager.saveTasks(context, tasks)
                    }
                },
                autoBackupEnabled = autoBackupEnabled,
                onAutoBackupChanged = { enabled ->
                    autoBackupEnabled = enabled
                    BackupManager.saveAutoBackupPref(context, enabled)
                }
            )
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
            val pullToRefreshState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    coroutineScope.launch {
                        isRefreshing = true
                        commitTodayToHistory()
                        currentDate = StorageManager.getTodayString()
                        history = StorageManager.loadHistory(context)
                        tasks = StorageManager.updateTasksWithDate(tasks, history, currentDate)
                        delay(500)
                        isRefreshing = false
                    }
                },
                state = pullToRefreshState,
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
            ) {
                Box(modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF3F4F6))) {
                    if (activeTab == 0) {
                        TrackerScreen(
                            tasks = tasks,
                            onToggleTask = { taskId ->
                                commitTodayToHistory()
                                tasks = tasks.map {
                                    if (it.id == taskId) it.copy(isRunning = !it.isRunning)
                                    else it.copy(isRunning = false)
                                }
                                StorageManager.saveTasks(context, tasks)
                            }
                        )
                    } else {
                        HistoryScreen(
                            history = history,
                            onEditRecord = { record -> historyRecordToEdit = record }
                        )
                    }
                }
            }
        }
    }
}
