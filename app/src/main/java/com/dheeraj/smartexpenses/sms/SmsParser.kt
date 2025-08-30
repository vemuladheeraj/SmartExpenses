package com.dheeraj.smartexpenses.sms

import android.util.Log
import com.dheeraj.smartexpenses.data.Transaction
import java.math.BigDecimal
import java.util.Locale

object SmsParser {
    
    fun init(context: android.content.Context) { 
        Log.d("SmsParser", "Initializing enhanced regex-only SMS parser with spam protection for Indian banks")
        // Context parameter kept for API compatibility
    }
    
    // ===================== Enhanced Transfer Detection =====================
    private object RecentAmountWindow {
        private val lock = Any()
        // Keep minimal recent events per amount: list of (ts, type, sender, accountTail)
        private val amountToEvents = mutableMapOf<Long, MutableList<TransferEvent>>()
        private const val WINDOW_MS: Long = 5 * 60 * 1000 // 5 minutes (increased from 3)
        private const val MAX_EVENTS_PER_AMOUNT = 10 // Reduced from 20 to prevent memory issues

        data class TransferEvent(
            val timestamp: Long,
            val type: String,
            val sender: String,
            val accountTail: String?,
            val body: String
        )

        fun recordAndDetect(amountMinor: Long, type: String, sender: String, accountTail: String?, body: String, ts: Long): TransferDetectionResult {
            synchronized(lock) {
                val list = amountToEvents.getOrPut(amountMinor) { mutableListOf() }
                // Drop stale events
                val cutoff = ts - WINDOW_MS
                val fresh = list.filter { it.timestamp >= cutoff }.toMutableList()

                // Look for transfer indicators
                val transferIndicators = detectTransferIndicators(fresh, type, sender, accountTail, body, ts)
                
                // Record current event
                fresh.add(TransferEvent(ts, type, sender, accountTail, body))
                
                // Keep bounded list to avoid memory growth
                if (fresh.size > MAX_EVENTS_PER_AMOUNT) {
                    fresh.removeFirst()
                }
                amountToEvents[amountMinor] = fresh

                return transferIndicators
            }
        }

        private fun detectTransferIndicators(
            events: List<TransferEvent>, 
            currentType: String, 
            currentSender: String, 
            currentAccountTail: String?, 
            currentBody: String, 
            currentTs: Long
        ): TransferDetectionResult {
            
            // Check for same-amount opposite transactions within window
            val oppositeEvents = events.filter { 
                it.type != currentType && 
                kotlin.math.abs(currentTs - it.timestamp) <= WINDOW_MS 
            }
            
            if (oppositeEvents.isNotEmpty()) {
                // Check for strong transfer indicators
                val hasStrongTransferIndicators = hasStrongTransferIndicators(currentBody, oppositeEvents, currentSender, currentAccountTail)
                if (hasStrongTransferIndicators) {
                    return TransferDetectionResult.INTERNAL_TRANSFER
                }
            }
            
            // Check for self-transfer keywords in current SMS
            if (hasSelfTransferKeywords(currentBody)) {
                return TransferDetectionResult.SELF_TRANSFER
            }
            
            // Check for account-to-account transfer patterns
            if (hasAccountTransferPattern(currentBody)) {
                return TransferDetectionResult.ACCOUNT_TRANSFER
            }
            
            return TransferDetectionResult.REGULAR_TRANSACTION
        }

        private fun hasStrongTransferIndicators(
            currentBody: String, 
            oppositeEvents: List<TransferEvent>, 
            currentSender: String, 
            currentAccountTail: String?
        ): Boolean {
            // Check if opposite events are from same bank/sender
            val sameBankOpposites = oppositeEvents.any { 
                it.sender == currentSender || 
                (it.accountTail != null && currentAccountTail != null && it.accountTail == currentAccountTail)
            }
            
            if (sameBankOpposites) return true
            
            // Check for transfer-specific keywords in current body
            val transferKeywords = listOf(
                "self transfer", "own account", "same account", "account transfer",
                "internal transfer", "own transfer", "self account"
            )
            
            return transferKeywords.any { currentBody.contains(it, ignoreCase = true) }
        }

