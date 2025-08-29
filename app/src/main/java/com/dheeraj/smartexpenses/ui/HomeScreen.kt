package com.dheeraj.smartexpenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dheeraj.smartexpenses.data.Transaction
import com.dheeraj.smartexpenses.ui.theme.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import com.dheeraj.smartexpenses.data.amount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeVm: HomeVm,
    onAddTransaction: () -> Unit,
    onViewAllTransactions: () -> Unit
) {
    val transactions by homeVm.items.collectAsState()
    val totalCredit by homeVm.totalCreditCurrentMonth.collectAsState()
    val totalDebit by homeVm.totalDebitCurrentMonth.collectAsState()
    val rangeMode by homeVm.rangeModeState.collectAsState()
    val isImporting by homeVm.isImporting.collectAsState()
    val importProgress by homeVm.importProgress.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Smart Expenses",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Track your money smartly",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTransaction,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Add, "Add transaction")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                if (isImporting) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Importing SMS...", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(6.dp))
                            val (cur, total) = importProgress
                            Text("$cur / $total processed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                // Small inline switcher for totals range
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = rangeMode == HomeVm.RangeMode.CALENDAR_MONTH,
                        onClick = { homeVm.setRangeMode(HomeVm.RangeMode.CALENDAR_MONTH) },
                        label = { Text("This month") }
                    )
                    FilterChip(
                        selected = rangeMode == HomeVm.RangeMode.ROLLING_MONTH,
                        onClick = { homeVm.setRangeMode(HomeVm.RangeMode.ROLLING_MONTH) },
                        label = { Text("Last 30 days") }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                MonthlySummaryCard(totalCredit = totalCredit, totalDebit = totalDebit)
            }
            
            item {
                QuickStatsCard(transactions = transactions)
            }
            
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Recent Transactions",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onViewAllTransactions) {
                        Text("View All")
                    }
                }
            }
            
            if (transactions.isEmpty()) {
                item {
                    EmptyStateCard()
                }
            } else {
                items(
                    items = transactions.take(10),
                    key = { it.id }
                ) { transaction ->
                    ModernTransactionCard(transaction = transaction)
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(80.dp)) // Space for FAB
            }
        }
    }
}

@Composable
fun MonthlySummaryCard(totalCredit: Double, totalDebit: Double) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")) }
    val balance = totalCredit - totalDebit

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(GradientStart, GradientEnd)
                    )
                )
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    "This Month",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    currencyFormat.format(balance),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Income",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            currencyFormat.format(totalCredit),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "Expenses",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            currencyFormat.format(totalDebit),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuickStatsCard(transactions: List<Transaction>) {
    val totalTransactions = transactions.size
    val creditTransactions = transactions.filter { it.type == "CREDIT" }
    val debitTransactions = transactions.filter { it.type == "DEBIT" }
    
    val avgAmount = if (totalTransactions > 0) {
        transactions.sumOf { it.amount } / totalTransactions
    } else 0.0
    
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(
                icon = Icons.Outlined.Receipt,
                title = "Total Transactions",
                value = totalTransactions.toString(),
                color = PrimaryBlue
            )
            
            StatItem(
                icon = Icons.Outlined.TrendingUp,
                title = "Avg Amount",
                value = currencyFormat.format(avgAmount),
                color = SecondaryGreen
            )
            
            StatItem(
                icon = Icons.Outlined.CalendarToday,
                title = "This Month",
                value = "${creditTransactions.size} Income, ${debitTransactions.size} Expense",
                color = AccentPurple
            )
        }
    }
}

