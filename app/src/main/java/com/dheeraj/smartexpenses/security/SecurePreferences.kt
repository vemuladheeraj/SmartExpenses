package com.dheeraj.smartexpenses.security

import android.content.Context
import android.content.SharedPreferences
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
    
    suspend fun saveApiKey(apiKey: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_API_KEY, apiKey).apply()
    }
    
    suspend fun getApiKey(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_API_KEY, null)
    }
    
    suspend fun saveCustomEndpoint(endpoint: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_CUSTOM_ENDPOINT, endpoint).apply()
    }
    
    suspend fun getCustomEndpoint(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_CUSTOM_ENDPOINT, null)
    }
    
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
    }
    
    fun isUsingEncryptedStorage(): Boolean = encryptedPrefs != null
    
    companion object {
        private const val KEY_API_KEY = "ai_api_key"
        private const val KEY_CUSTOM_ENDPOINT = "ai_custom_endpoint"
    }
}
