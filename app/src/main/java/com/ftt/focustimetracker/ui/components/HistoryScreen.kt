package com.ftt.focustimetracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ftt.focustimetracker.model.HistoryRecord
import com.ftt.focustimetracker.ui.theme.formatTime

@Composable
fun HistoryScreen(history: List<HistoryRecord>) {
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
