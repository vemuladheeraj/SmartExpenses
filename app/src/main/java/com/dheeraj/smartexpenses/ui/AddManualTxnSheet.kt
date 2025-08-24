package com.dheeraj.smartexpenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dheeraj.smartexpenses.ui.theme.*

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
    var selectedCategory by remember { mutableStateOf("Other") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Add Transaction",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close")
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Amount Input
            Text(
                "Amount",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = amount,
                onValueChange = { v ->
                    val parts = v.split('.')
                    val filteredValue = if (parts.size > 1) {
                        parts[0] + "." + parts.subList(1, parts.size).joinToString("").filter { it.isDigit() }
                    } else {
                        v.filter { it.isDigit() }
                    }
                    if (filteredValue.count { it == '.' } <= 1) {
                        amount = filteredValue
                    } else if (!filteredValue.contains('.')) {
                        amount = filteredValue
                    }
                },
                label = { Text("₹0.00") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                prefix = { Text("₹", style = MaterialTheme.typography.titleLarge) },
                textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Transaction Type
            Text(
                "Transaction Type",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TypeChip(
                    title = "Expense",
                    icon = Icons.Outlined.Remove,
                    selected = type == "DEBIT",
                    onClick = { type = "DEBIT" },
                    modifier = Modifier.weight(1f)
                )
                TypeChip(
                    title = "Income",
                    icon = Icons.Outlined.Add,
                    selected = type == "CREDIT",
                    onClick = { type = "CREDIT" },
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Category Selection
            Text(
                "Category",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            CategoryGrid(
                selectedCategory = selectedCategory,
                onCategorySelected = { selectedCategory = it }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Merchant Input
            Text(
                "Merchant/Description",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text("e.g., Starbucks, Uber, etc.") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(Icons.Outlined.Store, "Merchant")
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Payment Method
            Text(
                "Payment Method",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            ChannelDropdown(
                selected = channel,
                onChange = { channel = it },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Save Button
            Button(
                enabled = amount.toDoubleOrNull() != null && amount.isNotBlank(),
                onClick = {
                    onSave(
                        amount.toDouble(),
                        type,
                        merchant.ifBlank { null },
                        channel.ifBlank { null }
                    )
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Save, "Save", modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Transaction", fontWeight = FontWeight.SemiBold)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TypeChip(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, title, modifier = Modifier.size(16.dp))
                Text(title)
            }
        },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
private fun CategoryGrid(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    val categories = remember {
        listOf(
            "Food" to Icons.Outlined.Restaurant,
            "Transport" to Icons.Outlined.DirectionsCar,
            "Shopping" to Icons.Outlined.ShoppingCart,
            "Entertainment" to Icons.Outlined.Movie,
            "Bills" to Icons.Outlined.Receipt,
            "Health" to Icons.Outlined.LocalHospital,
            "Education" to Icons.Outlined.School,
            "Other" to Icons.Outlined.AccountBalance
        )
    }
    
    val categoryColors = remember {
        mapOf(
            "Food" to CategoryFood,
            "Transport" to CategoryTransport,
            "Shopping" to CategoryShopping,
            "Entertainment" to CategoryEntertainment,
            "Bills" to CategoryBills,
            "Health" to CategoryHealth,
            "Education" to CategoryEducation,
            "Other" to CategoryOther
        )
    }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (row in categories.chunked(4)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for ((category, icon) in row) {
                    CategoryChip(
                        category = category,
                        icon = icon,
                        color = categoryColors[category] ?: CategoryOther,
                        selected = selectedCategory == category,
                        onClick = { onCategorySelected(category) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    category: String,
    icon: ImageVector,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) color else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 4.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color.White else color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = category,
                    tint = if (selected) color else color,
                    modifier = Modifier.size(16.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = category,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelDropdown(
    selected: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = remember { 
        listOf("CASH", "UPI", "CARD", "IMPS", "NEFT", "POS", "WALLET", "OTHER") 
    }
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("Payment Method") },
            leadingIcon = {
                Icon(Icons.Outlined.Payment, "Payment Method")
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = {
                        onChange(opt)
                        expanded = false
                    }
                )
            }
        }
    }
}
