package com.ftt.focustimetracker.model

import java.util.UUID

data class HistoryRecord(
    val id: String = UUID.randomUUID().toString(),
    val date: String,
    val taskName: String,
    val totalSeconds: Int
)
