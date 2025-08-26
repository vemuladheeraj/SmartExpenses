package com.dheeraj.smartexpenses.sms

import android.util.Log
import com.dheeraj.smartexpenses.data.Transaction
import com.dheeraj.smartexpenses.data.bank.BankParserFactory
import com.dheeraj.smartexpenses.data.ParsedTransaction
import java.math.BigDecimal
import java.util.regex.Pattern

/**
 * Parses SMS messages into Transaction objects.
 * Now integrates with bank-specific parsers for improved accuracy.
 */
object SmsParser {
    // Configuration constants
    private const val MAX_MERCHANT_LENGTH = 50
    private const val MIN_MERCHANT_LENGTH = 2

    // --- Regexes (compiled once) ---

    // Amount pattern (INR included) with optional /- - more restrictive
    private val amtRegex = Regex(
        "(?:₹|rs\\.?|rupees|inr)\\s*(?:[:=\\-]?\\s*)?((?:\\d{1,3}(?:,\\d{2,3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?))(?:\\s*/-)?",
        setOf(RegexOption.IGNORE_CASE)
    )

    // Safe account tail regex
    private val accTailRegex = Regex(
        "(?:(?:a/?c|acct|account)\\s*(?:no\\.?)?\\s*)?(?:(?:xx+|x{2,}|XXXX?|####|-{2,})\\s*)?(\\d{2,6})\\b",
        setOf(RegexOption.IGNORE_CASE)
    )

    // Card mentions (incl. masked tail)
    private val cardRegex = Regex("""(?i)\b(?:CARD|VISA|MASTERCARD|RUPAY|AMEX)\b|(?:X{2,}|x{2,})\s*\d{4}\b""")

    // Channels
    private val upiWord = Regex("""(?i)\bUPI\b|\bVPA\b""")
    private val upiVpaRegex = Regex("""(?i)\b([a-z0-9.\-_]{2,}@[a-z0-9.\-]{2,})\b""")
    private val impsRegex = Regex("""(?i)\bIMPS\b""")
    private val neftRegex = Regex("""(?i)\bNEFT\b""")
    private val posRegex  = Regex("""(?i)\bPOS\b""")
    private val atmRegex  = Regex("""(?i)\bATM\b""")
    private val netRegex  = Regex("""(?i)\bNET\s*BANK(?:ING)?\b|\bINTERNET\s*BANKING\b""")
    private val nachRegex = Regex("""(?i)\bNACH\b|\bECS\b""")
    private val autopayRegex = Regex("""(?i)\bAUTO[- ]?PAY\b|\bAUTO[- ]?DEBIT\b""")
    private val fastagRegex = Regex("""(?i)\bFASTAG\b""")

    // Credit/Debit keywords - MUCH more restrictive
    private val creditKeywords = listOf(
        "credited", "received", "deposited", "refunded", "reversal", "reversed", 
        "chargeback", "cashback", "credit", "cr"
    )
    private val debitKeywords = listOf(
        "debited", "spent", "withdrawn", "purchase", "paid", "sent", "transfer to", 
        "upi payment", "pos", "atm withdrawal", "debit", "dr"
    )

    // CR/DR tokens (common in Kotak/ICICI statements etc.)
    private val crToken = Regex("""(?i)\bcr\b|cr[:\-]""")
    private val drToken = Regex("""(?i)\bdr\b|dr[:\-]""")

    // Drop if present - expanded
    private val negativeOutcome = listOf(
        "failed", "declined", "unsuccessful", "rejected", "cancelled", 
        "reversed by bank", "transaction failed", "payment failed", "insufficient funds",
        "limit exceeded", "card blocked", "account frozen"
    )
    private val otpNoise = Regex("""(?i)\bOTP\b|one[-\s]?time|verification\s*code|\bPIN\b|authenticate|verify""")
    
    // Balance and statement indicators - expanded
    private val balanceOnlyHints = listOf(
        "available balance", "avl bal", "ledger balance", "closing balance", 
        "current balance", "account balance", "bal", "balance", "total balance",
        "opening balance", "minimum balance", "min bal", "credit limit", "limit"
    )
    private val loanPromoHints = listOf(
        "pre-approved", "pre approved", "personal loan", "loan up to", "eligible for loan",
        "credit limit", "loan offer", "pre approved loan", "instant loan", "loan approved",
        "loan disbursed", "loan amount", "emi", "interest rate", "processing fee",
        "eligibility", "approval", "disbursement", "application"
    )
    private val statementHints = listOf(
        "statement is sent", "card statement", "minimum of rs", "total of rs", "due by",
        "monthly statement", "credit card statement", "statement generated", "bill generated",
        "payment due", "due date", "last date", "late fee", "penalty"
    )

