package com.example.mycalculator.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.mycalculator.domain.CalculatorAction
import com.example.mycalculator.domain.CalculatorOperation
import com.example.mycalculator.domain.CalculatorState

class CalculatorViewModel : ViewModel() {
    var state by mutableStateOf(CalculatorState())
        private set

    fun onAction(action: CalculatorAction) {
        when (action) {
            is CalculatorAction.Number -> enterNumber(action.number)
            is CalculatorAction.Operation -> enterOperation(action.operation)
            CalculatorAction.Decimal -> enterDecimal()
            CalculatorAction.Clear -> state = CalculatorState()
            CalculatorAction.Delete -> performDelete()
            CalculatorAction.Calculate -> performCalculation()
        }
    }

    private fun enterNumber(number: Int) {
        if (state.isError) {
            state = CalculatorState(number1 = number.toString(), displayText = number.toString())
            return
        }
        if (state.operation == null) {
            if (state.number1.length >= MAX_NUM_LENGTH) return
            val updated = appendDigit(state.number1, number)
            state = state.copy(number1 = updated, displayText = updated.ifEmpty { "0" }, isError = false)
            return
        }

        if (state.number2.length >= MAX_NUM_LENGTH) return
        val updated = appendDigit(state.number2, number)
        state = state.copy(number2 = updated, displayText = updated.ifEmpty { "0" }, isError = false)
    }

    private fun enterOperation(operation: CalculatorOperation) {
        if (state.isError) {
            state = CalculatorState()
            return
        }
        if (state.number1.isEmpty()) return
        if (state.number2.isNotEmpty()) {
            performCalculation()
        }
        state = state.copy(operation = operation, displayText = operation.symbol)
    }

    private fun enterDecimal() {
        if (state.isError) {
            state = CalculatorState(number1 = "0.", displayText = "0.")
            return
        }
        if (state.operation == null) {
            if (state.number1.contains(".")) return
            val updated = if (state.number1.isEmpty()) "0." else "${state.number1}."
            state = state.copy(number1 = updated, displayText = updated)
            return
        }
        if (state.number2.contains(".")) return
        val updated = if (state.number2.isEmpty()) "0." else "${state.number2}."
        state = state.copy(number2 = updated, displayText = updated)
    }

    private fun performDelete() {
        if (state.isError) {
            state = CalculatorState()
            return
        }
        when {
            state.number2.isNotEmpty() -> {
                val updated = state.number2.dropLast(1)
                state = state.copy(number2 = updated, displayText = updated.ifEmpty { "0" })
            }
            state.operation != null -> {
                val display = state.number1.ifEmpty { "0" }
                state = state.copy(operation = null, displayText = display)
            }
            state.number1.isNotEmpty() -> {
                val updated = state.number1.dropLast(1)
                state = state.copy(number1 = updated, displayText = updated.ifEmpty { "0" })
            }
        }
    }

    private fun performCalculation() {
        if (state.isError) {
            state = CalculatorState()
            return
        }
        val operation = state.operation ?: return
        val number1 = state.number1.toDoubleOrNull() ?: return
        val number2 = state.number2.toDoubleOrNull() ?: return

        if (operation == CalculatorOperation.DIVIDE && number2 == 0.0) {
            state = state.copy(displayText = "Error", isError = true)
            return
        }

        val result = when (operation) {
            CalculatorOperation.ADD -> number1 + number2
            CalculatorOperation.SUBTRACT -> number1 - number2
            CalculatorOperation.MULTIPLY -> number1 * number2
            CalculatorOperation.DIVIDE -> number1 / number2
        }

        if (!result.isFinite()) {
            state = state.copy(displayText = "Error", isError = true)
            return
        }

        val text = formatResult(result)
        state = CalculatorState(number1 = text, displayText = text, isError = false)
    }

    private fun appendDigit(current: String, digit: Int): String {
        return when {
            current == "0" -> digit.toString()
            current == "-0" -> "-$digit"
            else -> "$current$digit"
        }
    }

    private fun formatResult(value: Double): String {
        val longValue = value.toLong()
        return if (value == longValue.toDouble()) {
            longValue.toString()
        } else {
            value.toString()
        }
    }

    companion object {
        private const val MAX_NUM_LENGTH = 12
    }
}
