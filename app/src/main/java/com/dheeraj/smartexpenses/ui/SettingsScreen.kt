package com.dheeraj.smartexpenses.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.RadioButton
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
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.dheeraj.smartexpenses.data.AiInsightsRepository
import com.dheeraj.smartexpenses.security.SecurePreferences
import androidx.compose.foundation.border


@Composable
fun SettingsScreen(
    homeVm: HomeVm,
    onSettingsChanged: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showFreshStartConfirm by remember { mutableStateOf(false) }
    var showSmsLoader by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showHelpSupport by remember { mutableStateOf(false) }

    var smsCount by remember { mutableStateOf(0) }
    var transactionCount by remember { mutableStateOf(0) }
    var processingStatus by remember { mutableStateOf("") }
    var hasConfiguredKey by remember { mutableStateOf(false) }
    var savedApiKey by remember { mutableStateOf<String?>(null) }
    var savedCustomEndpoint by remember { mutableStateOf<String?>(null) }
    
    // Biometric and security state
    var biometricEnabled by remember { mutableStateOf(false) }
    var appLockEnabled by remember { mutableStateOf(false) }
    var showBiometricSettings by remember { mutableStateOf(false) }
    
    // Initialize biometric manager
    val biometricManager = remember { com.dheeraj.smartexpenses.security.BiometricManager(context) }
    
    // Load biometric settings
    LaunchedEffect(Unit) {
        biometricEnabled = biometricManager.isBiometricEnabled()
        appLockEnabled = biometricManager.isAppLockEnabled()
    }
    
    // Initialize AI repository
    val aiRepository = remember { 
        val txnDao = com.dheeraj.smartexpenses.data.AppDb.get(context).txnDao()
        val aiService = com.dheeraj.smartexpenses.data.AiService()
        val cache = com.dheeraj.smartexpenses.data.InsightsCache(context)
        val securePrefs = com.dheeraj.smartexpenses.security.SecurePreferences(context)
        
        AiInsightsRepository(context, txnDao, aiService, cache, securePrefs)
    }
    
    // Load AI configuration status
    LaunchedEffect(Unit) {
        try {
            hasConfiguredKey = aiRepository.hasConfiguredKey()
            savedApiKey = aiRepository.getSavedApiKey()
            savedCustomEndpoint = aiRepository.getSavedCustomEndpoint()
            Log.d("SettingsScreen", "Loaded AI config - hasConfiguredKey: $hasConfiguredKey, savedApiKey: ${savedApiKey?.take(10) ?: "null"}..., savedCustomEndpoint: $savedCustomEndpoint")
        } catch (e: Exception) {
            Log.e("SettingsScreen", "Error loading AI config", e)
        }
    }
    
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
        
        // SMS & Data Management Section (Consolidated)
        item {
            SettingsSection(
                title = "SMS & Data Management",
                items = listOf(
                    SettingsItem(
                        icon = Icons.Outlined.Message,
                        title = "SMS Statistics",
                        subtitle = "$smsCount SMS messages processed into $transactionCount transactions",
                        onClick = { /* Read-only info */ }
                    ),
                    SettingsItem(
                        icon = Icons.Outlined.Refresh,
                        title = "Re-import SMS",
                        subtitle = "Clear transactions and re-process recent SMS messages",
                        onClick = { 
                            scope.launch {
                                homeVm.clearSmsTransactions()
                                homeVm.importRecentSms()
                                Toast.makeText(context, "SMS re-import completed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ),
                    SettingsItem(
                        icon = Icons.Outlined.Sync,
                        title = "Import New SMS",
                        subtitle = "Import only new SMS messages since last import",
                        onClick = { 
                            scope.launch {
                                homeVm.importNewSms()
                                Toast.makeText(context, "New SMS import completed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ),
                    SettingsItem(
                        icon = Icons.Outlined.Clear,
                        title = "Fresh Start",
                        subtitle = "Clear all data and reset app completely",
                        onClick = { showFreshStartConfirm = true }
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
        
        // AI Configuration section
        item {
            SettingsSection(
                title = "AI Configuration",
                items = listOf(
                    SettingsItem(
                        icon = Icons.Outlined.Psychology,
                        title = "Configure AI Insights",
                        subtitle = if (hasConfiguredKey) {
                            if (savedApiKey != null) "Google AI Studio API Key configured" 
                            else "Custom endpoint configured"
                        } else "Set up AI insights with API key or custom endpoint",
                        onClick = { 
                            showApiKeyDialog = true
                        }
                    ),
                    SettingsItem(
                        icon = Icons.Outlined.Help,
                        title = "Get Free API Key",
                        subtitle = "Step-by-step guide to get your free API key",
                        onClick = { 
                            uriHandler.openUri("https://aistudio.google.com/app/apikey")
                        }
                    ),
                    SettingsItem(
                        icon = Icons.Outlined.Clear,
                        title = "Clear AI Configuration",
                        subtitle = "Remove API key and endpoint settings",
                        onClick = { 
                            scope.launch {
                                aiRepository.clearAllData()
                                hasConfiguredKey = false
                                savedApiKey = null
                                savedCustomEndpoint = null
                                Toast.makeText(context, "AI configuration cleared", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ),
                    SettingsItem(
                        icon = Icons.Outlined.BugReport,
                        title = "Debug API Key Status",
                        subtitle = "Check current API key configuration",
                        onClick = { 
                            scope.launch {
                                try {
                                    val currentApiKey = aiRepository.getSavedApiKey()
                                    val currentEndpoint = aiRepository.getSavedCustomEndpoint()
                                    val hasKey = aiRepository.hasConfiguredKey()
                                    val message = "API Key: ${currentApiKey?.take(10) ?: "null"}..., Endpoint: $currentEndpoint, HasKey: $hasKey"
                                    Log.d("SettingsScreen", "Debug info: $message")
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    Log.e("SettingsScreen", "Error debugging API key", e)
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
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
                        subtitle = if (biometricEnabled) "Enabled" else "Disabled",
                        onClick = { showBiometricSettings = true }
                    ),
                    SettingsItem(
                        icon = Icons.Outlined.Lock,
                        title = "App Lock",
                        subtitle = if (appLockEnabled) "Enabled" else "Disabled",
                        onClick = { showBiometricSettings = true }
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
                        onClick = { showHelpSupport = true }
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
    
    // API Key configuration dialog
    if (showApiKeyDialog) {
        ApiKeyDialog(
            currentApiKey = savedApiKey,
            currentEndpoint = savedCustomEndpoint,
            onDismiss = { showApiKeyDialog = false },
            onSave = { apiKey, endpoint ->
                scope.launch {
                    try {
                        Log.d("SettingsScreen", "onSave called with apiKey: ${apiKey?.take(10) ?: "null"}..., endpoint: ${endpoint ?: "null"}")
                        
                        if (!apiKey.isNullOrBlank()) {
                            Log.d("SettingsScreen", "Saving API key: ${apiKey.take(10)}...")
                            val result = aiRepository.saveApiKey(apiKey)
                            if (result.isSuccess) {
                                Log.d("SettingsScreen", "API key saved successfully")
                                savedApiKey = apiKey
                                savedCustomEndpoint = null
                                hasConfiguredKey = true
                                showApiKeyDialog = false
                                Toast.makeText(context, "API key saved successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                Log.e("SettingsScreen", "Failed to save API key: ${result.exceptionOrNull()?.message}")
                                Toast.makeText(context, "Failed to save API key: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                        } else if (!endpoint.isNullOrBlank()) {
                            Log.d("SettingsScreen", "Saving custom endpoint: $endpoint")
                            val result = aiRepository.saveCustomEndpoint(endpoint)
                            if (result.isSuccess) {
                                Log.d("SettingsScreen", "Custom endpoint saved successfully")
                                savedCustomEndpoint = endpoint
                                savedApiKey = null
                                hasConfiguredKey = true
                                showApiKeyDialog = false
                                Toast.makeText(context, "Custom endpoint saved successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                Log.e("SettingsScreen", "Failed to save endpoint: ${result.exceptionOrNull()?.message}")
                                Toast.makeText(context, "Failed to save endpoint: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Log.w("SettingsScreen", "Both apiKey and endpoint are null or blank")
                        }
                    } catch (e: Exception) {
                        Log.e("SettingsScreen", "Error saving configuration", e)
                        Toast.makeText(context, "Error saving configuration: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
    
    // Help & Support dialog
    if (showHelpSupport) {
        HelpSupportDialog(
            onDismiss = { showHelpSupport = false }
        )
    }
    

    
    // Biometric settings dialog
    if (showBiometricSettings) {
        BiometricSettingsDialog(
            isBiometricEnabled = biometricEnabled,
            isAppLockEnabled = appLockEnabled,
            onDismiss = { showBiometricSettings = false },
            onBiometricToggle = { enabled ->
                scope.launch {
                    biometricManager.setBiometricEnabled(enabled)
                    biometricEnabled = enabled
                    Toast.makeText(context, "Biometric lock ${if (enabled) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
                    onSettingsChanged() // Notify MainActivity to refresh biometric settings
                }
            },
            onAppLockToggle = { enabled ->
                scope.launch {
                    biometricManager.setAppLockEnabled(enabled)
                    appLockEnabled = enabled
                    Toast.makeText(context, "App lock ${if (enabled) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
                    onSettingsChanged() // Notify MainActivity to refresh biometric settings
                }
            }
        )
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
                .padding(16.dp)
                .clickable { item.onClick() },
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

@Composable
fun ApiKeyDialog(
    currentApiKey: String?,
    currentEndpoint: String?,
    onDismiss: () -> Unit,
    onSave: (String?, String?) -> Unit
) {
    var apiKey by remember { mutableStateOf(currentApiKey ?: "") }
    var showApiKey by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "Enter Your Google AI Studio API Key",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Simple help text
                Text(
                    "To use AI insights, you need a free API key from Google AI Studio.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Step-by-step guide
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "📱 How to get your free API key:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "1. Visit aistudio.google.com/app/apikey\n" +
                            "2. Sign in with your Google account\n" +
                            "3. Click 'Create API Key'\n" +
                            "4. Copy the key (starts with 'AIza...')\n" +
                            "5. Paste it below",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // API Key Input - Much clearer now
                Column {
                    Text(
                        "API Key:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { 
                            Text(
                                "AIza... (paste your API key here)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        label = { Text("Google AI Studio API Key") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { /* Hide keyboard */ }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    
                    // Show/Hide toggle
                    if (apiKey.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(
                                onClick = { showApiKey = !showApiKey },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (showApiKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showApiKey) "Hide API key" else "Show API key",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                if (showApiKey) "Hide key" else "Show key",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // Validation message
                if (apiKey.isNotEmpty() && !apiKey.startsWith("AIza")) {
                    Text(
                        "⚠️ API key should start with 'AIza'",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (apiKey.isNotBlank() && apiKey.startsWith("AIza")) {
                        isSaving = true
                        onSave(apiKey, null)
                    }
                },
                enabled = apiKey.isNotBlank() && apiKey.startsWith("AIza") && !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Saving...")
                } else {
                    Text("Save API Key")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving
            ) {
                Text("Cancel")
            }
        }
    )
}



@Composable
fun BiometricSettingsDialog(
    isBiometricEnabled: Boolean,
    isAppLockEnabled: Boolean,
    onDismiss: () -> Unit,
    onBiometricToggle: (Boolean) -> Unit,
    onAppLockToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val biometricManager = remember { com.dheeraj.smartexpenses.security.BiometricManager(context) }
    val biometricAvailable = remember { biometricManager.isBiometricAvailable() }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Security Settings") },
        text = {
            Column {
                Text(
                    "Configure security features to protect your app:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Biometric Lock
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Fingerprint,
                        contentDescription = "Biometric Lock",
                        tint = if (biometricAvailable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            "Biometric Lock",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        if (!biometricAvailable) {
                            Text(
                                "Not available on this device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = isBiometricEnabled,
                        onCheckedChange = onBiometricToggle,
                        enabled = biometricAvailable
                    )
                }

                // App Lock
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = "App Lock",
                        tint = if (biometricAvailable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            "App Lock",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        if (!biometricAvailable) {
                            Text(
                                "Requires biometric authentication",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = isAppLockEnabled,
                        onCheckedChange = onAppLockToggle,
                        enabled = biometricAvailable
                    )
                }
                
                if (!biometricAvailable) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            "Biometric authentication is not available on this device. " +
                            "Please check your device settings or contact support.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun HelpSupportDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "Help & Support",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Need help with SmartExpenses? We're here to assist you!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Contact Information
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Contact Information",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        
                        // Developer Name
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = "Developer",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Developer: Dheeraj",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        // Email
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Email,
                                contentDescription = "Email",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Email: appworks.dheeraj@gmail.com",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                
                // Support Options
                Text(
                    "Support Options:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                Text(
                    "• Email us for technical support\n" +
                    "• Report bugs or issues\n" +
                    "• Request new features\n" +
                    "• Get help with app usage",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}




