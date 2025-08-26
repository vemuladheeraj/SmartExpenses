package com.dheeraj.smartexpenses.data.bank

import com.dheeraj.smartexpenses.data.ParsedTransaction
import java.math.BigDecimal

/**
 * Base class for bank-specific message parsers.
 * Each bank should extend this class and implement its specific parsing logic.
 */
abstract class BankParser {
    
    /**
     * Returns the name of the bank this parser handles.
     */
    abstract fun getBankName(): String
    
    /**
     * Checks if this parser can handle messages from the given sender.
     */
    abstract fun canHandle(sender: String): Boolean
    
    /**
     * Parses an SMS message and extracts transaction information.
     * Returns null if the message cannot be parsed.
     */
    open fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        // Skip non-transaction messages
        if (!isTransactionMessage(smsBody)) {
            android.util.Log.d("BankParser", "Not a transaction message: ${smsBody.take(100)}")
            return null
        }
        
        val amount = extractAmount(smsBody)
        if (amount == null) {
            android.util.Log.d("BankParser", "Could not extract amount from: ${smsBody.take(100)}")
            return null
        }
        
        val type = extractTransactionType(smsBody)
        if (type == null) {
            android.util.Log.d("BankParser", "Could not extract transaction type from: ${smsBody.take(100)}")
            return null
        }
        