        private fun hasSelfTransferKeywords(body: String): Boolean {
            val selfTransferPatterns = listOf(
                Regex("""(?i)self\s+transfer"""),
                Regex("""(?i)own\s+account"""),
                Regex("""(?i)same\s+account"""),
                Regex("""(?i)account\s+transfer"""),
                Regex("""(?i)internal\s+transfer"""),
                Regex("""(?i)own\s+transfer""")
            )
            
            return selfTransferPatterns.any { it.containsMatchIn(body) }
        }

        private fun hasAccountTransferPattern(body: String): Boolean {
            // Check for patterns like "from A/c XXXX to A/c YYYY"
            val accountTransferPattern = Regex("""(?i)(?:from|to)\s+(?:a/c|account|ac|acc)\s+[xX]{4,}\d{4,}""")
            val accountCount = accountTransferPattern.findAll(body).count()
            
            return accountCount >= 2 // At least two account references
        }
    }

    sealed class TransferDetectionResult {
        object REGULAR_TRANSACTION : TransferDetectionResult()
        object INTERNAL_TRANSFER : TransferDetectionResult()
        object SELF_TRANSFER : TransferDetectionResult()
        object ACCOUNT_TRANSFER : TransferDetectionResult()
    }

    // ===================== Enhanced Spam Detection =====================
    private object SpamDetector {
        private val spamKeywords = listOf(
            "cashback", "rewards", "offer", "discount", "promotion", "limited time", 
            "special price", "bonus", "free", "gift", "win", "lucky", "chance",
            "exclusive", "premium", "vip", "elite", "gold", "platinum", "diamond",
            "congratulations", "you've won", "claim your", "activate now",
            "click here", "call now", "sms to", "reply with", "urgent"
        )
        
        private val promotionalPatterns = listOf(
            Regex("""(?i)(?:get|earn|receive)\s+₹?\d+"""),
            Regex("""(?i)(?:spend|purchase)\s+₹?\d+\s+(?:and|to)\s+(?:get|earn|receive)"""),
            Regex("""(?i)(?:minimum|min)\s+(?:spend|purchase|transaction)\s+₹?\d+"""),
            Regex("""(?i)(?:valid|offer)\s+(?:till|until|upto|till|by)\s+\d{1,2}[/\-\.]\d{1,2}[/\-\.]\d{2,4}"""),
            Regex("""(?i)(?:terms|conditions|t&c|tc)\s+(?:apply|applicable)"""),
            Regex("""(?i)(?:limited|limited\s+time|hurry|rush|quick)""")
        )
        
        fun isSpamMessage(body: String): Boolean {
            // Check for spam keywords (2 or more = spam)
            val spamKeywordCount = spamKeywords.count { body.contains(it, ignoreCase = true) }
            if (spamKeywordCount >= 2) return true
            
            // Check for promotional patterns
            val promotionalPatternCount = promotionalPatterns.count { it.containsMatchIn(body) }
            if (promotionalPatternCount >= 2) return true
            
            // Check for excessive promotional language
            val promotionalWords = body.lowercase().split(" ").count { word ->
                spamKeywords.any { it.lowercase() in word }
            }
            if (promotionalWords >= 3) return true
            
            return false
        }
        
        fun isPromotionalMessage(body: String): Boolean {
            // Less strict than spam - just promotional content
            val promotionalIndicators = listOf(
                "cashback", "rewards", "offer", "discount", "promotion", "bonus"
            )
            
            return promotionalIndicators.any { body.contains(it, ignoreCase = true) }
        }
    }

    // ===================== Enhanced Amount Validation =====================
    private object AmountValidator {
        private const val MIN_AMOUNT_PAISE = 1000L // ₹10 minimum
        private const val MAX_AMOUNT_PAISE = 1000000000L // ₹10 crore maximum
        
        fun isValidTransactionAmount(amountMinor: Long, body: String): Boolean {
            // Basic range validation
            if (amountMinor < MIN_AMOUNT_PAISE || amountMinor > MAX_AMOUNT_PAISE) {
                return false
            }
            
            // Check if amount appears in actual transaction context
            val hasTransactionContext = body.contains(Regex("""(?i)(?:credited|debited|withdrawn|deposited|sent|received|paid|charged|deducted|transferred|processed)"""))
            
            if (!hasTransactionContext) return false
            
            // Check for balance/limit indicators that shouldn't be transactions
            val balanceLimitPatterns = listOf(
                Regex("""(?i)(?:balance|bal)\s+(?:is|:)\s*₹?\d+"""),
                Regex("""(?i)(?:credit\s+)?limit\s+(?:is|:)\s*₹?\d+"""),
                Regex("""(?i)(?:available|avail)\s+(?:balance|credit)\s*₹?\d+"""),
                Regex("""(?i)(?:total|outstanding)\s+(?:amount|balance)\s*₹?\d+""")
            )
            
            // If it looks like a balance/limit message, it's not a transaction
            if (balanceLimitPatterns.any { it.containsMatchIn(body) }) {
                return false
            }
            
            return true
        }
        
