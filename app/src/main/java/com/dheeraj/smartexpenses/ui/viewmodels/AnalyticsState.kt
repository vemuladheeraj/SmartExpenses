package com.dheeraj.smartexpenses.ui.viewmodels

import com.dheeraj.smartexpenses.data.Transaction

data class AnalyticsState(
    val transactions: List<Transaction> = emptyList(),
    val totalCreditCurrentMonth: Double = 0.0,
    val totalDebitCurrentMonth: Double = 0.0,
    val totalCredit6Months: Double = 0.0,
    val totalDebit6Months: Double = 0.0,
    val error: String? = null,
    val isLoading: Boolean = false
) {
    val balance: Double get() = totalCreditCurrentMonth - totalDebitCurrentMonth
    val balance6Months: Double get() = totalCredit6Months - totalDebit6Months

    val validTransactions: List<Transaction> get() = transactions.filter { transaction ->
        // Filter out likely promotional messages
        if (transaction.merchant?.lowercase()?.let { merchant ->
            merchant.contains("offer") || merchant.contains("loan") ||
            merchant.contains("promo") || merchant.contains("invest")
        } == true) {
            return@filter false
        }

        // Filter out unrealistic amounts
        if (!isAmountRealistic(transaction.amount)) return@filter false

        // Filter out non-monetary messages
        if (transaction.rawBody.lowercase().let { body ->
            body.contains("loan") || body.contains("offer") ||
            body.contains("promo") || body.contains("kyc") ||
            body.contains("apply") || body.contains("subscription") ||
            body.contains("invest") || body.contains("click") ||
            body.contains("download")
        }) return@filter false

        true
    }

    private fun isAmountRealistic(amount: Double): Boolean {
        return when {
            amount <= 0.0 -> false
            amount > 10_000_000 -> false  // > 1 Crore
            else -> true
        }
    }
}
