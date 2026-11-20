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

    var tasks by remember { mutableStateOf(StorageManager.loadTasks(context)) }
    var history by remember { mutableStateOf(StorageManager.loadHistory(context)) }
    var currentDate by remember { mutableStateOf(StorageManager.getTodayString()) }
    var activeTab by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    tasks = StorageManager.updateTasksWithDate(tasks, history, currentDate)

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            try {
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

    LaunchedEffect(tasks) {
        StorageManager.saveTasks(context, tasks)
    }

    LaunchedEffect(history) {
        StorageManager.saveHistory(context, history)
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var taskToDelete by remember { mutableStateOf<Task?>(null) }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }

    val runningTask = tasks.find { it.isRunning }

    LaunchedEffect(runningTask?.id) {
        if (runningTask != null) {
            TimerService.start(context, runningTask.name, runningTask.totalSeconds)
        } else {
            TimerService.stop(context)
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    TimerService.ACTION_STOP_BROADCAST -> {
                        tasks = tasks.map { it.copy(isRunning = false) }
                    }
                    TimerService.ACTION_TICK_BROADCAST -> {
                        val newSeconds = intent.getIntExtra(TimerService.EXTRA_SECONDS, 0)
                        val today = StorageManager.getTodayString()
                        if (today != currentDate) {
                            currentDate = today
                            tasks = StorageManager.updateTasksWithDate(tasks, history, currentDate)
                        }
                        tasks = tasks.map {
                            if (it.isRunning) it.copy(totalSeconds = newSeconds) else it
                        }
                        history = history.map { record ->
                            val activeRunning = tasks.find { it.isRunning && it.name == record.taskName }
                            if (record.date == currentDate && activeRunning != null) {
                                record.copy(totalSeconds = newSeconds)
                            } else {
                                record
                            }
                        }
                        tasks.forEach { task ->
                            if (task.isRunning) {
                                val exists = history.any { it.date == currentDate && it.taskName == task.name }
                                if (!exists) {
                                    history = history + HistoryRecord(date = currentDate, taskName = task.name, totalSeconds = newSeconds)
                                }
                            }
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
                    currentDate = today
                }
                tasks = StorageManager.updateTasksWithDate(tasks, history, currentDate)

                val prefs = context.getSharedPreferences("ftt_timer_prefs", Context.MODE_PRIVATE)
                if (prefs.getBoolean("is_timer_running", false)) {
                    val startRealtime = prefs.getLong("start_elapsed_realtime", 0L)
                    if (startRealtime > 0L) {
                        val elapsedSecs = ((android.os.SystemClock.elapsedRealtime() - startRealtime) / 1000L).toInt()
                        if (elapsedSecs >= 0) {
                            tasks = tasks.map {
                                if (it.isRunning) it.copy(totalSeconds = elapsedSecs) else it
                            }
                            history = history.map { record ->
                                val activeRunning = tasks.find { it.isRunning && it.name == record.taskName }
                                if (record.date == currentDate && activeRunning != null) {
                                    record.copy(totalSeconds = elapsedSecs)
                                } else {
                                    record
                                }
                            }
                            tasks.forEach { task ->
                                if (task.isRunning) {
                                    val exists = history.any { it.date == currentDate && it.taskName == task.name }
                                    if (!exists) {
                                        history = history + HistoryRecord(date = currentDate, taskName = task.name, totalSeconds = elapsedSecs)
                                    }
                                }
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

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val today = StorageManager.getTodayString()
            if (today != currentDate) {
                currentDate = today
                tasks = StorageManager.updateTasksWithDate(tasks, history, currentDate)
            }

            tasks = tasks.map { task ->
                if (task.isRunning) {
                    val newSecs = task.totalSeconds + 1
                    task.copy(totalSeconds = newSecs)
                } else {
                    task
                }
            }

            history = history.map { record ->
                val activeRunning = tasks.find { it.isRunning && it.name == record.taskName }
                if (record.date == currentDate && activeRunning != null) {
                    record.copy(totalSeconds = activeRunning.totalSeconds)
                } else {
                    record
                }
            }

            tasks.forEach { task ->
                if (task.isRunning) {
                    val exists = history.any { it.date == currentDate && it.taskName == task.name }
                    if (!exists) {
                        history = history + HistoryRecord(date = currentDate, taskName = task.name, totalSeconds = task.totalSeconds)
                    }
                }
            }
        }
    }

    if (taskToDelete != null) {
        DeleteConfirmationDialog(
            task = taskToDelete!!,
            onConfirm = {
                taskToDelete?.let { t ->
                    tasks = tasks.filter { it.id != t.id }
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
                taskToEdit = null
            },
            onDismiss = { taskToEdit = null }
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
                    coroutineScope.launch { drawerState.close() }
                },
                onEditTask = { task -> taskToEdit = task },
                onDeleteTask = { task -> taskToDelete = task },
                onBackupClick = {
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
                                tasks = tasks.map {
                                    if (it.id == taskId) it.copy(isRunning = !it.isRunning)
                                    else it.copy(isRunning = false)
                                }
                            }
                        )
                    } else {
                        HistoryScreen(history = history)
                    }
                }
            }
        }
    }
}
