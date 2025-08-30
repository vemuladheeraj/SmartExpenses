package com.dheeraj.smartexpenses.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dheeraj.smartexpenses.data.AppDb
import com.dheeraj.smartexpenses.data.Budget
import com.dheeraj.smartexpenses.data.BudgetAnalysis
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

data class BudgetUiState(
    val budgetAnalysis: List<BudgetAnalysis> = emptyList(),
    val totalBudget: Double = 0.0,
    val totalSpent: Double = 0.0,
    val totalRemaining: Double = 0.0,
    val showAddBudgetDialog: Boolean = false,
    val editingBudget: Budget? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class BudgetViewModel(app: Application) : AndroidViewModel(app) {
    
    private val budgetDao = AppDb.get(getApplication()).budgetDao()
    
    private val _uiState = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()
    
    init {
        loadBudgetAnalysis()
    }
    
    private fun loadBudgetAnalysis() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                val calendar = Calendar.getInstance()
                val startOfMonth = calendar.apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                
                val endOfMonth = calendar.apply {
                    set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis
                
                budgetDao.getBudgetAnalysis(startOfMonth, endOfMonth)
                    .collect { analysis ->
                        val totalBudget = analysis.sumOf { it.monthlyLimitAmount }
                        val totalSpent = analysis.sumOf { it.spentAmountValue }
                        val totalRemaining = totalBudget - totalSpent
                        
                        _uiState.update { 
                            it.copy(
                                budgetAnalysis = analysis,
                                totalBudget = totalBudget,
                                totalSpent = totalSpent,
                                totalRemaining = totalRemaining,
                                isLoading = false,
                                error = null
                            )
                        }
                    }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load budget analysis"
                    )
                }
            }
        }
    }
    
    fun showAddBudgetDialog() {
        _uiState.update { 
            it.copy(
                showAddBudgetDialog = true,
                editingBudget = null
            )
        }
    }
    
    fun hideAddBudgetDialog() {
        _uiState.update { 
            it.copy(
                showAddBudgetDialog = false,
                editingBudget = null
            )
        }
    }
    
    fun editBudget(category: String) {
        viewModelScope.launch {
            try {
                val budget = budgetDao.getBudgetByCategory(category)
                _uiState.update { 
                    it.copy(
                        showAddBudgetDialog = true,
                        editingBudget = budget
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = e.message ?: "Failed to load budget")
                }
            }
        }
    }
    
    fun saveBudget(category: String, amount: Double) {
        viewModelScope.launch {
            try {
                val budget = Budget(
                    category = category,
                    monthlyLimit = (amount * 100).toLong(),
                    updatedAt = System.currentTimeMillis()
                )
                
                budgetDao.insertBudget(budget)
                hideAddBudgetDialog()
                loadBudgetAnalysis() // Refresh the analysis
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = e.message ?: "Failed to save budget")
                }
            }
        }
    }
    
    fun deleteBudget(category: String) {
        viewModelScope.launch {
            try {
                budgetDao.deleteBudgetByCategory(category)
                loadBudgetAnalysis() // Refresh the analysis
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = e.message ?: "Failed to delete budget")
                }
            }
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
