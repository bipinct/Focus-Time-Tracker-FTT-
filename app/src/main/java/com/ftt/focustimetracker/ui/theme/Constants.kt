package com.ftt.focustimetracker.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color

// ==========================================
// DEVELOPER DEBUG CONFIGURATION
// Set to true to enable editing history records in UI for debugging/correcting values
// ==========================================
const val ENABLE_HISTORY_EDIT_MODE = true

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

fun formatTime(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return String.format("%02d:%02d:%02d", h, m, s)
}
