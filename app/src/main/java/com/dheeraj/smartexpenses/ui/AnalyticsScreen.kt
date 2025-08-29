package com.dheeraj.smartexpenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dheeraj.smartexpenses.data.Transaction
import com.dheeraj.smartexpenses.sms.SmsClassifierTest
import com.dheeraj.smartexpenses.ui.theme.*
import java.text.NumberFormat
import java.util.*
import com.dheeraj.smartexpenses.data.amount
import android.util.Log

// Chart imports temporarily disabled for multi-task model integration

@Composable
fun AnalyticsScreen(homeVm: HomeVm) {
    val allTransactions by homeVm.allItems.collectAsState()
    var selectedMonths by remember { mutableStateOf(setOf(0)) } // default to current month
    val monthRangeToShow = 12
    var fromDateMillis by remember { mutableStateOf<Long?>(null) }
    var toDateMillis by remember { mutableStateOf<Long?>(null) }
    var showMonthlyOverview by remember { mutableStateOf(false) }
    var showRangeOverview by remember { mutableStateOf(true) }
    var showInsights by remember { mutableStateOf(true) }
    var showCategories by remember { mutableStateOf(true) }
    var showCharts by remember { mutableStateOf(true) }
    var showAdvancedAnalytics by remember { mutableStateOf(true) }

    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")) }

    val now = System.currentTimeMillis()
    // Build a label from selected chips like: Jan '25, Feb '25
    val selectedChipsLabel = remember(selectedMonths) {
        if (selectedMonths.isEmpty()) return@remember "Last 3 months"
        val cal = Calendar.getInstance()
        val labels = selectedMonths.toList().sorted().map { idx ->
            cal.timeInMillis = System.currentTimeMillis()
            cal.add(Calendar.MONTH, -idx)
            val m = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()) ?: ""
            val y = (cal.get(Calendar.YEAR) % 100).toString().padStart(2, '0')
            "$m '$y"
        }
        labels.joinToString(", ")
    }

    val rangeTransactions = remember(allTransactions, selectedMonths, now) {
        val cal = Calendar.getInstance()
        allTransactions.filter { t ->
            if (selectedMonths.isNotEmpty()) {
                selectedMonths.any { idx ->
                    cal.timeInMillis = now
                    cal.add(Calendar.MONTH, -idx)
                    val selMonth = cal.get(Calendar.MONTH)
                    val selYear = cal.get(Calendar.YEAR)
                    val txCal = Calendar.getInstance().apply { timeInMillis = t.ts }
                    txCal.get(Calendar.MONTH) == selMonth && txCal.get(Calendar.YEAR) == selYear
                }
            } else {
                // Default to last 3 months when none selected
                val cutoff = Calendar.getInstance().apply { add(Calendar.MONTH, -3) }.timeInMillis
                t.ts >= cutoff
            }
        }
    }

    // Exclude inter-account transfers and paired same-amount debit/credit
    val pairedIds = remember(rangeTransactions) { findPairedTransferIds(rangeTransactions) }
    val totalCredit = remember(rangeTransactions) {
        rangeTransactions
            .filter { it.type == "CREDIT" && !isInterAccountTransfer(it) && it.id !in pairedIds }
            .sumOf { it.amount }
    }
    val totalDebit  = remember(rangeTransactions) {
        rangeTransactions
            .filter { it.type == "DEBIT" && !isInterAccountTransfer(it) && it.id !in pairedIds }
            .sumOf { it.amount }
    }
    val balance     = totalCredit - totalDebit

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Analytics", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Track your spending patterns", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Removed AI model test section to declutter analytics

        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        repeat(monthRangeToShow) { idx ->
                            val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -idx) }
                            val label = (cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()) ?: "Unknown") + " '" + (cal.get(Calendar.YEAR) % 100)
                            val isSelected = selectedMonths.contains(idx)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedMonths = if (isSelected) selectedMonths - idx else selectedMonths + idx
                                fromDateMillis = null; toDateMillis = null
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        AssistChip(onClick = { showMonthlyOverview = !showMonthlyOverview }, label = { Text(if (showMonthlyOverview) "Hide Monthly" else "Show Monthly") })
                        AssistChip(onClick = { showRangeOverview = !showRangeOverview }, label = { Text(if (showRangeOverview) "Hide Range" else "Show Range") })
                        AssistChip(onClick = { showInsights = !showInsights }, label = { Text(if (showInsights) "Hide Insights" else "Show Insights") })
                        AssistChip(onClick = { showCategories = !showCategories }, label = { Text(if (showCategories) "Hide Categories" else "Show Categories") })
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        AssistChip(onClick = { showCharts = !showCharts }, label = { Text(if (showCharts) "Hide Charts" else "Show Charts") })
                        AssistChip(onClick = { showAdvancedAnalytics = !showAdvancedAnalytics }, label = { Text(if (showAdvancedAnalytics) "Hide Advanced" else "Show Advanced") })
                    }
                }
            }
        }

        if (showMonthlyOverview) {
            item {
                // Monthly overview for current calendar month
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                val end = System.currentTimeMillis()
                val monthTxns = allTransactions.filter { it.ts in start..end }
                val mCredit = monthTxns.filter { it.type == "CREDIT" }.sumOf { it.amount }
                val mDebit  = monthTxns.filter { it.type == "DEBIT" }.sumOf { it.amount }
                val mBalance = mCredit - mDebit
                MonthlyOverviewCard(totalCredit = mCredit, totalDebit = mDebit, balance = mBalance, currencyFormat = currencyFormat)
            }
        }

        if (showRangeOverview) {
            item { MonthlyOverviewCard(titleSuffix = selectedChipsLabel, totalCredit = totalCredit, totalDebit = totalDebit, balance = balance, currencyFormat = currencyFormat) }
            item { EnhancedOverviewCard(titleSuffix = selectedChipsLabel, transactions = rangeTransactions, currencyFormat = currencyFormat) }
        }

        if (showInsights) {
            item { SpendingInsightsCard(titleSuffix = selectedChipsLabel, transactions = rangeTransactions, currencyFormat = currencyFormat) }
        }

        if (showCategories) {
            item { CategoryBreakdownCard(titleSuffix = selectedChipsLabel, transactions = rangeTransactions, currencyFormat = currencyFormat) }
        }

        item { TransactionTypeBreakdownCard(titleSuffix = selectedChipsLabel, transactions = rangeTransactions, currencyFormat = currencyFormat) }
        item { TopMerchantsCard(titleSuffix = selectedChipsLabel, transactions = rangeTransactions, currencyFormat = currencyFormat) }
        item { BiggestExpensesCard(titleSuffix = selectedChipsLabel, transactions = rangeTransactions, currencyFormat = currencyFormat) }
        
        // Chart components temporarily disabled for multi-task model integration
        if (showCharts) {
            item { 
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("📊 Charts & Analytics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            InfoIconWithDialog(title = "Charts & Analytics", body = "Visual summaries like weekly trend and monthly comparison.")
                        }
                        if (!selectedChipsLabel.isNullOrBlank()) Text(selectedChipsLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Visualize spending and income trends for the selected period.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item { WeeklySpendingChart(titleSuffix = selectedChipsLabel, transactions = rangeTransactions, currencyFormat = currencyFormat) }
        }
        
        if (showAdvancedAnalytics) {
            item { SavingsGoalCard(titleSuffix = selectedChipsLabel, transactions = rangeTransactions, currencyFormat = currencyFormat) }
            item { SpendingPatternsCard(titleSuffix = selectedChipsLabel, transactions = rangeTransactions, currencyFormat = currencyFormat) }
        }
    }
}

