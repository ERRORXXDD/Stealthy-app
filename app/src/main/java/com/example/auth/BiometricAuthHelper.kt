package com.example.auth

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricAuthHelper {

    fun isBiometricAvailable(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            @Suppress("DEPRECATION")
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        }
        val status = biometricManager.canAuthenticate(authenticators)
        return status == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun showBiometricPrompt(
        activity: FragmentActivity,
        title: String = "Secret Vault Access",
        subtitle: String = "Scan fingerprint or face to verify identity",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            onError("Context is not active.")
            return
        }

        val state = activity.lifecycle.currentState
        if (!state.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            onError("Authentication window is busy. Please try again.")
            return
        }

        try {
            val executor = ContextCompat.getMainExecutor(activity)
            val biometricPrompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        onError(errString.toString())
                    }

                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        onSuccess()
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        onError("Recognition failed. Please try again.")
                    }
                }
            )

            val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                promptInfoBuilder.setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
            } else {
                promptInfoBuilder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                promptInfoBuilder.setNegativeButtonText("Use Pattern / PIN")
            }

            biometricPrompt.authenticate(promptInfoBuilder.build())
        } catch (e: Exception) {
            onError(e.localizedMessage ?: "Device authentication error")
        }
    }
}
