package com.dheeraj.smartexpenses.data.bank

import java.math.BigDecimal

/**
 * Parser for ICICI Bank SMS messages
 * 
 * Supported formats:
 * - Credit card transactions: "Rs.XXX spent on your ICICI Bank Credit Card ending XXXX"
 * - UPI transactions with merchant details
 * - Account debits/credits
 * - Investment transactions
 * 
 * Common senders: ICICI, ICICIB, variations with DLT patterns
 */
class ICICIBankParser : BankParser() {
    
    override fun getBankName() = "ICICI Bank"
    
    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()
        
        val iciciSenders = setOf(
            "ICICI",
            "ICICIB",
            "ICICIBANK"
        )
        
        if (upperSender in iciciSenders) return true
        
        return CompiledPatterns.ICICI.DLT_PATTERNS.any { it.matches(upperSender) }
    }
    
    override fun extractMerchant(message: String, sender: String): String? {
        // Check for ATM withdrawals
        if (message.contains("withdrawn", ignoreCase = true) || 
            message.contains("ATM", ignoreCase = true)) {
            return "ATM"
        }
        
        // Try ICICI specific patterns
        if (message.contains("Card", ignoreCase = true)) {
            CompiledPatterns.ICICI.CARD_PATTERN.find(message)?.let { match ->
                return "Credit Card"
            }
        }
        
        if (message.contains("UPI", ignoreCase = true)) {
            CompiledPatterns.ICICI.UPI_PATTERN.find(message)?.let { match ->
                val merchant = match.groupValues[1].trim()
                if (merchant.isNotEmpty() && !merchant.equals("UPI", ignoreCase = true)) {
                    return cleanMerchantName(merchant)
                }
            }
        }
        
        // Pattern for "at [merchant]"
        if (message.contains(" at ", ignoreCase = true)) {
            CompiledPatterns.ICICI.MERCHANT_PATTERN.find(message)?.let { match ->
                val merchant = match.groupValues[1].trim()
                if (merchant.isNotEmpty()) {
                    return cleanMerchantName(merchant)
                }
            }
        }
        
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
            // Credit card transactions - ONLY if message contains "spent on your ICICI Bank Credit Card"
            lowerMessage.contains("spent on your icici bank credit card") -> "CREDIT"
            lowerMessage.contains("spent on your credit card") -> "CREDIT"
            
            // Credit card bill payments (these are regular expenses from bank account)
            lowerMessage.contains("payment") && lowerMessage.contains("credit card") -> "DEBIT"
            lowerMessage.contains("towards") && lowerMessage.contains("credit card") -> "DEBIT"
            
            // Standard expense keywords
            lowerMessage.contains("debited") -> "DEBIT"
            lowerMessage.contains("withdrawn") -> "DEBIT"
            lowerMessage.contains("spent") -> "DEBIT"
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
    
    override fun extractChannel(message: String): String? {
        val lowerMessage = message.lowercase()
        
        return when {
            lowerMessage.contains("upi") -> Constants.Channels.UPI
            lowerMessage.contains("card") -> Constants.Channels.CARD
            lowerMessage.contains("atm") -> Constants.Channels.ATM
            lowerMessage.contains("imps") -> Constants.Channels.IMPS
            lowerMessage.contains("neft") -> Constants.Channels.NEFT
            lowerMessage.contains("rtgs") -> Constants.Channels.RTGS
            lowerMessage.contains("pos") -> Constants.Channels.POS
            lowerMessage.contains("netbanking") || lowerMessage.contains("internet banking") -> Constants.Channels.NETBANKING
            else -> super.extractChannel(message)
        }
    }
    
    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()
        
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
        
        // ICICI specific transaction keywords
        val iciciTransactionKeywords = listOf(
            "debited", "credited", "withdrawn", "deposited",
            "spent", "received", "transferred", "paid", 
            "charged", "purchase"
        )
        
        return iciciTransactionKeywords.any { lowerMessage.contains(it) }
    }
}