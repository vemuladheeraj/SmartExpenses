package com.dheeraj.smartexpenses.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SecurePreferences(context: Context) {
    
    private val masterKey = try {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    } catch (e: Exception) {
        null
    }
    
    private val encryptedPrefs: SharedPreferences? = try {
        masterKey?.let { key ->
            EncryptedSharedPreferences.create(
                context,
                "ai_insights_secure",
                key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    } catch (e: Exception) {
        null
    }
    
    private val fallbackPrefs: SharedPreferences = context.getSharedPreferences(
        "ai_insights_fallback",
        Context.MODE_PRIVATE
    )
    
    private val prefs: SharedPreferences = encryptedPrefs ?: fallbackPrefs
    
    init {
        Log.d(TAG, "SecurePreferences initialized - using encrypted storage: ${encryptedPrefs != null}")
        if (encryptedPrefs == null) {
            Log.w(TAG, "Falling back to unencrypted storage")
        }
    }
    
    suspend fun saveApiKey(apiKey: String) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Saving API key: ${apiKey.take(10)}...")
        val success = prefs.edit().putString(KEY_API_KEY, apiKey).commit()
        if (!success) {
            Log.e(TAG, "Failed to save API key to preferences")
            throw RuntimeException("Failed to save API key to preferences")
        }
        Log.d(TAG, "API key saved successfully")
    }
    
    suspend fun getApiKey(): String? = withContext(Dispatchers.IO) {
        val key = prefs.getString(KEY_API_KEY, null)
        Log.d(TAG, "Retrieved API key: ${key?.take(10) ?: "null"}...")
        key
    }
    
    suspend fun saveCustomEndpoint(endpoint: String) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Saving custom endpoint: $endpoint")
        val success = prefs.edit().putString(KEY_CUSTOM_ENDPOINT, endpoint).commit()
        if (!success) {
            Log.e(TAG, "Failed to save custom endpoint to preferences")
            throw RuntimeException("Failed to save custom endpoint to preferences")
        }
        Log.d(TAG, "Custom endpoint saved successfully")
    }
    
    suspend fun getCustomEndpoint(): String? = withContext(Dispatchers.IO) {
        val endpoint = prefs.getString(KEY_CUSTOM_ENDPOINT, null)
        Log.d(TAG, "Retrieved custom endpoint: $endpoint")
        endpoint
    }
    
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
    }
    
    fun isUsingEncryptedStorage(): Boolean = encryptedPrefs != null
    
    companion object {
        private const val TAG = "SecurePreferences"
        private const val KEY_API_KEY = "ai_api_key"
        private const val KEY_CUSTOM_ENDPOINT = "ai_custom_endpoint"
    }
}
