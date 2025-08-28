package com.dheeraj.smartexpenses.sms

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import com.dheeraj.smartexpenses.sms.AiSmsExtractorProvider

object ModelDownloadHelper {
    
    private const val PREFS_NAME = "model_download_prefs"
    private const val KEY_DOWNLOAD_OFFERED = "download_offered"
    private const val KEY_DOWNLOAD_COMPLETED = "download_completed"
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun shouldShowDownloadDialog(context: Context): Boolean {
        // Check both file existence and preference state
        val modelExists = isModelDownloaded(context)
        val downloadCompleted = isDownloadCompleted(context)
        
        android.util.Log.d("ModelDownloadHelper", "shouldShowDownloadDialog: modelExists=$modelExists, downloadCompleted=$downloadCompleted")
        
        // Only show if model doesn't exist AND download hasn't been marked as completed
        val shouldShow = !modelExists && !downloadCompleted
        android.util.Log.d("ModelDownloadHelper", "shouldShowDownloadDialog result: $shouldShow")
        return shouldShow
    }
    
    fun markDownloadCompleted(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_DOWNLOAD_COMPLETED, true).apply()
        android.util.Log.d("ModelDownloadHelper", "Download marked as completed")
    }
    
    fun isDownloadCompleted(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DOWNLOAD_COMPLETED, false)
    }
    
    fun isModelDownloaded(context: Context): Boolean {
        val modelFile = File(context.filesDir, "models/${ModelDownload.MODEL_FILE_NAME}")
        val exists = modelFile.exists()
        val correctSize = if (exists) modelFile.length() == ModelDownload.MODEL_SIZE_BYTES else false
        
        android.util.Log.d("ModelDownloadHelper", "Model file check: exists=$exists, size=${if (exists) modelFile.length() else 0}, expected=${ModelDownload.MODEL_SIZE_BYTES}, path=${modelFile.absolutePath}")
        
        return exists && correctSize
    }
    
    fun getModelPath(context: Context): String? {
        val modelFile = File(context.filesDir, "models/${ModelDownload.MODEL_FILE_NAME}")
        return if (modelFile.exists()) modelFile.absolutePath else null
    }
    
    fun clearDownloadState(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
    
    fun resetDownloadState(context: Context) {
        // Clear preferences but keep the downloaded file
        getPrefs(context).edit().putBoolean(KEY_DOWNLOAD_COMPLETED, false).apply()
    }
    
    fun forceRedownload(context: Context) {
        // Delete the model file and clear preferences to force a fresh download
        deleteModelFile(context)
        clearDownloadState(context)
        android.util.Log.d("ModelDownloadHelper", "Forced redownload - deleted model file and cleared preferences")
    }
    
    fun deleteModelFile(context: Context): Boolean {
        val modelFile = File(context.filesDir, "models/${ModelDownload.MODEL_FILE_NAME}")
        return if (modelFile.exists()) {
            modelFile.delete()
        } else false
    }
    
    fun getModelFileInfo(context: Context): String {
        val modelFile = File(context.filesDir, "models/${ModelDownload.MODEL_FILE_NAME}")
        val modelsDir = File(context.filesDir, "models")
        val aiExtractorAvailable = AiSmsExtractorProvider.instance != null
        
        return """
            Models directory exists: ${modelsDir.exists()}
            Model file exists: ${modelFile.exists()}
            Model file size: ${if (modelFile.exists()) modelFile.length() else 0} bytes
            Expected size: ${ModelDownload.MODEL_SIZE_BYTES} bytes
            Model file path: ${modelFile.absolutePath}
            Models directory path: ${modelsDir.absolutePath}
            AI Extractor available: $aiExtractorAvailable
            AI Extractor class: ${AiSmsExtractorProvider.instance?.javaClass?.simpleName ?: "null"}
            AI Model Status: ${if (isModelDownloaded(context)) "Downloaded" else "Not Downloaded"}
            AI Integration Status: ${if (aiExtractorAvailable) "Available (but needs MediaPipe integration)" else "Not Available"}
        """.trimIndent()
    }
    
    fun testAiModel(context: Context): String {
        val aiExtractor = AiSmsExtractorProvider.instance
        if (aiExtractor == null) {
            return "AI Extractor not available"
        }
        
        return try {
            // Test with a simple SMS to see if AI parsing works
            val testSms = "Your account has been credited with Rs.1000/- via UPI from Test User"
            val result = aiExtractor.extract("TESTBANK", testSms, System.currentTimeMillis())
            
            if (result != null) {
                "AI Test: SUCCESS - ${result.isTransaction}, ${result.type}, ₹${result.amountMinor?.div(100)}"
            } else {
                "AI Test: FAILED - No result returned"
            }
        } catch (e: Exception) {
            "AI Test: ERROR - ${e.message}"
        }
    }
}
