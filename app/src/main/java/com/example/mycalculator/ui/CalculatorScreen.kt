package com.example.mycalculator.ui

import android.content.ClipData
import android.content.res.Configuration
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.mycalculator.data.HistoryEntry
import com.example.mycalculator.data.HistoryRepository
import com.example.mycalculator.domain.CalculatorAction
import com.example.mycalculator.domain.CalculatorOperation
import com.example.mycalculator.domain.CalculatorState
import com.example.mycalculator.ui.theme.CalcError
import com.example.mycalculator.ui.theme.CalculatorPalette
import kotlinx.coroutines.launch
import java.util.Locale

private enum class ProtectedFeature { HISTORY, TTS }

@Composable
fun CalculatorScreen(
    state: CalculatorState,
    onAction: (CalculatorAction) -> Unit,
    palette: CalculatorPalette = CalculatorPalette.defaults(),
    isUnlocked: Boolean = true,
    hasPin: Boolean = false,
    authManager: AuthManager? = null,
    onSetupPin: (String) -> Unit = {},
    onVerifyPin: (String) -> Boolean = { false },
    onUnlockSuccess: () -> Unit = {},
    onResetAndWipe: (onComplete: () -> Unit) -> Unit = { it() }
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val numberStyle = TextStyle(
        fontSize = if (isLandscape) 24.sp else 30.sp,
        fontWeight = FontWeight.SemiBold
    )
    val actionStyle = TextStyle(
        fontSize = if (isLandscape) 24.sp else 30.sp,
        fontWeight = FontWeight.Bold
    )
    val utilityStyle = TextStyle(
        fontSize = if (isLandscape) 24.sp else 30.sp,
        fontWeight = FontWeight.Medium
    )
    val miniUtilityStyle = TextStyle(
        fontSize = if (isLandscape) 18.sp else 20.sp,
        fontWeight = FontWeight.Medium
    )

    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val snackbarHostState = remember { SnackbarHostState() }

    val scope = rememberCoroutineScope()
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }

    var showHistory by remember { mutableStateOf(false) }
    var historyItems by remember { mutableStateOf<List<HistoryEntry>>(emptyList()) }
    val historyRepository = remember { HistoryRepository() }
    var showSetupPinDialog by remember { mutableStateOf(false) }
    var showEnterPinDialog by remember { mutableStateOf(false) }
    var showResetWarningDialog by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }

    var pendingProtectedFeature by remember { mutableStateOf<ProtectedFeature?>(null) }

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
            localTts?.stop()
            localTts?.shutdown()
            tts = null
            isTtsReady = false
        }
    }

    val openHistory: () -> Unit = {
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

    val speakCurrentText: () -> Unit = {
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

    val runPendingProtectedFeature: () -> Unit = {
        when (pendingProtectedFeature) {
            ProtectedFeature.HISTORY -> openHistory()
            ProtectedFeature.TTS -> speakCurrentText()
            null -> Unit
        }
        pendingProtectedFeature = null
    }

    val requestProtectedAccess: (ProtectedFeature) -> Unit = { target ->
        pendingProtectedFeature = target
        pinError = null

        if (!hasPin) {
            showSetupPinDialog = true
        } else if (isUnlocked) {
            runPendingProtectedFeature()
        } else {
            val activity = context as? FragmentActivity
            val canUseBiometrics = authManager != null && activity != null && authManager.canUseBiometrics()
            if (canUseBiometrics) {
                authManager.authenticate(
                    onSuccess = {
                        onUnlockSuccess()
                        runPendingProtectedFeature()
                    },
                    onFallbackToPin = {
                        showEnterPinDialog = true
                    },
                    onError = { message ->
                        pinError = message
                        showEnterPinDialog = true
                    }
                )
            } else {
                showEnterPinDialog = true
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(
                    horizontal = if (isLandscape) 8.dp else 16.dp,
                    vertical = if (isLandscape) 4.dp else 8.dp
                )
        ) {
            val betweenGap = if (isLandscape) 4.dp else 8.dp
            val displayH = maxHeight * if (isLandscape) 0.28f else 0.20f
            val buttonsH = maxHeight - displayH - betweenGap

            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(displayH),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    DisplayArea(
                        state = state,
                        palette = palette,
                        scope = scope,
                        clipboard = clipboard,
                        snackbarHostState = snackbarHostState,
                        isLandscape = isLandscape
                    )
                }

                Spacer(modifier = Modifier.height(betweenGap))

                ButtonGrid(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(buttonsH),
                    isLandscape = isLandscape,
                    palette = palette,
                    onAction = onAction,
                    requestProtectedAccess = requestProtectedAccess,
                    miniUtilityStyle = miniUtilityStyle,
                    utilityStyle = utilityStyle,
                    actionStyle = actionStyle,
                    numberStyle = numberStyle
                )
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
                                onError = { }
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

        if (showSetupPinDialog) {
            SetupPinDialog(
                onConfirm = { pin ->
                    onSetupPin(pin)
                    showSetupPinDialog = false
                    onUnlockSuccess()
                    runPendingProtectedFeature()
                },
                onDismiss = {
                    showSetupPinDialog = false
                    pendingProtectedFeature = null
                }
            )
        }

        if (showEnterPinDialog) {
            EnterPinDialog(
                onConfirm = { pin ->
                    when {
                        pin.length != 4 -> {
                            pinError = "PIN must be 4 digits"
                        }

                        onVerifyPin(pin) -> {
                            pinError = null
                            showEnterPinDialog = false
                            runPendingProtectedFeature()
                        }

                        else -> {
                            pinError = "Incorrect PIN"
                        }
                    }
                },
                onForgotPin = {
                    showEnterPinDialog = false
                    showResetWarningDialog = true
                },
                onDismiss = {
                    showEnterPinDialog = false
                    pendingProtectedFeature = null
                },
                errorMessage = pinError
            )
        }

        if (showResetWarningDialog) {
            ResetWarningDialog(
                onConfirm = {
                    onResetAndWipe {
                        showResetWarningDialog = false
                        pendingProtectedFeature = null
                        pinError = null
                        showSetupPinDialog = true
                    }
                },
                onDismiss = {
                    showResetWarningDialog = false
                    pendingProtectedFeature = null
                }
            )
        }
    }
}

@Composable
private fun DisplayArea(
    state: CalculatorState,
    palette: CalculatorPalette,
    scope: kotlinx.coroutines.CoroutineScope,
    clipboard: androidx.compose.ui.platform.Clipboard,
    snackbarHostState: SnackbarHostState,
    isLandscape: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    if (state.displayText.isNotBlank()) {
                        scope.launch {
                            val clipEntry = ClipEntry(
                                android.content.ClipData.newPlainText(
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
        val baseFontSize = if (isLandscape) 40.sp else 56.sp
        val displayFontSize = when {
            displayLength <= 8 -> baseFontSize
            displayLength <= 12 -> baseFontSize * 0.8f
            displayLength <= 16 -> baseFontSize * 0.6f
            else -> baseFontSize * 0.5f
        }

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

@Composable
private fun ButtonGrid(
    modifier: Modifier = Modifier,
    isLandscape: Boolean,
    palette: CalculatorPalette,
    onAction: (CalculatorAction) -> Unit,
    requestProtectedAccess: (ProtectedFeature) -> Unit,
    miniUtilityStyle: TextStyle,
    utilityStyle: TextStyle,
    actionStyle: TextStyle,
    numberStyle: TextStyle
) {
    BoxWithConstraints(modifier = modifier) {
        val hGap = if (isLandscape) 8.dp else 10.dp
        val vGap = if (isLandscape) 5.dp else 10.dp
        val colCount = 4
        val rowCount = 6

        val btnW = (maxWidth - hGap * (colCount - 1)) / colCount
        val btnH = (maxHeight - vGap * (rowCount - 1)) / rowCount
        val miniH = btnH * 0.75f
        val wideW = btnW * 2 + hGap

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(vGap)
        ) {
            CalculatorRow(horizontalGap = hGap, horizontalAlignment = Alignment.Start) {
                CalculatorButton(
                    symbol = "⌛",
                    modifier = Modifier.size(width = btnW, height = miniH),
                    color = palette.utilityButton,
                    textStyle = miniUtilityStyle,
                    textColor = palette.text
                ) { requestProtectedAccess(ProtectedFeature.HISTORY) }
                CalculatorButton(
                    symbol = "\uD83D\uDD0A",
                    modifier = Modifier.size(width = btnW, height = miniH),
                    color = palette.utilityButton,
                    textStyle = miniUtilityStyle,
                    textColor = palette.text
                ) { requestProtectedAccess(ProtectedFeature.TTS) }
                Spacer(Modifier.size(width = btnW, height = miniH))
                Spacer(Modifier.size(width = btnW, height = miniH))
            }

            CalculatorRow(horizontalGap = hGap) {
                CalculatorButton("AC",  Modifier.size(btnW, btnH), palette.utilityButton,  utilityStyle, palette.text) { onAction(CalculatorAction.Clear) }
                CalculatorButton("Del", Modifier.size(btnW, btnH), palette.utilityButton,  utilityStyle, palette.text) { onAction(CalculatorAction.Delete) }
                CalculatorButton(".",   Modifier.size(btnW, btnH), palette.utilityButton,  utilityStyle, palette.text) { onAction(CalculatorAction.Decimal) }
                CalculatorButton("/",   Modifier.size(btnW, btnH), palette.operatorButton, actionStyle,  palette.text) { onAction(CalculatorAction.Operation(CalculatorOperation.DIVIDE)) }
            }

            CalculatorRow(horizontalGap = hGap) {
                NumberButton("7", numberStyle, btnW, btnH, palette.numberButton,   palette.text) { onAction(CalculatorAction.Number(7)) }
                NumberButton("8", numberStyle, btnW, btnH, palette.numberButton,   palette.text) { onAction(CalculatorAction.Number(8)) }
                NumberButton("9", numberStyle, btnW, btnH, palette.numberButton,   palette.text) { onAction(CalculatorAction.Number(9)) }
                OperatorButton("*", actionStyle, btnW, btnH, palette.operatorButton, palette.text) { onAction(CalculatorAction.Operation(CalculatorOperation.MULTIPLY)) }
            }

            CalculatorRow(horizontalGap = hGap) {
                NumberButton("4", numberStyle, btnW, btnH, palette.numberButton,   palette.text) { onAction(CalculatorAction.Number(4)) }
                NumberButton("5", numberStyle, btnW, btnH, palette.numberButton,   palette.text) { onAction(CalculatorAction.Number(5)) }
                NumberButton("6", numberStyle, btnW, btnH, palette.numberButton,   palette.text) { onAction(CalculatorAction.Number(6)) }
                OperatorButton("-", actionStyle, btnW, btnH, palette.operatorButton, palette.text) { onAction(CalculatorAction.Operation(CalculatorOperation.SUBTRACT)) }
            }

            CalculatorRow(horizontalGap = hGap) {
                NumberButton("1", numberStyle, btnW, btnH, palette.numberButton,   palette.text) { onAction(CalculatorAction.Number(1)) }
                NumberButton("2", numberStyle, btnW, btnH, palette.numberButton,   palette.text) { onAction(CalculatorAction.Number(2)) }
                NumberButton("3", numberStyle, btnW, btnH, palette.numberButton,   palette.text) { onAction(CalculatorAction.Number(3)) }
                OperatorButton("+", actionStyle, btnW, btnH, palette.operatorButton, palette.text) { onAction(CalculatorAction.Operation(CalculatorOperation.ADD)) }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(hGap, Alignment.CenterHorizontally)
            ) {
                CalculatorButton("0", Modifier.size(wideW, btnH), palette.numberButton,   numberStyle, palette.text) { onAction(CalculatorAction.Number(0)) }
                CalculatorButton("=", Modifier.size(wideW, btnH), palette.operatorButton, actionStyle,  palette.text) { onAction(CalculatorAction.Calculate) }
            }
        }
    }
}

@Composable
private fun CalculatorRow(
    horizontalGap: Dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(horizontalGap, horizontalAlignment),
        content = content
    )
}

@Composable
private fun NumberButton(
    symbol: String,
    style: TextStyle,
    width: Dp,
    height: Dp,
    color: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    CalculatorButton(
        symbol = symbol,
        modifier = Modifier.size(width = width, height = height),
        color = color,
        textStyle = style,
        textColor = textColor
    ) { onClick() }
}

@Composable
private fun OperatorButton(
    symbol: String,
    style: TextStyle,
    width: Dp,
    height: Dp,
    color: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    CalculatorButton(
        symbol = symbol,
        modifier = Modifier.size(width = width, height = height),
        color = color,
        textStyle = style,
        textColor = textColor
    ) { onClick() }
}