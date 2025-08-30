package com.dheeraj.smartexpenses.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.first

@Database(entities = [Transaction::class, Budget::class, Category::class], version = 1)
abstract class AppDb : RoomDatabase() {
    abstract fun txnDao(): TxnDao
    abstract fun budgetDao(): BudgetDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile private var I: AppDb? = null
        
        // No migrations needed for development - fresh database creation
        
        private fun populateDefaultCategories(db: SupportSQLiteDatabase) {
            try {
                // Insert default categories
                val defaultCategories = listOf(
                    "('Food', 'Restaurant', '#FF6B6B', 1)",
                    "('Transport', 'DirectionsCar', '#4ECDC4', 1)",
                    "('Shopping', 'ShoppingCart', '#45B7D1', 1)",
                    "('Entertainment', 'Movie', '#96CEB4', 1)",
                    "('Bills', 'Receipt', '#FFEAA7', 1)",
                    "('Health', 'LocalHospital', '#DDA0DD', 1)",
                    "('Education', 'School', '#98D8C8', 1)",
                    "('Other', 'AccountBalance', '#F7DC6F', 1)"
                )
                
                defaultCategories.forEach { category ->
                    db.execSQL("INSERT INTO categories (name, icon, color, isDefault) VALUES $category")
                }
                
                android.util.Log.d("AppDb", "Default categories populated successfully")
            } catch (e: Exception) {
                android.util.Log.e("AppDb", "Error populating default categories", e)
                e.printStackTrace()
            }
        }
        // Note: 2 -> 3 changes amount storage; relying on fallbackToDestructiveMigration since user reinstalled
        
        fun get(ctx: Context): AppDb = I ?: synchronized(this) {
            I ?: try {
                Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "smart_expenses.db")
                    .fallbackToDestructiveMigration() // Always start fresh in development
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Populate default categories when database is first created
                            populateDefaultCategories(db)
                        }
                    })
                    .build()
                    .also { I = it }
            } catch (e: Exception) {
                // If database creation fails, try with destructive migration
                e.printStackTrace()
                Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "smart_expenses.db")
                    .fallbackToDestructiveMigration()
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Populate default categories when database is first created
                            populateDefaultCategories(db)
                        }
                    })
                    .build()
                    .also { I = it }
            }
        }
        
        /**
         * Completely clear all data and reset the database
         * This should be called on first install or when user wants a fresh start
         */
        fun clearAllData(ctx: Context) {
            try {
                // Close the current database instance
                I?.close()
                I = null
                
                // Delete the database file
                ctx.deleteDatabase("smart_expenses.db")
                
                // Clear all SharedPreferences
                clearAllSharedPreferences(ctx)
                
                // Recreate the database instance
                get(ctx)
                
                android.util.Log.d("AppDb", "All data cleared successfully")
            } catch (e: Exception) {
                android.util.Log.e("AppDb", "Error clearing data", e)
            }
        }
        
        /**
         * Force a fresh database creation for first-time installs
         * This ensures no cached data interferes with the initial setup
         */
        fun forceFreshInstall(ctx: Context) {
            try {
                // Close the current database instance
                I?.close()
                I = null
                
                // Delete the database file to ensure fresh start
                ctx.deleteDatabase("smart_expenses.db")
                
                // Clear any potential cache
                ctx.cacheDir.deleteRecursively()
                
                android.util.Log.d("AppDb", "Forced fresh install - database and cache cleared")
            } catch (e: Exception) {
                android.util.Log.e("AppDb", "Error forcing fresh install", e)
            }
        }
        
        /**
         * Ensure database is properly initialized with default data
         * This should be called on first install to ensure default categories exist
         */
        suspend fun ensureDefaultData(ctx: Context) {
            try {
                val db = get(ctx)
                val categoryDao = db.categoryDao()
                
                // Check if default categories exist
                val defaultCategories = categoryDao.getDefaultCategories().first()
                if (defaultCategories.isEmpty()) {
                    android.util.Log.d("AppDb", "No default categories found, populating...")
                    // If no default categories exist, populate them
                    val defaultCategoryData = listOf(
                        "Food" to ("Restaurant" to "#FF6B6B"),
                        "Transport" to ("DirectionsCar" to "#4ECDC4"),
                        "Shopping" to ("ShoppingCart" to "#45B7D1"),
                        "Entertainment" to ("Movie" to "#96CEB4"),
                        "Bills" to ("Receipt" to "#FFEAA7"),
                        "Health" to ("LocalHospital" to "#DDA0DD"),
                        "Education" to ("School" to "#98D8C8"),
                        "Other" to ("AccountBalance" to "#F7DC6F")
                    )
                    
                    defaultCategoryData.forEach { (name, iconColor) ->
                        val (icon, color) = iconColor
                        val category = com.dheeraj.smartexpenses.data.Category(
                            name = name,
                            icon = icon,
                            color = color,
                            isDefault = true
                        )
                        categoryDao.insertCategory(category)
                    }
                    android.util.Log.d("AppDb", "Default categories populated successfully")
                } else {
                    android.util.Log.d("AppDb", "Default categories already exist: ${defaultCategories.size} found")
                }
            } catch (e: Exception) {
                android.util.Log.e("AppDb", "Error ensuring default data", e)
                e.printStackTrace()
            }
        }
        
        private fun clearAllSharedPreferences(ctx: Context) {
            try {
                // Clear all SharedPreferences files
                val prefsFiles = listOf(
                    "smart_expenses_prefs",
                    "ai_insights_prefs", 
                    "security_prefs"
                )
                
                prefsFiles.forEach { prefsName ->
                    try {
                        val prefs = ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                        prefs.edit().clear().apply()
                        android.util.Log.d("AppDb", "Cleared SharedPreferences: $prefsName")
                    } catch (e: Exception) {
                        android.util.Log.e("AppDb", "Error clearing SharedPreferences: $prefsName", e)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AppDb", "Error clearing SharedPreferences", e)
            }
        }
    }
}
