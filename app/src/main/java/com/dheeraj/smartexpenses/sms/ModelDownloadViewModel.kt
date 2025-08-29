package com.dheeraj.smartexpenses.sms

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ModelDownloadUiState {
    object Idle : ModelDownloadUiState()
    data class Downloading(val progress: Float) : ModelDownloadUiState()
    object Success : ModelDownloadUiState()
    data class Error(val message: String, val isRetryable: Boolean = true) : ModelDownloadUiState()
}

class ModelDownloadViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow<ModelDownloadUiState>(ModelDownloadUiState.Idle)
    val uiState: StateFlow<ModelDownloadUiState> = _uiState.asStateFlow()
    
    fun downloadModel(context: Context) {
        viewModelScope.launch {
            _uiState.value = ModelDownloadUiState.Success
        }
    }
    
    fun resetState() { _uiState.value = ModelDownloadUiState.Idle }
    fun forceRedownload(context: Context) { downloadModel(context) }
}

