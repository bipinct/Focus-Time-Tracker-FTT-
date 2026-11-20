package com.ftt.focustimetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ftt.focustimetracker.model.Task
import com.ftt.focustimetracker.ui.theme.availableIcons
import com.ftt.focustimetracker.ui.theme.formatTime

@Composable
fun TrackerScreen(
    tasks: List<Task>,
    onToggleTask: (String) -> Unit
) {
    Column(Modifier.padding(16.dp)) {
        val totalToday = tasks.sumOf { it.totalSeconds }

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
                            .clickable { onToggleTask(task.id) },
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
}
