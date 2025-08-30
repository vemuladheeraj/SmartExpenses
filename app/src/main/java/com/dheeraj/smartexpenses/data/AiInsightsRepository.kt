package com.dheeraj.smartexpenses.data

import android.content.Context
import android.util.Log
import com.dheeraj.smartexpenses.security.SecurePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class AiInsightsRepository(
    private val context: Context,
    private val txnDao: TxnDao,
    private val aiService: AiService,
    private val cache: InsightsCache,
    private val securePrefs: SecurePreferences
) {
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    suspend fun getCachedInsights(): AiInsights? = withContext(Dispatchers.IO) {
        cache.loadInsights()
    }
    
    suspend fun getLastUpdatedTime(): String = withContext(Dispatchers.IO) {
        cache.getLastUpdatedTimeFormatted()
    }
    
    suspend fun refreshInsights(): Result<AiInsights> = withContext(Dispatchers.IO) {
        try {
            // Get API key or custom endpoint
            val apiKey = securePrefs.getApiKey()
            val customEndpoint = securePrefs.getCustomEndpoint()
            
            if (apiKey.isNullOrBlank() && customEndpoint.isNullOrBlank()) {
                return@withContext Result.failure(IllegalStateException("No API key or endpoint configured"))
            }
            
            // Get recent transactions (last 30 days, max 500)
            val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
            val transactions = txnDao.inRange(thirtyDaysAgo, System.currentTimeMillis())
                .first() // Get the first emission from the Flow
            
            if (transactions.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No transactions found for analysis"))
            }
            
            // Convert to AI format and limit to 500
            val transactionsForAi = transactions
                .take(500)
                .map { transaction ->
                    TransactionForAi(
                        ts = transaction.ts,
                        date = dateFormat.format(Date(transaction.ts)),
                        amount = transaction.amount,
                        direction = if (transaction.type == "DEBIT") "debit" else "credit",
                        merchant = transaction.merchant,
                        rail = transaction.channel,
                        category = transaction.category
                    )
                }
            
            Log.d(TAG, "Sending ${transactionsForAi.size} transactions to AI")
            
            // Fetch insights from AI
            val result = aiService.fetchInsights(apiKey, customEndpoint, transactionsForAi)
            
            if (result.isSuccess) {
                val insights = result.getOrThrow()
                cache.saveInsights(insights)
                Log.d(TAG, "Insights refreshed successfully")
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh insights", e)
            Result.failure(e)
        }
    }
    
    suspend fun saveApiKey(apiKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!isValidApiKey(apiKey)) {
                return@withContext Result.failure(IllegalArgumentException("Invalid API key format"))
            }
            
            securePrefs.saveApiKey(apiKey)
            securePrefs.saveCustomEndpoint("") // Clear custom endpoint
            Log.d(TAG, "API key saved successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save API key", e)
            Result.failure(e)
        }
    }
    
    suspend fun saveCustomEndpoint(endpoint: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!isValidEndpoint(endpoint)) {
                return@withContext Result.failure(IllegalArgumentException("Invalid endpoint URL"))
            }
            
            securePrefs.saveCustomEndpoint(endpoint)
            securePrefs.saveApiKey("") // Clear API key
            Log.d(TAG, "Custom endpoint saved successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save custom endpoint", e)
            Result.failure(e)
        }
    }
    
    suspend fun getSavedApiKey(): String? = withContext(Dispatchers.IO) {
        securePrefs.getApiKey()
    }
    
    suspend fun getSavedCustomEndpoint(): String? = withContext(Dispatchers.IO) {
        securePrefs.getCustomEndpoint()
    }
    
    suspend fun clearAllData(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            securePrefs.clearAll()
            cache.clearCache()
            Log.d(TAG, "All AI insights data cleared")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear data", e)
            Result.failure(e)
        }
    }
    
    fun isUsingEncryptedStorage(): Boolean = securePrefs.isUsingEncryptedStorage()
    
    suspend fun hasConfiguredKey(): Boolean {
        return runCatching {
            val apiKey = securePrefs.getApiKey()
            val endpoint = securePrefs.getCustomEndpoint()
            !apiKey.isNullOrBlank() || !endpoint.isNullOrBlank()
        }.getOrNull() ?: false
    }
    
    private fun isValidApiKey(apiKey: String): Boolean {
        return apiKey.startsWith("AIza") && apiKey.length > 20
    }
    
    private fun isValidEndpoint(endpoint: String): Boolean {
        return endpoint.startsWith("http://") || endpoint.startsWith("https://")
    }
    
    companion object {
        private const val TAG = "AiInsightsRepository"
    }
}
