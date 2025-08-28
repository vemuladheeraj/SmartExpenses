package com.dheeraj.smartexpenses

import android.app.Application
import android.util.Log
import com.dheeraj.smartexpenses.sms.AiSmsExtractorProvider
import com.dheeraj.smartexpenses.sms.MediaPipeAiSmsExtractor

class SmartExpensesApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        try {
            Log.d("SmartExpensesApp", "Initializing AI SMS extractor...")
            
            // Initialize the AI SMS extractor with comprehensive error handling
            val aiExtractor = MediaPipeAiSmsExtractor(this)
            
            // Test the extractor to ensure it's working
            val testResult = aiExtractor.testExtraction()
            Log.d("SmartExpensesApp", "AI extractor test result: $testResult")
            
            AiSmsExtractorProvider.instance = aiExtractor
            
            Log.d("SmartExpensesApp", "AI SMS extractor initialized successfully")
        } catch (e: OutOfMemoryError ) {
            Log.e("SmartExpensesApp", "Out of memory during AI initialization, falling back to regex-only mode", e)
            // Clear any partial resources and continue without AI
            System.gc()
        } catch (e: Exception) {
            Log.e("SmartExpensesApp", "Error initializing AI SMS extractor: ${e.message}", e)
            // Don't crash the app if AI initialization fails, continue with regex-only mode
        }
    }
}
