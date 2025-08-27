package com.dheeraj.smartexpenses.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(homeVm: HomeVm) {
    val scope = rememberCoroutineScope()
    var totalTransactions by remember { mutableStateOf(0) }
    var smsTransactions by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }

    // Load transaction statistics
    LaunchedEffect(Unit) {
        try {
            val stats = homeVm.getTransactionStats()
            totalTransactions = stats.first
            smsTransactions = stats.second
        } catch (e: Exception) {
            // Handle error silently - don't crash the app
            e.printStackTrace()
        }
    }

    val rangeMode by homeVm.rangeModeState.collectAsState()

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
        
        item {
            SettingsSection(
                title = "Account",
                items = listOf(
                    SettingsItem(
                        icon = Icons.Outlined.Person,
                        title = "Profile",
                        subtitle = "Manage your account details",
                        onClick = { /* TODO */ }
                    ),
                    SettingsItem(
                        icon = Icons.Outlined.Notifications,
                        title = "Notifications",
                        subtitle = "Configure app notifications",
                        onClick = { /* TODO */ }
                    ),
                    SettingsItem(
                        icon = Icons.Outlined.Security,
                        title = "Privacy & Security",
                        subtitle = "Manage your privacy settings",
                        onClick = { /* TODO */ }
                    )
                )
            )
        }
        
        item {
            SettingsSection(
                title = "Preferences",
                items = listOf(
                    SettingsItem(
                        icon = Icons.Outlined.DateRange,
                        title = "Totals Date Range",
                        subtitle = when (rangeMode) {
                            HomeVm.RangeMode.CALENDAR_MONTH -> "Calendar month (1st to month end)"
                            HomeVm.RangeMode.ROLLING_MONTH -> "Rolling month (today vs same day last month)"
                        },
                        onClick = { /* No-op: shown as static row below */ }
                    ),
                    SettingsItem(
                        icon = Icons.Outlined.Language,
                        title = "Language",
                        subtitle = "English (US)",
                        onClick = { /* TODO */ }
                    ),
                    SettingsItem(
                        icon = Icons.Outlined.DarkMode,
                        title = "Theme",
                        subtitle = "Follow system",
                        onClick = { /* TODO */ }
                    ),
                    SettingsItem(
                        icon = Icons.Outlined.CurrencyExchange,
                        title = "Currency",
                        subtitle = "Indian Rupee (₹)",
                        onClick = { /* TODO */ }
                    )
                )
            )
        }

        item {
            // Inline segmented control to switch range mode
            Card(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Choose totals date range", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = rangeMode == HomeVm.RangeMode.CALENDAR_MONTH,
                            onClick = { homeVm.setRangeMode(HomeVm.RangeMode.CALENDAR_MONTH) },
                            label = { Text("Calendar month") },
                            leadingIcon = if (rangeMode == HomeVm.RangeMode.CALENDAR_MONTH) {
                                { Icon(Icons.Outlined.Check, contentDescription = null) }
                            } else null
                        )
                        FilterChip(
                            selected = rangeMode == HomeVm.RangeMode.ROLLING_MONTH,
                            onClick = { homeVm.setRangeMode(HomeVm.RangeMode.ROLLING_MONTH) },
                            label = { Text("Rolling month") },
                            leadingIcon = if (rangeMode == HomeVm.RangeMode.ROLLING_MONTH) {
                                { Icon(Icons.Outlined.Check, contentDescription = null) }
                            } else null
                        )
                    }
                }
            }
        }
        
        item {
            SettingsSection(
                title = "Data & Storage",
                items = listOf(
                    SettingsItem(
                        icon = Icons.Outlined.CloudUpload,
                        title = "Backup & Sync",
                        subtitle = "Manage your data backup",
                        onClick = { /* TODO */ }
                    ),
                    SettingsItem(
                        icon = Icons.Outlined.Storage,
                        title = "Storage",
                        subtitle = "Manage app storage",
                        onClick = { /* TODO */ }
                    ),
                    SettingsItem(
                        icon = Icons.Outlined.Download,
                        title = "Export Data",
                        subtitle = "Export your transaction data",
                        onClick = { /* TODO */ }
                    )
                )
            )
        }
        
        item {
            SettingsSection(
                title = "Transaction Data",
                items = listOf(
                    SettingsItem(
                        icon = Icons.Outlined.Analytics,
                        title = "Transaction Statistics",
                        subtitle = "Total: $totalTransactions, SMS: $smsTransactions",
                        onClick = { /* TODO: Show detailed stats */ }
                    ),
                    SettingsItem(
                        icon = Icons.Outlined.Refresh,
                        title = "Re-import SMS",
                        subtitle = "Clear and re-import SMS transactions",
                        onClick = {
                            scope.launch {
                                isLoading = true
                                homeVm.reimportSms()
                                // Refresh stats
                                val stats = homeVm.getTransactionStats()
                                totalTransactions = stats.first
                                smsTransactions = stats.second
                                isLoading = false
                            }
                        }
                    ),
                    SettingsItem(
                        icon = Icons.Outlined.Clear,
                        title = "Clear SMS Data",
                        subtitle = "Remove all SMS-imported transactions",
                        onClick = {
                            scope.launch {
                                isLoading = true
                                homeVm.clearSmsTransactions()
                                // Refresh stats
                                val stats = homeVm.getTransactionStats()
                                totalTransactions = stats.first
                                smsTransactions = stats.second
                                isLoading = false
                            }
                        }
                    )
                )
            )
        }
        
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
        
        item {
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = { /* TODO: Logout */ },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.Logout, "Logout", modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Logout", fontWeight = FontWeight.SemiBold)
            }
        }
    }
    
    // Loading indicator
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
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
            
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = "Navigate",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
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
