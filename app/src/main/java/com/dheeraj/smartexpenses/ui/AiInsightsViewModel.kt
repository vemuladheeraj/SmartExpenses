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
import java.text.NumberFormat
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
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load initial state", e)
                addMessage(
                    ChatMessage(
                        content = TextMessage("Sorry, I couldn't load my configuration. Please check your API settings."),
                        isUser = false
                    )
                )
            }
        }
    }
    
    fun sendMessage(userMessage: String) {
        if (userMessage.trim().isEmpty()) return
        
        // Add user message to chat
        addMessage(
            ChatMessage(
                content = TextMessage(userMessage),
                isUser = true
            )
        )
        
        // Check if AI is configured
        if (!_uiState.value.hasConfiguredKey) {
            addMessage(
                ChatMessage(
                    content = TextMessage("I need to be configured first. Please set up your AI API key in the settings to start helping you with financial insights."),
                    isUser = false
                )
            )
            return
        }
        
        // Set loading state
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        viewModelScope.launch {
            try {
                val response = processUserMessage(userMessage)
                addMessage(response)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process message", e)
                addMessage(
                    ChatMessage(
                        content = TextMessage("Sorry, I encountered an error while processing your request. Please try again."),
                        isUser = false
                    )
                )
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
    
    private suspend fun processUserMessage(message: String): ChatMessage {
        val lowerMessage = message.lowercase()
        
        return when {
            lowerMessage.contains("spending") || lowerMessage.contains("spend") || lowerMessage.contains("expense") -> {
                processSpendingAnalysis()
            }
            lowerMessage.contains("budget") || lowerMessage.contains("limit") -> {
                processBudgetAdvice()
            }
            lowerMessage.contains("insight") || lowerMessage.contains("analysis") || lowerMessage.contains("summary") -> {
                processGeneralInsights()
            }
            lowerMessage.contains("unusual") || lowerMessage.contains("anomaly") || lowerMessage.contains("strange") -> {
                processUnusualSpending()
            }
            lowerMessage.contains("category") || lowerMessage.contains("categorize") -> {
                processCategoryAnalysis()
            }
            lowerMessage.contains("goal") || lowerMessage.contains("target") -> {
                processFinancialGoals()
            }
            lowerMessage.contains("save") || lowerMessage.contains("saving") -> {
                processSavingAdvice()
            }
            else -> {
                // Default response with suggestions
                ChatMessage(
                    content = TextMessage(
                        "I can help you with:\n" +
                        "• Spending analysis and patterns\n" +
                        "• Budget recommendations\n" +
                        "• Financial insights and summaries\n" +
                        "• Unusual transaction detection\n" +
                        "• Category analysis\n" +
                        "• Financial goal setting\n" +
                        "• Saving advice\n\n" +
                        "What would you like to know about your finances?"
                    ),
                    isUser = false
                )
            }
        }
    }
    
    private suspend fun processSpendingAnalysis(): ChatMessage {
        val insights = repository.getInsights()
        return if (insights != null) {
            val analysis = SpendingAnalysis(
                insights = listOf(
                    "Your total spending is ₹${NumberFormat.getNumberInstance().format(insights.kpis.totalSpendInr)}",
                    "You made ${insights.kpis.debitCount} debit transactions",
                    "Your largest transaction was ₹${NumberFormat.getNumberInstance().format(insights.kpis.largestTxnAmount)}",
                    if (insights.kpis.unusualSpendFlag) "⚠️ Unusual spending patterns detected" else "✅ Spending patterns look normal"
                ),
                recommendations = listOf(
                    "Consider setting up a monthly budget",
                    "Review your largest transactions for potential savings",
                    "Track your spending categories to identify areas for reduction"
                )
            )
            ChatMessage(
                content = SpendingAnalysisMessage(analysis),
                isUser = false
            )
        } else {
            ChatMessage(
                content = TextMessage("I couldn't analyze your spending right now. Please try again later."),
                isUser = false
            )
        }
    }
    
    private suspend fun processBudgetAdvice(): ChatMessage {
        val insights = repository.getInsights()
        return if (insights != null) {
            val avgSpending = insights.kpis.totalSpendInr / insights.kpis.debitCount
            val suggestedBudget = avgSpending * 1.2 // 20% buffer
            
            ChatMessage(
                content = TextMessage(
                    "Based on your current spending patterns:\n\n" +
                    "💰 Average transaction: ₹${NumberFormat.getNumberInstance().format(avgSpending)}\n" +
                    "📊 Suggested monthly budget: ₹${NumberFormat.getNumberInstance().format(suggestedBudget)}\n\n" +
                    "💡 Tips:\n" +
                    "• Set aside 20% for savings\n" +
                    "• Use the 50/30/20 rule (50% needs, 30% wants, 20% savings)\n" +
                    "• Track your spending daily to stay on track"
                ),
                isUser = false
            )
        } else {
            ChatMessage(
                content = TextMessage("I need to analyze your spending first to provide budget advice. Please ask me about your spending patterns."),
                isUser = false
            )
        }
    }
    
    private suspend fun processGeneralInsights(): ChatMessage {
        val insights = repository.getInsights()
        return if (insights != null) {
            ChatMessage(
                content = InsightsMessage(insights),
                isUser = false
            )
        } else {
            ChatMessage(
                content = TextMessage("I'm analyzing your transactions to provide insights. This may take a moment..."),
                isUser = false
            )
        }
    }
    
    private suspend fun processUnusualSpending(): ChatMessage {
        val insights = repository.getInsights()
        return if (insights != null) {
            if (insights.kpis.unusualSpendFlag) {
                val largeTransactions = insights.largeTxns.take(3)
                val transactionList = largeTransactions.joinToString("\n") { 
                    "• ₹${NumberFormat.getNumberInstance().format(it.amount)} on ${it.date}"
                }
                
                ChatMessage(
                    content = TextMessage(
                        "🚨 Unusual spending detected!\n\n" +
                        "Large transactions:\n$transactionList\n\n" +
                        "💡 Recommendations:\n" +
                        "• Review these transactions for accuracy\n" +
                        "• Consider if any were impulse purchases\n" +
                        "• Set up spending alerts for large amounts"
                    ),
                    isUser = false
                )
            } else {
                ChatMessage(
                    content = TextMessage("✅ Your spending patterns look normal! No unusual transactions detected."),
                    isUser = false
                )
            }
        } else {
            ChatMessage(
                content = TextMessage("I need to analyze your transactions first to detect unusual spending."),
                isUser = false
            )
        }
    }
    
    private suspend fun processCategoryAnalysis(): ChatMessage {
        val insights = repository.getInsights()
        return if (insights != null && insights.breakdowns.byCategory.isNotEmpty()) {
            val categoryList = insights.breakdowns.byCategory.joinToString("\n") { 
                "• ${it.name ?: "Unknown"}: ₹${NumberFormat.getNumberInstance().format(it.amount)}"
            }
            
            ChatMessage(
                content = TextMessage(
                    "📊 Your spending by category:\n\n$categoryList\n\n" +
                    "💡 Tips:\n" +
                    "• Focus on reducing your highest spending category\n" +
                    "• Set category-specific budgets\n" +
                    "• Review discretionary spending regularly"
                ),
                isUser = false
            )
        } else {
            ChatMessage(
                content = TextMessage("I don't have enough category data to analyze. Try asking me about your general spending patterns first."),
                isUser = false
            )
        }
    }
    
    private suspend fun processFinancialGoals(): ChatMessage {
        return ChatMessage(
            content = TextMessage(
                "🎯 Financial Goal Setting Tips:\n\n" +
                "1. **Emergency Fund**: Save 3-6 months of expenses\n" +
                "2. **Short-term Goals**: Vacation, gadgets (1-2 years)\n" +
                "3. **Medium-term Goals**: Down payment, education (3-5 years)\n" +
                "4. **Long-term Goals**: Retirement, wealth building (10+ years)\n\n" +
                "💡 Start with one goal and build momentum!\n" +
                "Would you like me to help you set up a specific goal?"
            ),
            isUser = false
        )
    }
    
    private suspend fun processSavingAdvice(): ChatMessage {
        val insights = repository.getInsights()
        return if (insights != null) {
            val totalSpending = insights.kpis.totalSpendInr
            val suggestedSaving = totalSpending * 0.2 // 20% of spending
            
            ChatMessage(
                content = TextMessage(
                    "💰 Saving Advice:\n\n" +
                    "Based on your current spending of ₹${NumberFormat.getNumberInstance().format(totalSpending)}:\n" +
                    "• Aim to save: ₹${NumberFormat.getNumberInstance().format(suggestedSaving)} per month\n" +
                    "• Emergency fund target: ₹${NumberFormat.getNumberInstance().format(totalSpending * 3)}\n\n" +
                    "💡 Saving Strategies:\n" +
                    "• Pay yourself first (automate savings)\n" +
                    "• Use the envelope method\n" +
                    "• Cut one expense category by 10%\n" +
                    "• Save windfalls (bonuses, tax refunds)"
                ),
                isUser = false
            )
        } else {
            ChatMessage(
                content = TextMessage(
                    "💰 General Saving Tips:\n\n" +
                    "• Save 20% of your income\n" +
                    "• Build an emergency fund first\n" +
                    "• Automate your savings\n" +
                    "• Track your progress\n\n" +
                    "Ask me about your spending patterns for personalized advice!"
                ),
                isUser = false
            )
        }
    }
    
    private fun addMessage(message: ChatMessage) {
        val currentMessages = _uiState.value.messages.toMutableList()
        currentMessages.add(message)
        _uiState.value = _uiState.value.copy(messages = currentMessages)
    }
    
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(messages = emptyList())
    }
    
    fun saveApiKey(apiKey: String) {
        viewModelScope.launch {
            try {
                val result = repository.saveApiKey(apiKey)
                
                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        hasConfiguredKey = true,
                        savedApiKey = apiKey,
                        savedCustomEndpoint = null
                    )
                    
                    addMessage(
                        ChatMessage(
                            content = TextMessage("Great! I'm now configured and ready to help you with your finances. What would you like to know?"),
                            isUser = false
                        )
                    )
                } else {
                    addMessage(
                        ChatMessage(
                            content = TextMessage("Failed to save API key: ${result.exceptionOrNull()?.message}"),
                            isUser = false
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save API key", e)
                addMessage(
                    ChatMessage(
                        content = TextMessage("Error saving API key: ${e.message}"),
                        isUser = false
                    )
                )
            }
        }
    }
    
    fun saveCustomEndpoint(endpoint: String) {
        viewModelScope.launch {
            try {
                val result = repository.saveCustomEndpoint(endpoint)
                
                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        hasConfiguredKey = true,
                        savedApiKey = null,
                        savedCustomEndpoint = endpoint
                    )
                    
                    addMessage(
                        ChatMessage(
                            content = TextMessage("Great! I'm now configured with your custom endpoint and ready to help you with your finances. What would you like to know?"),
                            isUser = false
                        )
                    )
                } else {
                    addMessage(
                        ChatMessage(
                            content = TextMessage("Failed to save custom endpoint: ${result.exceptionOrNull()?.message}"),
                            isUser = false
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save custom endpoint", e)
                addMessage(
                    ChatMessage(
                        content = TextMessage("Error saving custom endpoint: ${e.message}"),
                        isUser = false
                    )
                )
            }
        }
    }
    
    fun clearAllData() {
        viewModelScope.launch {
            try {
                repository.clearAllData()
                _uiState.value = _uiState.value.copy(
                    hasConfiguredKey = false,
                    savedApiKey = null,
                    savedCustomEndpoint = null,
                    messages = emptyList()
                )
                
                addMessage(
                    ChatMessage(
                        content = TextMessage("All data cleared. Please configure your AI settings again to continue."),
                        isUser = false
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear data", e)
                addMessage(
                    ChatMessage(
                        content = TextMessage("Error clearing data: ${e.message}"),
                        isUser = false
                    )
                )
            }
        }
    }
    
    companion object {
        private const val TAG = "AiInsightsViewModel"
    }
}

data class AiInsightsUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val hasConfiguredKey: Boolean = false,
    val savedApiKey: String? = null,
    val savedCustomEndpoint: String? = null,
    val isUsingEncryptedStorage: Boolean = false
)



