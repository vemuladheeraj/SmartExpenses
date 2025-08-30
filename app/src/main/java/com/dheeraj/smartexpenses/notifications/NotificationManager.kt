package com.dheeraj.smartexpenses.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dheeraj.smartexpenses.MainActivity
import com.dheeraj.smartexpenses.R
import com.dheeraj.smartexpenses.data.BudgetAnalysis
import com.dheeraj.smartexpenses.data.Transaction
import com.dheeraj.smartexpenses.data.amount
import java.text.NumberFormat
import java.util.*

class SmartNotificationManager(private val context: Context) {
    
    companion object {
        const val CHANNEL_BUDGET_ALERTS = "budget_alerts"
        const val CHANNEL_LARGE_TRANSACTIONS = "large_transactions"
        const val CHANNEL_SPENDING_PATTERNS = "spending_patterns"
        const val CHANNEL_BILL_REMINDERS = "bill_reminders"
        
        const val NOTIFICATION_BUDGET_BREACH = 1001
        const val NOTIFICATION_LARGE_TRANSACTION = 1002
        const val NOTIFICATION_SPENDING_PATTERN = 1003
        const val NOTIFICATION_BILL_REMINDER = 1004
    }
    
    init {
        createNotificationChannels()
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_BUDGET_ALERTS,
                    "Budget Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for budget breaches and warnings"
                    enableVibration(true)
                },
                NotificationChannel(
                    CHANNEL_LARGE_TRANSACTIONS,
                    "Large Transactions",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications for large transactions"
                },
                NotificationChannel(
                    CHANNEL_SPENDING_PATTERNS,
                    "Spending Patterns",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Notifications for unusual spending patterns"
                },
                NotificationChannel(
                    CHANNEL_BILL_REMINDERS,
                    "Bill Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications for upcoming bill payments"
                }
            )
            
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            channels.forEach { channel ->
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
    
    fun showBudgetBreachNotification(analysis: BudgetAnalysis) {
        val currencyFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"))
        val overAmount = -analysis.remainingAmountValue
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_BUDGET_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Budget Breach Alert!")
            .setContentText("${analysis.category} budget exceeded by ${currencyFormat.format(overAmount)}")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("You've exceeded your ${analysis.category} budget by ${currencyFormat.format(overAmount)}. " +
                        "Consider reviewing your spending in this category."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_BUDGET_BREACH + analysis.category.hashCode(),
            notification
        )
    }
    
    fun showBudgetWarningNotification(analysis: BudgetAnalysis) {
        val currencyFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"))
        val remainingAmount = analysis.remainingAmountValue
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_BUDGET_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Budget Warning")
            .setContentText("${analysis.category} budget: ${String.format("%.1f", analysis.percentageUsed)}% used")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("You've used ${String.format("%.1f", analysis.percentageUsed)}% of your ${analysis.category} budget. " +
                        "Only ${currencyFormat.format(remainingAmount)} remaining this month."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_BUDGET_BREACH + analysis.category.hashCode() + 1000,
            notification
        )
    }
    
    fun showLargeTransactionNotification(transaction: Transaction, threshold: Double = 10000.0) {
        val currencyFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"))
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_LARGE_TRANSACTIONS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Large Transaction Detected")
            .setContentText("${currencyFormat.format(transaction.amount)} - ${transaction.merchant ?: "Unknown"}")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("A large transaction of ${currencyFormat.format(transaction.amount)} was made to ${transaction.merchant ?: "Unknown"}. " +
                        "Please review this transaction."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_LARGE_TRANSACTION + transaction.id.toInt(),
            notification
        )
    }
    
    fun showUnusualSpendingPatternNotification(
        category: String,
        currentAmount: Double,
        averageAmount: Double,
        percentage: Double
    ) {
        val currencyFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"))
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_SPENDING_PATTERNS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Unusual Spending Pattern")
            .setContentText("${category} spending is ${String.format("%.0f", percentage)}% higher than usual")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Your ${category} spending this month (${currencyFormat.format(currentAmount)}) is " +
                        "${String.format("%.0f", percentage)}% higher than your average (${currencyFormat.format(averageAmount)}). " +
                        "Consider reviewing your spending habits."))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_SPENDING_PATTERN + category.hashCode(),
            notification
        )
    }
    
    fun showBillReminderNotification(
        billName: String,
        dueDate: String,
        amount: Double? = null
    ) {
        val currencyFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"))
        val amountText = amount?.let { "Amount: ${currencyFormat.format(it)}" } ?: ""
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_BILL_REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Bill Payment Reminder")
            .setContentText("$billName due on $dueDate")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Your $billName bill is due on $dueDate. $amountText " +
                        "Don't forget to make the payment to avoid late fees."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_BILL_REMINDER + billName.hashCode(),
            notification
        )
    }
    
    fun cancelBudgetNotifications(category: String) {
        NotificationManagerCompat.from(context).cancel(
            NOTIFICATION_BUDGET_BREACH + category.hashCode()
        )
        NotificationManagerCompat.from(context).cancel(
            NOTIFICATION_BUDGET_BREACH + category.hashCode() + 1000
        )
    }
    
    fun cancelAllNotifications() {
        NotificationManagerCompat.from(context).cancelAll()
    }
}
