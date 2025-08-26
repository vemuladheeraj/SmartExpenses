package com.dheeraj.smartexpenses.data.bank

import java.math.BigDecimal

/**
 * Parser for Indian Bank SMS messages
 * 
 * Supported formats:
 * - Debit: "Your Indian Bank a/c XX1234 has been debited Rs.500/-"
 * - Credit: "Your Indian Bank a/c XX1234 has been credited Rs.10000.00"
 * - UPI, IMPS, and other transaction types
 * 
 * Common senders: INDIAN, INDIANB, variations with DLT patterns
 */
class IndianBankParser : BankParser() {
    
    override fun getBankName() = "Indian Bank"
    
    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()
        
        val indianSenders = setOf(
            "INDIAN",
            "INDIANB",
            "INDIANBANK"
        )
        
        if (upperSender in indianSenders) return true
        
        // Check for DLT patterns
        val dltPatterns = listOf(
            Regex("""INDIAN[A-Z0-9]{6,}"""),
            Regex("""INDIANB[A-Z0-9]{5,}""")
        )
        
        return dltPatterns.any { it.matches(upperSender) }
    }
    
    override fun extractMerchant(message: String, sender: String): String? {
        // Check for ATM withdrawals
        if (message.contains("withdrawn", ignoreCase = true) || 
            message.contains("ATM", ignoreCase = true)) {
            return "ATM"
        }
        
        // Try Indian Bank specific patterns
        if (message.contains("UPI", ignoreCase = true)) {
            val upiPattern = Regex("""(?i)UPI\s+([^@\s]+(?:@[^\s]+)?(?:\s+[^\s]+)?)""")
            upiPattern.find(message)?.let { match ->
                val merchant = match.groupValues[1].trim()
                if (merchant.isNotEmpty() && !merchant.equals("UPI", ignoreCase = true)) {
                    return cleanMerchantName(merchant)
                }
            }
        }
        
        // Pattern for "at [merchant]"
        if (message.contains(" at ", ignoreCase = true)) {
            val merchantPattern = Regex("""(?i)at\s+([^@\s]+(?:@[^\s]+)?(?:\s+[^\s]+)?)""")
            merchantPattern.find(message)?.let { match ->
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
            "axis securities",              // Stock broker
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
            // Standard expense keywords
            lowerMessage.contains("debited") -> "DEBIT"
            lowerMessage.contains("withdrawn") -> "DEBIT"
            lowerMessage.contains("spent") -> "DEBIT"
            lowerMessage.contains("charged") -> "DEBIT"
            lowerMessage.contains("paid") -> "DEBIT"
            lowerMessage.contains("purchase") -> "DEBIT"
            lowerMessage.contains("deducted") -> "DEBIT"
            
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
        
        // Indian Bank specific transaction keywords
        val indianTransactionKeywords = listOf(
            "debited", "credited", "withdrawn", "deposited",
            "spent", "received", "transferred", "paid", 
            "charged", "purchase", "deducted"
        )
        
        return indianTransactionKeywords.any { lowerMessage.contains(it) }
    }
}