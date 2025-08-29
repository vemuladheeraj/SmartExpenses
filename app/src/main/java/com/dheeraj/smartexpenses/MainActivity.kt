package com.dheeraj.smartexpenses

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.dheeraj.smartexpenses.ui.AddManualTxnSheet
import com.dheeraj.smartexpenses.ui.MainNavigation
import com.dheeraj.smartexpenses.ui.HomeVm
import com.dheeraj.smartexpenses.ui.theme.SmartExpensesTheme
import com.dheeraj.smartexpenses.sms.ModelDownloadDialog
import com.dheeraj.smartexpenses.sms.ModelDownloadHelper

class MainActivity : ComponentActivity() {

    private val vm: HomeVm by viewModels()

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
            
            // Don't request SMS permissions immediately - wait for AI model download to complete
            // The permissions will be requested after the download dialog is dismissed

            Log.d("MainActivity", "Setting content...")
            setContent { App(vm, requestPerms) }
            
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
    requestPerms: androidx.activity.result.ActivityResultLauncher<Array<String>>
) {
    val context = LocalContext.current
    
    var showManual by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { 
        mutableStateOf(ModelDownloadHelper.shouldShowDownloadDialog(context))
    }
    
    // Check if AI model download dialog should be shown - make it reactive
    val shouldShowDownloadDialog = remember {
        mutableStateOf(ModelDownloadHelper.shouldShowDownloadDialog(context))
    }
    
    // Update the state when context changes and also when the dialog is dismissed
    LaunchedEffect(context) {
        val shouldShow = ModelDownloadHelper.shouldShowDownloadDialog(context)
        android.util.Log.d("MainActivity", "Initial download state check: shouldShow=$shouldShow")
        shouldShowDownloadDialog.value = shouldShow
        showDownloadDialog = shouldShow
    }
    
    // If model is already downloaded, proceed to SMS permissions
    LaunchedEffect(shouldShowDownloadDialog.value) {
        if (!shouldShowDownloadDialog.value) {
            android.util.Log.d("MainActivity", "Model already downloaded, proceeding to SMS permissions")
            // Model is already downloaded, request SMS permissions
            requestPerms.launch(arrayOf(
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS
            ))
        }
    }
    
    // Function to update download state
    fun updateDownloadState() {
        val shouldShow = ModelDownloadHelper.shouldShowDownloadDialog(context)
        android.util.Log.d("MainActivity", "updateDownloadState: shouldShow=$shouldShow")
        shouldShowDownloadDialog.value = shouldShow
        showDownloadDialog = shouldShow
    }

    SmartExpensesTheme {
        MainNavigation(
            homeVm = vm,
            onAddTransaction = { showManual = true }
        )

        // Show AI model download dialog if needed
        if (shouldShowDownloadDialog.value && showDownloadDialog) {
            ModelDownloadDialog(
                onDismiss = {
                    showDownloadDialog = false
                    updateDownloadState() // Update the download state
                    // After download dialog is dismissed, request SMS permissions
                    requestPerms.launch(arrayOf(
                        Manifest.permission.READ_SMS,
                        Manifest.permission.RECEIVE_SMS
                    ))
                }
            )
        }

        if (showManual) {
            AddManualTxnSheet(
                onSave = { amount, type, merchant, channel ->
                    vm.addManual(amount, type, merchant, channel)
                    showManual = false
                },
                onDismiss = { showManual = false }
            )
        }
    }
}
