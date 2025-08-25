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
    val amount: Double,
    val currency: String = "INR",
    val type: String,              // "DEBIT" | "CREDIT"
    val channel: String?,          // UPI, CARD, CASH, IMPS, NEFT, POS, WALLET, OTHER
    val merchant: String?,
    val accountTail: String?,
    val bank: String?,             // inferred from SMS sender
    val source: String = "SMS",    // "SMS" or "MANUAL"
    val rawSender: String,
    val rawBody: String
)
