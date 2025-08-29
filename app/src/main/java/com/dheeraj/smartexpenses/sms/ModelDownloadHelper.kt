package com.dheeraj.smartexpenses.sms

import android.content.Context
import android.content.SharedPreferences

object ModelDownloadHelper {
    private const val PREFS_NAME = "model_download_prefs"
    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun shouldShowDownloadDialog(context: Context): Boolean = false
    fun markDownloadCompleted(context: Context) { }
    fun isDownloadCompleted(context: Context): Boolean = true
    fun isModelDownloaded(context: Context): Boolean = true
    fun getModelPath(context: Context): String? = null
    fun clearDownloadState(context: Context) { }
    fun resetDownloadState(context: Context) { }
    fun forceRedownload(context: Context) { }
    fun deleteModelFile(context: Context): Boolean = false
    fun getModelFileInfo(context: Context): String = "LLM removed; no downloads required"
    fun testAiModel(context: Context): String = "LLM removed"
}
