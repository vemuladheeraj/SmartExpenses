package com.dheeraj.smartexpenses.utils

import android.content.Context
import android.util.Log
import com.dheeraj.smartexpenses.data.AppDb

/**
 * Utility class to debug data persistence issues
 */
object DataDebugger {
    
    /**
     * Log all data state for debugging
     */
    fun logDataState(context: Context) {
        try {
            Log.d("DataDebugger", "=== DATA STATE DEBUG ===")
            
            // Check SharedPreferences
            logSharedPreferencesState(context)
            
            // Check Database
            logDatabaseState(context)
            
            Log.d("DataDebugger", "=== END DATA STATE DEBUG ===")
        } catch (e: Exception) {
            Log.e("DataDebugger", "Error logging data state", e)
        }
    }
    
    private fun logSharedPreferencesState(context: Context) {
        val prefsFiles = listOf(
            "smart_expenses_prefs",
            "ai_insights_prefs", 
            "security_prefs"
        )
        
        prefsFiles.forEach { prefsName ->
            try {
                val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                val allPrefs = prefs.all
                Log.d("DataDebugger", "SharedPreferences '$prefsName': $allPrefs")
            } catch (e: Exception) {
                Log.e("DataDebugger", "Error reading SharedPreferences: $prefsName", e)
            }
        }
    }
    
    private fun logDatabaseState(context: Context) {
        try {
            val db = AppDb.get(context)
            val txnDao = db.txnDao()
            val budgetDao = db.budgetDao()
            
            // Check transaction count - run in coroutine
            kotlinx.coroutines.runBlocking {
                val txnCount = try { txnDao.getSmsTransactionCount() } catch (e: Exception) { -1 }
                Log.d("DataDebugger", "Database transaction count: $txnCount")
                
                // Check budget count
                val budgetCount = try { budgetDao.getAllActiveBudgetsList().size } catch (e: Exception) { -1 }
                Log.d("DataDebugger", "Database budget count: $budgetCount")
            
            }
            
            // Check if database file exists
            val dbFile = context.getDatabasePath("smart_expenses.db")
            Log.d("DataDebugger", "Database file exists: ${dbFile.exists()}")
            Log.d("DataDebugger", "Database file size: ${if (dbFile.exists()) dbFile.length() else 0} bytes")
            
        } catch (e: Exception) {
            Log.e("DataDebugger", "Error reading database state", e)
        }
    }
    
    /**
     * Check if this appears to be a fresh install
     */
    fun isFreshInstall(context: Context): Boolean {
        try {
            val prefs = context.getSharedPreferences("smart_expenses_prefs", Context.MODE_PRIVATE)
            val initialImportDone = prefs.getBoolean("initial_import_done", false)
            
            val db = AppDb.get(context)
            val txnDao = db.txnDao()
            val txnCount = kotlinx.coroutines.runBlocking { 
                try { txnDao.getSmsTransactionCount() } catch (e: Exception) { 0 }
            }
            
            val dbFile = context.getDatabasePath("smart_expenses.db")
            val dbExists = dbFile.exists()
            
            Log.d("DataDebugger", "Fresh install check: initialImportDone=$initialImportDone, txnCount=$txnCount, dbExists=$dbExists")
            
            // Consider fresh if no transactions and either no import done or no database file
            return txnCount == 0 && (!initialImportDone || !dbExists)
            
        } catch (e: Exception) {
            Log.e("DataDebugger", "Error checking fresh install", e)
            return true // Assume fresh on error
        }
    }
}