        fun isReasonableAmount(amountMinor: Long): Boolean {
            // Check for common transaction amounts (not promotional amounts like ₹1, ₹5, ₹10)
            val amount = amountMinor / 100.0
            
            // Common promotional amounts to avoid
            val promotionalAmounts = listOf(1.0, 5.0, 10.0, 50.0, 100.0, 500.0)
            if (promotionalAmounts.contains(amount)) {
                // Only allow if it's a legitimate small transaction
                return false
            }
            
            return true
        }
    }

    // ===================== Core Regex (compiled once) =====================

    // Enhanced amount patterns for Indian bank SMS
    // Handles: ₹1,23,456.78, Rs.999/-, INR 2500, 1,234.00, 5000.00, 1,00,000
    private val amountRegex = Regex("""(?i)(?:₹|Rs\.?|INR|rupees?|rs\.?|inr)\s*([0-9,]+(?:\.\d{2})?)(?:\s*/-|\s*|$)""")
    
    // Alternative amount patterns for different formats
    private val amountAltRegex = Regex("""(?i)(?:amount|amt|value|sum)\s*[:\s]*([0-9,]+(?:\.\d{2})?)""")
    
    // Account numbers like: A/c XXXX1234, Account 5678, A/C 9012, Acc: XXXX1234
    private val accTailRegex = Regex("""(?i)(?:a/c|account|ac|acc)\s*[:\s]*[xX]{4,}(\d{4,})""")
    private val accTailLoose = Regex("""(?i)(?:a/c|account|ac|acc)\s*[:\s]*(\d{4,})""")
    
    // Enhanced transaction types: UPI, IMPS, NEFT, RTGS, POS, ATM, CARD
    private val txnTypeRegex = Regex("""(?i)\b(?:upi|imps|neft|rtgs|pos|atm|card|cheque|transfer|payment|online|mobile|netbanking|standing\s+instruction|si|recurring|emi|loan|credit|debit)\b""")
    
    // Enhanced transaction verbs for Indian banking context
    private val txnVerbRegex = Regex("""(?i)\b(?:credited?|debited?|withdrawn?|deposited?|sent|received|paid|charged|deducted?|transferred?|processed|completed|successful|failed|rejected|cancelled|reversed|refunded?|bounced|returned)\b""")
    
    // Enhanced bank context keywords
    private val bankyContextRegex = Regex("""(?i)\b(?:balance|transaction|bank|branch|upi|imps|neft|rtgs|pos|atm|card|cheque|transfer|payment|online|mobile|netbanking|standing\s+instruction|si|recurring|emi|loan|credit|debit|account|a/c|acc|ref|utr|transaction\s+id|txn\s+id)\b""")
    
    // Enhanced reference numbers: Ref, UTR, Transaction ID, etc. - Now includes NEFT, IMPS, RTGS directly
    private val refRegex = Regex("""(?i)(?:ref|utr|transaction\s+id|txn\s+id|order\s+id|payment\s+id|upi\s+ref|imps\s+ref|neft\s+ref|rtgs\s+ref|neft|imps|rtgs|upi)\s*[:\s]*([a-zA-Z0-9\-]+)""")
    
    // Enhanced date patterns for Indian format
    private val dateLikeRegex = Regex("""\b\d{1,2}[/\-\.]\d{1,2}[/\-\.]\d{2,4}\b""")
    
    // Credit card bill payment indicators - these should be treated as DEBIT (expenses), not TRANSFER
    private val creditCardBillKeywords = Regex("""(?i)\b(?:credit\s+card\s+bill|cc\s+bill|card\s+bill|credit\s+bill|bill\s+payment|statement\s+payment|outstanding\s+amount|due\s+amount|minimum\s+amount|full\s+payment|partial\s+payment)\b""")
    
