package com.dheeraj.smartexpenses.sms

import android.util.Log
import com.dheeraj.smartexpenses.data.Transaction
import java.math.BigDecimal
import java.util.Locale

object SmsParser {
    private lateinit var classifier: SmsMultiTaskClassifier
    
    fun init(context: android.content.Context) { 
        classifier = SmsMultiTaskClassifier(context)
        // Try to load the model with fallback options
        if (!classifier.loadModelWithFallback()) {
            Log.w("SmsParser", "All AI models failed to load, will use regex-based parsing")
        }
    }
    
    // ===================== Short-window internal transfer detector =====================
    private object RecentAmountWindow {
        private val lock = Any()
        // Keep minimal recent events per amount: list of (ts, type)
        private val amountToEvents = mutableMapOf<Long, MutableList<Pair<Long, String>>>()
        private const val WINDOW_MS: Long = 3 * 60 * 1000 // 3 minutes

        fun recordAndDetect(amountMinor: Long, type: String, ts: Long): Boolean {
            synchronized(lock) {
                val list = amountToEvents.getOrPut(amountMinor) { mutableListOf() }
                // Drop stale
                val cutoff = ts - WINDOW_MS
                val fresh = list.filter { it.first >= cutoff }.toMutableList()

                // Detect opposite type within window
                val hasOpposite = fresh.any { (t, ty) -> kotlin.math.abs(ts - t) <= WINDOW_MS && ty != type }

                // Record current
                fresh.add(ts to type)
                // Keep bounded list to avoid growth
                if (fresh.size > 20) fresh.removeFirst()
                amountToEvents[amountMinor] = fresh

                return hasOpposite
            }
        }
    }

    // ===================== Core Regex (compiled once) =====================

    // Amounts like: ₹1,23,456.78 /- , Rs.999/- , INR 2500, 1,234.00
    private val amountRegex = Regex("""(?i)(?:₹|Rs\.?|INR|rupees?)\s*([0-9,]+(?:\.\d{2})?)(?:\s*/-)?""")
    
    // Account numbers like: A/c XXXX1234, Account 5678, A/C 9012
    private val accTailRegex = Regex("""(?i)(?:a/c|account|ac)\s*[xX]{4,}(\d{4,})""")
    private val accTailLoose = Regex("""(?i)(?:a/c|account|ac)\s*(\d{4,})""")
    
    // Transaction types: UPI, IMPS, NEFT, RTGS, POS, ATM, CARD
    private val txnTypeRegex = Regex("""(?i)\b(?:upi|imps|neft|rtgs|pos|atm|card|cheque|transfer|payment)\b""")
    
    // Transaction verbs: credited, debited, withdrawn, deposited, sent, received
    private val txnVerbRegex = Regex("""(?i)\b(?:credited?|debited?|withdrawn?|deposited?|sent|received|paid|charged|deducted?)\b""")
    
    // Bank context: balance, transaction, bank, branch
    private val bankyContextRegex = Regex("""(?i)\b(?:balance|transaction|bank|branch|upi|imps|neft|rtgs)\b""")
    
    // Reference numbers: Ref, UTR, Transaction ID
    private val refRegex = Regex("""(?i)(?:ref|utr|transaction\s+id|txn\s+id)\s*[:\s]*([a-zA-Z0-9]+)""")
    
    // Date patterns: 12/08/2024, 12-08-2024, 12.08.2024
    private val dateLikeRegex = Regex("""\b\d{1,2}[/\-\.]\d{1,2}[/\-\.]\d{2,4}\b""")
    
    // Bank identification from sender
    private val bankFromSender = mapOf(
        "HDFCBK" to "HDFC Bank",
        "ICICIB" to "ICICI Bank",
        "SBICRD" to "SBI",
        "AXISBK" to "Axis Bank",
        "KOTAK" to "Kotak Bank",
        "YESB" to "Yes Bank",
        "IDFCBK" to "IDFC Bank",
        "PNB" to "Punjab National Bank",
        "CANBK" to "Canara Bank",
        "BOB" to "Bank of Baroda"
    )

    fun parse(sender: String, body: String, ts: Long): Transaction? {
        // Use the new multi-task classifier for primary analysis
        val aiAnalysis = classifier.analyzeSms(body)
        
        if (aiAnalysis != null && aiAnalysis.isTransactional) {
            // AI successfully identified this as a transactional SMS
            val amountMinor = aiAnalysis.amount?.let { extractAmountMinor(it) } ?: 0L
            val type = when (aiAnalysis.direction) {
                TransactionDirection.DEBIT -> "DEBIT"
                TransactionDirection.CREDIT -> "CREDIT"
                else -> null
            }
            
            if (amountMinor > 0 && type != null) {
                // Check for internal transfer
                val isInternalTransfer = RecentAmountWindow.recordAndDetect(amountMinor, type, ts)
                
                return Transaction(
                    ts = ts,
                    amountMinor = amountMinor,
                    type = type,
                    channel = aiAnalysis.transactionType ?: extractChannel(body),
                    merchant = aiAnalysis.merchant?.let { cleanMerchant(it) },
                    accountTail = extractAccountTail(body),
                    bank = bankFromSender[sender] ?: "Unknown",
                    source = "SMS_AI",
                    rawSender = sender,
                    rawBody = body
                )
            }
        }
        
        // Fallback to regex-based parsing if AI analysis fails or is inconclusive
        return parseWithRegex(sender, body, ts)
    }
    
