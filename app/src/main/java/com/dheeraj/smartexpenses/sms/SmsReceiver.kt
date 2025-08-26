package com.dheeraj.smartexpenses.sms


import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.dheeraj.smartexpenses.data.AppDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION != intent.action) return
        
        Log.d("SmsReceiver", "📱 === INCOMING SMS RECEIVED ===")
        
        val dao = AppDb.get(context).txnDao()
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        
        if (parts.isEmpty()) {
            Log.d("SmsReceiver", "❌ No SMS parts found in intent")
            return
        }
        
        val sender = parts.first().displayOriginatingAddress ?: "UNKNOWN"
        val body = parts.joinToString(separator = "") { it.displayMessageBody ?: "" }
        val ts = parts.minOf { it.timestampMillis }
        
        // ALWAYS LOG THE COMPLETE SMS FIRST - regardless of parsing result
        Log.d("SmsReceiver", "📨 COMPLETE SMS CONTENT:")
        Log.d("SmsReceiver", "  - Sender: $sender")
        Log.d("SmsReceiver", "  - Full Body: $body")
        Log.d("SmsReceiver", "  - Timestamp: $ts")
        Log.d("SmsReceiver", "  - Parts: ${parts.size}")
        
        // Log multi-part SMS details if applicable
        if (parts.size > 1) {
            Log.d("SmsReceiver", "  - Multi-part SMS detected:")
            parts.forEachIndexed { index, part ->
                Log.d("SmsReceiver", "    Part ${index + 1}: ${part.displayMessageBody}")
            }
        }
        
        Log.d("SmsReceiver", "🔍 Attempting to parse SMS...")
        
        val parsed = SmsParser.parse(sender, body, ts)
        
        if (parsed != null) {
            Log.d("SmsReceiver", "✅ SMS parsed successfully!")
            Log.d("SmsReceiver", "  - Type: ${parsed.type}")
            Log.d("SmsReceiver", "  - Amount: ${parsed.amount}")
            Log.d("SmsReceiver", "  - Channel: ${parsed.channel}")
            Log.d("SmsReceiver", "  - Merchant: ${parsed.merchant}")
            Log.d("SmsReceiver", "  - Bank: ${parsed.bank}")
            
            CoroutineScope(Dispatchers.IO).launch { 
                try {
                    dao.insert(parsed)
                    Log.d("SmsReceiver", "💾 Transaction saved to database successfully")
                } catch (e: Exception) {
                    Log.e("SmsReceiver", "❌ Failed to save transaction to database", e)
                }
            }
        } else {
            Log.d("SmsReceiver", "❌ SMS parsing failed - message rejected")
            Log.d("SmsReceiver", "  - This SMS was not a valid transaction")
            Log.d("SmsReceiver", "  - It may be promotional, balance update, or other non-transaction content")
        }
        
        Log.d("SmsReceiver", "📱 === END SMS PROCESSING ===")
    }
}
