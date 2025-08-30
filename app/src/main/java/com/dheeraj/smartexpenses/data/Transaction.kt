package com.dheeraj.smartexpenses.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["rawSender", "rawBody", "ts"], unique = true),
        Index(value = ["ts"]),
        Index(value = ["type", "ts"])
    ]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    // store as minor units (paise) to avoid floating-point errors
    val amountMinor: Long,
    val currency: String = "INR",
    val type: String,              // "DEBIT" | "CREDIT"
    val channel: String?,          // UPI, CARD, CASH, IMPS, NEFT, POS, WALLET, OTHER
    val merchant: String?,
    val category: String?,         // category for manual transactions
    val accountTail: String?,
    val bank: String?,             // inferred from SMS sender
    val source: String = "SMS",    // "SMS" or "MANUAL"
    val rawSender: String,
    val rawBody: String
)

// convenience computed property for UI/calculations
val Transaction.amount: Double get() = amountMinor / 100.0
