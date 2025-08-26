package com.dheeraj.smartexpenses.data.bank

import java.math.BigDecimal
import android.util.Log
import com.dheeraj.smartexpenses.data.ParsedTransaction

/**
 * Parser for HDFC Bank SMS messages
 * 
 * Supported formats:
 * - Standard debit/credit messages
 * - UPI transactions with VPA details
 * - Salary credits with company names
 * - E-Mandate notifications
 * - Card transactions
 * 
 * Common senders: HDFCBK, HDFCBANK, HDFC, variations with DLT patterns
 */
class HDFCBankParser : BankParser() {
    
    override fun getBankName() = "HDFC Bank"
    
    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()
        
        // Common HDFC sender IDs
        val hdfcSenders = setOf(
            "HDFCBK",
            "HDFCBANK", 
            "HDFC",
            "HDFCB"
        )
        
        // Direct match
        if (upperSender in hdfcSenders) return true
        
        // DLT patterns for transactions (-S suffix)
        return CompiledPatterns.HDFC.DLT_PATTERNS.any { it.matches(upperSender) }
    }
    
    override fun extractMerchant(message: String, sender: String): String? {
        // Check for ATM withdrawals first
        if (message.contains("withdrawn", ignoreCase = true) || 
            message.contains("ATM", ignoreCase = true)) {
            return "ATM"
        }
        
        // For credit card transactions (with BLOCK CC/PCC instruction), extract merchant after "At"
        if (message.contains("card", ignoreCase = true) && 
            message.contains(" at ", ignoreCase = true) &&
            (message.contains("block cc", ignoreCase = true) || message.contains("block pcc", ignoreCase = true))) {
            // Pattern for "at [merchant] by UPI" or just "at [merchant]"
            val atPattern = Regex("""at\s+([^@\s]+(?:@[^\s]+)?(?:\s+[^\s]+)?)(?:\s+by\s+|\s+on\s+|$)""", RegexOption.IGNORE_CASE)
            atPattern.find(message)?.let { match ->
                val merchant = match.groupValues[1].trim()
                // For UPI VPA, extract the part before @ (e.g., "paytmqr" from "paytmqr@paytm")
                val cleanedMerchant = if (merchant.contains("@")) {
                    val vpaName = merchant.substringBefore("@").trim()
                    // Clean up common UPI prefixes/suffixes
                    when {
                        vpaName.endsWith("qr", ignoreCase = true) -> vpaName.dropLast(2)
                        else -> vpaName
                    }
                } else {
                    merchant
                }
                if (cleanedMerchant.isNotEmpty()) {
                    return cleanMerchantName(cleanedMerchant)
                }
            }
        }
        
        // Try HDFC specific patterns
        
        // Pattern 1: Salary credit - "for XXXXX-ABC-XYZ MONTH SALARY-COMPANY NAME"
        if (message.contains("SALARY", ignoreCase = true) && message.contains("deposited", ignoreCase = true)) {
            CompiledPatterns.HDFC.SALARY_PATTERN.find(message)?.let { match ->
                return cleanMerchantName(match.groupValues[1].trim())
            }
            
            // Simpler salary pattern
            CompiledPatterns.HDFC.SIMPLE_SALARY_PATTERN.find(message)?.let { match ->
                val merchant = match.groupValues[1].trim()
                if (merchant.isNotEmpty() && !merchant.all { it.isDigit() }) {
                    return cleanMerchantName(merchant)
                }
            }
        }
        
        // Pattern 2: "Info: UPI/merchant/category" format
        if (message.contains("Info:", ignoreCase = true)) {
            CompiledPatterns.HDFC.INFO_PATTERN.find(message)?.let { match ->
                val merchant = match.groupValues[1].trim()
                if (merchant.isNotEmpty() && !merchant.equals("UPI", ignoreCase = true)) {
                    return cleanMerchantName(merchant)
                }
            }
        }
        
        // Pattern 3: "VPA merchant@bank (Merchant Name)" format
        if (message.contains("VPA", ignoreCase = true)) {
            // First try to get name in parentheses
            CompiledPatterns.HDFC.VPA_WITH_NAME.find(message)?.let { match ->
                return cleanMerchantName(match.groupValues[1].trim())
            }
        }
        
        // Pattern 4: UPI merchant pattern
        if (message.contains("UPI", ignoreCase = true)) {
            CompiledPatterns.HDFC.UPI_MERCHANT.find(message)?.let { match ->
                val merchant = match.groupValues[1].trim()
                if (merchant.isNotEmpty() && !merchant.equals("UPI", ignoreCase = true)) {
                    return cleanMerchantName(merchant)
                }
            }
        }
        
        // Fallback to base class implementation
        return super.extractMerchant(message, sender)
    }
    
    override fun extractTransactionType(message: String): String? {
        val lowerMessage = message.lowercase()
        
        // Check if it's an investment transaction
        val investmentKeywords = listOf(
            "indian clearing corporation",  // Mutual funds clearing
            "groww",                        // Investment platform
            "zerodha",                      // Stock broker
            "upstox",                       // Stock broker
            "kite",                         // Zerodha's platform
            "kuvera",                       // Mutual fund platform
            "paytm money",                  // Investment platform
            "etmoney",                      // Investment platform
            "coin by zerodha",              // Zerodha mutual funds
            "smallcase",                    // Investment platform
            "angel one",                    // Stock broker (formerly Angel Broking)
            "angel broking",                // Old name
            "5paisa",                       // Stock broker
            "icici securities",             // Stock broker
            "icici direct",                 // Stock broker
            "hdfc securities",              // Stock broker
            "kotak securities",             // Stock broker
            "motilal oswal",                // Stock broker
            "sharekhan",                    // Stock broker
            "edelweiss",                    // Stock broker
            "axis direct",                  // Stock broker
            "sbi securities",               // Stock broker
            "nse",                          // National Stock Exchange
            "bse",                          // Bombay Stock Exchange
            "cdsl",                         // Central Depository Services
            "nsdl",                         // National Securities Depository
            "mutual fund",                  // Generic mutual fund
            "sip",                          // Systematic Investment Plan
            "elss",                         // Tax saving funds
            "ipo",                          // Initial Public Offering
            "stockbroker",                  // Generic
            "demat"                         // Demat account related
        )
        
        if (investmentKeywords.any { lowerMessage.contains(it) }) {
            return "INVESTMENT"
        }
        
        return when {
            // Credit card transactions - ONLY if message contains CC or PCC indicators
            // Any transaction with BLOCK CC or BLOCK PCC is a credit card transaction
            lowerMessage.contains("block cc") || lowerMessage.contains("block pcc") -> "CREDIT"
            
            // Legacy pattern for older format that explicitly says "spent on card"
            lowerMessage.contains("spent on card") && !lowerMessage.contains("block dc") -> "CREDIT"
            
            // Credit card bill payments (these are regular expenses from bank account)
            lowerMessage.contains("payment") && lowerMessage.contains("credit card") -> "DEBIT"
            lowerMessage.contains("towards") && lowerMessage.contains("credit card") -> "DEBIT"
            
            // HDFC specific: "Sent Rs.X From HDFC Bank"
            lowerMessage.contains("sent") && lowerMessage.contains("from hdfc") -> "DEBIT"
            
            // HDFC specific: "Spent Rs.X From HDFC Bank Card" (debit card transactions)
            lowerMessage.contains("spent") && lowerMessage.contains("from hdfc bank card") -> "DEBIT"
            
            // Standard expense keywords
            lowerMessage.contains("debited") -> "DEBIT"
            lowerMessage.contains("withdrawn") && !lowerMessage.contains("block cc") -> "DEBIT"
            lowerMessage.contains("spent") && !lowerMessage.contains("card") -> "DEBIT"
            lowerMessage.contains("charged") -> "DEBIT"
            lowerMessage.contains("paid") -> "DEBIT"
            lowerMessage.contains("purchase") -> "DEBIT"
            
            // Income keywords
            lowerMessage.contains("credited") -> "CREDIT"
            lowerMessage.contains("deposited") -> "CREDIT"
            lowerMessage.contains("received") -> "CREDIT"
            lowerMessage.contains("refund") -> "CREDIT"
            lowerMessage.contains("cashback") && !lowerMessage.contains("earn cashback") -> "CREDIT"
            
            else -> null
        }
    }
    
    override fun extractReference(message: String): String? {
        // HDFC specific reference patterns
        val hdfcPatterns = listOf(
            CompiledPatterns.HDFC.REF_SIMPLE,
            CompiledPatterns.HDFC.UPI_REF_NO,
            CompiledPatterns.HDFC.REF_NO,
            CompiledPatterns.HDFC.REF_END
        )
        
        for (pattern in hdfcPatterns) {
            pattern.find(message)?.let { match ->
                return match.groupValues[1].trim()
            }
        }
        
        // Fall back to generic extraction
        return super.extractReference(message)
    }
    
    override fun extractAccountLast4(message: String): String? {
        // Additional pattern for "HDFC Bank XXNNNN" format (without A/c prefix)
        val hdfcBankPattern = Regex("""HDFC\s+Bank\s+([X\*]*\d+)""", RegexOption.IGNORE_CASE)
        hdfcBankPattern.find(message)?.let { match ->
            val accountStr = match.groupValues[1]
            val digitsOnly = accountStr.filter { it.isDigit() }
            return if (digitsOnly.length >= 4) {
                digitsOnly.takeLast(4)
            } else {
                digitsOnly
            }
        }
        
        // HDFC specific patterns
        val hdfcPatterns = listOf(
            CompiledPatterns.HDFC.ACCOUNT_DEPOSITED,
            CompiledPatterns.HDFC.ACCOUNT_FROM,
            CompiledPatterns.HDFC.ACCOUNT_SIMPLE,
            CompiledPatterns.HDFC.ACCOUNT_GENERIC
        )
        
        for (pattern in hdfcPatterns) {
            pattern.find(message)?.let { match ->
                return match.groupValues[1]
            }
        }
        
        return super.extractAccountLast4(message)
    }
    
    override fun extractBalance(message: String): BigDecimal? {
        // HDFC specific pattern for "Avl bal:INR NNNN.NN"
        val avlBalINRPattern = Regex("""Avl\s+bal:?\s*INR\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        avlBalINRPattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }
        
        // Pattern for "Available Balance: INR NNNN.NN"
        val availableBalINRPattern = Regex("""Available\s+Balance:?\s*INR\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        availableBalINRPattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }
        
        // Pattern for "Bal Rs.NNNN.NN" or "Bal Rs NNNN.NN"
        val balRsPattern = Regex("""Bal\s+Rs\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        balRsPattern.find(message)?.let { match ->
            val balanceStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balanceStr)
            } catch (e: NumberFormatException) {
                null
            }
        }
        
        // Fall back to base class patterns for Rs format
        return super.extractBalance(message)
    }
    
    override fun cleanMerchantName(merchant: String): String {
        // Use parent class implementation which already uses CompiledPatterns
        return super.cleanMerchantName(merchant)
    }
    
    /**
     * Checks if this is an E-Mandate notification (not a transaction).
     */
    fun isEMandateNotification(message: String): Boolean {
        return message.contains("E-Mandate!", ignoreCase = true)
    }
    
    /**
     * Checks if this is a future debit notification (subscription alert, not a current transaction).
     */
    fun isFutureDebitNotification(message: String): Boolean {
        return message.contains("will be", ignoreCase = true)
    }
    
    /**
     * Parses E-Mandate subscription information.
     */
    fun parseEMandateSubscription(message: String): EMandateInfo? {
        if (!isEMandateNotification(message)) {
            return null
        }
        
        // Extract amount
        val amount = CompiledPatterns.HDFC.AMOUNT_WILL_DEDUCT.find(message)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        } ?: return null
        
        // Extract date
        val dateStr = CompiledPatterns.HDFC.DEDUCTION_DATE.find(message)?.groupValues?.get(1)
        
        // Extract merchant name
        val merchant = CompiledPatterns.HDFC.MANDATE_MERCHANT.find(message)?.let { match ->
            cleanMerchantName(match.groupValues[1].trim())
        } ?: "Unknown Subscription"
        
        // Extract UMN (Unique Mandate Number)
        val umn = CompiledPatterns.HDFC.UMN_PATTERN.find(message)?.groupValues?.get(1)
        
        return EMandateInfo(
            amount = amount,
            nextDeductionDate = dateStr,
            merchant = merchant,
            umn = umn
        )
    }
    
    override fun isTransactionMessage(message: String): Boolean {
        // Skip E-Mandate notifications
        if (isEMandateNotification(message)) {
            return false
        }
        
        // Skip future debit notifications (these are subscription alerts, not transactions)
        if (isFutureDebitNotification(message)) {
            return false
        }
        
        val lowerMessage = message.lowercase()
        
        // Check for payment alerts (current transactions)
        if (lowerMessage.contains("payment alert")) {
            // Make sure it's not a future debit
            if (!lowerMessage.contains("will be")) {
                return true
            }
        }
        
        // Skip payment request messages
        if (lowerMessage.contains("has requested") || 
            lowerMessage.contains("payment request") ||
            lowerMessage.contains("to pay, download") ||
            lowerMessage.contains("collect request") ||
            lowerMessage.contains("ignore if already paid")) {
            return false
        }
        
        
        // Skip credit card payment confirmations
        if (lowerMessage.contains("received towards your credit card")) {
            return false
        }
        
        // Skip credit card payment credited notifications
        if (lowerMessage.contains("payment") && 
            lowerMessage.contains("credited to your card")) {
            return false
        }
        
        // Skip OTP and promotional messages
        if (lowerMessage.contains("otp") || 
            lowerMessage.contains("one time password") ||
            lowerMessage.contains("verification code") ||
            lowerMessage.contains("offer") || 
            lowerMessage.contains("discount") ||
            lowerMessage.contains("cashback offer") ||
            lowerMessage.contains("win ")) {
            return false
        }
        
        // HDFC specific transaction keywords
        val hdfcTransactionKeywords = listOf(
            "debited", "credited", "withdrawn", "deposited",
            "spent", "received", "transferred", "paid", 
            "sent", // HDFC uses "Sent Rs.X From HDFC Bank"
            "deducted", // Add support for "deducted from" pattern
            "txn" // HDFC uses "Txn Rs.X" for card transactions
        )
        
        return hdfcTransactionKeywords.any { lowerMessage.contains(it) }
    }
    
    /**
     * Parses future debit notifications for subscription tracking.
     * Similar to E-Mandate but for regular future debit alerts.
     */
    fun parseFutureDebit(message: String): EMandateInfo? {
        if (!isFutureDebitNotification(message)) {
            return null
        }
        
        // Extract amount
        val amountPattern = Regex("""INR\.?\s*([0-9,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
        val amount = amountPattern.find(message)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        } ?: return null
        
        // Extract date (DD/MM/YYYY format)
        val datePattern = Regex("""will\s+be\s+debited\s+on\s+(\d{2}/\d{2}/\d{4})""", RegexOption.IGNORE_CASE)
        val debitDate = datePattern.find(message)?.groupValues?.get(1)?.let { dateStr ->
            // Convert DD/MM/YYYY to DD/MM/YY for consistency with EMandateInfo
            try {
                val parts = dateStr.split("/")
                if (parts.size == 3) {
                    "${parts[0]}/${parts[1]}/${parts[2].takeLast(2)}"
                } else {
                    dateStr
                }
            } catch (e: Exception) {
                dateStr
            }
        }
        
        // Extract merchant using the existing method
        val merchant = extractMerchant(message, "HDFCBK") ?: "Unknown Subscription"
        
        // Return as EMandateInfo to reuse existing subscription creation logic
        return EMandateInfo(
            amount = amount,
            nextDeductionDate = debitDate,
            merchant = merchant,
            umn = null // Future debits don't have UMN
        )
    }
    
    /**
     * E-Mandate subscription information.
     */
    data class EMandateInfo(
        val amount: BigDecimal,
        val nextDeductionDate: String?,
        val merchant: String,
        val umn: String?
    )

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        Log.d("HDFCBankParser", "=== HDFC PARSING START ===")
        Log.d("HDFCBankParser", "Sender: $sender")
        Log.d("HDFCBankParser", "Body: $smsBody")
        
        try {
            if (!canHandle(sender)) {
                Log.d("HDFCBankParser", "Cannot handle this sender")
                return null
            }
            
            if (!isTransactionMessage(smsBody)) {
                Log.d("HDFCBankParser", "Not a transaction message")
                return null
            }
            
            Log.d("HDFCBankParser", "Processing as transaction message")
            
            val amount = extractAmount(smsBody)
            Log.d("HDFCBankParser", "Extracted amount: $amount")
            
            if (amount == null) {
                Log.d("HDFCBankParser", "No amount found")
                return null
            }
            
            val type = extractTransactionType(smsBody)
            Log.d("HDFCBankParser", "Extracted type: $type")
            
            if (type == null) {
                Log.d("HDFCBankParser", "No transaction type found")
                return null
            }
            
            val merchant = extractMerchant(smsBody, sender)
            Log.d("HDFCBankParser", "Extracted merchant: $merchant")
            
            val channel = extractChannel(smsBody)
            Log.d("HDFCBankParser", "Extracted channel: $channel")
            
            val accountTail = extractAccountLast4(smsBody)
            Log.d("HDFCBankParser", "Extracted account tail: $accountTail")
            
            val reference = extractReference(smsBody)
            Log.d("HDFCBankParser", "Extracted reference: $reference")
            
            val parsedTransaction = ParsedTransaction(
                amount = amount,
                type = type,
                merchant = merchant,
                reference = reference,
                accountLast4 = accountTail,
                balance = extractBalance(smsBody),
                smsBody = smsBody,
                sender = sender,
                timestamp = timestamp,
                bankName = getBankName(),
                channel = channel
            )
            
            Log.d("HDFCBankParser", "Created ParsedTransaction: $parsedTransaction")
            Log.d("HDFCBankParser", "=== HDFC PARSING SUCCESS ===")
            return parsedTransaction
            
        } catch (e: Exception) {
            Log.e("HDFCBankParser", "Error during parsing", e)
            return null
        }
    }
}
