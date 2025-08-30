package com.dheeraj.smartexpenses.security

import android.content.Context
import android.content.SharedPreferences
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class BiometricManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
    
    companion object {
        const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
    }
    
    fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> false
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> false
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> false
            else -> false
        }
    }
    
    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }
    
    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }
    
    fun isAppLockEnabled(): Boolean {
        return prefs.getBoolean(KEY_APP_LOCK_ENABLED, false)
    }
    
    fun setAppLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply()
    }
    
    suspend fun authenticateUser(activity: FragmentActivity): Boolean {
        return suspendCancellableCoroutine { continuation ->
            val executor = ContextCompat.getMainExecutor(context)
            
            val biometricPrompt = BiometricPrompt(activity, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        continuation.resume(false)
                    }
                    
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        continuation.resume(true)
                    }
                    
                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        continuation.resume(false)
                    }
                })
            
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("SmartExpenses Security")
                .setSubtitle("Authenticate to access your financial data")
                .setNegativeButtonText("Cancel")
                .build()
            
            biometricPrompt.authenticate(promptInfo)
            
            continuation.invokeOnCancellation {
                biometricPrompt.cancelAuthentication()
            }
        }
    }
    
    fun showBiometricPrompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFailed: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(context)
        
        val biometricPrompt = BiometricPrompt(activity, executor,
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
                    onFailed()
                }
            })
        
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("SmartExpenses Security")
            .setSubtitle("Authenticate to access your financial data")
            .setNegativeButtonText("Cancel")
            .build()
        
        biometricPrompt.authenticate(promptInfo)
    }
}
