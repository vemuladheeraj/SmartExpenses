package com.dheeraj.smartexpenses

import android.Manifest
import android.os.Bundle
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
        if (ok) vm.importRecentSms()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request SMS permissions on first launch
        requestPerms.launch(arrayOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS
        ))

        setContent { App(vm) }
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
