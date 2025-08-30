package com.dheeraj.smartexpenses.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.dheeraj.smartexpenses.data.Transaction
import com.dheeraj.smartexpenses.data.amount
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class ExportManager(private val context: Context) {
    
    companion object {
        const val AUTHORITY = "com.dheeraj.smartexpenses.fileprovider"
    }
    
    fun exportToCSV(
        transactions: List<Transaction>,
        fileName: String = "smartexpenses_${getCurrentDate()}.csv"
    ): Uri? {
        return try {
            val file = File(context.cacheDir, fileName)
            FileWriter(file).use { writer ->
                // Write CSV header
                writer.append("Date,Time,Amount,Currency,Type,Channel,Merchant,Account,Bank,Source\n")
                
                // Write transaction data
                transactions.forEach { transaction ->
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(transaction.ts))
                    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(transaction.ts))
                    
                    writer.append("$date,")
                    writer.append("$time,")
                    writer.append("${transaction.amount},")
                    writer.append("${transaction.currency},")
                    writer.append("${transaction.type},")
                    writer.append("${transaction.channel ?: ""},")
                    writer.append("\"${transaction.merchant ?: ""}\",")
                    writer.append("${transaction.accountTail ?: ""},")
                    writer.append("${transaction.bank ?: ""},")
                    writer.append("${transaction.source}\n")
                }
            }
            
            // Return content URI for sharing
            FileProvider.getUriForFile(context, AUTHORITY, file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun exportToPDF(
        transactions: List<Transaction>,
        fileName: String = "smartexpenses_${getCurrentDate()}.pdf"
    ): Uri? {
        return try {
            val file = File(context.cacheDir, fileName)
            
            // Create PDF content
            val pdfContent = buildString {
                appendLine("SmartExpenses Transaction Report")
                appendLine("Generated on: ${getCurrentDateTime()}")
                appendLine("Total Transactions: ${transactions.size}")
                appendLine()
                
                // Summary
                val totalCredit = transactions.filter { it.type == "CREDIT" }.sumOf { it.amount }
                val totalDebit = transactions.filter { it.type == "DEBIT" }.sumOf { it.amount }
                val balance = totalCredit - totalDebit
                
                appendLine("SUMMARY:")
                appendLine("Total Credits: ₹${String.format("%.2f", totalCredit)}")
                appendLine("Total Debits: ₹${String.format("%.2f", totalDebit)}")
                appendLine("Net Balance: ₹${String.format("%.2f", balance)}")
                appendLine()
                
                // Transactions by category
                val categoryMap = mutableMapOf<String, Double>()
                transactions.filter { it.type == "DEBIT" }.forEach { transaction ->
                    val category = getCategoryFromMerchant(transaction.merchant ?: "")
                    categoryMap[category] = categoryMap.getOrDefault(category, 0.0) + transaction.amount
                }
                
                appendLine("SPENDING BY CATEGORY:")
                categoryMap.entries.sortedByDescending { it.value }.forEach { (category, amount) ->
                    appendLine("$category: ₹${String.format("%.2f", amount)}")
                }
                appendLine()
                
                // Recent transactions
                appendLine("RECENT TRANSACTIONS:")
                appendLine("Date | Time | Amount | Type | Merchant | Channel")
                appendLine("-".repeat(80))
                
                transactions.sortedByDescending { it.ts }.take(50).forEach { transaction ->
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(transaction.ts))
                    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(transaction.ts))
                    val merchant = transaction.merchant ?: "Unknown"
                    val channel = transaction.channel ?: ""
                    
                    appendLine("$date | $time | ₹${String.format("%.2f", transaction.amount)} | ${transaction.type} | $merchant | $channel")
                }
            }
            
            // For now, we'll create a simple text file as PDF
            // In a real implementation, you'd use a PDF library like iText or PDFBox
            file.writeText(pdfContent)
            
            FileProvider.getUriForFile(context, AUTHORITY, file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun shareFile(uri: Uri, mimeType: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "SmartExpenses Report")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        val chooser = Intent.createChooser(intent, "Share Report")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
    
    fun getShareIntent(uri: Uri, mimeType: String, title: String): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "SmartExpenses Report")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    
    private fun getCurrentDate(): String {
        return SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
    }
    
    private fun getCurrentDateTime(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }
    
    private fun getCategoryFromMerchant(merchant: String): String {
        return when {
            merchant.contains("zomato", ignoreCase = true) || 
            merchant.contains("swiggy", ignoreCase = true) || 
            merchant.contains("food", ignoreCase = true) -> "Food"
            
            merchant.contains("fuel", ignoreCase = true) || 
            merchant.contains("uber", ignoreCase = true) || 
            merchant.contains("ola", ignoreCase = true) -> "Transport"
            
            merchant.contains("store", ignoreCase = true) || 
            merchant.contains("amazon", ignoreCase = true) || 
            merchant.contains("flipkart", ignoreCase = true) -> "Shopping"
            
            merchant.contains("entertainment", ignoreCase = true) || 
            merchant.contains("movie", ignoreCase = true) -> "Entertainment"
            
            merchant.contains("bill", ignoreCase = true) || 
            merchant.contains("electricity", ignoreCase = true) || 
            merchant.contains("water", ignoreCase = true) -> "Bills"
            
            merchant.contains("medical", ignoreCase = true) || 
            merchant.contains("hospital", ignoreCase = true) -> "Health"
            
            merchant.contains("education", ignoreCase = true) || 
            merchant.contains("school", ignoreCase = true) -> "Education"
            
            else -> "Other"
        }
    }
}