@Composable
fun InfoIconWithDialog(title: String, body: String) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(imageVector = Icons.Outlined.Info, contentDescription = "Info")
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { open = false }) { Text("OK") } },
            title = { Text(title) },
            text = { Text(body) }
        )
    }
}

@Composable
fun TopMerchantsCard(
    titleSuffix: String? = null,
    transactions: List<Transaction>,
    currencyFormat: NumberFormat
) {
    val debitTransactions = transactions.filter { it.type == "DEBIT" }
    val totals = debitTransactions.groupBy { ((it.merchant ?: extractCounterpartyName(it.rawBody)) ?: "Unknown").trim() }
        .mapValues { it.value.sumOf { t -> t.amount } }
        .toList()
        .sortedByDescending { it.second }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Top Merchants", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                InfoIconWithDialog(title = "Top Merchants", body = "Shows where you spent the most (grouped by merchant/person). Uses extracted names from SMS; may not always be perfect.")
            }
            if (!titleSuffix.isNullOrBlank()) Text(titleSuffix, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            totals.take(10).forEach { (name, amount) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(name.ifEmpty { "Unknown" }, style = MaterialTheme.typography.bodyMedium)
                    Text(currencyFormat.format(amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(6.dp))
            }
            if (totals.isEmpty()) Text("No data", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun BiggestExpensesCard(
    titleSuffix: String? = null,
    transactions: List<Transaction>,
    currencyFormat: NumberFormat
) {
    val biggest = transactions.filter { it.type == "DEBIT" }
        .sortedByDescending { it.amount }
        .take(10)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Biggest Expenses", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                InfoIconWithDialog(title = "Biggest Expenses", body = "Top individual transactions by amount. Each row is a single expense, not grouped.")
            }
            if (!titleSuffix.isNullOrBlank()) Text(titleSuffix, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            biggest.forEach { t ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text((t.merchant ?: extractCounterpartyName(t.rawBody) ?: "Transaction"), style = MaterialTheme.typography.bodyMedium)
                    Text(currencyFormat.format(t.amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = ErrorRed)
                }
                Spacer(Modifier.height(6.dp))
            }
            if (biggest.isEmpty()) Text("No data", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun MonthlyOverviewCard(
    titleSuffix: String? = null,
    totalCredit: Double,
    totalDebit: Double,
    balance: Double,
    currencyFormat: NumberFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Monthly Overview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                InfoIconWithDialog(title = "Monthly Overview", body = "Income, expenses, and balance for the selected months.")
            }
            if (!titleSuffix.isNullOrBlank()) Text(titleSuffix, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OverviewItem(
                    title = "Income",
                    amount = totalCredit,
                    color = SuccessGreen,
                    currencyFormat = currencyFormat
                )
                OverviewItem(
                    title = "Expenses",
                    amount = totalDebit,
                    color = ErrorRed,
                    currencyFormat = currencyFormat
                )
                OverviewItem(
                    title = "Balance",
                    amount = balance,
                    color = if (balance >= 0) SuccessGreen else ErrorRed,
                    currencyFormat = currencyFormat
                )
            }
        }
    }
}

@Composable
fun OverviewItem(
    title: String,
    amount: Double,
    color: Color,
    currencyFormat: NumberFormat
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = currencyFormat.format(amount),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun SpendingInsightsCard(
    titleSuffix: String? = null,
    transactions: List<Transaction>,
    currencyFormat: NumberFormat
) {
    val debitTransactions = transactions.filter { it.type == "DEBIT" }
    val avgSpending = if (debitTransactions.isNotEmpty()) {
        debitTransactions.sumOf { it.amount } / debitTransactions.size
    } else 0.0
    
    val maxSpending = debitTransactions.maxOfOrNull { it.amount } ?: 0.0
    val totalTransactions = transactions.size

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Spending Insights", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                InfoIconWithDialog(title = "Spending Insights", body = "High-level stats: average spend, biggest spend, and transaction counts.")
            }
            if (!titleSuffix.isNullOrBlank()) Text(titleSuffix, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                InsightItem(
                    icon = Icons.Outlined.TrendingUp,
                    title = "Avg Transaction",
                    value = currencyFormat.format(avgSpending),
                    color = PrimaryBlue
                )
                InsightItem(
                    icon = Icons.Outlined.AttachMoney,
                    title = "Highest Spend",
                    value = currencyFormat.format(maxSpending),
                    color = WarningOrange
                )
                InsightItem(
                    icon = Icons.Outlined.Receipt,
                    title = "Total Transactions",
                    value = totalTransactions.toString(),
                    color = AccentPurple
                )
            }
        }
    }
}

