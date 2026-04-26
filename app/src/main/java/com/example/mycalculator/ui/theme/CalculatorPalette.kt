package com.example.mycalculator.ui.theme

import android.graphics.Color.parseColor
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt

data class CalculatorPalette(
    val background: Color,
    val numberButton: Color,
    val operatorButton: Color,
    val utilityButton: Color,
    val text: Color,
    val statusBar: Color
) {
    companion object {
        fun defaults(): CalculatorPalette = CalculatorPalette(
            background = CalcBackground,
            numberButton = CalcNumberButton,
            operatorButton = CalcOperatorButton,
            utilityButton = CalcUtilityButton,
            text = CalcText,
            statusBar = CalcBackground
        )

        fun fromRemote(values: Map<String, String?>): CalculatorPalette {
            val defaults = defaults()
            return CalculatorPalette(
                background = values["background"].toColorOr(defaults.background),
                numberButton = values["numberButton"].toColorOr(defaults.numberButton),
                operatorButton = values["operatorButton"].toColorOr(defaults.operatorButton),
                utilityButton = values["utilityButton"].toColorOr(defaults.utilityButton),
                text = values["text"].toColorOr(defaults.text),
                statusBar = values["statusBar"].toColorOr(defaults.statusBar)
            )
        }
    }
}

private fun String?.toColorOr(fallback: Color): Color {
    if (this.isNullOrBlank()) return fallback
    return try {
        Color(this.toColorInt())
    } catch (_: IllegalArgumentException) {
        fallback
    }
}
