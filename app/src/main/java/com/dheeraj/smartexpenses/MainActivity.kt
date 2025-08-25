package com.dheeraj.smartexpenses

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import com.dheeraj.smartexpenses.ui.AddManualTxnSheet
import com.dheeraj.smartexpenses.ui.MainNavigation
import com.dheeraj.smartexpenses.ui.HomeVm
import com.dheeraj.smartexpenses.ui.theme.SmartExpensesTheme

class MainActivity : ComponentActivity() {

    private val vm: HomeVm by viewModels()

    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val ok = (granted[Manifest.permission.READ_SMS] == true) &&
                (granted[Manifest.permission.RECEIVE_SMS] == true)
        if (ok) {
            try {
                Log.d("MainActivity", "Starting SMS import...")
                vm.importRecentSms()
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
            
            // Request SMS permissions on first launch
            requestPerms.launch(arrayOf(
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS
            ))

            Log.d("MainActivity", "Setting content...")
            setContent { App(vm) }
            
            Log.d("MainActivity", "onCreate completed")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate", e)
            e.printStackTrace()
        }
    }
}

@Composable
fun App(vm: HomeVm) {
    var showManual by remember { mutableStateOf(false) }

    SmartExpensesTheme {
        MainNavigation(
            homeVm = vm,
            onAddTransaction = { showManual = true }
        )

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