    /**
     * Fallback regex-based parsing method
     */
    private fun parseWithRegex(sender: String, body: String, ts: Long): Transaction? {
        // (1) Quick heuristic check
        if (!looksLikeTransaction(sender, body)) return null
        
        // (2) Extract amount
        val amountMatch = amountRegex.find(body)
        if (amountMatch == null) return null
        val amountMinor = extractAmountMinor(amountMatch.groupValues[1])
        if (amountMinor <= 0) return null
        
        // (3) Determine transaction type (CREDIT/DEBIT)
        val type = when {
            body.contains(Regex("(?i)credited|received|deposited")) -> "CREDIT"
            body.contains(Regex("(?i)debited|withdrawn|deducted|sent|paid|charged")) -> "DEBIT"
            else -> null
        } ?: return null
        
        // (4) Check for internal transfer
        val isInternalTransfer = RecentAmountWindow.recordAndDetect(amountMinor, type, ts)
        
        // (5) Extract channel
        val channel = extractChannel(body)
        
        // (6) Extract merchant
        val merchant = extractMerchant(body)?.let { cleanMerchant(it) }
        
        // (7) Use AI classifier confidence for final decision (if available)
        val aiAnalysis = try {
            classifier.analyzeSms(body)
        } catch (e: Exception) {
            Log.w("SmsParser", "AI analysis failed, using regex-only parsing: ${e.message}")
            null
        }
        
        val aiConfidence = aiAnalysis?.confidence ?: 0f
        
        // Only proceed if AI has high confidence or regex extraction is strong
        val finalTransactional = when {
            aiConfidence >= 0.7f -> true
            aiConfidence >= 0.5f && amountMinor > 0 && type != null -> true
            amountMinor > 0 && type != null && (hasStrongTransactionSignals(body) || bankFromSender[sender] != null) -> true
            else -> false
        }
        
        if (!finalTransactional) return null
        
        return Transaction(
            ts = ts,
            amountMinor = amountMinor,
            type = type,
            channel = channel,
            merchant = merchant,
            accountTail = extractAccountTail(body),
            bank = bankFromSender[sender] ?: "Unknown",
            source = "SMS_REGEX",
            rawSender = sender,
            rawBody = body
        )
    }

    // ===================== Heuristics & Helpers =====================

    private fun looksLikeTransaction(sender: String, body: String): Boolean {
        val hasTxnVerb = txnVerbRegex.containsMatchIn(body)
        val hasAccTail = accTailRegex.containsMatchIn(body) || accTailLoose.containsMatchIn(body)
        val hasBankCtx = bankyContextRegex.containsMatchIn(body)
        val hasRef     = refRegex.containsMatchIn(body)
        val hasDate    = dateLikeRegex.containsMatchIn(body)
        val bankKnown  = bankFromSender[sender] != null

        // Need verbs + at least one other strong signal
        val strongCount = listOf(hasTxnVerb, hasAccTail, hasRef, hasDate, bankKnown, hasBankCtx).count { it }
        return hasTxnVerb && strongCount >= 2
    }

    private fun looksLikePromo(body: String): Boolean {
        val promoKeywords = listOf(
            "offer", "discount", "sale", "deal", "cashback", "reward", "bonus",
            "limited time", "special price", "buy now", "shop now", "avail now"
        )
        return promoKeywords.any { body.contains(it, ignoreCase = true) }
    }
    
    private fun hasStrongTransactionSignals(body: String): Boolean {
        val hasRef = refRegex.containsMatchIn(body)
        val hasAccTail = accTailRegex.containsMatchIn(body) || accTailLoose.containsMatchIn(body)
        val hasDate = dateLikeRegex.containsMatchIn(body)
        val hasBankCtx = bankyContextRegex.containsMatchIn(body)
        
        // Count strong signals
        val strongSignals = listOf(hasRef, hasAccTail, hasDate, hasBankCtx).count { it }
        return strongSignals >= 2
    }

    private fun extractAmountMinor(amountStr: String): Long {
        return try {
            val cleaned = amountStr.replace(",", "").replace(" ", "")
            val amount = BigDecimal(cleaned)
            (amount * BigDecimal(100)).toLong()
        } catch (e: Exception) {
            0L
        }
    }

    private fun extractChannel(body: String): String? {
        val channelMatch = txnTypeRegex.find(body)
        return channelMatch?.value?.uppercase()
    }

    private fun extractMerchant(body: String): String? {
        // Look for merchant names after common prepositions
        val merchantPatterns = listOf(
            Regex("""(?i)to\s+([A-Za-z\s]+?)(?:\s+via|\s+using|\s+for|\s+ref|$)"""),
            Regex("""(?i)for\s+([A-Za-z\s]+?)(?:\s+via|\s+using|\s+ref|$)"""),
            Regex("""(?i)at\s+([A-Za-z\s]+?)(?:\s+via|\s+using|\s+ref|$)"""),
            Regex("""(?i)from\s+([A-Za-z\s]+?)(?:\s+via|\s+using|\s+ref|$)""")
        )
        
        for (pattern in merchantPatterns) {
            val match = pattern.find(body)
            if (match != null) {
                val merchant = match.groupValues[1].trim()
                if (merchant.length > 2 && merchant.length < 50) {
                    return merchant
                }
            }
        }
        return null
    }

    private fun extractAccountTail(body: String): String? {
        val match = accTailRegex.find(body) ?: accTailLoose.find(body)
        return match?.groupValues?.get(1)
    }

    private fun cleanMerchant(merchant: String): String {
        return merchant.trim()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("^[^a-zA-Z]+|[^a-zA-Z]+$"), "")
            .take(100)
    }
}
