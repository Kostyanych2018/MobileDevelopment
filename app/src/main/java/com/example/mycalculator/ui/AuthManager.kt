package com.example.mycalculator.ui

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class AuthManager(private val activity: FragmentActivity) {

    private val allowedAuthenticators =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun canUseBiometrics(): Boolean {
        val manager = BiometricManager.from(activity)
        return manager.canAuthenticate(allowedAuthenticators) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(
        title: String = "Unlock feature",
        subtitle: String = "Use your biometric to continue",
        negativeButtonText: String = "Use PIN",
        onSuccess: () -> Unit,
        onFallbackToPin: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                when (errorCode) {
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_USER_CANCELED -> onFallbackToPin()
                    else -> onError(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(allowedAuthenticators)
            .build()

        prompt.authenticate(info)
    }

    companion object {
        fun isHardwareAvailable(context: Context): Boolean {
            val manager = BiometricManager.from(context)
            val status = manager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            return status == BiometricManager.BIOMETRIC_SUCCESS ||
                    status == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
        }
    }
}
