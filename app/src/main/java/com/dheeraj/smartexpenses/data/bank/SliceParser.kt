package com.dheeraj.smartexpenses.data.bank

import java.math.BigDecimal

/**
 * Parser for Slice (fintech) SMS messages
 * 
 * Supported formats:
 * - UPI transactions through Slice
 * - Payment confirmations
 * - Transaction notifications
 * 
 * Common senders: SLICE, variations with DLT patterns
 */
class SliceParser : BankParser() {
    
    override fun getBankName() = "Slice"
    
    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()
        
        val sliceSenders = setOf(
            "SLICE",
            "SLICEPAY"
        )
        
        if (upperSender in sliceSenders) return true
        
        // Check for DLT patterns
        val dltPatterns = listOf(
            Regex("""SLICE[A-Z0-9]{6,}"""),
            Regex("""SLICEPAY[A-Z0-9]{5,}""")
        )
        
        return dltPatterns.any { it.matches(upperSender) }
    }
    
    override fun extractMerchant(message: String, sender: String): String? {
        // Try Slice specific patterns
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
            else -> Constants.Channels.UPI // Default to UPI for Slice
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
        
        // Slice specific transaction keywords
        val sliceTransactionKeywords = listOf(
            "debited", "credited", "withdrawn", "deposited",
            "spent", "received", "transferred", "paid", 
            "charged", "purchase", "deducted", "upi"
        )
        
        return sliceTransactionKeywords.any { lowerMessage.contains(it) }
    }
}