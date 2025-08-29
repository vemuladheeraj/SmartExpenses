package com.dheeraj.smartexpenses

import android.app.Application
import android.util.Log
import com.dheeraj.smartexpenses.sms.SmsParser
import com.dheeraj.smartexpenses.sms.SmartContextProvider

class SmartExpensesApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        try {
            SmartContextProvider.app = this
            Log.d("SmartExpensesApp", "Initializing multi-task SMS classifier...")
            
            // Test model loading directly with step-by-step logging
                    Log.d("SmartExpensesApp", "Step 1: Creating SmsClassifier instance...")
        val testClassifier = com.dheeraj.smartexpenses.sms.SmsClassifier(this)
        Log.d("SmartExpensesApp", "Step 2: SmsClassifier created successfully")
            
            Log.d("SmartExpensesApp", "Step 3: Calling loadModelWithFallback()...")
            val modelLoaded = testClassifier.loadModelWithFallback()
            Log.d("SmartExpensesApp", "Step 4: loadModelWithFallback() returned: $modelLoaded")
            
            SmsParser.init(this)
            Log.d("SmartExpensesApp", "Multi-task classifier initialized successfully")
        } catch (e: Exception) {
            Log.e("SmartExpensesApp", "Error initializing multi-task classifier: ${e.message}", e)
            Log.e("SmartExpensesApp", "Stack trace: ${e.stackTrace.take(10).joinToString("\n")}")
        }
    }
}
