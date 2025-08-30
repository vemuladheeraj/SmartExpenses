package com.dheeraj.smartexpenses.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dheeraj.smartexpenses.data.*
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiInsightsScreen(
    viewModel: AiInsightsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Clear messages when screen becomes visible
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.clearMessages()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "AI Insights",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = "Get personalized financial insights powered by AI",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Messages
            if (uiState.errorMessage != null || uiState.successMessage != null || uiState.statusMessage != null) {
                item {
                    StatusMessageCard(
                        errorMessage = uiState.errorMessage,
                        successMessage = uiState.successMessage,
                        statusMessage = uiState.statusMessage
                    )
                }
            }
            
            // API Key Setup Section
            item {
                ApiKeySetupCard(
                    hasConfiguredKey = uiState.hasConfiguredKey,
                    savedApiKey = uiState.savedApiKey,
                    savedCustomEndpoint = uiState.savedCustomEndpoint,
                    isUsingEncryptedStorage = uiState.isUsingEncryptedStorage,
                    onSaveApiKey = { viewModel.saveApiKey(it) },
                    onSaveCustomEndpoint = { viewModel.saveCustomEndpoint(it) },
                    onClearData = { viewModel.clearAllData() }
                )
            }
            
            // YouTube Tutorial Section
            if (!uiState.hasConfiguredKey) {
                item {
                    TutorialCard()
                }
            }
            
            // Insights Display
            if (uiState.insights != null) {
                val insights = uiState.insights!!
                item {
                    InsightsOverviewCard(
                        insights = insights,
                        lastUpdatedTime = uiState.lastUpdatedTime,
                        onRefresh = { viewModel.refreshInsights() },
                        isLoading = uiState.isLoading
                    )
                }
                
                // KPIs Section
                item {
                    KpisCard(kpis = insights.kpis)
                }
                
                // Category Breakdown
                if (insights.breakdowns.byCategory.isNotEmpty()) {
                    item {
                        CategoryBreakdownCard(categories = insights.breakdowns.byCategory)
                    }
                }
                
                // Rail Breakdown
                if (insights.breakdowns.byRail.isNotEmpty()) {
                    item {
                        RailBreakdownCard(rails = insights.breakdowns.byRail)
                    }
                }
                
                // Large Transactions
                if (insights.largeTxns.isNotEmpty()) {
                    item {
                        LargeTransactionsCard(transactions = insights.largeTxns)
                    }
                }
                
                // Recurring Payments
                if (insights.recurring.isNotEmpty()) {
                    item {
                        RecurringPaymentsCard(payments = insights.recurring)
                    }
                }
                
                // Notes
                if (insights.notes.isNotBlank()) {
                    item {
                        NotesCard(notes = insights.notes)
                    }
                }
            } else if (uiState.hasConfiguredKey) {
                // Loading or no data state
                item {
                    NoInsightsCard(
                        isLoading = uiState.isLoading,
                        onRefresh = { viewModel.refreshInsights() }
                    )
                }
            }
        }
    }
}

@Composable
fun StatusMessageCard(
    errorMessage: String?,
    successMessage: String?,
    statusMessage: String?
) {
    val message = errorMessage ?: successMessage ?: statusMessage
    val isError = errorMessage != null
    val isSuccess = successMessage != null
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isError -> MaterialTheme.colorScheme.errorContainer
                isSuccess -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.secondaryContainer
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when {
                    isError -> Icons.Default.Error
                    isSuccess -> Icons.Default.CheckCircle
                    else -> Icons.Default.Info
                },
                contentDescription = null,
                tint = when {
                    isError -> MaterialTheme.colorScheme.error
                    isSuccess -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSecondaryContainer
                }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    isError -> MaterialTheme.colorScheme.onErrorContainer
                    isSuccess -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSecondaryContainer
                }
            )
        }
    }
}

