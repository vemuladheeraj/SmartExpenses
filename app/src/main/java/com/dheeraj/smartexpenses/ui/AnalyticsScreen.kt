package com.dheeraj.smartexpenses.ui

import androidx.compose.foundation.background
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

@Composable
fun AnalyticsScreen(homeVm: HomeVm) {
    val transactions by homeVm.items.collectAsState()
    val totalCredit by homeVm.totalCredit.collectAsState()
    val totalDebit by homeVm.totalDebit.collectAsState()
    
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")) }
    val balance = totalCredit - totalDebit

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Analytics",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Track your spending patterns",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        item {
            MonthlyOverviewCard(
                totalCredit = totalCredit,
                totalDebit = totalDebit,
                balance = balance,
                currencyFormat = currencyFormat
            )
        }
        
        item {
            SpendingInsightsCard(transactions = transactions, currencyFormat = currencyFormat)
        }
        
        item {
            CategoryBreakdownCard(transactions = transactions, currencyFormat = currencyFormat)
        }
        
        item {
            RecentTrendsCard(transactions = transactions, currencyFormat = currencyFormat)
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