    // Merchant/name hints
    private val payeeNameRegex = Regex("""(?i)\b(?:to|by|from|payee|beneficiary|merchant|at|in|on)\s*[:\-]?\s*([A-Z][A-Za-z0-9 .&'\-]{2,40})""")
    private val merchantHintRegex = Regex("""(?i)\b(?:at|to|for|by|from)\s+([A-Z0-9&\-\._ ]{3,40})""")

    // Words to avoid as merchant
    private val genericDiscardNames = listOf(
        "transaction", "payment", "transfer", "debit", "credit", "amount", "balance",
        "account", "card", "bank", "upi", "imps", "neft", "pos", "atm", "cash",
        "withdrawal", "deposit", "refund", "reversal", "chargeback", "cashback"
    )

    // Ads/promo filter - EXTREMELY comprehensive
    private val adSpamKeywords = listOf(
        "win","winner","prize","jackpot","promo","coupon","discount","sale","limited time","hurry",
        "cashback up to","offer valid","special offer","exclusive offer","limited offer","flash sale",
        "festival offer","diwali offer","new year offer","christmas offer","holiday offer",
        "bonus","reward","lucky","chance","opportunity","gift","free","complimentary",
        "discount code","promo code","voucher","deal","bargain","clearance","end of season",
        "anniversary","celebration","festival","occasion","special","exclusive","premium",
        "elite","vip","privilege","membership","subscription","renewal","upgrade",
        "valid","till","until","apply","click","visit","website",
        "app","download","install","register","sign up","join","become","member",
        "loyalty","points","rewards","cashback","discount","save","savings","reduced",
        "price","cost","fee","charge","rate","percentage","percent","%"
    )

    // Context-aware amount extraction to avoid picking balances
    private val txnWords = listOf(
        "debited","credited","spent","purchase","paid","sent","withdrawn",
        "imps","neft","upi","pos","atm","autopay","nach","fastag",
        "refund","cashback","reversal","chargeback","transfer","payment","transaction"
    )
    private val balWords = listOf(
        "available balance", "avl bal", "balance", "bal", "closing balance",
        "ledger balance", "minimum balance", "min bal", "current balance", "account balance",
        "total balance", "opening balance", "credit limit", "limit"
    )

    // STRONG transaction signal indicators - much more restrictive
    private val txnSignal = Regex(
        "(?i)\\b(txn|transaction|utr|ref\\.?\\s*no|auth\\s*code|a/?c|acct|account|ending\\s*\\d{2,}|xx\\d{2,}|card|upi|imps|neft|pos|atm)\\b"
    )

    /**
     * Determines the bank name based on the sender.
     */

    

    

    

    






