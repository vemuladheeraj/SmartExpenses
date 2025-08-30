package com.dheeraj.smartexpenses.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dheeraj.smartexpenses.data.*
import com.dheeraj.smartexpenses.security.SecurePreferences
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.*

class AiInsightsViewModel(app: Application) : AndroidViewModel(app) {
    
    private val repository: AiInsightsRepository by lazy {
        val txnDao = AppDb.get(app).txnDao()
        val aiService = AiService()
        val cache = InsightsCache(app)
        val securePrefs = SecurePreferences(app)
        
        AiInsightsRepository(app, txnDao, aiService, cache, securePrefs)
    }
    
    // UI State
    private val _uiState = MutableStateFlow(AiInsightsUiState())
    val uiState: StateFlow<AiInsightsUiState> = _uiState.asStateFlow()
    
    // Debounce state
    private var lastRefreshTime = 0L
    private val debounceDelay = 4000L // 4 seconds
    
    init {
        loadInitialState()
    }
    
    private fun loadInitialState() {
        viewModelScope.launch {
            try {
                // Load saved configuration
                val apiKey = repository.getSavedApiKey()
                val customEndpoint = repository.getSavedCustomEndpoint()
                
                _uiState.value = _uiState.value.copy(
                    hasConfiguredKey = !apiKey.isNullOrBlank() || !customEndpoint.isNullOrBlank(),
                    savedApiKey = apiKey,
                    savedCustomEndpoint = customEndpoint,
                    isUsingEncryptedStorage = repository.isUsingEncryptedStorage()
                )
                
                // Load cached insights if available
                val cachedInsights = repository.getCachedInsights()
                if (cachedInsights != null) {
                    _uiState.value = _uiState.value.copy(
                        insights = cachedInsights,
                        lastUpdatedTime = repository.getLastUpdatedTime()
                    )
                }
                
                // Auto-refresh on first load if key is configured
                if (_uiState.value.hasConfiguredKey) {
                    refreshInsights()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load initial state", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to load configuration: ${e.message}"
                )
            }
        }
    }
    
    fun saveApiKey(apiKey: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    errorMessage = null
                )
                
                val result = repository.saveApiKey(apiKey)
                
                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        hasConfiguredKey = true,
                        savedApiKey = apiKey,
                        savedCustomEndpoint = null,
                        successMessage = "API key saved successfully"
                    )
                    
                    // Auto-refresh insights
                    refreshInsights()
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to save API key"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save API key", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to save API key: ${e.message}"
                )
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
    
    fun saveCustomEndpoint(endpoint: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    errorMessage = null
                )
                
                val result = repository.saveCustomEndpoint(endpoint)
                
                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        hasConfiguredKey = true,
                        savedCustomEndpoint = endpoint,
                        savedApiKey = null,
                        successMessage = "Custom endpoint saved successfully"
                    )
                    
                    // Auto-refresh insights
                    refreshInsights()
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to save endpoint"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save custom endpoint", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to save endpoint: ${e.message}"
                )
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
    
    fun refreshInsights() {
        val now = System.currentTimeMillis()
        if (now - lastRefreshTime < debounceDelay) {
            _uiState.value = _uiState.value.copy(
                statusMessage = "Please wait a moment..."
            )
            return
        }
        
        lastRefreshTime = now
        
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    errorMessage = null,
                    statusMessage = "Refreshing..."
                )
                
                val result = repository.refreshInsights()
                
                if (result.isSuccess) {
                    val insights = result.getOrThrow()
                    _uiState.value = _uiState.value.copy(
                        insights = insights,
                        lastUpdatedTime = repository.getLastUpdatedTime(),
                        statusMessage = "AI insights updated.",
                        successMessage = "Insights refreshed successfully"
                    )
                } else {
                    val error = result.exceptionOrNull()
                    val errorMessage = when {
                        error is IllegalStateException && error.message?.contains("No API key") == true -> {
                            "No API key or endpoint configured"
                        }
                        error is IllegalStateException && error.message?.contains("No transactions") == true -> {
                            "No transactions found for analysis"
                        }
                        error is IllegalArgumentException -> {
                            error.message ?: "Invalid configuration"
                        }
                        else -> {
                            "Failed to fetch insights: ${error?.message ?: "Unknown error"}"
                        }
                    }
                    
                    _uiState.value = _uiState.value.copy(
                        errorMessage = errorMessage,
                        statusMessage = "Failed after retries. Showing cached data."
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh insights", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to refresh insights: ${e.message}",
                    statusMessage = "Failed after retries. Showing cached data."
                )
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
    
    fun clearAllData() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    errorMessage = null
                )
                
                val result = repository.clearAllData()
                
                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        hasConfiguredKey = false,
                        savedApiKey = null,
                        savedCustomEndpoint = null,
                        insights = null,
                        lastUpdatedTime = "Never",
                        successMessage = "All data cleared successfully"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to clear data"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear data", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to clear data: ${e.message}"
                )
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
    
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            successMessage = null,
            statusMessage = null
        )
    }
    
    fun getRedactedApiKey(): String {
        val apiKey = _uiState.value.savedApiKey
        return if (apiKey != null && apiKey.length > 8) {
            "${apiKey.take(4)}${"*".repeat(apiKey.length - 8)}${apiKey.takeLast(4)}"
        } else {
            apiKey ?: ""
        }
    }
    
    companion object {
        private const val TAG = "AiInsightsViewModel"
    }
}

data class AiInsightsUiState(
    val isLoading: Boolean = false,
    val hasConfiguredKey: Boolean = false,
    val savedApiKey: String? = null,
    val savedCustomEndpoint: String? = null,
    val isUsingEncryptedStorage: Boolean = false,
    val insights: AiInsights? = null,
    val lastUpdatedTime: String = "Never",
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val statusMessage: String? = null
)