    // Credit card usage indicators - these are actual expenses (DEBIT)
    private val creditCardUsageKeywords = Regex("""(?i)\b(?:credit\s+card\s+transaction|cc\s+transaction|card\s+transaction|credit\s+card\s+payment|cc\s+payment|card\s+payment|credit\s+card\s+charge|cc\s+charge|card\s+charge)\b""")

    // ===================== Enhanced Bank Identification =====================
    private val bankFromSender = mapOf(
        // Major Private Banks
        "HDFCBK" to "HDFC Bank",
        "ICICIB" to "ICICI Bank",
        "AXISBK" to "Axis Bank",
        "KOTAK" to "Kotak Bank",
        "YESB" to "Yes Bank",
        "IDFCBK" to "IDFC Bank",
        "INDUSB" to "IndusInd Bank",
        "FINOBN" to "Fino Bank",
        "RBLBNK" to "RBL Bank",
        "FEDERAL" to "Federal Bank",
        "KARNBNK" to "Karnataka Bank",
        "SOUTINB" to "South Indian Bank",
        "TMBL" to "Tamilnad Mercantile Bank",
        "CSBL" to "CSB Bank",
        "DCBB" to "DCB Bank",
        
        // Public Sector Banks
        "SBICRD" to "State Bank of India",
        "PNB" to "Punjab National Bank",
        "CANBK" to "Canara Bank",
        "BOB" to "Bank of Baroda",
        "UNION" to "Union Bank of India",
        "BANKIND" to "Bank of India",
        "CENTRAL" to "Central Bank of India",
        "UCO" to "UCO Bank",
        "INDIAN" to "Indian Bank",
        "PUNJAB" to "Punjab & Sind Bank",
        "BANKMAH" to "Bank of Maharashtra",
        
        // Regional Banks
        "VIJAYA" to "Vijaya Bank",
        "DENA" to "Dena Bank",
        "ANDHRA" to "Andhra Bank",
        "CORPORATION" to "Corporation Bank",
        "SYNDICATE" to "Syndicate Bank",
        "ORIENTAL" to "Oriental Bank of Commerce",
        "ALLAHABAD" to "Allahabad Bank",
        
        // Cooperative Banks
        "SVCB" to "SVC Co-operative Bank",
        "SARASWAT" to "Saraswat Co-operative Bank",
        "TJSB" to "TJSB Sahakari Bank",
        "APCOB" to "AP Co-operative Bank",
        
        // Payment Banks & Small Finance Banks
        "AIRTPB" to "Airtel Payments Bank",
        "PAYTM" to "Paytm Payments Bank",
        "IPOSB" to "India Post Payments Bank",
        "AU" to "AU Small Finance Bank",
        "UJJIVAN" to "Ujjivan Small Finance Bank",
        "ESAF" to "ESAF Small Finance Bank",
        "FINO" to "Fino Payments Bank",
        
        // UPI Apps & Payment Systems
        "UPI" to "UPI Payment",
        "GPAY" to "Google Pay",
        "PHONEPE" to "PhonePe",
        "PAYTM" to "Paytm",
        "BHIM" to "BHIM UPI"
    )

    fun parse(sender: String, body: String, ts: Long): Transaction? {
        // Use enhanced regex-only parsing for all SMS
        return parseWithRegex(sender, body, ts)
    }
    
    /**
     * Enhanced transaction type determination with correct credit card logic and transfer detection
     */
    private fun determineTransactionType(body: String): String? {
        // First, check if this is a credit card bill payment - these are DEBIT (expenses), not TRANSFER
        if (creditCardBillKeywords.containsMatchIn(body)) {
            return "DEBIT" // Credit card bill payments are expenses, not transfers
        }
        
        // Check for CREDIT indicators
        if (body.contains(Regex("""(?i)credited|received|deposited|successful|completed"""))) {
            return "CREDIT"
        }
        
        // Check for DEBIT indicators
        if (body.contains(Regex("""(?i)debited|withdrawn|deducted|sent|charged|processed"""))) {
            return "DEBIT"
        }
        
        // Handle "paid" keyword carefully - it can mean different things
        if (body.contains(Regex("""(?i)paid"""))) {
            // If it's a credit card bill payment, it's DEBIT
            if (creditCardBillKeywords.containsMatchIn(body)) {
                return "DEBIT"
            }
            // If it's payment received, it's CREDIT
            if (body.contains(Regex("""(?i)received|credited|deposited"""))) {
                return "CREDIT"
            }
            // If it's payment sent/made, it's DEBIT
            if (body.contains(Regex("""(?i)sent|made|to|for"""))) {
                return "DEBIT"
            }
            // Default for "paid" is DEBIT (expense)
            return "DEBIT"
        }
        
        return null
    }
    
