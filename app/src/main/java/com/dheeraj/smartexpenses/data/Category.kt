package com.dheeraj.smartexpenses.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val icon: String, // Store icon name as string
    val color: String, // Store color as string
    val isDefault: Boolean = false // To distinguish between default and custom categories
)
