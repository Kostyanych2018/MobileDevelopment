package com.example.mycalculator.data

data class HistoryEntry(
    val expression: String = "",
    val result: String = "",
    val timestamp: Long = System.currentTimeMillis()
)