    /**
     * Enhanced regex-based parsing method with comprehensive validation
     */
    private fun parseWithRegex(sender: String, body: String, ts: Long): Transaction? {
        // (1) Spam detection - reject spam messages immediately
        if (SpamDetector.isSpamMessage(body)) {
            Log.d("SmsParser", "Rejected spam message: ${body.take(50)}...")
            return null
        }
        
        // (2) Quick heuristic check
        if (!looksLikeTransaction(sender, body)) return null
        
        // (3) Extract amount - try primary pattern first, then alternative
        var amountMatch = amountRegex.find(body)
        if (amountMatch == null) {
            amountMatch = amountAltRegex.find(body)
        }
        if (amountMatch == null) return null
        
        val amountMinor = extractAmountMinor(amountMatch.groupValues[1])
        if (amountMinor <= 0) return null
        
        // (4) Enhanced amount validation
        if (!AmountValidator.isValidTransactionAmount(amountMinor, body)) {
            Log.d("SmsParser", "Rejected invalid amount: $amountMinor for body: ${body.take(50)}...")
            return null
        }
        
        if (!AmountValidator.isReasonableAmount(amountMinor)) {
            Log.d("SmsParser", "Rejected unreasonable amount: $amountMinor for body: ${body.take(50)}...")
            return null
        }
        
        // (5) Determine transaction type (CREDIT/DEBIT) with enhanced logic
        val type = determineTransactionType(body)
        if (type == null) return null
        
        // (6) Enhanced transfer detection
        val accountTail = extractAccountTail(body)
        val transferResult = RecentAmountWindow.recordAndDetect(amountMinor, type, sender, accountTail, body, ts)
        
        // (7) Extract channel
        val channel = extractChannel(body)
        
        // (8) Extract merchant
        val merchant = extractMerchant(body)?.let { cleanMerchant(it) }
        
        // (9) Final validation - ensure we have strong transaction signals
        if (!hasStrongTransactionSignals(body) && bankFromSender[sender] == null) {
            return null
        }
        
        // (10) Determine final transaction type based on transfer detection
        val finalType = when (transferResult) {
            is TransferDetectionResult.INTERNAL_TRANSFER -> "TRANSFER"
            is TransferDetectionResult.SELF_TRANSFER -> "TRANSFER"
            is TransferDetectionResult.ACCOUNT_TRANSFER -> "TRANSFER"
            is TransferDetectionResult.REGULAR_TRANSACTION -> type
        }
        
        return Transaction(
            ts = ts,
            amountMinor = amountMinor,
            type = finalType,
            channel = channel,
            merchant = merchant,
            category = null, // SMS transactions don't have categories initially
            accountTail = accountTail,
            bank = bankFromSender[sender] ?: "Unknown",
            source = "SMS_REGEX",
            rawSender = sender,
            rawBody = body
        )
    }

    // ===================== Enhanced Heuristics & Helpers =====================

    private fun looksLikeTransaction(sender: String, body: String): Boolean {
        // Reject promotional messages early
        if (SpamDetector.isPromotionalMessage(body)) {
            return false
        }
        
        val hasTxnVerb = txnVerbRegex.containsMatchIn(body)
        val hasAccTail = accTailRegex.containsMatchIn(body) || accTailLoose.containsMatchIn(body)
        val hasBankCtx = bankyContextRegex.containsMatchIn(body)
        val hasRef = refRegex.containsMatchIn(body)
        val hasDate = dateLikeRegex.containsMatchIn(body)
        val bankKnown = bankFromSender[sender] != null

        // Need verbs + at least one other strong signal
        val strongCount = listOf(hasTxnVerb, hasAccTail, hasRef, hasDate, bankKnown, hasBankCtx).count { it }
        return hasTxnVerb && strongCount >= 2
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
            Regex("""(?i)from\s+([A-Za-z\s]+?)(?:\s+via|\s+using|\s+ref|$)"""),
            Regex("""(?i)via\s+([A-Za-z\s]+?)(?:\s+ref|$)"""),
            Regex("""(?i)using\s+([A-Za-z\s]+?)(?:\s+ref|$)""")
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
