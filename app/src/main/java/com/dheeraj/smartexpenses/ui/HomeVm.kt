package com.dheeraj.smartexpenses.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dheeraj.smartexpenses.data.AppDb
import com.dheeraj.smartexpenses.data.Transaction
import com.dheeraj.smartexpenses.sms.SmsImporter
import com.dheeraj.smartexpenses.sms.SmsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class HomeVm(app: Application) : AndroidViewModel(app) {
    
    init {
        try {
            Log.d("HomeVm", "Initializing HomeViewModel...")
            AppDb.get(getApplication()).txnDao()
            Log.d("HomeVm", "Database initialized successfully")
        } catch (e: Exception) {
            Log.e("HomeVm", "Error initializing database", e)
            e.printStackTrace()
        }
    }
    
    private val dao = AppDb.get(getApplication()).txnDao()

    // User-provided account tails to improve own-transfer detection (e.g., ["0545", "9579"]) 
    private val userAccountTails = MutableStateFlow<Set<String>>(emptySet())
    fun setAccountTails(tails: Set<String>) { userAccountTails.value = tails.filter { it.length in 2..6 }.toSet() }

    private val now = MutableStateFlow(System.currentTimeMillis())
    fun refresh() { now.value = System.currentTimeMillis() }

    private fun monthBounds(epoch: Long): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = epoch
        
        // Set to first day of month at 00:00:00
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val start = calendar.timeInMillis
        
        // Set to first day of next month at 00:00:00, then subtract 1ms
        calendar.add(Calendar.MONTH, 1)
        val end = calendar.timeInMillis - 1
        
        return start to end
    }

    private val range = now.map { monthBounds(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L to Long.MAX_VALUE)

    val items = range.flatMapLatest { (s,e) -> dao.inRange(s,e) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Improved transfer pair detection with better logic
    private fun detectTransferPairIds(transactions: List<Transaction>): Set<Long> {
        if (transactions.isEmpty()) return emptySet()

        val windowMs = 15 * 60 * 1000L // 15 minutes (increased from 10)
        val candidateChannels = setOf("UPI", "IMPS", "NEFT", "NETBANKING", "TRANSFER")
        val byAmount = transactions.groupBy { it.amount }
        val ids = mutableSetOf<Long>()
        val tails = userAccountTails.value
        
        byAmount.forEach { (amount, sameAmountTxns) ->
            // Skip very small amounts (likely not transfers)
            if (amount < 100) return@forEach
            
            val sorted = sameAmountTxns.sortedBy { it.ts }
            for (i in 0 until sorted.size) {
                val a = sorted[i]
                for (j in i + 1 until sorted.size) {
                    val b = sorted[j]
                    if (b.ts - a.ts > windowMs) break
                    
                    val typesOpposite = (a.type == "CREDIT" && b.type == "DEBIT") || (a.type == "DEBIT" && b.type == "CREDIT")
                    if (!typesOpposite) continue
                    
                    val chA = a.channel
                    val chB = b.channel
                    val channelOk = (chA != null && chA in candidateChannels) || (chB != null && chB in candidateChannels)
                    
                    val tailsOk = when {
                        tails.isEmpty() -> true
                        else -> (a.accountTail != null && b.accountTail != null && a.accountTail in tails && b.accountTail in tails)
                    }
                    
                    // Additional checks for transfer likelihood
                    val sameBank = a.bank == b.bank
                    val reasonableAmount = amount in 100.0..1000000.0 // 100 to 10 lakhs
                    
                    if (channelOk && tailsOk && sameBank && reasonableAmount) {
                        ids.add(a.id)
                        ids.add(b.id)
                        Log.d("HomeVm", "Detected transfer pair: ${a.amount} ${a.type} -> ${b.type} (${a.bank})")
                    }
                }
            }
        }
        return ids
    }

    val transferPairIds = items.map { detectTransferPairIds(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // Use the new filtered database queries
    val totalDebit = range.flatMapLatest { (s,e) -> dao.totalDebitsFiltered(s,e) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    val totalCredit = range.flatMapLatest { (s,e) -> dao.totalCreditsFiltered(s,e) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    // Get all transactions for the last 6 months
    val allItems = dao.inRange(
        Calendar.getInstance().apply { add(Calendar.MONTH, -6) }.timeInMillis,
        System.currentTimeMillis()
    ).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Get total credits and debits for 6 months (excluding inter-account transfers)
    val totalDebit6Months = allItems.map { transactions ->
        transactions.filter { it.type == "DEBIT" && !isInterAccountTransfer(it) && it.amount in 1.0..1000000.0 }
            .sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    val totalCredit6Months = allItems.map { transactions ->
        transactions.filter { it.type == "CREDIT" && !isInterAccountTransfer(it) && it.amount in 1.0..1000000.0 }
            .sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    // Get current month totals excluding transfers and suspicious amounts
    val totalDebitCurrentMonth = combine(items, transferPairIds) { transactions, pairIds ->
        transactions.filter { 
            it.type == "DEBIT" && 
            !isInterAccountTransfer(it) && 
            it.id !in pairIds &&
            it.amount in 1.0..1000000.0
        }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    val totalCreditCurrentMonth = combine(items, transferPairIds) { transactions, pairIds ->
        transactions.filter { 
            it.type == "CREDIT" && 
            !isInterAccountTransfer(it) && 
            it.id !in pairIds &&
            it.amount in 1.0..1000000.0
        }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    // Helper function to detect inter-account transfers
    private fun isInterAccountTransfer(transaction: Transaction): Boolean {
        if (transaction.type == "TRANSFER") return true

        val body = transaction.rawBody.lowercase()

        // Stricter rules to reduce false positives
        val strongIndicators = listOf(
            "self transfer", "own account", "to own account", "between your accounts",
            "account to account", "a/c to a/c", "a/c transfer", "internal transfer",
            "intra bank transfer", "a2a", "a/c to a/c transfer"
        )

        val contextIndicators = listOf("to your account", "from your account", "between accounts")

        return strongIndicators.any { body.contains(it) } ||
               (body.contains("transfer") && contextIndicators.any { body.contains(it) })
    }

    /** Manual add */
    fun addManual(amount: Double, type: String, merchant: String?, channel: String?, whenTs: Long = System.currentTimeMillis()) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val t = Transaction(
                    ts = whenTs,
                    amount = amount,
                    type = type.uppercase(Locale.getDefault()),
                    channel = channel,
                    merchant = merchant,
                    accountTail = null,
                    bank = null,
                    source = "MANUAL",
                    rawSender = "MANUAL",
                    rawBody = ""
                )
                dao.insert(t)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** First-run import of existing SMS with duplicate prevention */
    fun importRecentSms(monthsBack: Long = 6) {
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("HomeVm", "Starting SMS import for last $monthsBack months")
                var importedCount = 0
                var skippedCount = 0
                var rejectedCount = 0
                
                SmsImporter.importRecent(ctx, monthsBack) { sender, body, ts ->
                    // Launch a new coroutine for each SMS processing
                    launch {
                        try {
                            // Check if this SMS already exists
                            val exists = dao.exists(sender, body, ts)
                            if (exists == 0) {
                                SmsParser.parse(sender, body, ts)?.let { transaction ->
                                    // Additional validation before inserting
                                    if (transaction.amount in 1.0..1000000.0) {
                                        dao.insert(transaction)
                                        importedCount++
                                        Log.d("HomeVm", "Imported transaction: ${transaction.merchant} - ${transaction.amount}")
                                    } else {
                                        rejectedCount++
                                        Log.d("HomeVm", "Rejected transaction with amount: ${transaction.amount}")
                                    }
                                } ?: run {
                                    rejectedCount++
                                    Log.d("HomeVm", "SMS parsing failed for: $sender")
                                }
                            } else {
                                skippedCount++
                            }
                        } catch (e: Exception) {
                            // Log error but don't crash
                            Log.e("HomeVm", "Error processing SMS", e)
                            e.printStackTrace()
                        }
                    }
                }
                
                // Wait a bit for all SMS processing to complete
                kotlinx.coroutines.delay(1000)
                
                // Log import results
                Log.d("HomeVm", "SMS Import completed: $importedCount imported, $skippedCount skipped, $rejectedCount rejected")
                println("SMS Import completed: $importedCount imported, $skippedCount skipped, $rejectedCount rejected")
            } catch (e: Exception) {
                // Log error but don't crash the app
                Log.e("HomeVm", "Error during SMS import", e)
                e.printStackTrace()
            }
        }
    }

    /** Clear all SMS transactions (useful for debugging) */
    fun clearSmsTransactions() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                dao.clearSmsTransactions()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** Get transaction statistics */
    suspend fun getTransactionStats(): Pair<Int, Int> {
        return try {
            dao.getTransactionCount() to dao.getSmsTransactionCount()
        } catch (e: Exception) {
            e.printStackTrace()
            0 to 0 // Return default values if database access fails
        }
    }

    /** Get suspicious transactions for debugging */
    suspend fun getSuspiciousTransactions(): List<Transaction> {
        return try {
            dao.getSuspiciousTransactions()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /** Debug function to show potential inflated numbers */
    suspend fun debugInflatedNumbers(): String {
        return try {
            val suspicious = dao.getSuspiciousTransactions()
            val allTxns = dao.inRange(
                Calendar.getInstance().apply { add(Calendar.MONTH, -1) }.timeInMillis,
                System.currentTimeMillis()
            ).first()
            
            val highAmountTxns = allTxns.filter { it.amount > 50000 }
            val transferTxns = allTxns.filter { it.type == "TRANSFER" }
            
            buildString {
                appendLine("=== DEBUG: Potential Inflated Numbers ===")
                appendLine("Suspicious transactions (>1L): ${suspicious.size}")
                suspicious.take(5).forEach { t ->
                    appendLine("  ${t.amount} ${t.type} - ${t.merchant} (${t.bank})")
                }
                appendLine("High amount transactions (>50K): ${highAmountTxns.size}")
                highAmountTxns.take(5).forEach { t ->
                    appendLine("  ${t.amount} ${t.type} - ${t.merchant} (${t.bank})")
                }
                appendLine("Transfer transactions: ${transferTxns.size}")
                appendLine("Total transactions this month: ${allTxns.size}")
                appendLine("Current month totals (filtered):")
                appendLine("  Debits: ${totalDebitCurrentMonth.value}")
                appendLine("  Credits: ${totalCreditCurrentMonth.value}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Error debugging: ${e.message}"
        }
    }

    /** Debug function to test SMS parsing with specific examples */
    suspend fun testSmsParsing(smsExamples: List<String>): String {
        return try {
            buildString {
                appendLine("=== SMS Parsing Test ===")
                smsExamples.forEach { sms ->
                    appendLine("Testing SMS: $sms")
                    val parsed = SmsParser.parse("TESTBANK", sms, System.currentTimeMillis())
                    if (parsed != null) {
                        appendLine("  ✅ PARSED: ${parsed.amount} ${parsed.type} - ${parsed.merchant}")
                    } else {
                        appendLine("  ❌ REJECTED")
                    }
                    appendLine()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Error testing SMS parsing: ${e.message}"
        }
    }

    /** Debug function to show recent SMS parsing results */
    suspend fun debugRecentSmsParsing(): String {
        return try {
            val recentTxns = dao.inRange(
                Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -7) }.timeInMillis,
                System.currentTimeMillis()
            ).first()
            
            buildString {
                appendLine("=== Recent SMS Parsing Results ===")
                appendLine("Last 7 days transactions: ${recentTxns.size}")
                appendLine()
                
                recentTxns.take(10).forEach { txn ->
                    appendLine("${txn.amount} ${txn.type} - ${txn.merchant} (${txn.bank})")
                    appendLine("  Source: ${txn.source}")
                    appendLine("  Channel: ${txn.channel}")
                    appendLine("  Raw body preview: ${txn.rawBody.take(100)}...")
                    appendLine()
                }
                
                // Show amounts by type
                val byType = recentTxns.groupBy { it.type }
                byType.forEach { (type, txns) ->
                    val total = txns.sumOf { it.amount }
                    appendLine("$type: ${txns.size} transactions, total: $total")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Error debugging recent SMS: ${e.message}"
        }
    }

    /** Comprehensive analysis of all transactions to find inflated numbers */
    suspend fun analyzeInflatedNumbers(): String {
        return try {
            val allTxns = dao.inRange(
                Calendar.getInstance().apply { add(Calendar.MONTH, -1) }.timeInMillis,
                System.currentTimeMillis()
            ).first()
            
            buildString {
                appendLine("=== INFLATED NUMBERS ANALYSIS ===")
                appendLine("Total transactions this month: ${allTxns.size}")
                appendLine()
                
                // Group by amount ranges to identify suspicious patterns
                val byAmountRange = allTxns.groupBy { txn ->
                    when {
                        txn.amount < 1000 -> "Under 1K"
                        txn.amount < 10000 -> "1K-10K"
                        txn.amount < 50000 -> "10K-50K"
                        txn.amount < 100000 -> "50K-1L"
                        txn.amount < 500000 -> "1L-5L"
                        else -> "Above 5L"
                    }
                }
                
                appendLine("=== TRANSACTIONS BY AMOUNT RANGE ===")
                byAmountRange.forEach { (range, txns) ->
                    val total = txns.sumOf { it.amount }
                    val byType = txns.groupBy { it.type }
                    appendLine("$range: ${txns.size} transactions, total: $total")
                    byType.forEach { (type, typeTxns) ->
                        val typeTotal = typeTxns.sumOf { it.amount }
                        appendLine("  $type: ${typeTxns.size} transactions, total: $typeTotal")
                    }
                    appendLine()
                }
                
                // Show high-value transactions that might be balances
                val highValueTxns = allTxns.filter { it.amount > 50000 }
                if (highValueTxns.isNotEmpty()) {
                    appendLine("=== HIGH VALUE TRANSACTIONS (>50K) - POTENTIAL BALANCES ===")
                    highValueTxns.sortedByDescending { it.amount }.take(10).forEach { txn ->
                        appendLine("${txn.amount} ${txn.type} - ${txn.merchant} (${txn.bank})")
                        appendLine("  Raw body: ${txn.rawBody}")
                        appendLine()
                    }
                }
                
                // Show promotional-looking transactions
                val promotionalKeywords = listOf(
                    "cashback", "offer", "promo", "discount", "sale", "bonus", "reward",
                    "up to", "upto", "valid", "till", "until", "apply", "click"
                )
                val suspiciousTxns = allTxns.filter { txn ->
                    val body = txn.rawBody.lowercase()
                    promotionalKeywords.any { body.contains(it) }
                }
                
                if (suspiciousTxns.isNotEmpty()) {
                    appendLine("=== SUSPICIOUS TRANSACTIONS (PROMOTIONAL KEYWORDS) ===")
                    suspiciousTxns.take(10).forEach { txn ->
                        appendLine("${txn.amount} ${txn.type} - ${txn.merchant} (${txn.bank})")
                        appendLine("  Raw body: ${txn.rawBody}")
                        appendLine()
                    }
                }
                
                // Show balance-looking transactions
                val balanceKeywords = listOf(
                    "balance", "bal", "available", "current", "total", "minimum", "maximum"
                )
                val balanceLikeTxns = allTxns.filter { txn ->
                    val body = txn.rawBody.lowercase()
                    balanceKeywords.any { body.contains(it) }
                }
                
                if (balanceLikeTxns.isNotEmpty()) {
                    appendLine("=== BALANCE-LIKE TRANSACTIONS ===")
                    balanceLikeTxns.take(10).forEach { txn ->
                        appendLine("${txn.amount} ${txn.type} - ${txn.merchant} (${txn.bank})")
                        appendLine("  Raw body: ${txn.rawBody}")
                        appendLine()
                    }
                }
                
                // Summary
                appendLine("=== SUMMARY ===")
                val totalDebit = allTxns.filter { it.type == "DEBIT" }.sumOf { it.amount }
                val totalCredit = allTxns.filter { it.type == "CREDIT" }.sumOf { it.amount }
                val totalTransfer = allTxns.filter { it.type == "TRANSFER" }.sumOf { it.amount }
                
                appendLine("Total Debits: $totalDebit")
                appendLine("Total Credits: $totalCredit")
                appendLine("Total Transfers: $totalTransfer")
                appendLine("Net Spending: ${totalDebit - totalCredit}")
                
                // Identify potential issues
                if (highValueTxns.isNotEmpty()) {
                    appendLine("⚠️  WARNING: Found ${highValueTxns.size} high-value transactions that might be balances")
                }
                if (suspiciousTxns.isNotEmpty()) {
                    appendLine("⚠️  WARNING: Found ${suspiciousTxns.size} suspicious transactions with promotional keywords")
                }
                if (balanceLikeTxns.isNotEmpty()) {
                    appendLine("⚠️  WARNING: Found ${balanceLikeTxns.size} transactions that look like balance updates")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Error analyzing inflated numbers: ${e.message}"
        }
    }

    /** Re-import SMS (clears existing SMS data first) */
    fun reimportSms(monthsBack: Long = 6) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Clear existing SMS transactions
                dao.clearSmsTransactions()
                // Import fresh
                importRecentSms(monthsBack)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun debugSmsParsing(smsText: String): String {
        return SmsParser.debugSmsContent(smsText)
    }

    fun testSmsExamples(): String {
        return SmsParser.testSmsExamples()
    }

    /** Log all recent SMS from device to show complete parsing picture */
    fun logAllRecentSms(limit: Int = 100): String {
        return SmsParser.importAndLogAllRecentSms(getApplication(), limit)
    }

    /** Log current UI data to show what's actually being displayed to user */
    suspend fun logCurrentUIData(): String {
        val results = StringBuilder()
        results.appendLine("=== CURRENT UI DATA LOGGING ===")
        results.appendLine("This shows exactly what the user sees in the UI")
        results.appendLine()
        
        try {
            // Get current totals
            val currentDebit = totalDebit.value ?: 0.0
            val currentCredit = totalCredit.value ?: 0.0
            val currentBalance = currentCredit - currentDebit
            
            results.appendLine("💰 CURRENT TOTALS:")
            results.appendLine("  - Total Debits (Expenses): ₹$currentDebit")
            results.appendLine("  - Total Credits (Income): ₹$currentCredit")
            results.appendLine("  - Net Balance: ₹$currentBalance")
            results.appendLine()
            
            // Get current month totals
            val currentMonthDebit = totalDebitCurrentMonth.value ?: 0.0
            val currentMonthCredit = totalCreditCurrentMonth.value ?: 0.0
            val currentMonthBalance = currentMonthCredit - currentMonthDebit
            
            results.appendLine("📅 CURRENT MONTH TOTALS:")
            results.appendLine("  - Month Debits (Expenses): ₹$currentMonthDebit")
            results.appendLine("  - Month Credits (Income): ₹$currentMonthCredit")
            results.appendLine("  - Month Net Balance: ₹$currentMonthBalance")
            results.appendLine()
            
            // Get 6 months totals
            val sixMonthDebit = totalDebit6Months.value ?: 0.0
            val sixMonthCredit = totalCredit6Months.value ?: 0.0
            val sixMonthBalance = sixMonthCredit - sixMonthDebit
            
            results.appendLine("📊 LAST 6 MONTHS TOTALS:")
            results.appendLine("  - 6M Debits (Expenses): ₹$sixMonthDebit")
            results.appendLine("  - 6M Credits (Income): ₹$sixMonthCredit")
            results.appendLine("  - 6M Net Balance: ₹$sixMonthBalance")
            results.appendLine()
            
            // Get recent transactions (last 50)
            results.appendLine("📋 RECENT TRANSACTIONS (Last 50):")
            val recentTransactions = dao.inRange(
                System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L), // Last 30 days
                System.currentTimeMillis()
            ).first()
            
            if (recentTransactions.isNotEmpty()) {
                recentTransactions.take(50).forEachIndexed { index, txn ->
                    results.appendLine("  ${index + 1}. ${txn.type} ₹${txn.amount} - ${txn.merchant ?: "Unknown"} (${txn.channel ?: "Unknown"})")
                    results.appendLine("     - Bank: ${txn.bank ?: "Unknown"}")
                    results.appendLine("     - Account: ${txn.accountTail ?: "Unknown"}")
                    results.appendLine("     - Time: ${java.text.SimpleDateFormat("dd-MM-yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(txn.ts))}")
                    results.appendLine("     - Raw SMS: ${txn.rawBody?.take(100) ?: "N/A"}...")
                    results.appendLine()
                }
            } else {
                results.appendLine("  ❌ No recent transactions found")
            }
            
            // Get transaction counts by type
            val creditCount = dao.getCreditTransactionCount().first()
            val debitCount = dao.getDebitTransactionCount().first()
            val transferCount = dao.getTransferTransactionCount().first()
            
            results.appendLine("📈 TRANSACTION COUNTS:")
            results.appendLine("  - Credit Transactions: $creditCount")
            results.appendLine("  - Debit Transactions: $debitCount")
            results.appendLine("  - Transfer Transactions: $transferCount")
            results.appendLine("  - Total Transactions: ${creditCount + debitCount + transferCount}")
            results.appendLine()
            
            // Get suspicious transactions
            val suspiciousTransactions = dao.getSuspiciousTransactions()
            if (suspiciousTransactions.isNotEmpty()) {
                results.appendLine("⚠️  SUSPICIOUS TRANSACTIONS (>₹100,000):")
                suspiciousTransactions.forEach { txn ->
                    results.appendLine("  - ${txn.type} ₹${txn.amount} - ${txn.merchant ?: "Unknown"}")
                    results.appendLine("    Raw SMS: ${txn.rawBody?.take(100) ?: "N/A"}...")
                }
                results.appendLine()
            }
            
            // Get transactions by channel
            results.appendLine("🔗 TRANSACTIONS BY CHANNEL:")
            val upiTransactions = dao.getTransactionsByChannel("UPI").first()
            val cardTransactions = dao.getTransactionsByChannel("CARD").first()
            val atmTransactions = dao.getTransactionsByChannel("ATM").first()
            val neftTransactions = dao.getTransactionsByChannel("NEFT").first()
            val impsTransactions = dao.getTransactionsByChannel("IMPS").first()
            
            results.appendLine("  - UPI: ${upiTransactions.size} transactions")
            results.appendLine("  - CARD: ${cardTransactions.size} transactions")
            results.appendLine("  - ATM: ${atmTransactions.size} transactions")
            results.appendLine("  - NEFT: ${neftTransactions.size} transactions")
            results.appendLine("  - IMPS: ${impsTransactions.size} transactions")
            results.appendLine()
            
            // Get transactions by bank
            results.appendLine("🏦 TRANSACTIONS BY BANK:")
            val hdfcTransactions = dao.getTransactionsByBank("HDFC").first()
            val iciciTransactions = dao.getTransactionsByBank("ICICI").first()
            val sbiTransactions = dao.getTransactionsByBank("SBI").first()
            val axisTransactions = dao.getTransactionsByBank("AXIS").first()
            
            results.appendLine("  - HDFC: ${hdfcTransactions.size} transactions")
            results.appendLine("  - ICICI: ${iciciTransactions.size} transactions")
            results.appendLine("  - SBI: ${sbiTransactions.size} transactions")
            results.appendLine("  - AXIS: ${axisTransactions.size} transactions")
            results.appendLine()
            
            results.appendLine("=== END UI DATA LOGGING ===")
            
        } catch (e: Exception) {
            results.appendLine("❌ Error during UI data logging: ${e.message}")
            e.printStackTrace()
        }
        
        return results.toString()
    }

    /** Non-suspend wrapper to trigger comprehensive UI data logging */
    fun triggerUIDataLogging() {
        viewModelScope.launch {
            try {
                val logData = logCurrentUIData()
                Log.d("HomeVm", "=== COMPREHENSIVE UI DATA LOG ===")
                Log.d("HomeVm", logData)
                Log.d("HomeVm", "=== END UI DATA LOG ===")
            } catch (e: Exception) {
                Log.e("HomeVm", "Failed to log UI data", e)
            }
        }
    }

    fun analyzeCurrentData(): String {
        val analysis = StringBuilder()
        analysis.appendLine("=== Current Data Analysis ===")
        
        viewModelScope.launch {
            try {
                val endTime = System.currentTimeMillis()
                val startTime = endTime - (30 * 24 * 60 * 60 * 1000L) // Last 30 days
                
                val allTransactions = dao.inRange(startTime, endTime).first()
                val credits = allTransactions.filter { it.type == "CREDIT" }
                val debits = allTransactions.filter { it.type == "DEBIT" }
                val transfers = allTransactions.filter { it.type == "TRANSFER" }
                
                analysis.appendLine("Total transactions: ${allTransactions.size}")
                analysis.appendLine("Credits: ${credits.size}")
                analysis.appendLine("Debits: ${debits.size}")
                analysis.appendLine("Transfers: ${transfers.size}")
                
                if (credits.isNotEmpty()) {
                    val avgCredit = credits.map { it.amount }.average()
                    val maxCredit = credits.map { it.amount }.maxOrNull()
                    analysis.appendLine("Average credit: $avgCredit")
                    analysis.appendLine("Max credit: $maxCredit")
                }
                
                if (debits.isNotEmpty()) {
                    val avgDebit = debits.map { it.amount }.average()
                    val maxDebit = debits.map { it.amount }.maxOrNull()
                    analysis.appendLine("Average debit: $avgDebit")
                    analysis.appendLine("Max debit: $maxDebit")
                }
                
                // Show suspicious transactions
                val suspicious = allTransactions.filter { it.amount > 100000 }
                if (suspicious.isNotEmpty()) {
                    analysis.appendLine("\nSuspicious transactions (>1L):")
                    suspicious.forEach { txn ->
                        analysis.appendLine("- ${txn.type} ${txn.amount} (${txn.merchant ?: "Unknown"}) - ${txn.rawBody.take(100)}")
                    }
                }
            } catch (e: Exception) {
                analysis.appendLine("Error analyzing data: ${e.message}")
                e.printStackTrace()
            }
        }
        
        return analysis.toString()
    }
}
