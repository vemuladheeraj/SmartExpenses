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

    // Get total credits and debits for 6 months (excluding inter-account transfers)
    val totalDebit6Months = allItems.map { transactions ->
        transactions.filter { it.type == "DEBIT" && !isInterAccountTransfer(it) }
            .sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    val totalCredit6Months = allItems.map { transactions ->
        transactions.filter { it.type == "CREDIT" && !isInterAccountTransfer(it) }
            .sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    // Get current month totals excluding transfers
    val totalDebitCurrentMonth = items.map { transactions ->
        transactions.filter { it.type == "DEBIT" && !isInterAccountTransfer(it) }
            .sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    val totalCreditCurrentMonth = items.map { transactions ->
        transactions.filter { it.type == "CREDIT" && !isInterAccountTransfer(it) }
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
                
                SmsImporter.importRecent(ctx, monthsBack) { sender, body, ts ->
                    // Launch a new coroutine for each SMS processing
                    launch {
                        try {
                            // Check if this SMS already exists
                            val exists = dao.exists(sender, body, ts)
                            if (exists == 0) {
                                SmsParser.parse(sender, body, ts)?.let { transaction ->
                                    dao.insert(transaction)
                                    importedCount++
                                    Log.d("HomeVm", "Imported transaction: ${transaction.merchant} - ${transaction.amount}")
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
                Log.d("HomeVm", "SMS Import completed: $importedCount imported, $skippedCount skipped")
                println("SMS Import completed: $importedCount imported, $skippedCount skipped")
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
}
