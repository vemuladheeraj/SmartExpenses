package com.dheeraj.smartexpenses.sms

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.URL
import java.net.HttpURLConnection
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.Context.CONNECTIVITY_SERVICE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext

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
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = ModelDownloadUiState.Downloading(0f)
                
                // Check network connectivity first
                if (!isNetworkAvailable(context)) {
                    _uiState.value = ModelDownloadUiState.Error(
                        "No internet connection. Please check your network and try again.",
                        isRetryable = true
                    )
                    return@launch
                }
                
                // Check storage space
                val availableSpace = getAvailableStorageSpace(context)
                if (availableSpace < ModelDownload.REQUIRED_SPACE_BYTES) {
                    _uiState.value = ModelDownloadUiState.Error(
                        "Insufficient storage space. Need at least ${ModelDownload.MODEL_SIZE_MB}MB free space.",
                        isRetryable = false
                    )
                    return@launch
                }
                
                // Create models directory if it doesn't exist
                val modelsDir = File(context.filesDir, "models")
                if (!modelsDir.exists()) {
                    modelsDir.mkdirs()
                }
                val outputFile = File(modelsDir, ModelDownload.MODEL_FILE_NAME)
                
                // Try download with retries
                var lastError: Exception? = null
                var retryCount = 0
                val maxRetries = 3
                
                while (retryCount <= maxRetries) {
                    try {
                        downloadWithProgress(ModelDownload.MODEL_URL, outputFile) { progress ->
                            _uiState.value = ModelDownloadUiState.Downloading(progress)
                        }
                        
                        // Verify downloaded file
                        if (outputFile.exists() && outputFile.length() >= ModelDownload.MODEL_SIZE_BYTES * 0.95) {
                            // Mark as completed
                            ModelDownloadHelper.markDownloadCompleted(context)
                            _uiState.value = ModelDownloadUiState.Success
                            return@launch
                        } else {
                            throw Exception("Downloaded file is incomplete or corrupted")
                        }
                        
                    } catch (e: Exception) {
                        lastError = e
                        retryCount++
                        
                        if (retryCount <= maxRetries) {
                            // Wait before retry with exponential backoff
                            val delayMs = (1000L * (1 shl (retryCount - 1))).coerceAtMost(10000L)
                            android.util.Log.w("ModelDownloadViewModel", "Download failed, retrying in ${delayMs}ms (attempt $retryCount/$maxRetries)")
                            delay(delayMs)
                        }
                    }
                }
                
                // All retries failed
                android.util.Log.e("ModelDownloadViewModel", "Download failed after $maxRetries retries", lastError)
                val errorMessage = when (lastError) {
                    is ConnectException -> "Connection failed after $maxRetries attempts. Please check your internet connection and try again."
                    is SocketTimeoutException -> "Download timed out after $maxRetries attempts. Please check your internet connection and try again."
                    is UnknownHostException -> "Cannot reach the download server after $maxRetries attempts. Please check your internet connection."
                    is java.net.ProtocolException -> "Network protocol error after $maxRetries attempts. Please try again."
                    is java.io.IOException -> "Network error after $maxRetries attempts: ${lastError.localizedMessage ?: "Please check your connection"}"
                    is CancellationException -> "Download was cancelled."
                    else -> "Download failed after $maxRetries attempts: ${lastError?.localizedMessage ?: "Unknown error occurred"}"
                }
                
                _uiState.value = ModelDownloadUiState.Error(errorMessage, isRetryable = true)
                
            } catch (e: Exception) {
                android.util.Log.e("ModelDownloadViewModel", "Download error", e)
                val errorMessage = when (e) {
                    is ConnectException -> "Connection failed. Please check your internet connection and try again."
                    is SocketTimeoutException -> "Download timed out. Please check your internet connection and try again."
                    is UnknownHostException -> "Cannot reach the download server. Please check your internet connection."
                    is java.net.ProtocolException -> "Network protocol error. Please try again."
                    is java.io.IOException -> "Network error: ${e.localizedMessage ?: "Please check your connection"}"
                    is android.os.NetworkOnMainThreadException -> "Network operation error. Please try again."
                    is CancellationException -> "Download was cancelled."
                    else -> "Download failed: ${e.localizedMessage ?: "Unknown error occurred"}"
                }
                
                _uiState.value = ModelDownloadUiState.Error(errorMessage, isRetryable = true)
            }
        }
    }
    

    
    private suspend fun downloadWithProgress(
        urlString: String,
        outputFile: File,
        onProgress: (Float) -> Unit
    ) {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 60000 // 60 seconds connect timeout
        connection.readTimeout = 300000 // 5 minutes read timeout for large file
        connection.setRequestProperty("User-Agent", "SmartExpenses/1.0")
        connection.setRequestProperty("Accept", "*/*")
        connection.setRequestProperty("Connection", "keep-alive")
        
        // Check if we can resume download from where we left off
        // This handles cases where download was interrupted (network issues, app backgrounded, etc.)
        val existingSize = if (outputFile.exists()) outputFile.length() else 0L
        if (existingSize > 0) {
            connection.setRequestProperty("Range", "bytes=$existingSize-")
            android.util.Log.d("ModelDownloadViewModel", "Resuming download from byte $existingSize")
        }
        
        try {
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                val errorStream = connection.errorStream
                val errorMessage = errorStream?.bufferedReader()?.readText() ?: "HTTP Error: $responseCode"
                throw Exception("Download failed: $errorMessage")
            }
            
            val contentLength = connection.contentLength.toLong()
            val totalSize = if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                existingSize + contentLength
            } else {
                contentLength
            }
            
            var downloadedBytes = existingSize
            
            // Use RandomAccessFile for resumable downloads
            val randomAccessFile = RandomAccessFile(outputFile, "rw")
            if (existingSize > 0) {
                randomAccessFile.seek(existingSize)
            }
            
            try {
                connection.inputStream.use { input ->
                    val buffer = ByteArray(16384) // Larger buffer for better performance
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        randomAccessFile.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        
                        val progress = if (totalSize > 0) {
                            (downloadedBytes.toFloat() / totalSize).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        onProgress(progress)
                        
                        // Progress update
                    }
                }
                
                // Verify file size
                if (downloadedBytes != totalSize && totalSize > 0) {
                    throw Exception("Download incomplete: $downloadedBytes/$totalSize bytes")
                }
                
            } finally {
                randomAccessFile.close()
            }
        } finally {
            connection.disconnect()
        }
    }
    
    private fun getAvailableStorageSpace(context: Context): Long {
        val filesDir = context.filesDir
        return filesDir.freeSpace
    }
    
    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            else -> false
        }
    }
    
    fun resetState() {
        _uiState.value = ModelDownloadUiState.Idle
    }
    
    fun forceRedownload(context: Context) {
        // Reset state and start fresh download
        resetState()
        downloadModel(context)
    }
}

