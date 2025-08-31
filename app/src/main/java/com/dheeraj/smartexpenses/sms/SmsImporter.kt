package com.dheeraj.smartexpenses.sms

// LLM download flow removed; importer unchanged.

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Calendar

object SmsImporter {
    private fun hasSmsPermission(context: Context): Boolean {
        val readGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        return readGranted
    }

    fun importRecent(context: Context, monthsBack: Long = 6, onEach: (String, String, Long) -> Unit) {
        if (!hasSmsPermission(context)) {
            Log.w("SmsImporter", "READ_SMS permission not granted. Skipping importRecent().")
            return
        }
        try {
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
        } catch (se: SecurityException) {
            Log.e("SmsImporter", "SecurityException while reading SMS. Permission may be revoked.", se)
        } catch (t: Throwable) {
            Log.e("SmsImporter", "Unexpected error while importing recent SMS", t)
        }
    }

    fun getSmsCount(context: Context, monthsBack: Long = 6): Int {
        if (!hasSmsPermission(context)) {
            Log.w("SmsImporter", "READ_SMS permission not granted. Returning count=0.")
            return 0
        }
        return try {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.MONTH, -monthsBack.toInt())
            val since = calendar.timeInMillis
            val uri = Telephony.Sms.Inbox.CONTENT_URI
            val sel = "${Telephony.Sms.DATE} >= ?"
            val args = arrayOf(since.toString())
            
            context.contentResolver.query(uri, arrayOf("COUNT(*)"), sel, args, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getInt(0)
                } else 0
            } ?: 0
        } catch (se: SecurityException) {
            Log.e("SmsImporter", "SecurityException while counting SMS.", se)
            0
        } catch (t: Throwable) {
            Log.e("SmsImporter", "Unexpected error while counting SMS", t)
            0
        }
    }

    fun importWithProgress(
        context: Context, 
        monthsBack: Long = 6, 
        onProgress: (Int, Int) -> Unit,
        onEach: (String, String, Long) -> Unit
    ) {
        if (!hasSmsPermission(context)) {
            Log.w("SmsImporter", "READ_SMS permission not granted. Skipping importWithProgress().")
            onProgress(0, 0)
            return
        }
        val totalSms = getSmsCount(context, monthsBack)
        var processedCount = 0
        
        try {
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
                    processedCount++
                    onProgress(processedCount, totalSms)
                }
            }
        } catch (se: SecurityException) {
            Log.e("SmsImporter", "SecurityException while importing SMS with progress.", se)
        } catch (t: Throwable) {
            Log.e("SmsImporter", "Unexpected error while importing SMS with progress", t)
        }
    }

    fun getSmsCountSince(context: Context, sinceTimestamp: Long): Int {
        if (!hasSmsPermission(context)) {
            Log.w("SmsImporter", "READ_SMS permission not granted. Returning count=0.")
            return 0
        }
        return try {
            val uri = Telephony.Sms.Inbox.CONTENT_URI
            val sel = "${Telephony.Sms.DATE} > ?"
            val args = arrayOf(sinceTimestamp.toString())
            
            context.contentResolver.query(uri, arrayOf("COUNT(*)"), sel, args, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getInt(0)
                } else 0
            } ?: 0
        } catch (se: SecurityException) {
            Log.e("SmsImporter", "SecurityException while counting SMS since timestamp.", se)
            0
        } catch (t: Throwable) {
            Log.e("SmsImporter", "Unexpected error while counting SMS since timestamp", t)
            0
        }
    }

    fun importSinceTimestamp(
        context: Context, 
        sinceTimestamp: Long,
        onProgress: (Int, Int) -> Unit,
        onEach: (String, String, Long) -> Unit
    ) {
        if (!hasSmsPermission(context)) {
            Log.w("SmsImporter", "READ_SMS permission not granted. Skipping importSinceTimestamp().")
            onProgress(0, 0)
            return
        }
        val totalSms = getSmsCountSince(context, sinceTimestamp)
        var processedCount = 0
        
        try {
            val uri = Telephony.Sms.Inbox.CONTENT_URI
            val proj = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
            val sel = "${Telephony.Sms.DATE} > ?"
            val args = arrayOf(sinceTimestamp.toString())
            
            context.contentResolver.query(uri, proj, sel, args, "${Telephony.Sms.DATE} DESC")?.use { c ->
                val iAddr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val iBody = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val iDate = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                
                while (c.moveToNext()) {
                    onEach(c.getString(iAddr) ?: "", c.getString(iBody) ?: "", c.getLong(iDate))
                    processedCount++
                    onProgress(processedCount, totalSms)
                }
            }
        } catch (se: SecurityException) {
            Log.e("SmsImporter", "SecurityException while importing SMS since timestamp.", se)
        } catch (t: Throwable) {
            Log.e("SmsImporter", "Unexpected error while importing SMS since timestamp", t)
        }
    }
}