@Composable
fun InsightItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun CategoryBreakdownCard(
    titleSuffix: String? = null,
    transactions: List<Transaction>,
    currencyFormat: NumberFormat
) {
    val debitTransactions = transactions.filter { it.type == "DEBIT" }
    val categoryMap = mutableMapOf<String, Double>()
    
    debitTransactions.forEach { transaction ->
        val category = getCategoryFromMerchant((transaction.merchant ?: extractCounterpartyName(transaction.rawBody) ?: ""))
        categoryMap[category] = categoryMap.getOrDefault(category, 0.0) + transaction.amount
    }
    
    val sortedCategories = categoryMap.toList().sortedByDescending { it.second }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Category Breakdown", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                InfoIconWithDialog(title = "Category Breakdown", body = "Estimated categorization based on merchant names; shows top categories and shares.")
            }
            if (!titleSuffix.isNullOrBlank()) Text(titleSuffix, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (sortedCategories.isEmpty()) {
                Text(
                    "No spending data available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // Chart temporarily disabled - Vico library integration in progress
                if (sortedCategories.size > 1) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .padding(bottom = 16.dp)
                    ) {
                        val values = sortedCategories.map { it.second }
                        val labels = sortedCategories.map { it.first }
                        val maxVal = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
                        val barCount = values.size.coerceAtMost(5)
                        val barSpacing = 16f
                        val contentWidth = size.width
                        val contentHeight = size.height
                        val barWidth = (contentWidth - (barSpacing * (barCount + 1))) / barCount
                        for (i in 0 until barCount) {
                            val v = values[i].toFloat()
                            val h = (v / maxVal.toFloat()) * (contentHeight * 0.8f)
                            val left = barSpacing + i * (barWidth + barSpacing)
                            val top = contentHeight - h
                            val right = left + barWidth
                            val bottom = contentHeight
                            val color = categoryColorStatic(labels[i])
                            drawRect(color = color, topLeft = androidx.compose.ui.geometry.Offset(left, top), size = androidx.compose.ui.geometry.Size(barWidth, h))
                        }
                    }
                }
                
                // Category list with enhanced styling
                sortedCategories.take(5).forEach { (category, amount) ->
                    CategoryItem(
                        category = category,
                        amount = amount,
                        total = categoryMap.values.sum(),
                        currencyFormat = currencyFormat
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // Show total spending
                if (sortedCategories.isNotEmpty()) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Total Spending",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            currencyFormat.format(categoryMap.values.sum()),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = ErrorRed
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryItem(
    category: String,
    amount: Double,
    total: Double,
    currencyFormat: NumberFormat
) {
    val percentage = if (total > 0) (amount / total) * 100 else 0.0
    val categoryColor = getCategoryColor(category)
    val categoryIcon = getCategoryIcon(category)

    Row(
        modifier = Modifier.fillMaxWidth(),
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
                contentDescription = category,
                tint = categoryColor,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = category,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = currencyFormat.format(amount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Text(
            text = "${"%.1f".format(percentage)}%",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = categoryColor
        )
    }
}

@Composable
fun WeeklySpendingChart(
    titleSuffix: String? = null,
    transactions: List<Transaction>,
    currencyFormat: NumberFormat
) {
    val debitTransactions = transactions.filter { it.type == "DEBIT" }
    val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val totalsByDay = MutableList(7) { 0.0 }
    debitTransactions.forEach { t ->
        val cal = Calendar.getInstance()
        cal.timeInMillis = t.ts
        val idx = (cal.get(Calendar.DAY_OF_WEEK) - 1).coerceIn(0, 6)
        totalsByDay[idx] = totalsByDay[idx] + t.amount
    }
    val maxVal = totalsByDay.maxOrNull() ?: 0.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Weekly Spending", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                InfoIconWithDialog(title = "Weekly Spending", body = "Bars show total spent each weekday across the selection.")
            }
            if (!titleSuffix.isNullOrBlank()) Text(titleSuffix, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(16.dp))
            Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                    .padding(horizontal = 8.dp)
            ) {
                val width = size.width
                val height = size.height
                val barSpacing = 16f
                val barWidth = (width - (barSpacing * (7 + 1))) / 7f
                val chartHeight = height * 0.8f
                for (i in 0 until 7) {
                    val value = totalsByDay[i].toFloat()
                    val h = if (maxVal > 0) (value / maxVal.toFloat()) * chartHeight else 0f
                    val left = barSpacing + i * (barWidth + barSpacing)
                    val top = height - h
                    drawRect(
                        color = ErrorRed,
                        topLeft = androidx.compose.ui.geometry.Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(barWidth, h)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                days.forEach { d -> Text(d, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
fun SixMonthOverviewCard(
    totalCredit: Double,
    totalDebit: Double,
    balance: Double,
    currencyFormat: NumberFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                "6 Months Overview",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OverviewItem(
                    title = "Income",
                    amount = totalCredit,
                    color = SuccessGreen,
                    currencyFormat = currencyFormat
                )
                OverviewItem(
                    title = "Expenses",
                    amount = totalDebit,
                    color = ErrorRed,
                    currencyFormat = currencyFormat
                )
                OverviewItem(
                    title = "Balance",
                    amount = balance,
                    color = if (balance >= 0) SuccessGreen else ErrorRed,
                    currencyFormat = currencyFormat
                )
            }
        }
    }
}

@Composable
fun TransactionTypeBreakdownCard(
    titleSuffix: String? = null,
    transactions: List<Transaction>,
    currencyFormat: NumberFormat
) {
    val paired = remember(transactions) { findPairedTransferIds(transactions) }
    val creditTransactions = transactions.filter { it.type == "CREDIT" && !isInterAccountTransfer(it) && it.id !in paired }
    val debitTransactions = transactions.filter { it.type == "DEBIT" && !isInterAccountTransfer(it) && it.id !in paired }
    val transferTransactions = transactions.filter { isInterAccountTransfer(it) || it.id in paired }
    
    val totalCredit = creditTransactions.sumOf { it.amount }
    val totalDebit = debitTransactions.sumOf { it.amount }
    val totalTransfer = transferTransactions.sumOf { it.amount }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Transaction Type Breakdown", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                InfoIconWithDialog(title = "Transaction Type", body = "Split between income, expenses, and transfers in the selected period.")
            }
            if (!titleSuffix.isNullOrBlank()) Text(titleSuffix, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Income",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        currencyFormat.format(totalCredit),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SuccessGreen
                    )
                    Text(
                        "${creditTransactions.size} transactions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Expenses",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        currencyFormat.format(totalDebit),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ErrorRed
                    )
                    Text(
                        "${debitTransactions.size} transactions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Transfers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        currencyFormat.format(totalTransfer),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${transferTransactions.size} transactions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ChannelBreakdownCard(
    titleSuffix: String? = null,
    transactions: List<Transaction>,
    currencyFormat: NumberFormat
) {
    val channelMap = mutableMapOf<String, Double>()
    val channelCountMap = mutableMapOf<String, Int>()
    
    transactions.forEach { transaction ->
        val channel = transaction.channel ?: "Other"
        channelMap[channel] = channelMap.getOrDefault(channel, 0.0) + transaction.amount
        channelCountMap[channel] = channelCountMap.getOrDefault(channel, 0) + 1
    }
    
    val sortedChannels = channelMap.toList().sortedByDescending { it.second }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                "Payment Channel Breakdown" + (titleSuffix?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (sortedChannels.isEmpty()) {
                Text(
                    "No channel data available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                sortedChannels.take(5).forEach { (channel, amount) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = channel,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${channelCountMap[channel]} transactions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Text(
                            text = currencyFormat.format(amount),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

private fun isInterAccountTransfer(transaction: Transaction): Boolean {
    if (transaction.type == "TRANSFER") return true
    
    val body = transaction.rawBody.lowercase()
    val transferKeywords = listOf(
        "transfer", "transferred", "moved", "shifted", "account to account",
        "a/c transfer", "account transfer", "internal transfer", "self transfer",
        "own account", "same bank", "intra bank", "inter account"
    )
    
    return transferKeywords.any { body.contains(it) } ||
           (transaction.merchant?.lowercase()?.contains("transfer") == true) ||
           (transaction.channel?.lowercase()?.contains("transfer") == true) ||
           (body.contains("credited") && body.contains("debited")) ||
           (body.contains("from") && body.contains("to") && 
            (body.contains("account") || body.contains("a/c")))
}

// Pair same-amount credit/debit within a 2-hour window to identify likely internal transfers
private fun findPairedTransferIds(transactions: List<Transaction>, timeWindowMillis: Long = 2 * 60 * 60 * 1000): Set<Long> {
    if (transactions.isEmpty()) return emptySet()

    val credits = transactions.filter { it.type == "CREDIT" }.sortedBy { it.ts }
    val debits  = transactions.filter { it.type == "DEBIT"  }.sortedBy { it.ts }

    val pairedIds = mutableSetOf<Long>()
    var i = 0
    var j = 0

    while (i < credits.size && j < debits.size) {
        val c = credits[i]
        val d = debits[j]
        val dt = kotlin.math.abs(c.ts - d.ts)

        if (c.ts < d.ts && (d.ts - c.ts) > timeWindowMillis) { i++; continue }
        if (d.ts < c.ts && (c.ts - d.ts) > timeWindowMillis) { j++; continue }

        val amountMatch = kotlin.math.abs(c.amount - d.amount) < 0.01
        val bankMatch = c.bank != null && d.bank != null && c.bank == d.bank
        val tailMatch = c.accountTail != null && d.accountTail != null && c.accountTail == d.accountTail

        if (dt <= timeWindowMillis && amountMatch && (bankMatch || tailMatch)) {
            pairedIds += c.id
            pairedIds += d.id
            i++; j++
        } else {
            if (c.ts <= d.ts) i++ else j++
        }
    }

    return pairedIds
}

// ===== NEW ENHANCED CHART COMPONENTS =====

@Composable
fun SpendingTrendChart(
    transactions: List<Transaction>,
    currencyFormat: NumberFormat
) {
    val debitTransactions = transactions.filter { it.type == "DEBIT" }
    val dailySpending = debitTransactions
        .groupBy { 
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = it.ts
            calendar.get(Calendar.DAY_OF_MONTH)
        }
        .mapValues { it.value.sumOf { transaction -> transaction.amount } }
        .toList()
        .sortedBy { it.first }

    val chartEntries = dailySpending.mapIndexed { index, (day, amount) ->
        // Chart data temporarily disabled
        index.toFloat() to amount.toFloat()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Daily Spending Trend",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            
            if (chartEntries.isNotEmpty()) {
                // Chart temporarily disabled - Vico library integration in progress
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "📈 Daily Spending Trend Chart\n(Coming Soon with Multi-Task AI)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Text(
                    "No spending data available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun CategoryPieChart(
    transactions: List<Transaction>,
    currencyFormat: NumberFormat
) {
    val debitTransactions = transactions.filter { it.type == "DEBIT" }
    val categoryMap = mutableMapOf<String, Double>()
    
    debitTransactions.forEach { transaction ->
        val category = getCategoryFromMerchant(transaction.merchant ?: transaction.channel ?: "")
        categoryMap[category] = categoryMap.getOrDefault(category, 0.0) + transaction.amount
    }
    
    val sortedCategories = categoryMap.toList().sortedByDescending { it.second }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Spending by Category",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            
            if (sortedCategories.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Pie Chart
                    Canvas(
                        modifier = Modifier
                            .weight(1f)
                            .height(200.dp)
                    ) {
                        val total = sortedCategories.sumOf { it.second }
                        if (total <= 0) return@Canvas
                        var startAngle = -90f
                        val radius = size.minDimension / 2f * 0.8f
                        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = radius * 0.3f)
                        sortedCategories.take(6).forEach { (cat, amount) ->
                            val sweep = (amount / total).toFloat() * 360f
                            drawArc(
                                color = categoryColorStatic(cat),
                                startAngle = startAngle,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = androidx.compose.ui.geometry.Offset(center.x - radius, center.y - radius),
                                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                                style = stroke
                            )
                            startAngle += sweep
                        }
                    }
                    
                    // Category Legend
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp)
                    ) {
                        sortedCategories.take(6).forEach { (category, amount) ->
                            val percentage = if (categoryMap.values.sum() > 0) {
                                (amount / categoryMap.values.sum()) * 100
                            } else 0.0
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(categoryColorStatic(category))
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = category,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${"%.1f".format(percentage)}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = categoryColorStatic(category)
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    "No category data available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun MonthlyComparisonChart(
    transactions: List<Transaction>,
    currencyFormat: NumberFormat
) {
    val monthlyData = mutableMapOf<String, Pair<Double, Double>>() // month -> (credit, debit)
    
    transactions.forEach { transaction ->
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = transaction.ts
        val monthKey = "${calendar.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()) ?: "Unknown"} '${calendar.get(Calendar.YEAR) % 100}"
        
        val current = monthlyData.getOrDefault(monthKey, 0.0 to 0.0)
        if (transaction.type == "CREDIT") {
            monthlyData[monthKey] = current.first + transaction.amount to current.second
        } else if (transaction.type == "DEBIT") {
            monthlyData[monthKey] = current.first to current.second + transaction.amount
        }
    }
    
    val monthOrder = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
    val sortedMonths = monthlyData.toList().sortedWith(compareBy({ it.first.takeLast(2).toIntOrNull() ?: 0 }, { monthOrder.indexOf(it.first.take(3)) }))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Monthly Income vs Expenses",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            
            if (sortedMonths.isNotEmpty()) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .padding(8.dp)
                ) {
                    val data = sortedMonths
                    val maxVal = data.maxOf { maxOf(it.second.first, it.second.second) }.coerceAtLeast(1.0)
                    val count = data.size.coerceAtMost(6)
                    val groupSpacing = 20f
                    val barSpacing = 8f
                    val barWidth = ((size.width - (groupSpacing * (count + 1))) / count - barSpacing) / 2f
                    val chartHeight = size.height * 0.8f
                    fun barHeight(v: Double) = (v / maxVal) * chartHeight
                    for (i in 0 until count) {
                        val (_, pair) = data[i]
                        val income = barHeight(pair.first).toFloat()
                        val expense = barHeight(pair.second).toFloat()
                        val leftBase = groupSpacing + i * ((barWidth * 2) + barSpacing + groupSpacing)
                        val bottom = size.height * 0.9f
                        drawRect(
                            color = SuccessGreen,
                            topLeft = androidx.compose.ui.geometry.Offset(leftBase, bottom - income),
                            size = androidx.compose.ui.geometry.Size(barWidth, income)
                        )
                        drawRect(
                            color = ErrorRed,
                            topLeft = androidx.compose.ui.geometry.Offset(leftBase + barWidth + barSpacing, bottom - expense),
                            size = androidx.compose.ui.geometry.Size(barWidth, expense)
                        )
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(SuccessGreen)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Income", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(ErrorRed)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Expenses", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                Text(
                    "No monthly data available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun DailySpendingChart(
    transactions: List<Transaction>,
    currencyFormat: NumberFormat
) {
    val debitTransactions = transactions.filter { it.type == "DEBIT" }
    val weeklySpending = mutableMapOf<String, Double>()
    
    // Group by day of week
    debitTransactions.forEach { transaction ->
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = transaction.ts
        val dayOfWeek = calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()) ?: "Unknown"
        weeklySpending[dayOfWeek] = weeklySpending.getOrDefault(dayOfWeek, 0.0) + transaction.amount
    }
    
    val daysOfWeek = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    val chartEntries = daysOfWeek.mapIndexed { index, day ->
        // Chart data temporarily disabled
        index.toFloat() to weeklySpending.getOrDefault(day, 0.0).toFloat()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Weekly Spending Pattern",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            
            if (chartEntries.any { it.second > 0 }) {
                // Chart temporarily disabled - Vico library integration in progress
                Text(
                    "Chart: Weekly spending pattern",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    "No weekly spending data available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun SavingsGoalCard(
    titleSuffix: String? = null,
    transactions: List<Transaction>,
    currencyFormat: NumberFormat
) {
    val totalIncome = transactions.filter { it.type == "CREDIT" }.sumOf { it.amount }
    val totalExpenses = transactions.filter { it.type == "DEBIT" }.sumOf { it.amount }
    val savings = totalIncome - totalExpenses
    val savingsRate = if (totalIncome > 0) (savings / totalIncome) * 100 else 0.0
    
    val goalAmount = totalIncome * 0.2 // 20% savings goal
    val progress = if (goalAmount > 0) (savings / goalAmount) * 100 else 0.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Savings & Goals" + (titleSuffix?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Total Savings",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        currencyFormat.format(savings),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (savings >= 0) SuccessGreen else ErrorRed
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "Savings Rate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${"%.1f".format(savingsRate)}%",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (savingsRate >= 20) SuccessGreen else WarningOrange
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Text(
                "20% Savings Goal Progress",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = if (progress >= 100) SuccessGreen else AccentPurple,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            
            Spacer(Modifier.height(8.dp))
            Text(
                "${"%.1f".format(progress)}% of goal achieved",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SpendingPatternsCard(
    titleSuffix: String? = null,
    transactions: List<Transaction>,
    currencyFormat: NumberFormat
) {
    val debitTransactions = transactions.filter { it.type == "DEBIT" }
    
    // Time-based patterns
    val hourlySpending = mutableMapOf<Int, Double>()
    val monthlySpending = mutableMapOf<Int, Double>()
    
    debitTransactions.forEach { transaction ->
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = transaction.ts
        
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val month = calendar.get(Calendar.MONTH)
        
        hourlySpending[hour] = hourlySpending.getOrDefault(hour, 0.0) + transaction.amount
        monthlySpending[month] = monthlySpending.getOrDefault(month, 0.0) + transaction.amount
    }
    
    val peakHour = hourlySpending.maxByOrNull { it.value }?.key ?: 0
    val peakMonth = monthlySpending.maxByOrNull { it.value }?.key ?: 0
    val avgTransactionSize = if (debitTransactions.isNotEmpty()) {
        debitTransactions.sumOf { it.amount } / debitTransactions.size
    } else 0.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Spending Patterns & Insights" + (titleSuffix?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = "Peak Hour",
                        tint = WarningOrange,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${peakHour}:00",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Peak Hour",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = "Peak Month",
                        tint = AccentPurple,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = Calendar.getInstance().apply { set(Calendar.MONTH, peakMonth) }
                            .getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()) ?: "Unknown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Peak Month",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.Analytics,
                        contentDescription = "Avg Transaction",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = currencyFormat.format(avgTransactionSize),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Avg Transaction",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

fun getCategoryFromMerchant(merchant: String): String {
    return when {
        merchant.contains("food", ignoreCase = true) || 
        merchant.contains("restaurant", ignoreCase = true) ||
        merchant.contains("swiggy", ignoreCase = true) ||
        merchant.contains("zomato", ignoreCase = true) -> "Food"
        
        merchant.contains("uber", ignoreCase = true) ||
        merchant.contains("ola", ignoreCase = true) ||
        merchant.contains("transport", ignoreCase = true) ||
        merchant.contains("fuel", ignoreCase = true) -> "Transport"
        
        merchant.contains("amazon", ignoreCase = true) ||
        merchant.contains("flipkart", ignoreCase = true) ||
        merchant.contains("shopping", ignoreCase = true) ||
        merchant.contains("store", ignoreCase = true) -> "Shopping"
        
        merchant.contains("movie", ignoreCase = true) ||
        merchant.contains("netflix", ignoreCase = true) ||
        merchant.contains("entertainment", ignoreCase = true) -> "Entertainment"
        
        merchant.contains("electricity", ignoreCase = true) ||
        merchant.contains("water", ignoreCase = true) ||
        merchant.contains("gas", ignoreCase = true) ||
        merchant.contains("bill", ignoreCase = true) -> "Bills"
        
        merchant.contains("hospital", ignoreCase = true) ||
        merchant.contains("pharmacy", ignoreCase = true) ||
        merchant.contains("medical", ignoreCase = true) -> "Health"
        
        merchant.contains("school", ignoreCase = true) ||
        merchant.contains("college", ignoreCase = true) ||
        merchant.contains("education", ignoreCase = true) -> "Education"
        
        else -> "Other"
    }
}

// Non-composable color mapping for charts and legends
private fun categoryColorStatic(categoryOrMerchant: String): Color {
    val category = getCategoryFromMerchant(categoryOrMerchant)
    return when (category) {
        "Food" -> ErrorRed
        "Transport" -> PrimaryBlue
        "Shopping" -> AccentPurple
        "Entertainment" -> WarningOrange
        "Bills" -> AccentPurple
        "Health" -> SuccessGreen
        "Education" -> PrimaryBlue
        else -> PrimaryBlue
    }
}

// Lightweight extraction similar to HomeScreen display logic to avoid channel names
private fun extractCounterpartyName(body: String?): String? {
    if (body == null) return null
    val patterns = listOf(
        Regex("(?i)(?:to|paid to|transfer to)\\s+([A-Za-z0-9 &._-]{3,40})"),
        Regex("(?i)(?:from|received from)\\s+([A-Za-z0-9 &._-]{3,40})"),
        Regex("(?i)(?:by)\\s+([A-Za-z0-9 &._-]{3,40})"),
        Regex("(?i)(?:at)\\s+([A-Za-z0-9 &._-]{3,40})")
    )
    val bankKeywords = listOf(
        "bank", "sbi", "icici", "hdfc", "axis", "kotak", "pnb", "boi", "canara",
        "union bank", "yes bank", "idfc", "idbi", "federal", "rbl", "indusind",
        "citibank", "citi", "hsbc", "standard chartered", "scb", "au small finance",
        "paytm payments bank", "airtel payments bank"
    )
    for (r in patterns) {
        val m = r.find(body)
        if (m != null) {
            var name = m.groupValues.getOrNull(1)?.trim()
            if (!name.isNullOrBlank()) {
                name = name
                    ?.replace(Regex("(?i)\\b(a/c|acct|account|no\\.?|number)\\b.*$"), "")
                    ?.replace(Regex("\\s+"), " ")
                    ?.trim()
                val lower = name!!.lowercase()
                val isBank = bankKeywords.any { lower.contains(it) }
                if (!isBank && name.length >= 3) return name
            }
        }
    }
    return null
}

@Composable
fun EnhancedOverviewCard(
    titleSuffix: String? = null,
    transactions: List<Transaction>,
    currencyFormat: NumberFormat
) {
    val creditTransactions = transactions.filter { it.type == "CREDIT" }
    val debitTransactions = transactions.filter { it.type == "DEBIT" }
    
    val totalCredit = creditTransactions.sumOf { it.amount }
    val totalDebit = debitTransactions.sumOf { it.amount }
    val balance = totalCredit - totalDebit
    
    val avgCredit = if (creditTransactions.isNotEmpty()) totalCredit / creditTransactions.size else 0.0
    val avgDebit = if (debitTransactions.isNotEmpty()) totalDebit / debitTransactions.size else 0.0
    
    val creditCount = creditTransactions.size
    val debitCount = debitTransactions.size
    val totalTransactions = transactions.size
    
    val netWorthChange = balance
    val spendingEfficiency = if (totalCredit > 0) ((totalCredit - totalDebit) / totalCredit) * 100 else 0.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Financial Health Overview" + (titleSuffix?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(Modifier.height(16.dp))
            
            // Key metrics in a grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = currencyFormat.format(netWorthChange),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (netWorthChange >= 0) SuccessGreen else ErrorRed
                    )
                    Text(
                        text = "Net Change",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${"%.1f".format(spendingEfficiency)}%",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (spendingEfficiency >= 20) SuccessGreen else WarningOrange
                    )
                    Text(
                        text = "Savings Rate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = totalTransactions.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue
                    )
                    Text(
                        text = "Total Txns",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Detailed breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Income Details",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = SuccessGreen
                    )
                    Text(
                        currencyFormat.format(totalCredit),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "$creditCount transactions • Avg: ${currencyFormat.format(avgCredit)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "Expense Details",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ErrorRed
                    )
                    Text(
                        currencyFormat.format(totalDebit),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "$debitCount transactions • Avg: ${currencyFormat.format(avgDebit)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Progress indicators
            Column {
                Text(
                    "Income vs Expenses Ratio",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                
                val ratio = if (totalDebit > 0) totalCredit / totalDebit else 0.0
                
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (ratio >= 1.2) SuccessGreen else if (ratio >= 1.0) WarningOrange else ErrorRed,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                
                Spacer(Modifier.height(4.dp))
                Text(
                    "${"%.2f".format(ratio)}x (${"%.1f".format(ratio * 100)}% of expenses covered by income)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

