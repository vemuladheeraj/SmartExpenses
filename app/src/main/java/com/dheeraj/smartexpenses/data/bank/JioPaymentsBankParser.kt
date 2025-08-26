package com.dheeraj.smartexpenses.data.bank

import java.math.BigDecimal

/**
 * Parser for Jio Payments Bank SMS messages
 * 
 * Supported formats:
 * - UPI transactions through Jio Payments Bank
 * - Payment confirmations
 * - Transaction notifications
 * 
 * Common senders: JIO, JIOPAY, variations with DLT patterns
 */
class JioPaymentsBankParser : BankParser() {
    
    override fun getBankName() = "Jio Payments Bank"
    
    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()
        
        val jioSenders = setOf(
            "JIO",
            "JIOPAY",
            "JIOPAYMENTS"
        )
        
        if (upperSender in jioSenders) return true
        
        // Check for DLT patterns
        val dltPatterns = listOf(
            Regex("""JIO[A-Z0-9]{6,}"""),
            Regex("""JIOPAY[A-Z0-9]{5,}""")
        )
        
        return dltPatterns.any { it.matches(upperSender) }
    }
    
    override fun extractMerchant(message: String, sender: String): String? {
        // Try Jio Payments Bank specific patterns
        if (message.contains("UPI", ignoreCase = true)) {
            val upiPattern = Regex("""(?i)UPI\s+([^@\s]+(?:@[^\s]+)?(?:\s+[^\s]+)?)""")
            upiPattern.find(message)?.let { match ->
                val merchant = match.groupValues[1].trim()
                if (merchant.isNotEmpty() && !merchant.equals("UPI", ignoreCase = true)) {
                    return cleanMerchantName(merchant)
                }
            }
        }
        
        // Pattern for "to [merchant]"
        if (message.contains(" to ", ignoreCase = true)) {
            val merchantPattern = Regex("""(?i)to\s+([^@\s]+(?:@[^\s]+)?(?:\s+[^\s]+)?)""")
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
            
            // UPI specific
            lowerMessage.contains("upi") -> "DEBIT"
            
            else -> null
        }
    }
    
    override fun extractChannel(message: String): String? {
        val lowerMessage = message.lowercase()
        
        return when {
            lowerMessage.contains("upi") -> Constants.Channels.UPI
            lowerMessage.contains("card") -> Constants.Channels.CARD
            lowerMessage.contains("netbanking") || lowerMessage.contains("internet banking") -> Constants.Channels.NETBANKING
            else -> Constants.Channels.UPI // Default to UPI for Jio Payments Bank
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
        
        // Jio Payments Bank specific transaction keywords
        val jioTransactionKeywords = listOf(
            "debited", "credited", "withdrawn", "deposited",
            "spent", "received", "transferred", "paid", 
            "charged", "purchase", "deducted", "upi"
        )
        
        return jioTransactionKeywords.any { lowerMessage.contains(it) }
    }
}