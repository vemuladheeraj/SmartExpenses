package com.dheeraj.smartexpenses.sms


import android.content.Context
import android.provider.Telephony
import java.util.Calendar

object SmsImporter {
    fun importRecent(context: Context, monthsBack: Long = 6, onEach: (String, String, Long) -> Unit) {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -monthsBack.toInt())
        val since = calendar.timeInMillis
        val uri = Telephony.Sms.Inbox.CONTENT_URI
        val proj = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        val sel = "${Telephony.Sms.DATE} >= ?"
        val args = arrayOf(since.toString())
        context.contentResolver.query(uri, proj, sel, args, "${Telephony.Sms.DATE} DESC")?.use { c ->
            val iAddr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val iBody = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val iDate = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                onEach(c.getString(iAddr) ?: "", c.getString(iBody) ?: "", c.getLong(iDate))
            }
        }
    }
}
