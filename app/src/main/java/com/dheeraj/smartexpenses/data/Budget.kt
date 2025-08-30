package com.dheeraj.smartexpenses.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "budgets",
    indices = [
        Index(value = ["category"], unique = true, name = "unique_budget_category")
    ]
)
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,           // "Food", "Transport", "Shopping", etc.
    val monthlyLimit: Long,         // Amount in minor units (paise)
    val currency: String = "INR",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// convenience computed property for UI/calculations
val Budget.monthlyLimitAmount: Double get() = monthlyLimit / 100.0
