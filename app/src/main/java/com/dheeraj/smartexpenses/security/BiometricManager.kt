package com.dheeraj.smartexpenses.security

import android.content.Context
import android.content.SharedPreferences
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import android.util.Log

class BiometricManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
    
    companion object {
        const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
    }
    
    fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(context)
        val result = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        Log.d("BiometricManager", "Biometric availability check result: $result")
        return when (result) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                Log.d("BiometricManager", "Biometric is available and ready")
                true
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                Log.w("BiometricManager", "No biometric hardware available")
                false
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                Log.w("BiometricManager", "Biometric hardware unavailable")
                false
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                Log.w("BiometricManager", "No biometric enrolled")
                false
            }
            else -> {
                Log.w("BiometricManager", "Unknown biometric error: $result")
                false
            }
        }
    }
    
    fun isBiometricEnabled(): Boolean {
        val enabled = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
        Log.d("BiometricManager", "isBiometricEnabled: $enabled")
        return enabled
    }
    
    fun setBiometricEnabled(enabled: Boolean) {
        Log.d("BiometricManager", "setBiometricEnabled: $enabled")
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }
    
    fun isAppLockEnabled(): Boolean {
        val enabled = prefs.getBoolean(KEY_APP_LOCK_ENABLED, false)
        Log.d("BiometricManager", "isAppLockEnabled: $enabled")
        return enabled
    }
    
    fun setAppLockEnabled(enabled: Boolean) {
        Log.d("BiometricManager", "setAppLockEnabled: $enabled")
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
                    Log.w("BiometricManager", "Authentication error: $errorCode - $errString")
                    
                    when (errorCode) {
                        BiometricPrompt.ERROR_CANCELED -> {
                            Log.d("BiometricManager", "Authentication canceled by user")
                            // User canceled, don't call onError
                        }
                        BiometricPrompt.ERROR_LOCKOUT -> {
                            Log.w("BiometricManager", "Biometric lockout")
                            onError("Too many failed attempts. Try again later.")
                        }
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                            Log.w("BiometricManager", "Permanent biometric lockout")
                            onError("Biometric permanently locked. Use device security.")
                        }
                        BiometricPrompt.ERROR_USER_CANCELED -> {
                            Log.d("BiometricManager", "User canceled authentication")
                            // User canceled, don't call onError
                        }
                        else -> {
                            onError(errString.toString())
                        }
                    }
                }
                
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Log.d("BiometricManager", "Authentication succeeded")
                    onSuccess()
                }
                
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Log.w("BiometricManager", "Authentication failed")
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
