package com.example.mycalculator.ui

import android.content.ClipData
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mycalculator.data.HistoryEntry
import com.example.mycalculator.data.HistoryRepository
import com.example.mycalculator.domain.CalculatorOperation
import com.example.mycalculator.domain.CalculatorAction
import com.example.mycalculator.domain.CalculatorState
import com.example.mycalculator.ui.theme.CalcError
import com.example.mycalculator.ui.theme.CalcNumberButton
import com.example.mycalculator.ui.theme.CalculatorPalette
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun CalculatorScreen(
    state: CalculatorState,
    onAction: (CalculatorAction) -> Unit,
    palette: CalculatorPalette = CalculatorPalette.defaults()
) {
    val numberStyle = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
    val actionStyle = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold)
    val utilityStyle = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Medium)
    val miniUtilityStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium)

    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val snackbarHostState = remember { SnackbarHostState() }

    val scope = rememberCoroutineScope()
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }

    var showHistory by remember { mutableStateOf(false) }
    var historyItems by remember { mutableStateOf<List<HistoryEntry>>(emptyList()) }
    val historyRepository = remember { HistoryRepository() }

    DisposableEffect(context) {
        var localTts: TextToSpeech? = null
        localTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = localTts
                val languageResult = engine?.setLanguage(Locale.getDefault())
                isTtsReady = languageResult != TextToSpeech.LANG_MISSING_DATA &&
                        languageResult != TextToSpeech.LANG_NOT_SUPPORTED
            } else {
                isTtsReady = false
            }
        }
        tts = localTts

        onDispose {
            localTts.stop()
            localTts.shutdown()
            tts = null
            isTtsReady = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .padding(horizontal = 8.dp)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = {
                                if (state.displayText.isNotBlank()) {
                                    scope.launch {
                                        val clipEntry = ClipEntry(
                                            ClipData.newPlainText(
                                                "calculator_result",
                                                state.displayText
                                            )
                                        )
                                        clipboard.setClipEntry(clipEntry)
                                        snackbarHostState.showSnackbar("Copied to clipboard")
                                    }
                                }
                            }
                        ),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    val displayLength = state.displayText.length
                    val displayFontSize = when {
                        displayLength <= 8 -> 56.sp
                        displayLength <= 12 -> 44.sp
                        displayLength <= 16 -> 34.sp
                        else -> 28.sp
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.displayText,
                            fontSize = displayFontSize,
                            color = if (state.isError) CalcError else palette.text,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val horizontalGap = 10.dp
                    val buttonSize = ((maxWidth - horizontalGap * 3) / 4f).coerceIn(66.dp, 90.dp)
                    val wideButtonWidth = buttonSize * 2 + horizontalGap
                    val miniButtonSize = (buttonSize * 0.72f).coerceIn(44.dp, 64.dp)

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CalculatorRow {
                            CalculatorButton(
                                symbol = "⌛",
                                modifier = Modifier.size(miniButtonSize),
                                color = palette.utilityButton,
                                textStyle = miniUtilityStyle
                            ) {
                                scope.launch {
                                    historyRepository.loadRecentHistory(
                                        limit = 20,
                                        onSuccess = { items ->
                                            historyItems = items
                                            showHistory = true
                                        },
                                        onError = {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Failed to load history")
                                            }
                                        }
                                    )
                                }
                            }
                            CalculatorButton(
                                symbol = "\uD83D\uDD0A",
                                modifier = Modifier.size(miniButtonSize),
                                color = palette.utilityButton,
                                textStyle = miniUtilityStyle
                            ) {
                                val textToSpeak = state.displayText.trim()
                                scope.launch {
                                    when {
                                        textToSpeak.isBlank() -> {
                                            snackbarHostState.showSnackbar("Nothing to read")
                                        }

                                        !isTtsReady || tts == null -> {
                                            snackbarHostState.showSnackbar("Text-to-Speech unavailable")
                                        }

                                        else -> {
                                            tts?.speak(
                                                textToSpeak,
                                                TextToSpeech.QUEUE_FLUSH,
                                                null,
                                                "calculator_readout"
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.size(miniButtonSize))
                            Spacer(modifier = Modifier.size(miniButtonSize))
                        }

                        CalculatorRow {
                            CalculatorButton(
                                symbol = "AC",
                                modifier = Modifier.size(buttonSize),
                                color = palette.utilityButton,
                                textStyle = utilityStyle
                            ) { onAction(CalculatorAction.Clear) }
                            CalculatorButton(
                                symbol = "Del",
                                modifier = Modifier.size(buttonSize),
                                color = palette.utilityButton,
                                textStyle = utilityStyle
                            ) { onAction(CalculatorAction.Delete) }
                            CalculatorButton(
                                symbol = ".",
                                modifier = Modifier.size(buttonSize),
                                color = palette.utilityButton,
                                textStyle = utilityStyle
                            ) { onAction(CalculatorAction.Decimal) }
                            CalculatorButton(
                                symbol = "/",
                                modifier = Modifier.size(buttonSize),
                                color = palette.operatorButton,
                                textStyle = actionStyle
                            ) { onAction(CalculatorAction.Operation(CalculatorOperation.DIVIDE)) }
                        }

                        CalculatorRow {
                            NumberButton(
                                "7",
                                numberStyle,
                                buttonSize,
                                palette.numberButton
                            ) { onAction(CalculatorAction.Number(7)) }
                            NumberButton(
                                "8",
                                numberStyle,
                                buttonSize,
                                palette.numberButton
                            ) { onAction(CalculatorAction.Number(8)) }
                            NumberButton(
                                "9",
                                numberStyle,
                                buttonSize,
                                palette.numberButton
                            ) { onAction(CalculatorAction.Number(9)) }
                            OperatorButton("*", actionStyle, buttonSize, palette.operatorButton) {
                                onAction(CalculatorAction.Operation(CalculatorOperation.MULTIPLY))
                            }
                        }

                        CalculatorRow {
                            NumberButton(
                                "4",
                                numberStyle,
                                buttonSize,
                                palette.numberButton
                            ) { onAction(CalculatorAction.Number(4)) }
                            NumberButton(
                                "5",
                                numberStyle,
                                buttonSize,
                                palette.numberButton
                            ) { onAction(CalculatorAction.Number(5)) }
                            NumberButton(
                                "6",
                                numberStyle,
                                buttonSize,
                                palette.numberButton
                            ) { onAction(CalculatorAction.Number(6)) }
                            OperatorButton("-", actionStyle, buttonSize, palette.operatorButton) {
                                onAction(CalculatorAction.Operation(CalculatorOperation.SUBTRACT))
                            }
                        }

                        CalculatorRow {
                            NumberButton(
                                "1",
                                numberStyle,
                                buttonSize,
                                palette.numberButton
                            ) { onAction(CalculatorAction.Number(1)) }
                            NumberButton(
                                "2",
                                numberStyle,
                                buttonSize,
                                palette.numberButton
                            ) { onAction(CalculatorAction.Number(2)) }
                            NumberButton(
                                "3",
                                numberStyle,
                                buttonSize,
                                palette.numberButton
                            ) { onAction(CalculatorAction.Number(3)) }
                            OperatorButton("+", actionStyle, buttonSize, palette.operatorButton) {
                                onAction(CalculatorAction.Operation(CalculatorOperation.ADD))
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(
                                horizontalGap,
                                Alignment.CenterHorizontally
                            )
                        ) {
                            CalculatorButton(
                                symbol = "0",
                                modifier = Modifier.size(
                                    width = wideButtonWidth,
                                    height = buttonSize
                                ),
                                color = palette.numberButton,
                                textStyle = numberStyle
                            ) { onAction(CalculatorAction.Number(0)) }
                            CalculatorButton(
                                symbol = "=",
                                modifier = Modifier.size(
                                    width = wideButtonWidth,
                                    height = buttonSize
                                ),
                                color = palette.operatorButton,
                                textStyle = actionStyle
                            ) { onAction(CalculatorAction.Calculate) }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 8.dp)
        )

        if (showHistory) {
            AlertDialog(
                onDismissRequest = { showHistory = false },
                confirmButton = {
                    TextButton(onClick = { showHistory = false }) {
                        Text("Close")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            historyRepository.clearAllHistory(
                                onSuccess = {
                                    historyItems = emptyList()
                                    showHistory = false
                                },
                                onError = {  }
                            )
                        }
                    ) {
                        Text("Clear All", color = Color.Red)
                    }
                },
                title = { Text("History") },
                text = {
                    if (historyItems.isEmpty()) {
                        Text("No history yet")
                    } else {
                        LazyColumn {
                            items(historyItems) { item ->
                                Text("${item.expression} = ${item.result}")
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun CalculatorRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.Start),
        content = content
    )
}

@Composable
private fun NumberButton(
    symbol: String,
    style: TextStyle,
    buttonSize: Dp,
    color: Color,
    onClick: () -> Unit
) {
    CalculatorButton(
        symbol = symbol,
        modifier = Modifier.size(buttonSize),
        color = color,
        textStyle = style
    ) { onClick() }
}

@Composable
private fun OperatorButton(
    symbol: String,
    style: TextStyle,
    buttonSize: Dp,
    color: Color,
    onClick: () -> Unit
) {
    CalculatorButton(
        symbol = symbol,
        modifier = Modifier.size(buttonSize),
        color = color,
        textStyle = style
    ) { onClick() }
}