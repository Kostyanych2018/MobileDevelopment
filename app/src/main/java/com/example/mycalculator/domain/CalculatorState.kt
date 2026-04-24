package com.example.mycalculator.domain

data class CalculatorState(
    val number1: String = "",
    val number2: String = "",
    val operation: CalculatorOperation? = null,
    val displayText: String = "0",
    val isError: Boolean = false
)
