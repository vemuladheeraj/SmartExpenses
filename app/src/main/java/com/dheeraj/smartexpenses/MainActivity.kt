package com.dheeraj.smartexpenses

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.dheeraj.smartexpenses.ui.AddManualTxnSheet
import com.dheeraj.smartexpenses.ui.MainNavigation
import com.dheeraj.smartexpenses.ui.HomeVm
import com.dheeraj.smartexpenses.ui.theme.SmartExpensesTheme
import com.dheeraj.smartexpenses.data.AppDb
import com.dheeraj.smartexpenses.security.BiometricManager
import kotlinx.coroutines.delay


class MainActivity : FragmentActivity() {

    private val vm: HomeVm by viewModels()
    private lateinit var biometricManager: BiometricManager

    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val ok = (granted[Manifest.permission.READ_SMS] == true) &&
                (granted[Manifest.permission.RECEIVE_SMS] == true)
        if (ok) {
            try {
                Log.d("MainActivity", "Permissions granted; triggering importIfFirstRun()")
                vm.importIfFirstRun()
            } catch (e: Exception) {
                Log.e("MainActivity", "Error during SMS import", e)
                e.printStackTrace()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            Log.d("MainActivity", "onCreate started")
            
            // Initialize biometric manager
            biometricManager = BiometricManager(this)
            
            // Ensure default data is populated on first install
            CoroutineScope(Dispatchers.IO).launch {
                AppDb.ensureDefaultData(this@MainActivity)
            }
            
            // Model is available locally in assets, so we can proceed normally
            Log.d("MainActivity", "Setting content...")
            setContent { App(vm, requestPerms, biometricManager) }
            
            Log.d("MainActivity", "onCreate completed")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate", e)
            e.printStackTrace()
        }
    }
}

@Composable
fun App(
    vm: HomeVm, 
    requestPerms: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    biometricManager: BiometricManager
) {
    val context = LocalContext.current
    
    var showManual by remember { mutableStateOf(false) }
    var isAuthenticated by remember { mutableStateOf(false) }
    var showBiometricPrompt by remember { mutableStateOf(false) }
    
    // Check if app lock is enabled - make it reactive
    var appLockEnabled by remember { mutableStateOf(biometricManager.isAppLockEnabled()) }
    var biometricEnabled by remember { mutableStateOf(biometricManager.isBiometricEnabled()) }
    var biometricAvailable by remember { mutableStateOf(biometricManager.isBiometricAvailable()) }
    
    // Update biometric settings when the composable recomposes
    LaunchedEffect(Unit) {
        appLockEnabled = biometricManager.isAppLockEnabled()
        biometricEnabled = biometricManager.isBiometricEnabled()
        biometricAvailable = biometricManager.isBiometricAvailable()
        
        Log.d("MainActivity", "=== BIOMETRIC DEBUG ===")
        Log.d("MainActivity", "App Lock Enabled: $appLockEnabled")
        Log.d("MainActivity", "Biometric Enabled: $biometricEnabled")
        Log.d("MainActivity", "Biometric Available: $biometricAvailable")
        Log.d("MainActivity", "Is Authenticated: $isAuthenticated")
        Log.d("MainActivity", "========================")
    }
    
    // Refresh biometric settings when the app comes to foreground
    LaunchedEffect(Unit) {
        // This will run when the composable is first created
        // We'll also need to refresh when returning from settings
    }
    
    // Show biometric prompt if app lock is enabled and not authenticated
    LaunchedEffect(appLockEnabled, biometricEnabled, biometricAvailable) {
        Log.d("MainActivity", "LaunchedEffect triggered - appLockEnabled: $appLockEnabled, isAuthenticated: $isAuthenticated")
        if (appLockEnabled && !isAuthenticated) {
            Log.d("MainActivity", "Setting showBiometricPrompt = true")
            showBiometricPrompt = true
        } else {
            Log.d("MainActivity", "Not showing biometric prompt - appLockEnabled: $appLockEnabled, isAuthenticated: $isAuthenticated")
        }
    }
    
    // Since we have the model locally in assets, request SMS permissions immediately
    LaunchedEffect(context) {
        android.util.Log.d("MainActivity", "Model available locally, requesting SMS permissions")
        requestPerms.launch(arrayOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS
        ))
    }

    // Biometric authentication prompt
    if (showBiometricPrompt && appLockEnabled) {
        LaunchedEffect(Unit) {
            try {
                biometricManager.showBiometricPrompt(
                    activity = context as androidx.fragment.app.FragmentActivity,
                    onSuccess = {
                        Log.d("MainActivity", "Biometric authentication successful")
                        isAuthenticated = true
                        showBiometricPrompt = false
                    },
                    onError = { error ->
                        Log.e("MainActivity", "Biometric error: $error")
                        // Check if biometric is available, if not, allow access
                        if (biometricManager.isBiometricAvailable()) {
                            // Biometric available but error occurred, retry on next launch
                            showBiometricPrompt = false
                        } else {
                            // Biometric not available, allow access
                            Log.w("MainActivity", "Biometric not available, allowing access")
                            isAuthenticated = true
                            showBiometricPrompt = false
                        }
                    },
                    onFailed = {
                        Log.w("MainActivity", "Biometric authentication failed")
                        // Authentication failed, keep showing prompt
                        // User can retry or cancel
                    }
                )
            } catch (e: Exception) {
                Log.e("MainActivity", "Error showing biometric prompt", e)
                // On error, allow access to prevent app lockout
                isAuthenticated = true
                showBiometricPrompt = false
            }
        }
    }
    
    // Handle biometric prompt cancellation
    LaunchedEffect(showBiometricPrompt) {
        if (!showBiometricPrompt && appLockEnabled && !isAuthenticated) {
            // User canceled biometric prompt, allow access after a delay
            delay(100)
            isAuthenticated = true
        }
    }

    // Only show main content if authenticated or app lock is disabled
    if (!appLockEnabled || isAuthenticated) {
        SmartExpensesTheme {
            MainNavigation(
                homeVm = vm,
                onAddTransaction = { showManual = true },
                onSettingsChanged = {
                    // Refresh biometric settings when returning from settings
                    val newAppLockEnabled = biometricManager.isAppLockEnabled()
                    val newBiometricEnabled = biometricManager.isBiometricEnabled()
                    val newBiometricAvailable = biometricManager.isBiometricAvailable()
                    
                    appLockEnabled = newAppLockEnabled
                    biometricEnabled = newBiometricEnabled
                    biometricAvailable = newBiometricAvailable
                    
                    // If app lock was just enabled, show biometric prompt immediately
                    if (newAppLockEnabled && !isAuthenticated) {
                        showBiometricPrompt = true
                    }
                }
            )

            if (showManual) {
                AddManualTxnSheet(
                    onSave = { amount, type, merchant, channel, category ->
                        vm.addManual(amount, type, merchant, channel, category)
                        showManual = false
                    },
                    onDismiss = { showManual = false }
                )
            }
        }
    }
}
