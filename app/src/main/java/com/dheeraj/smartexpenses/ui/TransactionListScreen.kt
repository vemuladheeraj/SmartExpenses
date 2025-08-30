package com.dheeraj.smartexpenses.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dheeraj.smartexpenses.data.Transaction
import com.dheeraj.smartexpenses.ui.theme.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import com.dheeraj.smartexpenses.data.amount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(
    homeVm: HomeVm,
    onBack: () -> Unit
) {
    val allTransactions by homeVm.allItems.collectAsState()
    val totalDebit6Months by homeVm.totalDebit6Months.collectAsState()
    val totalCredit6Months by homeVm.totalCredit6Months.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf<String?>(null) }
    var showTransfersOnly by remember { mutableStateOf(false) }
    var selectedChannel by remember { mutableStateOf<String?>(null) }
    var showFilters by remember { mutableStateOf(true) }
    var monthsBack by remember { mutableStateOf(6f) } // fallback only
    var selectedMonthIndex by remember { mutableStateOf<Int?>(0) } // default to current month
    var fromDateMillis by remember { mutableStateOf<Long?>(null) }
    var toDateMillis by remember { mutableStateOf<Long?>(null) }
    var past30Days by remember { mutableStateOf(false) }

    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")) }
    val balance6Months = totalCredit6Months - totalDebit6Months

    // Compute active date range from controls
    val nowMillis = remember { System.currentTimeMillis() }
    val dateRange: Pair<Long, Long>? = remember(monthsBack, selectedMonthIndex, fromDateMillis, toDateMillis, nowMillis, past30Days) {
        // Highest precedence: explicit date range
        if (fromDateMillis != null && toDateMillis != null && fromDateMillis!! <= toDateMillis!!) {
            fromDateMillis!! to toDateMillis!!
        } else if (past30Days) {
            val end = nowMillis
            val start = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, -30)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            start to end
        } else if (selectedMonthIndex != null) {
            // Month chip selection
            val cal = Calendar.getInstance()
            cal.add(Calendar.MONTH, -selectedMonthIndex!!)
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis
            cal.add(Calendar.MONTH, 1)
            val end = cal.timeInMillis - 1
            start to end
        } else {
            // Fallback: last N months up to now
            val cal = Calendar.getInstance()
            cal.timeInMillis = nowMillis
            cal.add(Calendar.MONTH, -monthsBack.toInt())
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis to nowMillis
        }
    }

    // Filter transactions based on search, filters and date selection
    fun isInterAccountTransferLocal(t: Transaction): Boolean {
        if (t.type == "TRANSFER") return true
        val body = t.rawBody.lowercase()
        val strongTransferKeywords = listOf(
            "self transfer", "own account", "between your accounts", "internal transfer",
            "intra bank", "same bank", "account to account", "a/c to a/c", "inter account"
        )
        if (strongTransferKeywords.any { body.contains(it) }) return true
        if (body.contains("credited") && body.contains("debited")) return true
        val tails = Regex("(?i)(?:a/c|account)(?:.*?)([0-9]{2,6})").findAll(t.rawBody).toList()
        if (tails.size >= 2) return true
        return false
    }

    val filteredTransactions = remember(allTransactions, searchQuery, selectedType, selectedChannel, dateRange, showTransfersOnly) {
        val (startTs, endTs) = dateRange ?: (Long.MIN_VALUE to Long.MAX_VALUE)
        allTransactions.filter { transaction ->
            val inDateRange = transaction.ts in startTs..endTs
            val isTransfer = isInterAccountTransferLocal(transaction)
            val matchesSearch = searchQuery.isEmpty() ||
                transaction.merchant?.contains(searchQuery, ignoreCase = true) == true ||
                transaction.channel?.contains(searchQuery, ignoreCase = true) == true ||
                transaction.rawBody.contains(searchQuery, ignoreCase = true)

            val matchesType = selectedType == null || transaction.type == selectedType
            val matchesChannel = selectedChannel == null || transaction.channel == selectedChannel

            val base = inDateRange && matchesSearch && matchesType && matchesChannel
            // Show only transfers when the chip is ON; otherwise hide all transfers
            base && (isTransfer == showTransfersOnly)
        }
    }

    // Summary based on filtered transactions
    // Exclude inter-account transfers and paired same-amount debit/credits like Home
    fun findPairedIdsLocal(list: List<Transaction>, windowMs: Long = 2 * 60 * 60 * 1000): Set<Long> {
        val credits = list.filter { it.type == "CREDIT" }.sortedBy { it.ts }
        val debits  = list.filter { it.type == "DEBIT"  }.sortedBy { it.ts }
        val paired = mutableSetOf<Long>()
        var i = 0; var j = 0
        while (i < credits.size && j < debits.size) {
            val c = credits[i]; val d = debits[j]
            val dt = kotlin.math.abs(c.ts - d.ts)
            if (c.ts < d.ts && (d.ts - c.ts) > windowMs) { i++; continue }
            if (d.ts < c.ts && (c.ts - d.ts) > windowMs) { j++; continue }
            val amt = kotlin.math.abs(c.amount - d.amount) < 0.01
            val bank = c.bank != null && d.bank != null && c.bank == d.bank
            val tail = c.accountTail != null && d.accountTail != null && c.accountTail == d.accountTail
            val name = run {
                fun norm(s: String?) = s?.lowercase()?.replace("[^a-z0-9 ]".toRegex(), " ")?.trim()
                val ca = norm(c.merchant) ?: norm(c.rawSender)
                val da = norm(d.merchant) ?: norm(d.rawSender)
                ca != null && da != null && (ca == da || ca.contains(da) || da.contains(ca))
            }
            if (dt <= windowMs && amt && (bank || tail || name)) { paired += c.id; paired += d.id; i++; j++ } else { if (c.ts <= d.ts) i++ else j++ }
        }
        return paired
    }

    val pairedIds = remember(filteredTransactions) { findPairedIdsLocal(filteredTransactions) }

    val summaryCredit = remember(filteredTransactions, pairedIds) {
        filteredTransactions.filter { it.type == "CREDIT" && !isInterAccountTransferLocal(it) && it.id !in pairedIds }.sumOf { it.amount }
    }
    val summaryDebit  = remember(filteredTransactions, pairedIds) {
        filteredTransactions.filter { it.type == "DEBIT"  && !isInterAccountTransferLocal(it) && it.id !in pairedIds }.sumOf { it.amount }
    }
    val summaryBalance = summaryCredit - summaryDebit

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(
                            "All Transactions",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        val rangeLabel = when {
                            fromDateMillis != null && toDateMillis != null -> {
                                val df = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                                df.format(Date(fromDateMillis!!)) + " - " + df.format(Date(toDateMillis!!))
                            }
                            past30Days -> "Past 30 days"
                            selectedMonthIndex != null -> {
                                val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -selectedMonthIndex!!) }
                                cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()) +
                                        " " + cal.get(Calendar.YEAR)
                            }
                            else -> "Last ${monthsBack.toInt()} month(s)"
                        }
                        Text(rangeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(
                            if (showFilters) Icons.Filled.FilterList else Icons.Outlined.FilterList,
                            "Filters"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // Summary Card for the currently selected range
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text("Summary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    "Income",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    currencyFormat.format(summaryCredit),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessGreen
                                )
                            }

                            Column {
                                Text(
                                    "Expenses",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    currencyFormat.format(summaryDebit),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ErrorRed
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "Balance",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    currencyFormat.format(summaryBalance),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (summaryBalance >= 0) SuccessGreen else ErrorRed
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            "${filteredTransactions.size} transactions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search transactions...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, "Search") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Outlined.Clear, "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            if (showFilters) {
                item {
                    // Filters
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                "Filters",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text("Date Range", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                // Past 30 days chip
                                FilterChip(
                                    selected = past30Days,
                                    onClick = {
                                        past30Days = !past30Days
                                        if (past30Days) { selectedMonthIndex = null; fromDateMillis = null; toDateMillis = null }
                                    },
                                    label = { Text("Past 30 days") }
                                )
                                repeat(6) { idx ->
                                    val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -idx) }
                                    val label = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()) + " '" + (cal.get(Calendar.YEAR) % 100)
                                    FilterChip(
                                        selected = selectedMonthIndex == idx,
                                        onClick = {
                                            if (selectedMonthIndex == idx) selectedMonthIndex = null else selectedMonthIndex = idx
                                            past30Days = false; fromDateMillis = null; toDateMillis = null
                                        },
                                        label = { Text(label) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Type Filter
                            Text(
                                "Transaction Type",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = selectedType == null && !showTransfersOnly,
                                    onClick = { selectedType = null; showTransfersOnly = false },
                                    label = { Text("All") }
                                )
                                FilterChip(
                                    selected = selectedType == "CREDIT",
                                    onClick = { selectedType = "CREDIT"; showTransfersOnly = false },
                                    label = { Text("Income") }
                                )
                                FilterChip(
                                    selected = selectedType == "DEBIT",
                                    onClick = { selectedType = "DEBIT"; showTransfersOnly = false },
                                    label = { Text("Expense") }
                                )
                                FilterChip(
                                    selected = showTransfersOnly,
                                    onClick = { showTransfersOnly = !showTransfersOnly; if (showTransfersOnly) selectedType = null },
                                    label = { Text("Transfers") }
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Channel Filter
                            Text(
                                "Payment Channel",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = selectedChannel == null,
                                    onClick = { selectedChannel = null },
                                    label = { Text("All") }
                                )
                                FilterChip(
                                    selected = selectedChannel == "UPI",
                                    onClick = { selectedChannel = "UPI" },
                                    label = { Text("UPI") }
                                )
                                FilterChip(
                                    selected = selectedChannel == "CARD",
                                    onClick = { selectedChannel = "CARD" },
                                    label = { Text("Card") }
                                )
                            }
                        }
                    }
                }
            }

            if (filteredTransactions.isEmpty()) {
                item {
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
                                imageVector = Icons.Outlined.Search,
                                contentDescription = "No transactions",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                "No transactions found",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                "Try adjusting your search or filters",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(
                    items = filteredTransactions,
                    key = { it.id }
                ) { transaction ->
                    ModernTransactionCard(
                        transaction = transaction,
                        onUpdateCategory = { transactionId, category ->
                            homeVm.updateTransactionCategory(transactionId, category)
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}
