package com.dheeraj.smartexpenses.sms

import android.content.Context
import android.util.Log
import java.util.*

/**
 * Example usage of the enhanced SMS classifier with early-drop gates and pair cancellation
 */
class TransactionAggregator(private val context: Context) {
    private val classifier = SmsClassifier(context)
    private val TAG = "TransactionAggregator"
    
    /**
     * Process a list of SMS messages and return filtered, aggregated results
     */
    fun processSmsBatch(smsList: List<SmsMessage>): AggregationResult {
        Log.d(TAG, "Processing ${smsList.size} SMS messages")
        
        // Step 1: Analyze each SMS (early-drop gates are applied automatically)
        val analyses = mutableListOf<SmsAnalysis>()
        val parsedRows = mutableListOf<ParsedRow>()
        
        smsList.forEach { sms ->
            val analysis = classifier.analyzeSms(sms.text)
            if (analysis != null) {
                analyses.add(analysis)
                
                // Convert to ParsedRow for aggregation
                val parsedRow = classifier.analysisToParsedRow(analysis, sms.timestamp, sms.text)
                if (parsedRow != null) {
                    parsedRows.add(parsedRow)
                }
            }
        }
        
        Log.d(TAG, "Initial analysis: ${analyses.size} transactional SMS found")
        
        // Step 2: Apply pair cancellation to remove internal transfers
        TransactionPairUtils.markOffsettingPairs(parsedRows)
        
        val cancelledPairs = parsedRows.count { it.ignoreForCalculations }
        Log.d(TAG, "Pair cancellation: $cancelledPairs pairs marked as ignored")
        
        // Step 3: Filter out card payments and refunds from totals
        val filteredRows = parsedRows.filter { row ->
            !row.ignoreForCalculations && !TransactionPairUtils.shouldSkipForTotals(row.rawText)
        }
        
        val skippedForTotals = parsedRows.size - filteredRows.size - cancelledPairs
        Log.d(TAG, "Total filtering: $skippedForTotals additional transactions skipped for totals")
        
        // Step 4: Calculate totals
        val totalDebit = filteredRows
            .filter { it.direction == TransactionDirection.DEBIT }
            .sumOf { it.amountPaise }
        
        val totalCredit = filteredRows
            .filter { it.direction == TransactionDirection.CREDIT }
            .sumOf { it.amountPaise }
        
        return AggregationResult(
            totalSmsProcessed = smsList.size,
            transactionalSmsFound = analyses.size,
            pairsCancelled = cancelledPairs,
            skippedForTotals = skippedForTotals,
            finalDebitTotal = totalDebit / 100.0, // Convert back to rupees
            finalCreditTotal = totalCredit / 100.0,
            finalTransactions = filteredRows.size,
            allParsedRows = parsedRows
        )
    }
    
    /**
     * Clean up resources
     */
    fun close() {
        classifier.close()
    }
}

/**
 * Data class for SMS message with timestamp
 */
data class SmsMessage(
    val text: String,
    val timestamp: Long,
    val sender: String? = null
)

/**
 * Result of SMS aggregation with filtering and pair cancellation
 */
data class AggregationResult(
    val totalSmsProcessed: Int,
    val transactionalSmsFound: Int,
    val pairsCancelled: Int,
    val skippedForTotals: Int,
    val finalDebitTotal: Double,
    val finalCreditTotal: Double,
    val finalTransactions: Int,
    val allParsedRows: List<ParsedRow>
) {
    fun getSummary(): String {
        return """
            📊 SMS Aggregation Summary
            =========================
            📱 Total SMS processed: $totalSmsProcessed
            ✅ Transactional SMS found: $transactionalSmsFound
            🔄 Internal transfer pairs cancelled: $pairsCancelled
            ⏭️ Skipped for totals (card payments/refunds): $skippedForTotals
            💸 Final debit total: ₹${"%.2f".format(finalDebitTotal)}
            💰 Final credit total: ₹${"%.2f".format(finalCreditTotal)}
            📈 Final transaction count: $finalTransactions
        """.trimIndent()
    }
}

/**
 * Example usage and testing
 */
object TransactionAggregatorExample {
    
    fun runExample(context: Context): String {
        val aggregator = TransactionAggregator(context)
        
        // Sample SMS messages (some should be early-dropped, some should form pairs)
        val sampleSms = listOf(
            // Should be early-dropped (OTP)
            SmsMessage(
                "Your OTP for Net Banking is 123456. Valid for 10 minutes.",
                System.currentTimeMillis()
            ),
            // Should be early-dropped (promo)
            SmsMessage(
                "Get 50% off on your next purchase! Limited time offer.",
                System.currentTimeMillis()
            ),
            // Should be early-dropped (loan offer)
            SmsMessage(
                "Pre-approved personal loan of ₹5,00,000 available. Apply now!",
                System.currentTimeMillis()
            ),
            // Transactional - debit
            SmsMessage(
                "Rs.5000 debited from A/c XXXX1234 for UPI transaction to Amazon. Ref: ABC123456789",
                System.currentTimeMillis()
            ),
            // Transactional - credit (same amount, should form pair)
            SmsMessage(
                "Rs.5000 credited to A/c XXXX1234 from Amazon. Ref: ABC123456789",
                System.currentTimeMillis() + 1000 // 1 second later
            ),
            // Transactional - different amount
            SmsMessage(
                "Rs.2500 credited to A/c XXXX5678 for Salary",
                System.currentTimeMillis()
            ),
            // Card payment (should be skipped for totals)
            SmsMessage(
                "Rs.3000 debited from A/c XXXX9012 for payment of credit card",
                System.currentTimeMillis()
            ),
            // Refund (should be skipped for totals)
            SmsMessage(
                "Rs.1500 credited to A/c XXXX3456 for refund of cancelled order",
                System.currentTimeMillis()
            )
        )
        
        return try {
            val result = aggregator.processSmsBatch(sampleSms)
            result.getSummary()
        } finally {
            aggregator.close()
        }
    }
}