@Composable
fun ApiKeySetupCard(
    hasConfiguredKey: Boolean,
    savedApiKey: String?,
    savedCustomEndpoint: String?,
    isUsingEncryptedStorage: Boolean,
    onSaveApiKey: (String) -> Unit,
    onSaveCustomEndpoint: (String) -> Unit,
    onClearData: () -> Unit
) {
    val context = LocalContext.current
    var showApiKeyInput by remember { mutableStateOf(false) }
    var showEndpointInput by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf("") }
    var endpointInput by remember { mutableStateOf("") }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hasConfiguredKey) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (hasConfiguredKey) Icons.Default.CheckCircle else Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = if (hasConfiguredKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (hasConfiguredKey) "AI Configuration" else "Setup AI Insights",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (!hasConfiguredKey) {
                Text(
                    text = "Connect your Google AI Studio API key or custom endpoint to get AI-powered financial insights.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create Google AI Studio API Key")
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedButton(
                    onClick = { showApiKeyInput = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Key, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Paste API Key")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedButton(
                    onClick = { showEndpointInput = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Link, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Use Custom Endpoint")
                }
            } else {
                Text(
                    text = if (savedApiKey != null) {
                        "API Key: ${savedApiKey.take(4)}${"*".repeat(savedApiKey.length - 8)}${savedApiKey.takeLast(4)}"
                    } else {
                        "Custom Endpoint: ${savedCustomEndpoint}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (isUsingEncryptedStorage) {
                    Text(
                        text = "✓ Securely stored with encryption",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedButton(
                    onClick = onClearData,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear Configuration")
                }
            }
        }
    }
    
    // API Key Input Dialog
    if (showApiKeyInput) {
        AlertDialog(
            onDismissRequest = { showApiKeyInput = false },
            title = { Text("Enter API Key") },
            text = {
                Column {
                    Text(
                        text = "Paste your Google AI Studio API key (starts with AIza):",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        placeholder = { Text("AIza...") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (apiKeyInput.isNotBlank()) {
                                    onSaveApiKey(apiKeyInput)
                                    showApiKeyInput = false
                                    apiKeyInput = ""
                                }
                            }
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (apiKeyInput.isNotBlank()) {
                            onSaveApiKey(apiKeyInput)
                            showApiKeyInput = false
                            apiKeyInput = ""
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyInput = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Custom Endpoint Input Dialog
    if (showEndpointInput) {
        AlertDialog(
            onDismissRequest = { showEndpointInput = false },
            title = { Text("Enter Custom Endpoint") },
            text = {
                Column {
                    Text(
                        text = "Enter your custom AI endpoint URL:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = endpointInput,
                        onValueChange = { endpointInput = it },
                        placeholder = { Text("https://your-endpoint.com/ai") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (endpointInput.isNotBlank()) {
                                    onSaveCustomEndpoint(endpointInput)
                                    showEndpointInput = false
                                    endpointInput = ""
                                }
                            }
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (endpointInput.isNotBlank()) {
                            onSaveCustomEndpoint(endpointInput)
                            showEndpointInput = false
                            endpointInput = ""
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndpointInput = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun TutorialCard() {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "How to Setup AI Insights",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Watch this tutorial to learn how to create and configure your Google AI Studio API key for personalized financial insights.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // YouTube Tutorial Embed (placeholder)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tutorial Video",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Replace VIDEO_ID with actual YouTube video ID",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun InsightsOverviewCard(
    insights: AiInsights,
    lastUpdatedTime: String,
    onRefresh: () -> Unit,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Financial Insights",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Updated $lastUpdatedTime",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Button(
                    onClick = onRefresh,
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isLoading) "Refreshing..." else "Refresh")
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                InsightStat(
                    label = "Total Spend",
                    value = "₹${NumberFormat.getNumberInstance().format(insights.kpis.totalSpendInr)}"
                )
                InsightStat(
                    label = "Transactions",
                    value = "${insights.kpis.debitCount + insights.kpis.creditCount}"
                )
                InsightStat(
                    label = "Largest",
                    value = "₹${NumberFormat.getNumberInstance().format(insights.kpis.largestTxnAmount)}"
                )
            }
        }
    }
}

@Composable
fun InsightStat(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun KpisCard(kpis: Kpis) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Key Metrics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Debits",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = kpis.debitCount.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column {
                    Text(
                        text = "Credits",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = kpis.creditCount.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column {
                    Text(
                        text = "Largest Merchant",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = kpis.largestTxnMerchant,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            if (kpis.unusualSpendFlag) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Unusual spending detected",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryBreakdownCard(categories: List<CategoryBreakdown>) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Spending by Category",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            categories.forEach { category ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "₹${NumberFormat.getNumberInstance().format(category.amount)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun RailBreakdownCard(rails: List<RailBreakdown>) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Payment Methods",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            rails.forEach { rail ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = rail.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "₹${NumberFormat.getNumberInstance().format(rail.amount)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun LargeTransactionsCard(transactions: List<LargeTransaction>) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Large Transactions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            LazyColumn {
                items(transactions.take(5)) { transaction ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = transaction.merchant,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = transaction.date,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "₹${NumberFormat.getNumberInstance().format(transaction.amount)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecurringPaymentsCard(payments: List<RecurringPayment>) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Recurring Payments",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            LazyColumn {
                items(payments.take(5)) { payment ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = payment.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Day ${payment.dayOfMonth} of month",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "₹${NumberFormat.getNumberInstance().format(payment.amount)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NotesCard(notes: String) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "AI Insights",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = notes,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun NoInsightsCard(
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoading) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Analyzing your transactions...",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No insights available",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Tap refresh to generate AI insights from your transaction data",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Insights")
                }
            }
        }
    }
}
