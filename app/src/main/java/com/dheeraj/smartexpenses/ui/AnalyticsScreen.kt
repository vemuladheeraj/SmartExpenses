package com.dheeraj.smartexpenses.ui

import androidx.compose.foundation.background
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dheeraj.smartexpenses.data.Transaction
import com.dheeraj.smartexpenses.ui.theme.*
import java.text.NumberFormat
import java.util.*
import com.dheeraj.smartexpenses.data.amount

@Composable
fun AnalyticsScreen(homeVm: HomeVm) {
    val allTransactions by homeVm.allItems.collectAsState()
    var monthsBack by remember { mutableStateOf(3f) }
    var selectedMonthIndex by remember { mutableStateOf<Int?>(null) }
    var fromDateMillis by remember { mutableStateOf<Long?>(null) }
    var toDateMillis by remember { mutableStateOf<Long?>(null) }
    var showMonthlyOverview by remember { mutableStateOf(true) }
    var showRangeOverview by remember { mutableStateOf(true) }
    var showInsights by remember { mutableStateOf(true) }
    var showCategories by remember { mutableStateOf(true) }

    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")) }

    val now = System.currentTimeMillis()
    val (rangeStart, rangeEnd) = remember(monthsBack, selectedMonthIndex, fromDateMillis, toDateMillis, now) {
        if (fromDateMillis != null && toDateMillis != null && fromDateMillis!! <= toDateMillis!!) {
            fromDateMillis!! to toDateMillis!!
        } else if (selectedMonthIndex != null) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.MONTH, -selectedMonthIndex!!)
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis
            cal.add(Calendar.MONTH, 1)
            (start to cal.timeInMillis - 1)
        } else {
            val cal = Calendar.getInstance()
            cal.timeInMillis = now
            cal.add(Calendar.MONTH, -monthsBack.toInt())
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            (cal.timeInMillis to now)
        }
    }

    val rangeTransactions = remember(allTransactions, rangeStart, rangeEnd) {
        allTransactions.filter { it.ts in rangeStart..rangeEnd }
    }

    val totalCredit = remember(rangeTransactions) { rangeTransactions.filter { it.type == "CREDIT" }.sumOf { it.amount } }
    val totalDebit  = remember(rangeTransactions) { rangeTransactions.filter { it.type == "DEBIT"  }.sumOf { it.amount } }
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

        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Last ${monthsBack.toInt()} month(s)")
                    Slider(value = monthsBack, onValueChange = { monthsBack = it; selectedMonthIndex = null; fromDateMillis = null; toDateMillis = null }, valueRange = 1f..6f, steps = 4)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        repeat(6) { idx ->
                            val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -idx) }
                            val label = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()) + " '" + (cal.get(Calendar.YEAR) % 100)
                            FilterChip(selected = selectedMonthIndex == idx, onClick = {
                                if (selectedMonthIndex == idx) selectedMonthIndex = null else selectedMonthIndex = idx
                                fromDateMillis = null; toDateMillis = null
                            }, label = { Text(label) })
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
            item { MonthlyOverviewCard(totalCredit = totalCredit, totalDebit = totalDebit, balance = balance, currencyFormat = currencyFormat) }
        }

        if (showInsights) {
            item { SpendingInsightsCard(transactions = rangeTransactions, currencyFormat = currencyFormat) }
        }

        if (showCategories) {
            item { CategoryBreakdownCard(transactions = rangeTransactions, currencyFormat = currencyFormat) }
        }

        item { RecentTrendsCard(transactions = rangeTransactions, currencyFormat = currencyFormat) }
        item { TransactionTypeBreakdownCard(transactions = rangeTransactions, currencyFormat = currencyFormat) }
        item { ChannelBreakdownCard(transactions = rangeTransactions, currencyFormat = currencyFormat) }
        item { TopMerchantsCard(transactions = rangeTransactions, currencyFormat = currencyFormat) }
        item { BiggestExpensesCard(transactions = rangeTransactions, currencyFormat = currencyFormat) }
    }
}

@Composable
fun TopMerchantsCard(
    transactions: List<Transaction>,
    currencyFormat: NumberFormat
) {
    val debitTransactions = transactions.filter { it.type == "DEBIT" }
    val totals = debitTransactions.groupBy { (it.merchant ?: it.channel ?: "Unknown").trim() }
        .mapValues { it.value.sumOf { t -> t.amount } }
        .toList()
        .sortedByDescending { it.second }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Top Merchants", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
            Text("Biggest Expenses", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            biggest.forEach { t ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text((t.merchant ?: t.channel ?: "Transaction"), style = MaterialTheme.typography.bodyMedium)
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
                "Monthly Overview",
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
            Text(
                "Spending Insights",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
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
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                "Category Breakdown",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
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
                sortedCategories.take(5).forEach { (category, amount) ->
                    CategoryItem(
                        category = category,
                        amount = amount,
                        total = categoryMap.values.sum(),
                        currencyFormat = currencyFormat
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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
fun RecentTrendsCard(
    transactions: List<Transaction>,
    currencyFormat: NumberFormat
) {
    val recentTransactions = transactions.take(10)
    val dailySpending = recentTransactions
        .filter { it.type == "DEBIT" }
        .groupBy { 
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = it.ts
            calendar.get(Calendar.DAY_OF_MONTH)
        }
        .mapValues { it.value.sumOf { transaction -> transaction.amount } }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                "Recent Trends",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (dailySpending.isEmpty()) {
                Text(
                    "No recent spending data",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                dailySpending.entries.sortedBy { it.key }.forEach { (day, amount) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Day $day",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = currencyFormat.format(amount),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = ErrorRed
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
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
    transactions: List<Transaction>,
    currencyFormat: NumberFormat
) {
    val creditTransactions = transactions.filter { it.type == "CREDIT" && !isInterAccountTransfer(it) }
    val debitTransactions = transactions.filter { it.type == "DEBIT" && !isInterAccountTransfer(it) }
    val transferTransactions = transactions.filter { isInterAccountTransfer(it) }
    
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
            Text(
                "Transaction Type Breakdown",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
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
                "Payment Channel Breakdown",
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

@Composable
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
