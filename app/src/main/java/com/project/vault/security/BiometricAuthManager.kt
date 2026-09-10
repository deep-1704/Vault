package com.project.vault.security

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages biometric and device credential authentication using AndroidX [BiometricPrompt].
 *
 * Supports fingerprint / strong biometrics with device PIN/pattern/password fallback.
 *
 * A successful authentication is cached in-memory for [SESSION_DURATION_MS] (10 seconds).
 * Subsequent calls to [authenticate] within that window skip the system prompt and invoke
 * [onSuccess] immediately, so the user isn't re-prompted for rapid successive actions
 * (e.g. viewing then syncing a credential). The session resets on process death.
 */
@Singleton
class BiometricAuthManager @Inject constructor() {

    /**
     * Monotonic timestamp (via [SystemClock.elapsedRealtime]) of the last successful
     * biometric authentication. 0 means no auth has occurred yet in this process.
     */
    @Volatile
    private var lastAuthTimestamp: Long = 0L

    /** Returns true if a successful auth was recorded within the last [SESSION_DURATION_MS]. */
    private fun isSessionValid(): Boolean =
        lastAuthTimestamp != 0L &&
            (SystemClock.elapsedRealtime() - lastAuthTimestamp) < SESSION_DURATION_MS

    sealed class BiometricStatus {
        object Ready : BiometricStatus()
        object NoneEnrolled : BiometricStatus()
        object HardwareUnavailable : BiometricStatus()
        object Unsupported : BiometricStatus()
    }

    /**
     * Evaluates whether the device has biometric or screen lock hardware and registered credentials.
     */
    fun canAuthenticate(context: Context): BiometricStatus {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.Ready
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NoneEnrolled
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.HardwareUnavailable
            else -> BiometricStatus.Unsupported
        }
    }

    /**
     * Prompts the user with [BiometricPrompt] to authenticate, unless a valid biometric
     * session is already active (last successful auth within [SESSION_DURATION_MS]).
     *
     * @param fragment The calling fragment hosting the lifecycle.
     * @param title Title displayed in the system biometric dialog.
     * @param subtitle Subtitle description displayed in the dialog.
     * @param onSuccess Callback executed when authentication succeeds (or session is still valid).
     * @param onError Callback executed when authentication fails or is cancelled.
     */
    fun authenticate(
        fragment: Fragment,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (errorMessage: String) -> Unit
    ) {
        // Bypass the prompt if the user authenticated recently.
        if (isSessionValid()) {
            onSuccess()
            return
        }

        val executor = ContextCompat.getMainExecutor(fragment.requireContext())

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                lastAuthTimestamp = SystemClock.elapsedRealtime()
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Called on a single rejected attempt (e.g. wrong finger), dialog remains open
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()

        val biometricPrompt = BiometricPrompt(fragment, executor, callback)
        biometricPrompt.authenticate(promptInfo)
    }

    /**
     * Opens system settings to guide the user to enroll biometrics or set up a screen lock.
     */
    fun openEnrollmentSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, AUTHENTICATORS)
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure {
                // Fallback to general security settings if biometric enroll intent is not supported
                val fallbackIntent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            }
    }

    companion object {
        const val AUTHENTICATORS = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

        /** Duration (ms) for which a successful biometric auth remains valid. */
        const val SESSION_DURATION_MS = 10_000L
    }
}
