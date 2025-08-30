package com.dheeraj.smartexpenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dheeraj.smartexpenses.data.Budget
import com.dheeraj.smartexpenses.data.BudgetAnalysis
import com.dheeraj.smartexpenses.data.monthlyLimitAmount
import com.dheeraj.smartexpenses.ui.theme.*
import java.text.NumberFormat
import java.util.*

@Composable
fun BudgetScreen(
    viewModel: BudgetViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")) }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Text(
                text = "Budget Management",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Set and track your spending limits",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Budget Overview Card
        item {
            BudgetOverviewCard(
                totalBudget = uiState.totalBudget,
                totalSpent = uiState.totalSpent,
                totalRemaining = uiState.totalRemaining,
                currencyFormat = currencyFormat
            )
        }
        
        // Add Budget Button
        item {
            Button(
                onClick = { viewModel.showAddBudgetDialog() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Budget")
            }
        }
        
        // Budget Analysis List
        items(uiState.budgetAnalysis) { analysis ->
            BudgetAnalysisCard(
                analysis = analysis,
                currencyFormat = currencyFormat,
                onEdit = { viewModel.editBudget(analysis.category) },
                onDelete = { viewModel.deleteBudget(analysis.category) }
            )
        }
        
        // Empty State
        if (uiState.budgetAnalysis.isEmpty()) {
            item {
                EmptyBudgetState(
                    onAddBudget = { viewModel.showAddBudgetDialog() }
                )
            }
        }
    }
    
    // Add/Edit Budget Dialog
    if (uiState.showAddBudgetDialog) {
        AddBudgetDialog(
            budget = uiState.editingBudget,
            onDismiss = { viewModel.hideAddBudgetDialog() },
            onSave = { category, amount ->
                viewModel.saveBudget(category, amount)
            }
        )
    }
}

@Composable
fun BudgetOverviewCard(
    totalBudget: Double,
    totalSpent: Double,
    totalRemaining: Double,
    currencyFormat: NumberFormat
) {
    val percentageUsed = if (totalBudget > 0) (totalSpent / totalBudget) * 100 else 0.0
    val isOverBudget = totalSpent > totalBudget
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                "Monthly Budget Overview",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            // Progress Bar
            LinearProgressIndicator(
                progress = (percentageUsed / 100).toFloat().coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = when {
                    isOverBudget -> ErrorRed
                    percentageUsed >= 80 -> WarningOrange
                    else -> SuccessGreen
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BudgetStat(
                    label = "Budget",
                    value = currencyFormat.format(totalBudget),
                    color = MaterialTheme.colorScheme.primary
                )
                BudgetStat(
                    label = "Spent",
                    value = currencyFormat.format(totalSpent),
                    color = if (isOverBudget) ErrorRed else MaterialTheme.colorScheme.onSurface
                )
                BudgetStat(
                    label = "Remaining",
                    value = currencyFormat.format(totalRemaining),
                    color = if (totalRemaining < 0) ErrorRed else SuccessGreen
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "${String.format("%.1f", percentageUsed)}% used",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun BudgetStat(
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun BudgetAnalysisCard(
    analysis: BudgetAnalysis,
    currencyFormat: NumberFormat,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val categoryColor = getCategoryColor(analysis.category)
    val categoryIcon = getCategoryIcon(analysis.category)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(categoryColor.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = categoryIcon,
                            contentDescription = analysis.category,
                            tint = categoryColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            analysis.category,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${String.format("%.1f", analysis.percentageUsed)}% used",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Progress Bar
            LinearProgressIndicator(
                progress = (analysis.percentageUsed / 100).toFloat().coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = when {
                    analysis.isOverBudget -> ErrorRed
                    analysis.isNearLimit -> WarningOrange
                    else -> SuccessGreen
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Limit: ${currencyFormat.format(analysis.monthlyLimitAmount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Spent: ${currencyFormat.format(analysis.spentAmountValue)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (analysis.isOverBudget) ErrorRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Left: ${currencyFormat.format(analysis.remainingAmountValue)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (analysis.remainingAmountValue < 0) ErrorRed else SuccessGreen
                )
            }
            
            // Alert for over budget
            if (analysis.isOverBudget) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Over budget by ${currencyFormat.format(-analysis.remainingAmountValue)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyBudgetState(
    onAddBudget: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.AccountBalanceWallet,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No Budgets Set",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Set spending limits for different categories to track your expenses better",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onAddBudget,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Set Your First Budget")
            }
        }
    }
}

@Composable
fun AddBudgetDialog(
    budget: Budget?,
    onDismiss: () -> Unit,
    onSave: (String, Double) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(budget?.category ?: "Food") }
    var amountText by remember { mutableStateOf(budget?.monthlyLimitAmount?.toString() ?: "") }
    var amountError by remember { mutableStateOf<String?>(null) }
    
    val categories = listOf("Food", "Transport", "Shopping", "Entertainment", "Bills", "Health", "Education", "Other")
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (budget == null) "Add Budget" else "Edit Budget")
        },
        text = {
            Column {
                // Category Selection
                Text(
                    "Category",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category) },
                            leadingIcon = {
                                Icon(
                                    getCategoryIcon(category),
                                    contentDescription = null,
                                    tint = if (selectedCategory == category) Color.White else getCategoryColor(category)
                                )
                            }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Amount Input
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { 
                        amountText = it
                        amountError = null
                    },
                    label = { Text("Monthly Limit (₹)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    ),
                    isError = amountError != null,
                    supportingText = amountError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    if (amount == null || amount <= 0) {
                        amountError = "Please enter a valid amount"
                        return@TextButton
                    }
                    onSave(selectedCategory, amount)
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
