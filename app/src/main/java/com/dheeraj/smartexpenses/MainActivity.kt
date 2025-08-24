package com.dheeraj.smartexpenses


import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dheeraj.smartexpenses.data.Transaction
import com.dheeraj.smartexpenses.ui.AddManualTxnSheet
import com.dheeraj.smartexpenses.ui.HomeVm
import com.dheeraj.smartexpenses.ui.theme.SmartExpensesTheme
import java.text.SimpleDateFormat
import java.util.*

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: HomeVm) {
    val items by vm.items.collectAsState()
    val debit by vm.totalDebit.collectAsState()
    val credit by vm.totalCredit.collectAsState()

    var showManual by remember { mutableStateOf(false) }

    SmartExpensesTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("SmartExpenses") }) },
            floatingActionButton = {
                FloatingActionButton(onClick = { showManual = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add manual")
                }
            }
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                SummaryCard(debit = debit, credit = credit)
                TxnList(items)
            }
        }

        if (showManual) AddManualTxnSheet(
            onSave = { amount, type, merchant, channel ->
                vm.addManual(amount, type, merchant, channel)
                showManual = false
            },
            onDismiss = { showManual = false }
        )
    }
}

@Composable
private fun SummaryCard(debit: Double, credit: Double) {
    Card(Modifier.padding(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("This Month", style = MaterialTheme.typography.titleMedium)
            Text("Debits: ₹${"%.0f".format(debit)}", style = MaterialTheme.typography.titleLarge)
            Text("Credits: ₹${"%.0f".format(credit)}")
        }
    }
}

@Composable
private fun TxnList(items: List<Transaction>) {
    val fmt = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }
    LazyColumn(Modifier.fillMaxSize()) {
        items(items) { t ->
            ListItem(
                headlineContent = {
                    Text("${t.type} • ₹${"%.0f".format(t.amount)} ${t.channel?.let { "• $it" } ?: ""}")
                },
                supportingContent = {
                    val parts = listOfNotNull(
                        t.merchant,
                        t.bank,
                        t.accountTail?.let { "A/c ****$it" },
                        t.source
                    )
                    Text(parts.joinToString(" • "))
                },
                trailingContent = { Text(fmt.format(Date(t.ts))) }
            )
            HorizontalDivider()
        }
    }
}