@Composable
fun StatItem(
    icon: ImageVector,
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ModernTransactionCard(transaction: Transaction) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")) }
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    
    val displayName = transaction.merchant
        ?: extractCounterpartyForDisplay(transaction.rawBody)
        ?: transaction.channel
        ?: "Transaction"
    val categoryIcon = getCategoryIcon(displayName)
    val categoryColor = getCategoryColor(displayName)
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(categoryColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon,
                    contentDescription = "Category",
                    tint = categoryColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Transaction Details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateFormat.format(Date(transaction.ts)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (transaction.channel != null) {
                        Text(
                            text = " • ${transaction.channel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Source indicator
                    Spacer(modifier = Modifier.width(6.dp))
                    SourceIndicator(source = transaction.source)
                }
            }
            
            // Amount
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "${if (transaction.type == "CREDIT") "+" else "-"} ${currencyFormat.format(transaction.amount)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (transaction.type == "CREDIT") SuccessGreen else ErrorRed
                )
                
                Text(
                    text = transaction.type,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (expanded) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    "Original SMS",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = transaction.rawBody,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun SourceIndicator(source: String) {
    val (icon, tint, desc) = when {
        source.equals("SMS_AI", ignoreCase = true) -> Triple(Icons.Outlined.AutoAwesome, AccentPurple, "AI extracted")
        source.equals("SMS_REGEX", ignoreCase = true) -> Triple(Icons.Outlined.Rule, PrimaryBlue, "Regex extracted")
        source.equals("MANUAL", ignoreCase = true) -> Triple(Icons.Outlined.Edit, MaterialTheme.colorScheme.onSurfaceVariant, "Manual")
        else -> Triple(Icons.Outlined.Info, MaterialTheme.colorScheme.onSurfaceVariant, source)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = " • ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
            imageVector = icon,
            contentDescription = desc,
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
    }
}

// Prefer person/merchant-like name from SMS body for display if merchant is null
private fun extractCounterpartyForDisplay(body: String?): String? {
    if (body == null) return null
    val regexes = listOf(
        // to <Name> ...
        Regex("(?i)(?:to|paid to|transfer to)\\s+([A-Za-z0-9 &._-]{3,40})"),
        // from <Name> ... (credits)
        Regex("(?i)(?:from|received from)\\s+([A-Za-z0-9 &._-]{3,40})"),
        // by <Name> ... (e.g., credited by <Name>)
        Regex("(?i)(?:by)\\s+([A-Za-z0-9 &._-]{3,40})"),
        // at <Name> ... (POS)
        Regex("(?i)(?:at)\\s+([A-Za-z0-9 &._-]{3,40})")
    )
    val bankKeywords = listOf(
        "bank", "sbi", "icici", "hdfc", "axis", "kotak", "pnb", "boi", "canara",
        "union bank", "yes bank", "idfc", "idbi", "federal", "rbl", "indusind",
        "citibank", "citi", "hsbc", "standard chartered", "scb", "au small finance",
        "paytm payments bank", "airtel payments bank"
    )
    for (r in regexes) {
        val m = r.find(body)
        if (m != null) {
            var name = m.groupValues.getOrNull(1)?.trim()
            if (!name.isNullOrBlank()) {
                // Clean common trailing tokens
                name = name
                    ?.replace(Regex("(?i)\\b(a/c|acct|account|no\\.?|number)\\b.*$"), "")
                    ?.replace(Regex("\\s+"), " ")
                    ?.trim()
                // Skip obvious bank names
                val lower = name!!.lowercase()
                val isBank = bankKeywords.any { lower.contains(it) }
                if (!isBank && name.length >= 3) return name
            }
        }
    }
    return null
}

@Composable
fun EmptyStateCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.Receipt,
                contentDescription = "No transactions",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                "No transactions yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "Start tracking your expenses by adding your first transaction",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun getCategoryIcon(merchant: String): ImageVector {
    return when {
        merchant.contains("food", ignoreCase = true) || 
        merchant.contains("restaurant", ignoreCase = true) ||
        merchant.contains("swiggy", ignoreCase = true) ||
        merchant.contains("zomato", ignoreCase = true) -> Icons.Outlined.Restaurant
        
        merchant.contains("uber", ignoreCase = true) ||
        merchant.contains("ola", ignoreCase = true) ||
        merchant.contains("transport", ignoreCase = true) ||
        merchant.contains("fuel", ignoreCase = true) -> Icons.Outlined.DirectionsCar
        
        merchant.contains("amazon", ignoreCase = true) ||
        merchant.contains("flipkart", ignoreCase = true) ||
        merchant.contains("shopping", ignoreCase = true) ||
        merchant.contains("store", ignoreCase = true) -> Icons.Outlined.ShoppingCart
        
        merchant.contains("movie", ignoreCase = true) ||
        merchant.contains("netflix", ignoreCase = true) ||
        merchant.contains("entertainment", ignoreCase = true) -> Icons.Outlined.Movie
        
        merchant.contains("electricity", ignoreCase = true) ||
        merchant.contains("water", ignoreCase = true) ||
        merchant.contains("gas", ignoreCase = true) ||
        merchant.contains("bill", ignoreCase = true) -> Icons.Outlined.Receipt
        
        merchant.contains("hospital", ignoreCase = true) ||
        merchant.contains("pharmacy", ignoreCase = true) ||
        merchant.contains("medical", ignoreCase = true) -> Icons.Outlined.LocalHospital
        
        merchant.contains("school", ignoreCase = true) ||
        merchant.contains("college", ignoreCase = true) ||
        merchant.contains("education", ignoreCase = true) -> Icons.Outlined.School
        
        else -> Icons.Outlined.AccountBalance
    }
}

@Composable
fun getCategoryColor(merchant: String): Color {
    return when {
        merchant.contains("food", ignoreCase = true) || 
        merchant.contains("restaurant", ignoreCase = true) ||
        merchant.contains("swiggy", ignoreCase = true) ||
        merchant.contains("zomato", ignoreCase = true) -> CategoryFood
        
        merchant.contains("uber", ignoreCase = true) ||
        merchant.contains("ola", ignoreCase = true) ||
        merchant.contains("transport", ignoreCase = true) ||
        merchant.contains("fuel", ignoreCase = true) -> CategoryTransport
        
        merchant.contains("amazon", ignoreCase = true) ||
        merchant.contains("flipkart", ignoreCase = true) ||
        merchant.contains("shopping", ignoreCase = true) ||
        merchant.contains("store", ignoreCase = true) -> CategoryShopping
        
        merchant.contains("movie", ignoreCase = true) ||
        merchant.contains("netflix", ignoreCase = true) ||
        merchant.contains("entertainment", ignoreCase = true) -> CategoryEntertainment
        
        merchant.contains("electricity", ignoreCase = true) ||
        merchant.contains("water", ignoreCase = true) ||
        merchant.contains("gas", ignoreCase = true) ||
        merchant.contains("bill", ignoreCase = true) -> CategoryBills
        
        merchant.contains("hospital", ignoreCase = true) ||
        merchant.contains("pharmacy", ignoreCase = true) ||
        merchant.contains("medical", ignoreCase = true) -> CategoryHealth
        
        merchant.contains("school", ignoreCase = true) ||
        merchant.contains("college", ignoreCase = true) ||
        merchant.contains("education", ignoreCase = true) -> CategoryEducation
        
        else -> CategoryOther
    }
}
