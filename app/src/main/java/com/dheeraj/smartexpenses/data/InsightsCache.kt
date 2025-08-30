package com.dheeraj.smartexpenses.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class InsightsCache(private val context: Context) {
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        coerceInputValues = true
    }
    
    private val cacheFile = File(context.cacheDir, "ai_insights_cache.json")
    private val timestampFile = File(context.cacheDir, "ai_insights_timestamp.txt")
    
    suspend fun saveInsights(insights: AiInsights) = withContext(Dispatchers.IO) {
        try {
            val insightsJson = json.encodeToString(AiInsights.serializer(), insights)
            cacheFile.writeText(insightsJson)
            
            val timestamp = System.currentTimeMillis()
            timestampFile.writeText(timestamp.toString())
            
            Log.d(TAG, "Insights cached successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache insights", e)
        }
    }
    
    suspend fun loadInsights(): AiInsights? = withContext(Dispatchers.IO) {
        try {
            if (!cacheFile.exists()) {
                return@withContext null
            }
            
            val insightsJson = cacheFile.readText()
            val insights = json.decodeFromString(AiInsights.serializer(), insightsJson)
            
            Log.d(TAG, "Insights loaded from cache")
            insights
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cached insights", e)
            null
        }
    }
    
    suspend fun getLastUpdatedTime(): Long = withContext(Dispatchers.IO) {
        try {
            if (!timestampFile.exists()) {
                return@withContext 0L
            }
            
            timestampFile.readText().toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read timestamp", e)
            0L
        }
    }
    
    fun getLastUpdatedTimeFormatted(): String {
        val timestamp = runCatching { 
            if (timestampFile.exists()) timestampFile.readText().toLongOrNull() ?: 0L else 0L 
        }.getOrNull() ?: 0L
        
        if (timestamp == 0L) {
            return "Never"
        }
        
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < 60000 -> "Just now" // < 1 minute
            diff < 3600000 -> "${diff / 60000} min ago" // < 1 hour
            diff < 86400000 -> "${diff / 3600000} hour${if (diff / 3600000 > 1) "s" else ""} ago" // < 1 day
            diff < 604800000 -> "${diff / 86400000} day${if (diff / 86400000 > 1) "s" else ""} ago" // < 1 week
            else -> {
                val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                dateFormat.format(Date(timestamp))
            }
        }
    }
    
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        try {
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
            if (timestampFile.exists()) {
                timestampFile.delete()
            }
            Log.d(TAG, "Cache cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear cache", e)
        }
    }
    
    fun hasCachedData(): Boolean = cacheFile.exists() && timestampFile.exists()
    
    companion object {
        private const val TAG = "InsightsCache"
    }
}
