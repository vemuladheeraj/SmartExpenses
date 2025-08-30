package com.dheeraj.smartexpenses.ui

import android.app.Application
import android.util.Log
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dheeraj.smartexpenses.data.AppDb
import com.dheeraj.smartexpenses.data.Transaction
import com.dheeraj.smartexpenses.sms.SmsImporter
import com.dheeraj.smartexpenses.sms.SmsParser
import com.dheeraj.smartexpenses.utils.DataDebugger
import com.dheeraj.smartexpenses.security.SecurePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.Calendar
import java.util.Locale
import com.dheeraj.smartexpenses.data.amount

@OptIn(ExperimentalCoroutinesApi::class)
class HomeVm(app: Application) : AndroidViewModel(app) {
    
    init {
        try {
            Log.d("HomeVm", "Initializing HomeViewModel...")
            AppDb.get(getApplication()).txnDao()
            Log.d("HomeVm", "Database initialized successfully")
            
            // Check AI insights configuration
            viewModelScope.launch {
                checkAiInsightsConfiguration()
            }
        } catch (e: Exception) {
            Log.e("HomeVm", "Error initializing database", e)
            e.printStackTrace()
        }
    }
    
    private val dao = AppDb.get(getApplication()).txnDao()
    private val prefs: SharedPreferences by lazy {
        getApplication<Application>().getSharedPreferences("smart_expenses_prefs", Context.MODE_PRIVATE)
    }
    // Background SMS import state for UI
    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()
    private val _importProgress = MutableStateFlow(0 to 0)
    val importProgress: StateFlow<Pair<Int, Int>> = _importProgress.asStateFlow()
    
    // Additional state properties for reset functionality
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()
    private val _processingStats = MutableStateFlow(SmsProcessingStats())
    val processingStats: StateFlow<SmsProcessingStats> = _processingStats.asStateFlow()
    private val _lastImportTime = MutableStateFlow<Long?>(null)
    val lastImportTime: StateFlow<Long?> = _lastImportTime.asStateFlow()
    private val _lastProcessingTime = MutableStateFlow<Long?>(null)
    val lastProcessingTime: StateFlow<Long?> = _lastProcessingTime.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()
    private val _hasAiInsightsConfigured = MutableStateFlow(false)
    val hasAiInsightsConfigured: StateFlow<Boolean> = _hasAiInsightsConfigured.asStateFlow()

    enum class RangeMode { CALENDAR_MONTH, ROLLING_MONTH }

    private val now = MutableStateFlow(System.currentTimeMillis())
    private val rangeMode = MutableStateFlow(RangeMode.CALENDAR_MONTH)
    val rangeModeState: StateFlow<RangeMode> = rangeMode.asStateFlow()
    fun refresh() { now.value = System.currentTimeMillis() }
    fun setRangeMode(mode: RangeMode) { rangeMode.value = mode; refresh() }

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

    private fun rollingMonthBounds(epoch: Long): Pair<Long, Long> {
        val end = epoch
        val startCal = Calendar.getInstance()
        startCal.timeInMillis = epoch
        // Move back one month keeping the same day as today (or last day if shorter month)
        val targetDay = startCal.get(Calendar.DAY_OF_MONTH)
        startCal.add(Calendar.MONTH, -1)
        val maxDay = startCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        startCal.set(Calendar.DAY_OF_MONTH, minOf(targetDay, maxDay))
        startCal.set(Calendar.HOUR_OF_DAY, 0)
        startCal.set(Calendar.MINUTE, 0)
        startCal.set(Calendar.SECOND, 0)
        startCal.set(Calendar.MILLISECOND, 0)
        val start = startCal.timeInMillis
        return start to end
    }

    private val range = combine(now, rangeMode) { currentNow, mode ->
        when (mode) {
            RangeMode.CALENDAR_MONTH -> monthBounds(currentNow)
            RangeMode.ROLLING_MONTH -> rollingMonthBounds(currentNow)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0L to Long.MAX_VALUE)
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L to Long.MAX_VALUE)

