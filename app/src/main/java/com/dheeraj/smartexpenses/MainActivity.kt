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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.dheeraj.smartexpenses.ui.AddManualTxnSheet
import com.dheeraj.smartexpenses.ui.MainNavigation
import com.dheeraj.smartexpenses.ui.HomeVm
import com.dheeraj.smartexpenses.ui.theme.SmartExpensesTheme
import com.dheeraj.smartexpenses.data.AppDb

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
            
            // Force fresh install for development - ensures clean database
            AppDb.forceFreshInstall(this)
            
            // Ensure default data is populated on first install
            CoroutineScope(Dispatchers.IO).launch {
                AppDb.ensureDefaultData(this@MainActivity)
            }
            
            // Model is available locally in assets, so we can proceed normally
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
    
    // Since we have the model locally in assets, request SMS permissions immediately
    LaunchedEffect(context) {
        android.util.Log.d("MainActivity", "Model available locally, requesting SMS permissions")
        requestPerms.launch(arrayOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS
        ))
    }

    SmartExpensesTheme {
        MainNavigation(
            homeVm = vm,
            onAddTransaction = { showManual = true }
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
