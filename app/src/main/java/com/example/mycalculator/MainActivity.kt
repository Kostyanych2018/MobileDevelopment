package com.example.mycalculator

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mycalculator.data.MyFirebaseMessagingService
import com.example.mycalculator.data.SecurityRepository
import com.example.mycalculator.data.ThemeRepository
import com.example.mycalculator.ui.AuthManager
import com.example.mycalculator.ui.CalculatorScreen
import com.example.mycalculator.ui.CalculatorViewModel
import com.example.mycalculator.ui.CalculatorViewModelFactory
import com.example.mycalculator.ui.theme.CalculatorPalette
import com.example.mycalculator.ui.theme.MyCalculatorTheme
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        createNotificationChannel()
        logFcmToken()

        val securityRepository = SecurityRepository(applicationContext)
        val authManager = AuthManager(this)
        val viewModelFactory = CalculatorViewModelFactory(securityRepository)

        setContent {
            MyCalculatorTheme {
                var palette by remember { mutableStateOf(CalculatorPalette.defaults()) }
                val themeRepository = remember { ThemeRepository() }
                val viewModel: CalculatorViewModel = viewModel(factory = viewModelFactory)

                LaunchedEffect(Unit) {
                    themeRepository.loadTheme(
                        onSuccess = { remote ->
                            Log.d("ThemeDebug", "Remote theme payload: $remote")
                            val parsed = CalculatorPalette.fromRemote(remote)
                            Log.d("ThemeDebug", "Parsed palette: $parsed")
                            palette = parsed
                        },
                        onError = {
                            Log.e("ThemeDebug", "Theme load failed", it)
                        }
                    )
                }

                NotificationPermissionHandler()

                val statusBarArgb = palette.statusBar.toArgb()
                val useDarkIcons = palette.statusBar.luminance() > 0.5f
                SideEffect {
                    applySystemBars(
                        statusBarArgb = statusBarArgb,
                        lightIcons = useDarkIcons
                    )
                }

                CalculatorScreen(
                    state = viewModel.state,
                    onAction = viewModel::onAction,
                    palette = palette,
                    isUnlocked = viewModel.isUnlocked,
                    hasPin = viewModel.hasPin,
                    authManager = authManager,
                    onSetupPin = viewModel::setupPin,
                    onVerifyPin = viewModel::verifyPin,
                    onUnlockSuccess = viewModel::unlock,
                    onResetAndWipe = viewModel::resetPinAndWipe
                )
            }
        }
    }

    private fun applySystemBars(statusBarArgb: Int, lightIcons: Boolean) {
        enableEdgeToEdge(
            statusBarStyle = if (lightIcons) {
                SystemBarStyle.light(statusBarArgb, statusBarArgb)
            } else {
                SystemBarStyle.dark(statusBarArgb)
            }
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                MyFirebaseMessagingService.CHANNEL_ID,
                "Calculator Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Push notifications from the Calculator app"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun logFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCMService", "Fetching FCM token failed", task.exception)
                return@addOnCompleteListener
            }
            Log.d("FCMService", "Current FCM token: ${task.result}")
        }
    }
}

@Composable
private fun NotificationPermissionHandler() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d("FCMService", "POST_NOTIFICATIONS granted=$granted")
    }

    LaunchedEffect(Unit) {
        val alreadyGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!alreadyGranted) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
