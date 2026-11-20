package com.ftt.focustimetracker.model

data class RestoreData(
    val tasks: List<Task>,
    val history: List<HistoryRecord>
)
