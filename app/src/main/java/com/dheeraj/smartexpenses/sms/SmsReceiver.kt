package com.dheeraj.smartexpenses.sms


import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.dheeraj.smartexpenses.data.AppDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION != intent.action) return
        val dao = AppDb.get(context).txnDao()
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        for (msg in messages) {
            val sender = msg.displayOriginatingAddress ?: continue
            val body = msg.displayMessageBody ?: continue
            val ts = msg.timestampMillis

            val parsed = SmsParser.parse(sender, body, ts) ?: continue
            CoroutineScope(Dispatchers.IO).launch { dao.insert(parsed) }
        }
    }
}
