package com.dheeraj.smartexpenses.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.dheeraj.smartexpenses.ui.SmsProcessingStats
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext


@Composable
fun SettingsScreen(homeVm: HomeVm) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showFreshStartConfirm by remember { mutableStateOf(false) }
    var showSmsLoader by remember { mutableStateOf(false) }
    var smsCount by remember { mutableStateOf(0) }
    var transactionCount by remember { mutableStateOf(0) }
    var processingStatus by remember { mutableStateOf("") }
    
    // Load SMS and transaction counts
    LaunchedEffect(Unit) {
        val stats = homeVm.getTransactionStats()
        smsCount = stats.second
        transactionCount = stats.first
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Customize your experience",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // SMS Management Section
        item {
            SettingsSection(
                title = "SMS Management",
                items = listOf(
                    SettingsItem(
                        icon = Icons.Outlined.Message,
                        title = "SMS Statistics",
                        subtitle = "$smsCount SMS messages processed into $transactionCount transactions",
                        onClick = { /* Read-only info */ }
                    ),
                    SettingsItem(
                        icon = Icons.Outlined.DeleteForever,
                        title = "Clear All Data & Retry",
                        subtitle = "Delete all transactions and reprocess SMS with AI",
                        onClick = { showDeleteConfirm = true }
                    )
                )
            )
        }
        
        // Export section
        item {
            SettingsSection(
                title = "Export & Backup",
                items = listOf(
                    SettingsItem(
                        icon = Icons.Outlined.Download,
                        title = "Export to CSV",
                        subtitle = "Export all transactions to CSV file",
                        onClick = { 
                            scope.launch {
                                val exportManager = com.dheeraj.smartexpenses.export.ExportManager(context)
                                val transactions = homeVm.getAllTransactions()
                                val uri = exportManager.exportToCSV(transactions)
                                uri?.let {
                                    exportManager.shareFile(it, "text/csv", "SmartExpenses Transactions")
                                }
                            }
                        }
                    ),
                    SettingsItem(
                        icon = Icons.Outlined.PictureAsPdf,
                        title = "Export to PDF",
                        subtitle = "Generate PDF report of transactions",
                        onClick = { 
                            scope.launch {
                                val exportManager = com.dheeraj.smartexpenses.export.ExportManager(context)
                                val transactions = homeVm.getAllTransactions()
                                val uri = exportManager.exportToPDF(transactions)
                                uri?.let {
                                    exportManager.shareFile(it, "application/pdf", "SmartExpenses Report")
                                }
                            }
                        }
                    )
                )
            )
        }
        
        // Security section
        item {
            SettingsSection(
                title = "Security",
                items = listOf(
                    SettingsItem(
                        icon = Icons.Outlined.Fingerprint,
                        title = "Biometric Lock",
                        subtitle = "Secure app with fingerprint or face unlock",
                        onClick = { /* TODO: Implement biometric settings */ }
                    ),
                    SettingsItem(
                        icon = Icons.Outlined.Lock,
                        title = "App Lock",
                        subtitle = "Require authentication to open app",
                        onClick = { /* TODO: Implement app lock settings */ }
                    )
                )
            )
        }
        
        // Data Management section
        item {
            SettingsSection(
                title = "Data Management",
                items = listOf(
                    SettingsItem(
                        icon = Icons.Outlined.Clear,
                        title = "Clear & Reprocess",
                        subtitle = "Clear transactions and re-import SMS",
                        onClick = { 
                            scope.launch {
                                homeVm.clearSmsTransactions()
                                homeVm.importRecentSms()
                            }
                        }
                    ),
                    SettingsItem(
                        icon = Icons.Outlined.Refresh,
                        title = "Fresh Start",
                        subtitle = "Clear all data and reset app completely",
                        onClick = { 
                            showFreshStartConfirm = true
                        }
                    )
                )
            )
        }
        
        // Support section
        item {
            SettingsSection(
                title = "Support",
                items = listOf(
                    SettingsItem(
                        icon = Icons.Outlined.Help,
                        title = "Help & Support",
                        subtitle = "Get help and contact support",
                        onClick = { /* TODO */ }
                    ),
                    SettingsItem(
                        icon = Icons.Outlined.Feedback,
                        title = "Send Feedback",
                        subtitle = "Share your thoughts with us",
                        onClick = { /* TODO */ }
                    ),
                    SettingsItem(
                        icon = Icons.Outlined.Star,
                        title = "Rate App",
                        subtitle = "Rate us on Google Play",
                        onClick = { /* TODO */ }
                    )
                )
            )
        }
        
        // About section
        item {
            SettingsSection(
                title = "About",
                items = listOf(
                    SettingsItem(
                        icon = Icons.Outlined.Info,
                        title = "App Version",
                        subtitle = "1.0.0",
                        onClick = { /* TODO */ }
                    ),
                    SettingsItem(
                        icon = Icons.Outlined.Description,
                        title = "Terms of Service",
                        subtitle = "Read our terms and conditions",
                        onClick = { /* TODO */ }
                    ),
                    SettingsItem(
                        icon = Icons.Outlined.Policy,
                        title = "Privacy Policy",
                        subtitle = "Read our privacy policy",
                        onClick = { /* TODO */ }
                    )
                )
            )
        }
    }
    
    // Delete confirmation dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Clear All Data?") },
            text = { 
                Text(
                    "This will delete all $transactionCount transactions and reprocess all SMS messages. " +
                    "This action cannot be undone. The first load may take time due to AI processing."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        showSmsLoader = true
                        scope.launch {
                            homeVm.clearSmsTransactions()
                            homeVm.importRecentSms()
                            // Reload counts after processing
                            val stats = homeVm.getTransactionStats()
                            smsCount = stats.second
                            transactionCount = stats.first
                            showSmsLoader = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clear & Reprocess")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFreshStartConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Fresh Start confirmation dialog
    if (showFreshStartConfirm) {
        AlertDialog(
            onDismissRequest = { showFreshStartConfirm = false },
            title = { Text("Fresh Start") },
            text = { 
                Text(
                    "This will delete ALL data including transactions, budgets, and settings. " +
                    "This action cannot be undone. Are you sure?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFreshStartConfirm = false
                        scope.launch {
                            try {
                                // Clear all data
                                com.dheeraj.smartexpenses.data.AppDb.clearAllData(context)
                                
                                // Reset the ViewModel
                                homeVm.resetViewModel()
                                
                                // Show success message
                                Toast.makeText(context, "All data cleared. App will restart fresh.", Toast.LENGTH_LONG).show()
                                
                                // Reload counts after clearing
                                val stats = homeVm.getTransactionStats()
                                smsCount = stats.second
                                transactionCount = stats.first
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error clearing data: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Yes, Clear Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // SMS Processing Loader
    if (showSmsLoader) {
        SmsProcessingLoader(processingStatus)
    }
    

}

@Composable
fun SmsProcessingLoader(status: String = "Processing SMS Messages") {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Semi-transparent background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
        )
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Animated loading indicator
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 6.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = status,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "The first load always takes time for beautiful AI-powered insights",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Progress dots animation
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(3) { index ->
                        val delay = index * 200
                        var isVisible by remember { mutableStateOf(false) }
                        
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(delay.toLong())
                            isVisible = true
                        }
                        
                        AnimatedVisibility(
                            visible = isVisible,
                            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    items: List<SettingsItem>
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column {
                items.forEachIndexed { index, item ->
                    SettingsItemRow(
                        item = item,
                        showDivider = index < items.size - 1
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsItemRow(
    item: SettingsItem,
    showDivider: Boolean
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (item.title != "SMS Count") {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = "Navigate",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 56.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        }
    }
}

data class SettingsItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit
)