    /**
     * Parses an SMS message into a Transaction object.
     * First tries bank-specific parsers, then falls back to generic parsing.
     */
    fun parse(sender: String, body: String, timestamp: Long): Transaction? {
        Log.d("SmsParser", "=== STARTING SMS PARSING ===")
        Log.d("SmsParser", "Sender: $sender")
        Log.d("SmsParser", "Body: $body")
        Log.d("SmsParser", "Timestamp: $timestamp")
        
        try {
            // Try to get bank-specific parser
            val parser = BankParserFactory.getParser(sender)
            Log.d("SmsParser", "Bank parser found: ${parser?.javaClass?.simpleName ?: "NONE"}")
            
            if (parser != null) {
                try {
                    val parsedTransaction = parser.parse(body, sender, timestamp)
                    Log.d("SmsParser", "Parser result: $parsedTransaction")
                    
                    if (parsedTransaction != null) {
                        val transaction = parsedTransaction.toTransaction()
                        Log.d("SmsParser", "Converted to Transaction: $transaction")
                        Log.d("SmsParser", "=== PARSING SUCCESSFUL ===")
                        return transaction
                    } else {
                        Log.d("SmsParser", "Parser returned null - SMS not recognized as transaction")
                    }
                } catch (e: Exception) {
                    Log.e("SmsParser", "Error during bank-specific parsing", e)
                }
            } else {
                Log.d("SmsParser", "No bank-specific parser found for sender: $sender")
            }
            
            Log.d("SmsParser", "=== PARSING FAILED - NO TRANSACTION EXTRACTED ===")
            return null
            
        } catch (e: Exception) {
            Log.e("SmsParser", "Critical error in SmsParser.parse", e)
            return null
        }
    }
    

    
    // Debug function to analyze SMS content
    fun debugSmsContent(body: String): String {
        val lower = body.lowercase()
        val analysis = StringBuilder()
        
        analysis.appendLine("=== SMS Content Analysis ===")
        analysis.appendLine("Original: $body")
        analysis.appendLine("Lowercase: $lower")
        
        // Check for transaction signals
        val hasTxnSignal = txnSignal.containsMatchIn(body)
        analysis.appendLine("Has transaction signal: $hasTxnSignal")
        
        // Check for credit/debit keywords
        val hasCredWord = creditKeywords.any { lower.contains(it) } || crToken.containsMatchIn(body)
        val hasDebWord = debitKeywords.any { lower.contains(it) } || drToken.containsMatchIn(body)
        analysis.appendLine("Has credit keywords: $hasCredWord")
        analysis.appendLine("Has debit keywords: $hasDebWord")
        
        // Check for promotional content
        val hasPromoKeywords = adSpamKeywords.any { lower.contains(it) }
        val hasLoanPromo = loanPromoHints.any { lower.contains(it) }
        analysis.appendLine("Has promotional keywords: $hasPromoKeywords")
        analysis.appendLine("Has loan promotional: $hasLoanPromo")
        
        // Check for balance/statement content
        val hasBalanceHints = balanceOnlyHints.any { lower.contains(it) }
        val hasStatementHints = statementHints.any { lower.contains(it) }
        analysis.appendLine("Has balance hints: $hasBalanceHints")
        analysis.appendLine("Has statement hints: $hasStatementHints")
        
        // Check for OTP/noise
        val hasOtpNoise = otpNoise.containsMatchIn(body)
        val hasNegativeOutcome = negativeOutcome.any { lower.contains(it) }
        analysis.appendLine("Has OTP/noise: $hasOtpNoise")
        analysis.appendLine("Has negative outcome: $hasNegativeOutcome")
        
        // Check for suspicious patterns
        val hasUpTo = lower.contains("up to") || lower.contains("upto")
        val hasCashback = lower.contains("cashback")
        val hasOffer = lower.contains("offer")
        val hasDiscount = lower.contains("discount")
        analysis.appendLine("Has 'up to': $hasUpTo")
        analysis.appendLine("Has cashback: $hasCashback")
        analysis.appendLine("Has offer: $hasOffer")
        analysis.appendLine("Has discount: $hasDiscount")
        
        // Final decision - no longer using generic parser
        analysis.appendLine("Final decision: Will be handled by bank-specific parser")
        
        return analysis.toString()
    }

    // Test function to validate SMS examples
    fun testSmsExamples(): String {
        val examples = listOf(
            "Credit Alert! Rs.5000 credited to your A/c XX1234 on 15-12-2024. Avl Bal: Rs.25000. Thank you for using our services.",
            "Your A/c XX5678 has been debited Rs.1500 on 15-12-2024 for UPI payment to MERCHANT123. Avl Bal: Rs.8500.",
            "Congratulations! You've won Rs.10000! Click here to claim your prize now! Limited time offer!",
            "Your credit card statement is ready. Total amount due: Rs.5000. Due date: 25-12-2024.",
            "Available balance in your A/c XX9012 is Rs.50000 as on 15-12-2024.",
            "Pre-approved personal loan up to Rs.500000 available for you! Interest rate starting from 10.99% p.a.",
            "Cashback up to Rs.2000 on UPI transactions! Use code CASHBACK20. Valid till 31-12-2024.",
            "NEFT credit of Rs.25000 received in your A/c XX3456 from SENDER NAME. Ref No: 123456789.",
            "Your card XX7890 has been charged Rs.2500 for purchase at MERCHANT456 on 15-12-2024."
        )
        
        val results = StringBuilder()
        results.appendLine("=== SMS Example Testing ===")
        
        examples.forEachIndexed { index, sms ->
            results.appendLine("\n--- Example ${index + 1} ---")
            results.appendLine(debugSmsContent(sms))
            val parsed = parse("TESTBANK", sms, System.currentTimeMillis())
            results.appendLine("Parse result: ${if (parsed != null) "SUCCESS - ${parsed.type} ${parsed.amount}" else "REJECTED"}")
        }
        
        return results.toString()
    }