        return ParsedTransaction(
            amount = amount,
            type = type,
            merchant = extractMerchant(smsBody, sender),
            reference = extractReference(smsBody),
            accountLast4 = extractAccountLast4(smsBody),
            balance = extractBalance(smsBody),
            smsBody = smsBody,
            sender = sender,
            timestamp = timestamp,
            bankName = getBankName(),
            channel = extractChannel(smsBody)
        )
    }
    
    /**
     * Checks if the message is a transaction message (not OTP, promotional, etc.)
     */
    protected open fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()
        
        // Skip OTP messages
        if (lowerMessage.contains("otp") || 
            lowerMessage.contains("one time password") ||
            lowerMessage.contains("verification code")) {
            return false
        }
        
        // Skip promotional messages
        if (lowerMessage.contains("offer") || 
            lowerMessage.contains("discount") ||
            lowerMessage.contains("cashback offer") ||
            lowerMessage.contains("win ")) {
            return false
        }
        
        // Skip payment request messages (common across banks)
        if (lowerMessage.contains("has requested") || 
            lowerMessage.contains("payment request") ||
            lowerMessage.contains("collect request") ||
            lowerMessage.contains("requesting payment") ||
            lowerMessage.contains("requests rs") ||
            lowerMessage.contains("ignore if already paid")) {
            return false
        }
        
        // Skip promotional/loan/limit messages unless they have explicit transaction verbs
        if (isPromotionalOrLimitMessage(lowerMessage)) {
            return hasExplicitTransactionVerbs(lowerMessage)
        }
        
        // Must contain transaction keywords
        val transactionKeywords = listOf(
            "debited", "credited", "withdrawn", "deposited",
            "spent", "received", "transferred", "paid"
        )
        
        return transactionKeywords.any { lowerMessage.contains(it) }
    }
    
    /**
     * Checks if the message is promotional or about limits/loans
     */
    private fun isPromotionalOrLimitMessage(message: String): Boolean {
        val promotionalKeywords = listOf(
            "increase limit", "limit increase", "pre-approved", "loan", "emi", 
            "apply", "offer", "up to", "click", "call", "t&c", "eligibility", 
            "interest", "rate", "overdraft", "personal loan", "credit limit",
            "cashback up to", "discount", "sale", "bonus", "reward", "lucky",
            "win", "winner", "prize", "jackpot", "chance", "opportunity"
        )
        
        return promotionalKeywords.any { message.contains(it) }
    }
    
    /**
     * Checks if the message has explicit transaction verbs
     */
    private fun hasExplicitTransactionVerbs(message: String): Boolean {
        val explicitVerbs = listOf(
            "debited", "credited", "spent", "paid", "received", "success", 
            "txn", "utr", "ref no", "auth code", "neft", "imps", "upi"
        )
        
        return explicitVerbs.any { message.contains(it) }
    }
    
    /**
     * Extracts the transaction amount from the message.
     */
    protected open fun extractAmount(message: String): BigDecimal? {
        for (pattern in CompiledPatterns.Amount.ALL_PATTERNS) {
            pattern.find(message)?.let { match ->
                val amountStr = match.groupValues[1].replace(",", "")
                return try {
                    BigDecimal(amountStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }
        
        return null
    }
    
    /**
     * Extracts the transaction type (CREDIT/DEBIT/TRANSFER).
     */
    protected open fun extractTransactionType(message: String): String? {
        val lowerMessage = message.lowercase()
        
        return when {
            lowerMessage.contains("debited") -> Constants.TransactionTypes.DEBIT
            lowerMessage.contains("withdrawn") -> Constants.TransactionTypes.DEBIT
            lowerMessage.contains("spent") -> Constants.TransactionTypes.DEBIT
            lowerMessage.contains("charged") -> Constants.TransactionTypes.DEBIT
            lowerMessage.contains("paid") -> Constants.TransactionTypes.DEBIT
            lowerMessage.contains("purchase") -> Constants.TransactionTypes.DEBIT
            
            lowerMessage.contains("credited") -> Constants.TransactionTypes.CREDIT
            lowerMessage.contains("deposited") -> Constants.TransactionTypes.CREDIT
            lowerMessage.contains("received") -> Constants.TransactionTypes.CREDIT
            lowerMessage.contains("refund") -> Constants.TransactionTypes.CREDIT
            lowerMessage.contains("cashback") && !lowerMessage.contains("earn cashback") -> Constants.TransactionTypes.CREDIT
            
            else -> null
        }
    }
    
    /**
     * Extracts merchant/payee information.
     */
    protected open fun extractMerchant(message: String, sender: String): String? {
        for (pattern in CompiledPatterns.Merchant.ALL_PATTERNS) {
            pattern.find(message)?.let { match ->
                val merchant = cleanMerchantName(match.groupValues[1].trim())
                if (isValidMerchantName(merchant)) {
                    return merchant
                }
            }
        }
        
        return null
    }
    
    /**
     * Extracts transaction reference number.
     */
    protected open fun extractReference(message: String): String? {
        for (pattern in CompiledPatterns.Reference.ALL_PATTERNS) {
            pattern.find(message)?.let { match ->
                return match.groupValues[1].trim()
            }
        }
        
        return null
    }
    
    /**
     * Extracts last 4 digits of account number.
     */
    protected open fun extractAccountLast4(message: String): String? {
        for (pattern in CompiledPatterns.Account.ALL_PATTERNS) {
            pattern.find(message)?.let { match ->
                return match.groupValues[1]
            }
        }
        
        return null
    }
    
    /**
     * Extracts balance after transaction.
     */
    protected open fun extractBalance(message: String): BigDecimal? {
        for (pattern in CompiledPatterns.Balance.ALL_PATTERNS) {
            pattern.find(message)?.let { match ->
                val balanceStr = match.groupValues[1].replace(",", "")
                return try {
                    BigDecimal(balanceStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }
        
        return null
    }
    
    /**
     * Extracts the transaction channel.
     */
    protected open fun extractChannel(message: String): String? {
        val lowerMessage = message.lowercase()
        
        return when {
            lowerMessage.contains("upi") -> Constants.Channels.UPI
            lowerMessage.contains("imps") -> Constants.Channels.IMPS
            lowerMessage.contains("neft") -> Constants.Channels.NEFT
            lowerMessage.contains("rtgs") -> Constants.Channels.RTGS
            lowerMessage.contains("card") -> Constants.Channels.CARD
            lowerMessage.contains("atm") -> Constants.Channels.ATM
            lowerMessage.contains("pos") -> Constants.Channels.POS
            lowerMessage.contains("netbanking") || lowerMessage.contains("internet banking") -> Constants.Channels.NETBANKING
            else -> null
        }
    }
    
    /**
     * Cleans merchant name by removing common suffixes and noise.
     */
    protected open fun cleanMerchantName(merchant: String): String {
        return merchant
            .replace(CompiledPatterns.Cleaning.TRAILING_PARENTHESES, "")
            .replace(CompiledPatterns.Cleaning.REF_NUMBER_SUFFIX, "")
            .replace(CompiledPatterns.Cleaning.DATE_SUFFIX, "")
            .replace(CompiledPatterns.Cleaning.UPI_SUFFIX, "")
            .replace(CompiledPatterns.Cleaning.TIME_SUFFIX, "")
            .replace(CompiledPatterns.Cleaning.TRAILING_DASH, "")
            .replace(CompiledPatterns.Cleaning.PVT_LTD, "")
            .replace(CompiledPatterns.Cleaning.LTD, "")
            .trim()
    }
    
    /**
     * Validates if the extracted merchant name is valid.
     */
    protected open fun isValidMerchantName(name: String): Boolean {
        val commonWords = setOf("USING", "VIA", "THROUGH", "BY", "WITH", "FOR", "TO", "FROM", "AT", "THE")
        
        return name.length >= Constants.Parsing.MIN_MERCHANT_NAME_LENGTH && 
               name.any { it.isLetter() } && 
               name.uppercase() !in commonWords &&
               !name.all { it.isDigit() } &&
               !name.contains("@") // Not a UPI ID
    }
}
