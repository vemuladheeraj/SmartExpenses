package com.dheeraj.smartexpenses.data

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class AiService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        coerceInputValues = true
    }
    
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    
    suspend fun fetchInsights(
        apiKey: String?,
        customEndpoint: String?,
        transactions: List<TransactionForAi>
    ): Result<AiInsights> {
        return try {
            when {
                apiKey != null && apiKey.startsWith("AIza") -> {
                    fetchFromGemini(apiKey, transactions)
                }
                customEndpoint != null && (customEndpoint.startsWith("http://") || customEndpoint.startsWith("https://")) -> {
                    fetchFromCustomEndpoint(customEndpoint, transactions)
                }
                else -> {
                    Result.failure(IllegalArgumentException("Invalid API key or endpoint"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching insights", e)
            Result.failure(e)
        }
    }
    
    private suspend fun fetchFromGemini(
        apiKey: String,
        transactions: List<TransactionForAi>
    ): Result<AiInsights> {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=${apiKey}"
        
        val prompt = buildGeminiPrompt(transactions)
        val requestBody = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(GeminiPart(text = prompt))
                )
            )
        )
        
        val request = Request.Builder()
            .url(url)
            .post(json.encodeToString(GeminiRequest.serializer(), requestBody).toRequestBody(jsonMediaType))
            .build()
        
        return executeWithRetry(request) { responseBody ->
            val geminiResponse = json.decodeFromString(GeminiResponse.serializer(), responseBody)
            val text = geminiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw IOException("No response content from Gemini")
            
            // Clean the response text to extract JSON
            val cleanedText = extractJsonFromResponse(text)
            Log.d(TAG, "Cleaned JSON response: $cleanedText")
            
            // Try to parse as direct JSON first, then as wrapped JSON
            try {
                json.decodeFromString(AiInsights.serializer(), cleanedText)
            } catch (e: Exception) {
                // Try wrapped format
                try {
                    val wrappedResponse = json.decodeFromString(CustomEndpointResponse.serializer(), cleanedText)
                    wrappedResponse.insights ?: throw IOException("Insights field is null in wrapped response")
                } catch (e2: Exception) {
                    // If both fail, log the actual response for debugging
                    Log.e(TAG, "Failed to parse Gemini response. Original text: $text")
                    Log.e(TAG, "Cleaned text: $cleanedText")
                    Log.e(TAG, "Direct parsing error: ${e.message}")
                    Log.e(TAG, "Wrapped parsing error: ${e2.message}")
                    throw e2
                }
            }
        }
    }
    
    private suspend fun fetchFromCustomEndpoint(
        endpoint: String,
        transactions: List<TransactionForAi>
    ): Result<AiInsights> {
        val requestBody = CustomEndpointRequest(
            transactions = transactions,
            instructions = buildCustomEndpointInstructions()
        )
        
        val request = Request.Builder()
            .url(endpoint)
            .post(json.encodeToString(CustomEndpointRequest.serializer(), requestBody).toRequestBody(jsonMediaType))
            .build()
        
        return executeWithRetry(request) { responseBody ->
            // Try direct format first, then wrapped format
            try {
                json.decodeFromString(AiInsights.serializer(), responseBody)
            } catch (e: Exception) {
                try {
                    val wrappedResponse = json.decodeFromString(CustomEndpointResponse.serializer(), responseBody)
                    wrappedResponse.insights ?: throw IOException("Insights field is null in wrapped response")
                } catch (e2: Exception) {
                    // If both fail, log the actual response for debugging
                    Log.e(TAG, "Failed to parse custom endpoint response. Response body: $responseBody")
                    Log.e(TAG, "Direct parsing error: ${e.message}")
                    Log.e(TAG, "Wrapped parsing error: ${e2.message}")
                    throw e2
                }
            }
        }
    }
    
    private suspend fun executeWithRetry(
        request: Request,
        parseResponse: (String) -> AiInsights
    ): Result<AiInsights> {
        var lastException: Exception? = null
        val delays = listOf(5000L, 10000L, 20000L) // 5s, 10s, 20s
        
        for (attempt in 0..delays.size) {
            try {
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    
                    // Check for Retry-After header
                    val retryAfter = response.header("Retry-After")?.toLongOrNull()
                    
                    if (response.code == 429 || response.code in 500..599) {
                        if (attempt < delays.size) {
                            val delay = retryAfter?.let { it * 1000 } ?: delays[attempt]
                            Log.w(TAG, "Retrying in ${delay}ms (attempt ${attempt + 1}/${delays.size + 1})")
                            delay(delay)
                            continue
                        }
                    }
                    
                    throw IOException("HTTP ${response.code}: $responseBody")
                }
                
                val responseBody = response.body?.string() ?: throw IOException("Empty response body")
                val insights = parseResponse(responseBody)
                return Result.success(insights)
                
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Request failed (attempt ${attempt + 1}/${delays.size + 1})", e)
                
                if (attempt < delays.size) {
                    delay(delays[attempt])
                }
            }
        }
        
        return Result.failure(lastException ?: IOException("All retry attempts failed"))
    }
    
    private fun buildGeminiPrompt(transactions: List<TransactionForAi>): String {
        val transactionsJson = json.encodeToString(
            ListSerializer(TransactionForAi.serializer()),
            transactions
        )
        
        return """
            You are a financial analysis AI. Analyze the transaction data and return ONLY a valid JSON object with this exact structure. Do not include any markdown, code fences, explanations, or additional text.

            Expected JSON structure:
            {
              "kpis": {
                "total_spend_inr": 0.0,
                "debit_count": 0,
                "credit_count": 0,
                "largest_txn_amount": 0.0,
                "largest_txn_merchant": null,
                "unusual_spend_flag": false
              },
              "breakdowns": {
                "by_category": [{"name": null, "amount": 0.0}],
                "by_rail": [{"name": "", "amount": 0.0}]
              },
              "large_txns": [{"date": "YYYY-MM-DD", "merchant": null, "amount": 0.0}],
              "recurring": [{"name": "", "day_of_month": 1, "amount": 0.0}],
              "notes": ""
            }

            Transaction data:
            $transactionsJson
            
            Analysis instructions:
            - Calculate total spend from debit transactions only
            - Count debit and credit transactions separately
            - Find the largest transaction amount and merchant (use null if merchant is unknown)
            - Flag unusual spending (transactions > 2x average transaction amount)
            - Group by category and payment rail (UPI/CARD/IMPS/NEFT/POS)
            - For categories, use null if category cannot be determined, otherwise use descriptive names like "Food", "Transport", "Shopping", etc.
            - Find large transactions (>₹1000)
            - Identify potential recurring payments (same merchant, similar amount, same day of month)
            - Provide 1-2 line insights about spending patterns
            - Ensure all numeric fields are numbers, not strings
            - Use null for missing merchant names or unknown values
            - Return ONLY the JSON object, no markdown, no code blocks, no explanations
        """.trimIndent()
    }
    
    private fun buildCustomEndpointInstructions(): String {
        return """
            Analyze the provided transaction data and return insights in the specified JSON format.
            Focus on spending patterns, categories, payment methods, and financial insights.
        """.trimIndent()
    }
    
    private fun extractJsonFromResponse(text: String): String {
        // Remove markdown code blocks
        var cleaned = text.trim()
        
        // Remove ```json and ``` markers
        cleaned = cleaned.replace(Regex("```json\\s*"), "")
        cleaned = cleaned.replace(Regex("```\\s*"), "")
        
        // Find the first { and last } to extract JSON
        val startIndex = cleaned.indexOf('{')
        val endIndex = cleaned.lastIndexOf('}')
        
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            cleaned = cleaned.substring(startIndex, endIndex + 1)
        }
        
        // Additional cleaning for common AI response artifacts
        cleaned = cleaned.replace(Regex("^[^{]*"), "") // Remove text before first {
        cleaned = cleaned.replace(Regex("[^}]*$"), "") // Remove text after last }
        
        return cleaned.trim()
    }
    
    companion object {
        private const val TAG = "AiService"
    }
}
