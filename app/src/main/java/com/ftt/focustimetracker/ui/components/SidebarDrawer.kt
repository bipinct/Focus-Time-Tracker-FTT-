package com.ftt.focustimetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ftt.focustimetracker.model.Task
import com.ftt.focustimetracker.ui.theme.availableIcons
import com.ftt.focustimetracker.ui.theme.presetColors
import com.ftt.focustimetracker.worker.BackupWorker

@Composable
fun SidebarDrawer(
    tasks: List<Task>,
    onAddTask: (String, Color, String) -> Unit,
    onEditTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onRestoreAutoBackupClick: () -> Unit,
    autoBackupEnabled: Boolean,
    onAutoBackupChanged: (Boolean) -> Unit
) {
    var newTaskName by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(presetColors[0]) }
    var selectedIcon by remember { mutableStateOf("Star") }

    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp)
        ) {
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
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

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
                        onAddTask(newTaskName.trim(), selectedColor, selectedIcon)
                        newTaskName = ""
                        selectedColor = presetColors[0]
                        selectedIcon = "Star"
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
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
                        IconButton(onClick = { onEditTask(task) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray)
                        }
                        IconButton(onClick = { onDeleteTask(task) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text("Cloud Backup & Restore", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onBackupClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = "Backup", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Backup", fontSize = 12.sp)
                }
                Button(
                    onClick = onRestoreClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = "Restore", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Restore", fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onRestoreAutoBackupClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) {
                Icon(Icons.Default.Restore, contentDescription = "Restore Auto-Backup", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Restore Auto-Backup File", fontSize = 12.sp)
            }

            Spacer(Modifier.height(4.dp))
            androidx.compose.ui.platform.LocalContext.current.let { context ->
                TextButton(
                    onClick = {
                        val workRequest = OneTimeWorkRequestBuilder<BackupWorker>().build()
                        WorkManager.getInstance(context).enqueue(workRequest)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text("Test Auto-Backup Now", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAutoBackupChanged(!autoBackupEnabled) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = autoBackupEnabled,
                    onCheckedChange = { onAutoBackupChanged(it) }
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
