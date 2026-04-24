package com.example.mycalculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mycalculator.ui.CalculatorScreen
import com.example.mycalculator.ui.CalculatorViewModel
import com.example.mycalculator.ui.theme.MyCalculatorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyCalculatorTheme {
                val viewModel: CalculatorViewModel = viewModel()
                CalculatorScreen(state = viewModel.state, onAction = viewModel::onAction)
            }
        }
    }
}