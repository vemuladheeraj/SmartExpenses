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
            Log.d("SmartExpensesApp", "Initializing regex-only SMS parser...")
            
            SmsParser.init(this)
            Log.d("SmartExpensesApp", "Regex-only SMS parser initialized successfully")
        } catch (e: Exception) {
            Log.e("SmartExpensesApp", "Error initializing SMS parser: ${e.message}", e)
            Log.e("SmartExpensesApp", "Stack trace: ${e.stackTrace.take(10).joinToString("\n")}")
        }
    }
}
