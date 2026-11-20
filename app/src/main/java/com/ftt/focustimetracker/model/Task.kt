package com.ftt.focustimetracker.model

import androidx.compose.ui.graphics.Color
import java.util.UUID

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