    val items = range.flatMapLatest { (s,e) -> dao.inRange(s,e) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val totalDebit = range.flatMapLatest { (s,e) -> dao.totalDebits(s,e) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    val totalCredit = range.flatMapLatest { (s,e) -> dao.totalCredits(s,e) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    // Get all transactions for the last 6 months
    val allItems = dao.inRange(
        Calendar.getInstance().apply { add(Calendar.MONTH, -6) }.timeInMillis,
        System.currentTimeMillis()
    ).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Get total credits and debits for 6 months (excluding inter-account transfers and paired same-amount debit/credits)
    val totalDebit6Months = allItems.map { transactions ->
        val paired = findPairedTransferIds(transactions)
        transactions.filter { it.type == "DEBIT" && !isInterAccountTransfer(it) && it.id !in paired }
            .sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    val totalCredit6Months = allItems.map { transactions ->
        val paired = findPairedTransferIds(transactions)
        transactions.filter { it.type == "CREDIT" && !isInterAccountTransfer(it) && it.id !in paired }
            .sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    // Get current month totals excluding transfers
    val totalDebitCurrentMonth = items.map { transactions ->
        val paired = findPairedTransferIds(transactions)
        transactions.filter { it.type == "DEBIT" && !isInterAccountTransfer(it) && it.id !in paired }
            .sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    val totalCreditCurrentMonth = items.map { transactions ->
        val paired = findPairedTransferIds(transactions)
        transactions.filter { it.type == "CREDIT" && !isInterAccountTransfer(it) && it.id !in paired }
            .sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    // Helper function to detect inter-account transfers
    private fun isInterAccountTransfer(transaction: Transaction): Boolean {
        // Check if it's marked as TRANSFER type
        if (transaction.type == "TRANSFER") return true
        
        val body = transaction.rawBody.lowercase()
        
        // Strong indicators of internal transfer; avoid broad keywords that misclassify card/UPI
        val strongTransferKeywords = listOf(
            "self transfer", "own account", "between your accounts", "internal transfer",
            "intra bank", "same bank", "account to account", "a/c to a/c", "inter account"
        )

        if (strongTransferKeywords.any { body.contains(it) }) return true

        // One SMS showing both credit and debit (typical for internal movement)
        if (body.contains("credited") && body.contains("debited")) return true

        // If two account tails are present, likely internal
        val tails = Regex("(?i)(?:a/c|account)(?:.*?)([0-9]{2,6})").findAll(transaction.rawBody).toList()
        if (tails.size >= 2) return true

        return false
    }

    /**
     * Identify likely inter-account transfers by pairing a debit and a credit
     * with the exact same amount occurring close in time (default 2 hours),
     * optionally matching on bank or account tail when available.
     * Returns the set of transaction ids that are part of such pairs.
     */
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

            // Move pointers to keep within time window
            if (c.ts < d.ts && (d.ts - c.ts) > timeWindowMillis) { i++; continue }
            if (d.ts < c.ts && (c.ts - d.ts) > timeWindowMillis) { j++; continue }

            val amountMatch = kotlin.math.abs(c.amount - d.amount) < 0.01
            val bankMatch = c.bank != null && d.bank != null && c.bank == d.bank
            val tailMatch = c.accountTail != null && d.accountTail != null && c.accountTail == d.accountTail
            val nameMatch = namesSimilar(c.merchant, d.merchant) ||
                    namesSimilar(extractCounterparty(c.rawBody), extractCounterparty(d.rawBody)) ||
                    (normalizeName(c.rawSender) != null && normalizeName(c.rawSender) == normalizeName(d.rawSender))

            if (dt <= timeWindowMillis && amountMatch && (bankMatch || tailMatch || nameMatch)) {
                pairedIds += c.id
                pairedIds += d.id
                i++; j++
            } else {
                // advance the earlier one to search for a closer match
                if (c.ts <= d.ts) i++ else j++
            }
        }

        return pairedIds
    }

    private fun normalizeName(input: String?): String? {
        if (input == null) return null
        val s = input.lowercase(Locale.getDefault()).trim()
        val cleaned = s.replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\n".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
        return if (cleaned.isEmpty()) null else cleaned
    }

    private fun namesSimilar(a: String?, b: String?): Boolean {
        val na = normalizeName(a) ?: return false
        val nb = normalizeName(b) ?: return false
        return na == nb || na.contains(nb) || nb.contains(na)
    }

    private fun extractCounterparty(body: String?): String? {
        if (body == null) return null
        val regex = Regex("(?i)(?:to|from|at)\\s+([A-Za-z0-9 &._-]{3,40})")
        val match = regex.find(body) ?: return null
        return match.groupValues.getOrNull(1)
    }

    /** Manual add */
    fun addManual(amount: Double, type: String, merchant: String?, channel: String?, category: String? = null, whenTs: Long = System.currentTimeMillis()) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val t = Transaction(
                    ts = whenTs,
                    amountMinor = kotlin.math.round(amount * 100.0).toLong(),
                    type = type.uppercase(Locale.getDefault()),
                    channel = channel,
                    merchant = merchant,
                    category = category,
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
                // Skip if already imported once and DB has entries
                val alreadyDone = prefs.getBoolean("initial_import_done", false)
                val existingCount = try { dao.getSmsTransactionCount() } catch (e: Exception) { 0 }
                if (alreadyDone && existingCount > 0) {
                    Log.d("HomeVm", "Initial SMS import previously completed; skipping.")
                    _isImporting.value = false
                    return@launch
                }
                _isImporting.value = true
                _importProgress.value = 0 to 0
                // Fast import
                Log.d("HomeVm", "Starting SMS import for last $monthsBack months")
                var importedCount = 0
                var skippedCount = 0
                val totalSms = SmsImporter.getSmsCount(ctx, monthsBack)
                _importProgress.value = 0 to totalSms
                
                Log.d("HomeVm", "Found $totalSms SMS messages to process")
                
                SmsImporter.importWithProgress(ctx, monthsBack, 
                    onProgress = { current, total ->
                        _importProgress.value = current to total
                        Log.d("HomeVm", "Processing SMS $current/$total")
                    }
                ) { sender, body, ts ->
                    // Launch a new coroutine for each SMS processing
                    launch {
                        try {
                            // Check if this SMS already exists
                            val exists = dao.exists(sender, body, ts)
                            if (exists == 0) {
                                // Enhanced SMS parsing with detailed logging
                                val transaction = SmsParser.parse(sender, body, ts)
                                if (transaction != null) {
                                    // Log transaction details for debugging
                                    val transactionType = when (transaction.type) {
                                        "TRANSFER" -> "TRANSFER (Internal)"
                                        "CREDIT" -> "CREDIT (Income)"
                                        "DEBIT" -> "DEBIT (Expense)"
                                        else -> transaction.type
                                    }
                                    
                                    Log.d("HomeVm", "Imported $transactionType: ${transaction.merchant ?: "Unknown"} - ₹${transaction.amountMinor / 100} from ${transaction.bank}")
                                    
                                    dao.insert(transaction)
                                    importedCount++
                                } else {
                                    // Log why SMS was rejected
                                    Log.d("HomeVm", "SMS parsing failed for $sender: ${body.take(50)}...")
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
                Log.d("HomeVm", "SMS Import completed: $importedCount imported, $skippedCount skipped out of $totalSms total")
                println("SMS Import completed: $importedCount imported, $skippedCount skipped out of $totalSms total")
                _isImporting.value = false
                // Mark first-run import as completed
                prefs.edit().putBoolean("initial_import_done", true).apply()

                // Background enrichment removed with LLM path
            } catch (e: Exception) {
                // Log error but don't crash the app
                Log.e("HomeVm", "Error during SMS import", e)
                e.printStackTrace()
                _isImporting.value = false
            }
        }
    }

    /** Wrapper to only import on first run or when DB empty */
    fun importIfFirstRun(monthsBack: Long = 6) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Log data state for debugging
                DataDebugger.logDataState(getApplication())
                
                val alreadyDone = prefs.getBoolean("initial_import_done", false)
                val existingCount = try { dao.getSmsTransactionCount() } catch (e: Exception) { 0 }
                
                Log.d("HomeVm", "importIfFirstRun: alreadyDone=$alreadyDone, existingCount=$existingCount")
                
                // If database is empty, clear the flag to ensure fresh import
                if (existingCount == 0 && alreadyDone) {
                    Log.d("HomeVm", "Database is empty but flag shows import done. Clearing flag for fresh import.")
                    prefs.edit().putBoolean("initial_import_done", false).apply()
                }
                
                // Check again after potential flag reset
                val shouldImport = !prefs.getBoolean("initial_import_done", false) || existingCount == 0
                
                if (shouldImport) {
                    Log.d("HomeVm", "Starting fresh SMS import. existingCount=$existingCount, alreadyDone=$alreadyDone")
                    importRecentSms(monthsBack)
                } else {
                    Log.d("HomeVm", "Skipping importIfFirstRun: already done and DB has $existingCount entries")
                }
            } catch (e: Exception) { 
                Log.e("HomeVm", "Error in importIfFirstRun", e)
            }
        }
    }

    // Simple in-memory cache by template hash to avoid reclassifying repeats
    private val enrichmentCache = mutableMapOf<String, Pair<String?, String?>>() // key -> (channel, merchant)

    private suspend fun enrichInBackground(maxItems: Int = 120, delayMsBetween: Long = 300L) {
        try {
            val needing = dao.findNeedingEnrichment(limit = maxItems)
            for (t in needing) {
                try {
                    // Build a template key using sender + body with digits normalized
                    val key = (t.rawSender + "|" + t.rawBody.replace("\\d".toRegex(), "#")).take(512)
                    val cached = enrichmentCache[key]
                    val channel = cached?.first
                    val merchant = cached?.second

                    val updatedChannel: String?
                    val updatedMerchant: String?

                    if (channel != null || merchant != null) {
                        updatedChannel = channel ?: t.channel
                        updatedMerchant = merchant ?: t.merchant
                    } else {
                        // Use AI via SmsParser enrichment path by re-parsing; it will run AI since enabled
                        val enriched = SmsParser.parse(t.rawSender, t.rawBody, t.ts)
                        updatedChannel = enriched?.channel ?: t.channel
                        updatedMerchant = enriched?.merchant ?: t.merchant
                        // Cache the result for this template
                        enrichmentCache[key] = (updatedChannel to updatedMerchant)
                        // Rate limit between AI calls
                        kotlinx.coroutines.delay(delayMsBetween)
                    }

                    if (updatedChannel != t.channel || updatedMerchant != t.merchant) {
                        dao.update(t.copy(channel = updatedChannel, merchant = updatedMerchant))
                    }
                } catch (ie: InterruptedException) {
                    throw ie
                } catch (e: Exception) {
                    Log.e("HomeVm", "Enrichment failed for id=${t.id}", e)
                }
            }
        } catch (e: Exception) {
            Log.e("HomeVm", "Background enrichment error", e)
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
    
    /** Get all transactions for export */
    suspend fun getAllTransactions(): List<Transaction> {
        return try {
            dao.getAllTransactions()
        } catch (e: Exception) {
            Log.e("HomeVm", "Error getting all transactions", e)
            emptyList()
        }
    }
    
    /** Reset ViewModel state for fresh start */
    fun resetViewModel() {
        _isImporting.value = false
        _isProcessing.value = false
        _processingStats.value = SmsProcessingStats()
        _lastImportTime.value = null
        _lastProcessingTime.value = null
        _errorMessage.value = null
        _successMessage.value = null
    }

    /** Get detailed SMS processing statistics */
    suspend fun getSmsProcessingStats(): SmsProcessingStats {
        return try {
            val totalTransactions = dao.getTransactionCount()
            val smsTransactions = dao.getSmsTransactionCount()
            val totalDebits = dao.getTotalDebits()
            val totalCredits = dao.getTotalCredits()
            
            SmsProcessingStats(
                totalSmsProcessed = smsTransactions,
                transactionsExtracted = totalTransactions,
                conversionRate = if (smsTransactions > 0) {
                    (totalTransactions.toFloat() / smsTransactions * 100).toInt()
                } else 0,
                totalDebits = totalDebits,
                totalCredits = totalCredits,
                netAmount = totalCredits - totalDebits
            )
        } catch (e: Exception) {
            e.printStackTrace()
            SmsProcessingStats()
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

    /** Update transaction category by changing merchant name */
    fun updateTransactionCategory(transactionId: Long, newMerchant: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val transaction = dao.getTransactionById(transactionId)
                if (transaction != null) {
                    val updatedTransaction = transaction.copy(merchant = newMerchant)
                    dao.update(updatedTransaction)
                }
            } catch (e: Exception) {
                Log.e("HomeVm", "Error updating transaction category", e)
            }
        }
    }

    /** Get transaction by ID */
    suspend fun getTransactionById(id: Long): Transaction? {
        return try {
            dao.getTransactionById(id)
        } catch (e: Exception) {
            Log.e("HomeVm", "Error getting transaction by ID", e)
            null
        }
    }
    
    /** Check if AI insights are configured */
    suspend fun hasAiInsightsConfigured(): Boolean {
        return try {
            val securePrefs = SecurePreferences(getApplication())
            val apiKey = securePrefs.getApiKey()
            val customEndpoint = securePrefs.getCustomEndpoint()
            !apiKey.isNullOrBlank() || !customEndpoint.isNullOrBlank()
        } catch (e: Exception) {
            Log.e("HomeVm", "Error checking AI insights configuration", e)
            false
        }
    }
    
    /** Check and update AI insights configuration state */
    private suspend fun checkAiInsightsConfiguration() {
        try {
            val configured = hasAiInsightsConfigured()
            _hasAiInsightsConfigured.value = configured
        } catch (e: Exception) {
            Log.e("HomeVm", "Error checking AI insights configuration", e)
            _hasAiInsightsConfigured.value = false
        }
    }
}

data class SmsProcessingStats(
    val totalSmsProcessed: Int = 0,
    val transactionsExtracted: Int = 0,
    val conversionRate: Int = 0,
    val totalDebits: Double = 0.0,
    val totalCredits: Double = 0.0,
    val netAmount: Double = 0.0
)
