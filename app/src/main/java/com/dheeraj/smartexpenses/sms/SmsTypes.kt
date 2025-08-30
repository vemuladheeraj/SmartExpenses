package com.dheeraj.smartexpenses.sms

/**
 * Transaction direction enum
 */
enum class TransactionDirection {
    DEBIT,      // Money going out
    CREDIT,     // Money coming in
    TRANSFER,   // Internal transfer between accounts
    NONE        // No clear direction
}

/**
 * Parsed row for aggregation
 */
data class ParsedRow(
    val timestamp: Long,
    val rawText: String,
    val amountPaise: Long,
    val direction: TransactionDirection,
    val type: String,
    val merchant: String?,
    val confidence: Float,
    var ignoreForCalculations: Boolean
)

/**
 * Enhanced transaction pair utilities for detecting internal transfers
 */
object TransactionPairUtils {
    
    /**
     * Enhanced method to mark offsetting pairs as ignored
     * Now considers multiple transfer scenarios
     */
    fun markOffsettingPairs(rows: List<ParsedRow>) {
        // Group by amount and timestamp window
        val amountGroups = rows.groupBy { it.amountPaise }
        
        amountGroups.forEach { (_, groupRows) ->
            if (groupRows.size >= 2) {
                // Look for opposite directions within time window
                val debits = groupRows.filter { it.direction == TransactionDirection.DEBIT }
                val credits = groupRows.filter { it.direction == TransactionDirection.CREDIT }
                val transfers = groupRows.filter { it.direction == TransactionDirection.TRANSFER }
                
                // Scenario 1: DEBIT + CREDIT pairs (internal transfers)
                if (debits.isNotEmpty() && credits.isNotEmpty()) {
                    // Check if they're likely internal transfers
                    val isInternalTransfer = detectInternalTransfer(debits, credits)
                    if (isInternalTransfer) {
                        debits.forEach { it.ignoreForCalculations = true }
                        credits.forEach { it.ignoreForCalculations = true }
                    }
                }
                
                // Scenario 2: Multiple TRANSFER transactions (redundant transfers)
                if (transfers.size > 1) {
                    // Keep only the first transfer, mark others as ignored
                    transfers.drop(1).forEach { it.ignoreForCalculations = true }
                }
                
                // Scenario 3: DEBIT/CREDIT + TRANSFER pairs (one-sided transfers)
                if (transfers.isNotEmpty() && (debits.isNotEmpty() || credits.isNotEmpty())) {
                    // If we have a transfer, the corresponding debit/credit should be ignored
                    if (debits.isNotEmpty()) {
                        debits.forEach { it.ignoreForCalculations = true }
                    }
                    if (credits.isNotEmpty()) {
                        credits.forEach { it.ignoreForCalculations = true }
                    }
                }
            }
        }
    }
    
    /**
     * Enhanced internal transfer detection
     */
    private fun detectInternalTransfer(debits: List<ParsedRow>, credits: List<ParsedRow>): Boolean {
        // Check for same-amount pairs within reasonable time window
        val timeWindowMs = 10 * 60 * 1000L // 10 minutes
        
        for (debit in debits) {
            for (credit in credits) {
                val timeDiff = kotlin.math.abs(debit.timestamp - credit.timestamp)
                if (timeDiff <= timeWindowMs) {
                    // Check for transfer indicators in the text
                    if (hasTransferIndicators(debit.rawText) || hasTransferIndicators(credit.rawText)) {
                        return true
                    }
                    
                    // Check for similar merchant names (self-transfers)
                    if (isSimilarMerchant(debit.merchant, credit.merchant)) {
                        return true
                    }
                }
            }
        }
        
        return false
    }
    
    /**
     * Check for transfer indicators in SMS text
     */
    private fun hasTransferIndicators(text: String): Boolean {
        val transferKeywords = listOf(
            "self transfer", "own account", "same account", "account transfer",
            "internal transfer", "own transfer", "self account", "between accounts",
            "from account", "to account", "account to account"
        )
        
        return transferKeywords.any { text.contains(it, ignoreCase = true) }
    }
    
    /**
     * Check if two merchant names are similar (for self-transfers)
     */
    private fun isSimilarMerchant(merchant1: String?, merchant2: String?): Boolean {
        if (merchant1 == null || merchant2 == null) return false
        
        val clean1 = merchant1.lowercase().trim()
        val clean2 = merchant2.lowercase().trim()
        
        // Exact match
        if (clean1 == clean2) return true
        
        // Check for common self-transfer patterns
        val selfTransferPatterns = listOf(
            "self", "own", "same", "account", "transfer"
        )
        
        val hasSelfTransferWords = selfTransferPatterns.any { word ->
            clean1.contains(word) || clean2.contains(word)
        }
        
        return hasSelfTransferWords
    }
    
    /**
     * Enhanced method to check if row should be skipped for totals
     */
    fun shouldSkipForTotals(row: ParsedRow): Boolean {
        // Skip transfers (they're neither income nor expense)
        if (row.direction == TransactionDirection.TRANSFER) {
            return true
        }
        
        // Skip card payments, refunds, etc.
        if (row.type.contains("CARD", ignoreCase = true) ||
            row.rawText.contains("refund", ignoreCase = true) ||
            row.rawText.contains("reversal", ignoreCase = true)) {
            return true
        }
        
        // Skip promotional transactions
        if (isPromotionalTransaction(row.rawText)) {
            return true
        }
        
        // Skip if explicitly marked to ignore
        return row.ignoreForCalculations
    }
    
    /**
     * Check if transaction is promotional
     */
    private fun isPromotionalTransaction(text: String): Boolean {
        val promotionalKeywords = listOf(
            "cashback", "rewards", "bonus", "offer", "promotion", "discount"
        )
        
        return promotionalKeywords.any { text.contains(it, ignoreCase = true) }
    }
    
    /**
     * Get transaction summary excluding transfers and ignored items
     */
    fun getTransactionSummary(rows: List<ParsedRow>): TransactionSummary {
        val validRows = rows.filter { !shouldSkipForTotals(it) }
        
        val totalIncome = validRows
            .filter { it.direction == TransactionDirection.CREDIT }
            .sumOf { it.amountPaise }
        
        val totalExpenses = validRows
            .filter { it.direction == TransactionDirection.DEBIT }
            .sumOf { it.amountPaise }
        
        val transferCount = rows.count { it.direction == TransactionDirection.TRANSFER }
        val ignoredCount = rows.count { it.ignoreForCalculations }
        
        return TransactionSummary(
            totalIncome = totalIncome,
            totalExpenses = totalExpenses,
            netAmount = totalIncome - totalExpenses,
            transferCount = transferCount,
            ignoredCount = ignoredCount,
            totalTransactions = rows.size
        )
    }
}

/**
 * Transaction summary data class
 */
data class TransactionSummary(
    val totalIncome: Long,      // in paise
    val totalExpenses: Long,    // in paise
    val netAmount: Long,        // in paise (positive = net income, negative = net expense)
    val transferCount: Int,
    val ignoredCount: Int,
    val totalTransactions: Int
) {
    // Convenience properties for UI
    val totalIncomeRupees: Double get() = totalIncome / 100.0
    val totalExpensesRupees: Double get() = totalExpenses / 100.0
    val netAmountRupees: Double get() = netAmount / 100.0
    val isNetIncome: Boolean get() = netAmount > 0
    val isNetExpense: Boolean get() = netAmount < 0
    val isBalanced: Boolean get() = netAmount == 0L
}
