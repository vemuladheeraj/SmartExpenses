package com.dheeraj.smartexpenses.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class AiInsights(
    val kpis: Kpis,
    val breakdowns: Breakdowns,
    @SerialName("large_txns")
    val largeTxns: List<LargeTransaction>,
    val recurring: List<RecurringPayment>,
    val notes: String
)

@Serializable
data class Kpis(
    @SerialName("total_spend_inr")
    val totalSpendInr: Double,
    @SerialName("debit_count")
    val debitCount: Int,
    @SerialName("credit_count")
    val creditCount: Int,
    @SerialName("largest_txn_amount")
    val largestTxnAmount: Double,
    @SerialName("largest_txn_merchant")
    val largestTxnMerchant: String?,
    @SerialName("unusual_spend_flag")
    val unusualSpendFlag: Boolean
)

@Serializable
data class Breakdowns(
    @SerialName("by_category")
    val byCategory: List<CategoryBreakdown>,
    @SerialName("by_rail")
    val byRail: List<RailBreakdown>
)

@Serializable
data class CategoryBreakdown(
    val name: String?,
    val amount: Double
)

@Serializable
data class RailBreakdown(
    val name: String,
    val amount: Double
)

@Serializable
data class LargeTransaction(
    val date: String, // YYYY-MM-DD
    val merchant: String?,
    val amount: Double
)

@Serializable
data class RecurringPayment(
    val name: String,
    @SerialName("day_of_month")
    val dayOfMonth: Int,
    val amount: Double
)

// Transaction data sent to AI
@Serializable
data class TransactionForAi(
    val ts: Long, // epoch ms
    val date: String, // YYYY-MM-DD
    val amount: Double,
    val direction: String, // "debit" or "credit"
    val merchant: String?,
    val rail: String?,
    val category: String?
)

// Gemini API request/response models
@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>
)

@Serializable
data class GeminiContent(
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(
    val text: String
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent
)

// Custom endpoint request/response models
@Serializable
data class CustomEndpointRequest(
    val transactions: List<TransactionForAi>,
    val instructions: String
)

@Serializable
data class CustomEndpointResponse(
    val insights: AiInsights? = null
)
