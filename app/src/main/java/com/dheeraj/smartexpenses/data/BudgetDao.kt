package com.dheeraj.smartexpenses.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    
    @Query("SELECT * FROM budgets WHERE isActive = 1 ORDER BY category")
    fun getAllActiveBudgets(): Flow<List<Budget>>
    
    @Query("SELECT * FROM budgets WHERE isActive = 1 ORDER BY category")
    suspend fun getAllActiveBudgetsList(): List<Budget>
    
    @Query("SELECT * FROM budgets WHERE category = :category LIMIT 1")
    suspend fun getBudgetByCategory(category: String): Budget?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: Budget): Long
    
    @Update
    suspend fun updateBudget(budget: Budget)
    
    @Delete
    suspend fun deleteBudget(budget: Budget)
    
    @Query("DELETE FROM budgets WHERE category = :category")
    suspend fun deleteBudgetByCategory(category: String)
    
    // Budget vs Spending Analysis
    @Query("""
        SELECT 
            b.category,
            b.monthlyLimit,
            COALESCE(SUM(t.amountMinor), 0) as spentAmount,
            b.monthlyLimit - COALESCE(SUM(t.amountMinor), 0) as remainingAmount,
            CASE 
                WHEN b.monthlyLimit > 0 THEN 
                    (COALESCE(SUM(t.amountMinor), 0) * 100.0 / b.monthlyLimit)
                ELSE 0 
            END as percentageUsed
        FROM budgets b
        LEFT JOIN transactions t ON 
            b.category = (
                CASE 
                    WHEN t.merchant LIKE '%zomato%' OR t.merchant LIKE '%swiggy%' OR t.merchant LIKE '%food%' THEN 'Food'
                    WHEN t.merchant LIKE '%fuel%' OR t.merchant LIKE '%uber%' OR t.merchant LIKE '%ola%' THEN 'Transport'
                    WHEN t.merchant LIKE '%store%' OR t.merchant LIKE '%amazon%' OR t.merchant LIKE '%flipkart%' THEN 'Shopping'
                    WHEN t.merchant LIKE '%entertainment%' OR t.merchant LIKE '%movie%' THEN 'Entertainment'
                    WHEN t.merchant LIKE '%bill%' OR t.merchant LIKE '%electricity%' OR t.merchant LIKE '%water%' THEN 'Bills'
                    WHEN t.merchant LIKE '%medical%' OR t.merchant LIKE '%hospital%' THEN 'Health'
                    WHEN t.merchant LIKE '%education%' OR t.merchant LIKE '%school%' THEN 'Education'
                    ELSE 'Other'
                END
            )
            AND t.type = 'DEBIT'
            AND t.ts >= :startOfMonth
            AND t.ts <= :endOfMonth
        WHERE b.isActive = 1
        GROUP BY b.category, b.monthlyLimit
        ORDER BY percentageUsed DESC
    """)
    fun getBudgetAnalysis(startOfMonth: Long, endOfMonth: Long): Flow<List<BudgetAnalysis>>
}

data class BudgetAnalysis(
    val category: String,
    val monthlyLimit: Long,
    val spentAmount: Long,
    val remainingAmount: Long,
    val percentageUsed: Double
) {
    val isOverBudget: Boolean get() = spentAmount > monthlyLimit
    val isNearLimit: Boolean get() = percentageUsed >= 80.0 && percentageUsed < 100.0
    val monthlyLimitAmount: Double get() = monthlyLimit / 100.0
    val spentAmountValue: Double get() = spentAmount / 100.0
    val remainingAmountValue: Double get() = remainingAmount / 100.0
}
