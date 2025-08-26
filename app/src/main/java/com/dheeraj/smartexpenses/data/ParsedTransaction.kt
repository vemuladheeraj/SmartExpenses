package com.dheeraj.smartexpenses.data

import android.util.Log
import java.math.BigDecimal
import java.security.MessageDigest

/**
 * Represents a parsed transaction from an SMS message.
 * This is an intermediate representation before converting to the main Transaction entity.
 */
data class ParsedTransaction(
    val amount: BigDecimal,
    val type: String, // "CREDIT", "DEBIT", "TRANSFER"
    val merchant: String?,
    val reference: String?,
    val accountLast4: String?,
    val balance: BigDecimal?,
    val smsBody: String,
    val sender: String,
    val timestamp: Long,
    val bankName: String,
    val channel: String? = null
) {
    /**
     * Generates a unique transaction ID based on sender, amount, and timestamp.
     * This helps in duplicate detection.
     */
    fun generateTransactionId(): String {
        val normalizedAmount = amount.setScale(2, java.math.RoundingMode.HALF_UP)
        val data = "$sender|$normalizedAmount|$timestamp"
        
        return MessageDigest.getInstance("MD5")
            .digest(data.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Converts this parsed transaction to a Transaction entity.
     */
    fun toTransaction(): Transaction {
        // Normalize merchant name to proper case
        val normalizedMerchant = merchant?.let { normalizeMerchantName(it) }
        
        return Transaction(
            id = 0, // Auto-generated
            ts = timestamp,
            amount = amount.toDouble(),
            type = type,
            channel = channel,
            merchant = normalizedMerchant,
            accountTail = accountLast4,
            bank = bankName,
            source = "SMS",
            rawSender = sender,
            rawBody = smsBody
        )
    }
    
    /**
     * Normalizes merchant name to consistent format.
     * Converts all-caps to proper case, preserves already mixed case.
     */
    private fun normalizeMerchantName(name: String): String {
        val trimmed = name.trim()
        
        // If it's all uppercase, convert to proper case
        return if (trimmed == trimmed.uppercase()) {
            trimmed.lowercase().split(" ").joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
        } else {
            // Already has mixed case, keep as is
            trimmed
        }
    }
}