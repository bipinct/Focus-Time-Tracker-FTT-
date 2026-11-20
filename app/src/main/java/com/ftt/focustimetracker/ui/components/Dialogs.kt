package com.ftt.focustimetracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ftt.focustimetracker.model.HistoryRecord
import com.ftt.focustimetracker.model.Task

@Composable
fun DeleteConfirmationDialog(
    task: Task,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Task") },
        text = { Text("Are you sure you want to delete \"${task.name}\"?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = Color.Red)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditTaskDialog(
    task: Task,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var editedName by remember { mutableStateOf(task.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Task Name") },
        text = {
            OutlinedTextField(
                value = editedName,
                onValueChange = { editedName = it },
                label = { Text("Task Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (editedName.isNotBlank()) {
                        onSave(editedName.trim())
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditHistoryDialog(
    record: HistoryRecord,
    onSave: (Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var hours by remember { mutableStateOf((record.totalSeconds / 3600).toString()) }
    var minutes by remember { mutableStateOf(((record.totalSeconds % 3600) / 60).toString()) }
    var seconds by remember { mutableStateOf((record.totalSeconds % 60).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit History: ${record.taskName}") },
        text = {
            Column {
                Text("Date: ${record.date}", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = hours,
                        onValueChange = { hours = it },
                        label = { Text("Hours") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it },
                        label = { Text("Minutes") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = seconds,
                        onValueChange = { seconds = it },
                        label = { Text("Seconds") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text("Delete")
                }
                TextButton(
                    onClick = {
                        val h = hours.toIntOrNull() ?: 0
                        val m = minutes.toIntOrNull() ?: 0
                        val s = seconds.toIntOrNull() ?: 0
                        val totalSecs = h * 3600 + m * 60 + s
                        onSave(totalSecs)
                    }
                ) {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
