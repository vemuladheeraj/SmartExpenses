package com.dheeraj.smartexpenses.ui


import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddManualTxnSheet(
    onSave: (amount: Double, type: String, merchant: String?, channel: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("DEBIT") }
    var merchant by remember { mutableStateOf("") }
    var channel by remember { mutableStateOf("CASH") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp)) {
            Text("Add Transaction", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = amount,
                onValueChange = { v -> amount = v.filter { it.isDigit() || it == '.' } },
                label = { Text("Amount (₹)") })

            Spacer(Modifier.height(8.dp))
            Row {
                TypeChip("DEBIT", type) { type = "DEBIT" }
                Spacer(Modifier.width(8.dp))
                TypeChip("CREDIT", type) { type = "CREDIT" }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = merchant, onValueChange = { merchant = it }, label = { Text("Merchant (optional)") })
            Spacer(Modifier.height(8.dp))
            ChannelDropdown(channel) { channel = it }

            Spacer(Modifier.height(16.dp))
            Button(
                enabled = amount.toDoubleOrNull() != null,
                onClick = {
                    onSave(amount.toDouble(), type, merchant.ifBlank { null }, channel.ifBlank { null })
                    onDismiss()
                }
            ) { Text("Save") }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TypeChip(title: String, current: String, onClick: () -> Unit) {
    FilterChip(selected = current == title, onClick = onClick, label = { Text(title) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelDropdown(selected: String, onChange: (String) -> Unit) {
    val options = listOf("CASH","UPI","CARD","IMPS","NEFT","POS","WALLET","OTHER")
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selected, 
            onValueChange = {}, 
            readOnly = true, 
            label = { Text("Channel") }, 
            modifier = Modifier.menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt -> DropdownMenuItem(text = { Text(opt) }, onClick = { onChange(opt); expanded = false }) }
        }
    }
}