    // Function to manually import and log all recent SMS from device
    fun importAndLogAllRecentSms(context: android.content.Context, limit: Int = 100): String {
        val results = StringBuilder()
        results.appendLine("=== MANUAL SMS IMPORT AND LOGGING ===")
        results.appendLine("This will show ALL recent SMS and their parsing results")
        results.appendLine("Limit: $limit messages")
        results.appendLine()
        
        try {
            // Get all SMS from device
            val cursor = context.contentResolver.query(
                android.provider.Telephony.Sms.CONTENT_URI,
                arrayOf(
                    android.provider.Telephony.Sms.ADDRESS,
                    android.provider.Telephony.Sms.BODY,
                    android.provider.Telephony.Sms.DATE
                ),
                null,
                null,
                "${android.provider.Telephony.Sms.DATE} DESC LIMIT $limit"
            )
            
            if (cursor == null) {
                results.appendLine("❌ Failed to query SMS content provider")
                return results.toString()
            }
            
            var totalSms = 0
            var parsedCount = 0
            var rejectedCount = 0
            
            results.appendLine("📱 Processing recent SMS messages...")
            results.appendLine()
            
            while (cursor.moveToNext() && totalSms < limit) {
                totalSms++
                val sender = cursor.getString(0) ?: "UNKNOWN"
                val body = cursor.getString(1) ?: ""
                val timestamp = cursor.getLong(2)
                
                results.appendLine("--- SMS #$totalSms ---")
                results.appendLine("Sender: $sender")
                results.appendLine("Body: $body")
                results.appendLine("Timestamp: $timestamp")
                
                // Try to parse the SMS
                val parsed = parse(sender, body, timestamp)
                
                if (parsed != null) {
                    parsedCount++
                    results.appendLine("✅ PARSED SUCCESSFULLY")
                    results.appendLine("  - Type: ${parsed.type}")
                    results.appendLine("  - Amount: ${parsed.amount}")
                    results.appendLine("  - Channel: ${parsed.channel}")
                    results.appendLine("  - Merchant: ${parsed.merchant}")
                    results.appendLine("  - Bank: ${parsed.bank}")
                } else {
                    rejectedCount++
                    results.appendLine("❌ REJECTED")
                    results.appendLine("  - Reason: Failed parsing checks")
                }
                
                results.appendLine()
            }
            
            cursor.close()
            
            results.appendLine("=== SUMMARY ===")
            results.appendLine("Total SMS processed: $totalSms")
            results.appendLine("Successfully parsed: $parsedCount")
            results.appendLine("Rejected: $rejectedCount")
            results.appendLine("Success rate: ${if (totalSms > 0) (parsedCount * 100.0 / totalSms) else 0.0}%")
            
        } catch (e: Exception) {
            results.appendLine("❌ Error during SMS import: ${e.message}")
            e.printStackTrace()
        }
        
        return results.toString()
    }

    /** Simple function to just log SMS content without parsing - for debugging */
    fun logSmsContentOnly(sender: String, body: String, timestamp: Long) {
        Log.d("SmsParser", "📱 === RAW SMS CONTENT (NO PARSING) ===")
        Log.d("SmsParser", "📨 Sender: $sender")
        Log.d("SmsParser", "📝 Full Body: $body")
        Log.d("SmsParser", "⏰ Timestamp: $timestamp")
        Log.d("SmsParser", "📱 === END RAW SMS CONTENT ===")
    }

    // --- Helpers ---

    private fun extractMerchant(body: String, sender: String): String? {
        val payeeName = payeeNameRegex.find(body)?.groupValues?.getOrNull(1)?.trim()
        val upiVpa = upiVpaRegex.find(body)?.groupValues?.getOrNull(1)?.trim()
        val infoNameMatch = Regex("""(?i)\b(?:info|narration)\s*[:\-]?\s*([^\n]+)""").find(body)?.groupValues?.getOrNull(1)
        val infoDerivedName = infoNameMatch?.split('-','/','|')?.lastOrNull()?.trim()?.takeIf { it.length in 3..40 }

        return when {
            !payeeName.isNullOrBlank() -> sanitizeName(payeeName)
            !infoDerivedName.isNullOrBlank() -> sanitizeName(infoDerivedName)
            !upiVpa.isNullOrBlank() -> upiVpa.lowercase()
            else -> sanitizeName(merchantHintRegex.find(body)?.groupValues?.getOrNull(1))
        }?.takeIf { it.any { ch -> ch.isLetterOrDigit() } }
    }

    private fun sanitizeName(name: String?): String? {
        return name?.trim()?.replace(Regex("""[^\w\s\-\.]"""), "")?.takeIf { it.length in 2..50 }
    }

    private fun isInterAccountTransfer(body: String): Boolean {
        val lower = body.lowercase()
        return lower.contains("transfer") || lower.contains("imps") || lower.contains("neft") || 
               lower.contains("rtgs") || lower.contains("internal transfer")
    }

    private fun bankFromSender(sender: String): String? {
        return when {
            sender.contains("HDFC", ignoreCase = true) -> "HDFC"
            sender.contains("ICICI", ignoreCase = true) -> "ICICI"
            sender.contains("SBI", ignoreCase = true) -> "SBI"
            sender.contains("AXIS", ignoreCase = true) -> "AXIS"
            sender.contains("KOTAK", ignoreCase = true) -> "KOTAK"
            sender.contains("YES", ignoreCase = true) -> "YES"
            sender.contains("IDFC", ignoreCase = true) -> "IDFC"
            sender.contains("INDUSIND", ignoreCase = true) -> "INDUSIND"
            else -> null
        }
    }
}