package com.dheeraj.smartexpenses.sms

import android.content.Context
import android.util.Log

/**
 * Test utility for the multi-task SMS classifier
 */
object SmsClassifierTest {
    private const val TAG = "SmsClassifierTest"
    
    /**
     * Test the classifier with sample SMS messages
     */
    fun testClassifier(context: Context): String {
        val classifier = SmsMultiTaskClassifier(context)
        
        if (!classifier.loadModel("transaction_model.tflite")) {
            return "❌ Failed to load transaction model"
        }
        
        val testCases = listOf(
            "Rs.5000 debited from A/c XXXX1234 for UPI transaction to Amazon",
            "Dear Customer, Rs.2500 credited to A/c XXXX5678 for Salary",
            "Your OTP for Net Banking is 123456. Valid for 10 minutes.",
            "Rs.1500 debited from A/c XXXX9012 for IMPS transfer to John Doe",
            "Rs.3000 credited to A/c XXXX3456 via NEFT from Company Ltd"
        )
        
        val results = StringBuilder()
        results.append("🧪 Multi-Task SMS Classifier Test Results\n")
        results.append("=====================================\n\n")
        
        testCases.forEachIndexed { index, sms ->
            results.append("Test ${index + 1}: \"$sms\"\n")
            
            try {
                val analysis = classifier.analyzeSms(sms)
                if (analysis != null) {
                    results.append("✅ Analysis: Transactional=${analysis.isTransactional}, ")
                    results.append("Confidence=${"%.3f".format(analysis.confidence)}, ")
                    results.append("Direction=${analysis.direction}, ")
                    results.append("Merchant=${analysis.merchant ?: "N/A"}, ")
                    results.append("Amount=${analysis.amount ?: "N/A"}, ")
                    results.append("Type=${analysis.transactionType ?: "N/A"}\n")
                } else {
                    results.append("❌ Analysis failed\n")
                }
            } catch (e: Exception) {
                results.append("❌ Error: ${e.message}\n")
            }
            
            results.append("\n")
        }
        
        classifier.close()
        return results.toString()
    }
    
    /**
     * Test specific SMS analysis
     */
    fun testSpecificSms(context: Context, smsText: String): String {
        val classifier = SmsMultiTaskClassifier(context)
        
        if (!classifier.loadModel("transaction_model.tflite")) {
            return "❌ Failed to load transaction model"
        }
        
        return try {
            val analysis = classifier.analyzeSms(smsText)
            if (analysis != null) {
                """
                📱 SMS Analysis Result
                =====================
                SMS: "$smsText"
                
                ✅ Transactional: ${analysis.isTransactional}
                📊 Confidence: ${"%.3f".format(analysis.confidence)}
                💰 Direction: ${analysis.direction}
                🏪 Merchant: ${analysis.merchant ?: "N/A"}
                💵 Amount: ${analysis.amount ?: "N/A"}
                🔄 Type: ${analysis.transactionType ?: "N/A"}
                🎯 Direction Confidence: ${"%.3f".format(analysis.directionConfidence)}
                """.trimIndent()
            } else {
                "❌ Analysis failed for SMS: $smsText"
            }
        } catch (e: Exception) {
            "❌ Error analyzing SMS: ${e.message}"
        } finally {
            classifier.close()
        }
    }
}
