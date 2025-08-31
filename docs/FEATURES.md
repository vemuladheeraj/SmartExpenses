# SmartExpenses - Feature Documentation

## 📱 Core Features

### 1. Automatic SMS Processing

#### **Smart SMS Parsing**
SmartExpenses automatically extracts transaction data from SMS messages sent by Indian banks using advanced regex-based parsing.

**Key Components:**
- `SmsParser.kt` - Main parsing logic
- `SmsReceiver.kt` - Real-time SMS processing
- `SmsTypes.kt` - Data structures for SMS analysis

**Features:**
- **Multi-bank Support**: Supports 50+ Indian banks and payment systems
- **Spam Detection**: Filters out promotional and marketing messages
- **Amount Validation**: Context-aware amount extraction with balance filtering
- **Transfer Detection**: Intelligent detection of internal account transfers
- **Real-time Processing**: Live SMS processing with background updates

**Supported SMS Formats:**
```
DEBIT: "Rs.500.00 debited from A/c **1234 on 15-Jan-24. Avl Bal Rs.25,000.00"
CREDIT: "Rs.10,000.00 credited to A/c **5678 on 15-Jan-24. Avl Bal Rs.35,000.00"
UPI: "Rs.250.00 paid to SWIGGY via UPI on 15-Jan-24. UPI Ref: 123456789012"
```

#### **Spam Detection System**
Multi-layered spam filtering to prevent processing of promotional messages.

**Detection Patterns:**
- Promotional keywords: "offer", "discount", "cashback", "reward"
- Marketing phrases: "limited time", "exclusive", "special deal"
- Balance alerts: "your balance is", "available balance"
- OTP messages: "OTP", "verification code", "one-time password"

**Configuration:**
```kotlin
// Spam detection can be configured in SmsParser.kt
private val spamKeywords = listOf(
    "offer", "discount", "cashback", "reward", "promo",
    "limited time", "exclusive", "special deal", "sale"
)
```

#### **Amount Validation**
Context-aware amount extraction that prevents false positives from balance alerts.

**Validation Rules:**
- Minimum amount: ₹10
- Maximum amount: ₹10 crore
- Context filtering: Excludes balance alerts and promotional amounts
- Format support: ₹1,23,456.78, Rs.999/-, INR 2500

**Implementation:**
```kotlin
private fun isValidTransactionAmount(amountMinor: Long, body: String): Boolean {
    // Check amount range
    if (amountMinor < 1000 || amountMinor > 1000000000) return false
    
    // Check for balance context
    if (body.contains("balance", ignoreCase = true)) return false
    
    // Check for promotional amounts
    if (amountMinor <= 1000 && isPromotionalAmount(body)) return false
    
    return true
}
```

### 2. Transaction Management

#### **Automatic Categorization**
AI-powered merchant categorization based on transaction data.

**Categories:**
- **Food**: Restaurants, food delivery, groceries
- **Transport**: Uber, Ola, fuel, public transport
- **Shopping**: Amazon, Flipkart, retail stores
- **Entertainment**: Movies, streaming services, games
- **Bills**: Electricity, water, gas, internet
- **Health**: Hospitals, pharmacies, medical
- **Education**: Schools, colleges, courses
- **Other**: Miscellaneous expenses

**Categorization Logic:**
```kotlin
fun getCategoryFromMerchant(merchant: String): String {
    return when {
        merchant.contains("food", ignoreCase = true) || 
        merchant.contains("restaurant", ignoreCase = true) ||
        merchant.contains("swiggy", ignoreCase = true) ||
        merchant.contains("zomato", ignoreCase = true) -> "Food"
        
        merchant.contains("uber", ignoreCase = true) ||
        merchant.contains("ola", ignoreCase = true) ||
        merchant.contains("transport", ignoreCase = true) -> "Transport"
        
        // ... more categories
        else -> "Other"
    }
}
```

#### **Payment Channel Detection**
Automatic detection of payment methods from SMS content.

**Supported Channels:**
- **UPI**: Google Pay, PhonePe, Paytm, BHIM
- **IMPS**: Immediate Payment Service
- **NEFT**: National Electronic Funds Transfer
- **RTGS**: Real Time Gross Settlement
- **POS**: Point of Sale transactions
- **ATM**: Automated Teller Machine
- **CARD**: Credit/Debit card transactions
- **CHEQUE**: Cheque transactions

#### **Transfer Detection**
Intelligent detection of internal account transfers to avoid double-counting.

**Detection Methods:**
1. **Same Amount Pairs**: DEBIT and CREDIT with identical amounts
2. **Time Window**: Transactions within 5 minutes
3. **Account Matching**: Same account tail or bank
4. **Keyword Detection**: "self transfer", "own account", "internal transfer"

**Implementation:**
```kotlin
private fun findPairedTransferIds(transactions: List<Transaction>): Set<Long> {
    val credits = transactions.filter { it.type == "CREDIT" }.sortedBy { it.ts }
    val debits = transactions.filter { it.type == "DEBIT" }.sortedBy { it.ts }
    
    val pairedIds = mutableSetOf<Long>()
    // Algorithm to find matching pairs within time window
    // Returns set of transaction IDs that are part of transfers
    return pairedIds
}
```

### 3. Manual Transaction Entry

#### **Add Transaction Interface**
Users can manually add transactions with full customization.

**Fields:**
- **Amount**: Transaction amount with currency formatting
- **Type**: Income (CREDIT) or Expense (DEBIT)
- **Merchant**: Merchant or person name
- **Channel**: Payment method selection
- **Category**: Spending category selection
- **Date**: Transaction date and time

**UI Components:**
```kotlin
@Composable
fun AddManualTxnSheet(
    onSave: (amount: Double, type: String, merchant: String, channel: String, category: String) -> Unit,
    onDismiss: () -> Unit
) {
    // Material 3 bottom sheet with form fields
    // Input validation and error handling
    // Category and channel selection
}
```

## 🤖 AI-Powered Features

### 1. AI Insights & Chat

#### **Google AI Studio Integration**
Connect with Google AI Studio (Gemini) for personalized financial insights.

**Setup Process:**
1. **Get API Key**: Visit [Google AI Studio](https://aistudio.google.com/app/apikey)
2. **Create API Key**: Sign in and create a new API key
3. **Configure App**: Enter API key in AI Insights settings
4. **Enable Features**: Grant permission for AI insights and tips

**API Configuration:**
```kotlin
class AiService {
    private val geminiApiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent"
    
    suspend fun getInsights(transactions: List<TransactionForAi>): Result<AiInsights> {
        // Prepare transaction data for AI analysis
        // Send request to Gemini API
        // Parse and return insights
    }
}
```

#### **Financial Chat Assistant**
Interactive AI assistant for financial questions and advice.

**Capabilities:**
- **Spending Analysis**: "How am I spending my money?"
- **Budgeting Help**: "Help me create a budget"
- **Saving Tips**: "How can I save more money?"
- **Investment Advice**: "What should I invest in?"
- **Debt Management**: "How can I reduce my debt?"
- **Financial Goals**: "Help me set financial goals"

**Chat Interface:**
```kotlin
@Composable
fun AiInsightsScreen(viewModel: AiInsightsViewModel) {
    // Chat interface with message history
    // Input field with send button
    // Loading states and error handling
    // Message types: Text, Insights, Analysis
}
```

#### **AI-Generated Insights**
Comprehensive financial analysis powered by AI.

**Insight Types:**
- **KPIs**: Total spend, transaction counts, largest transactions
- **Breakdowns**: Category-wise and payment method analysis
- **Large Transactions**: Identification of significant expenses
- **Recurring Payments**: Detection of subscription services
- **Notes**: Personalized financial advice and recommendations

**Data Format:**
```json
{
  "kpis": {
    "total_spend_inr": 25000.0,
    "debit_count": 45,
    "credit_count": 12,
    "largest_txn_amount": 5000.0,
    "largest_txn_merchant": "Amazon",
    "unusual_spend_flag": false
  },
  "breakdowns": {
    "by_category": [{"name": "Food", "amount": 8000.0}],
    "by_rail": [{"name": "UPI", "amount": 15000.0}]
  },
  "large_txns": [{"date": "2024-01-15", "merchant": "Amazon", "amount": 5000.0}],
  "recurring": [{"name": "Netflix", "day_of_month": 15, "amount": 499.0}],
  "notes": "Your spending shows a healthy balance..."
}
```

### 2. Smart Caching & Performance

#### **Intelligent Caching**
File-based caching system for AI responses and analytics.

**Cache Features:**
- **Instant Loading**: Cached data loads immediately
- **Background Refresh**: Fresh data fetched in background
- **Timestamp Tracking**: Cache validation with timestamps
- **Automatic Cleanup**: Old cache files automatically removed

**Implementation:**
```kotlin
class InsightsCache(private val context: Context) {
    private val cacheFile = File(context.cacheDir, "ai_insights_cache.json")
    
    fun saveInsights(insights: AiInsights) {
        // Save insights to cache file with timestamp
    }
    
    fun loadInsights(): AiInsights? {
        // Load cached insights if valid
    }
    
    fun clearCache() {
        // Clear all cached data
    }
}
```

#### **Performance Optimizations**
- **Debouncing**: 4-second soft debounce for refresh button
- **Data Limiting**: Max 500 transactions sent to AI
- **Efficient UI**: LazyColumn with proper item management
- **Background Processing**: All I/O operations on Dispatchers.IO

## 💰 Budget Management

### 1. Budget Creation & Management

#### **Budget Entity**
Database entity for storing budget information.

```kotlin
@Entity(
    tableName = "budgets",
    indices = [Index(value = ["category"], unique = true)]
)
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,           // "Food", "Transport", etc.
    val monthlyLimit: Long,         // Amount in minor units (paise)
    val currency: String = "INR",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
```

#### **Budget DAO Operations**
Database operations for budget management.

```kotlin
@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: Budget)
    
    @Update
    suspend fun update(budget: Budget)
    
    @Delete
    suspend fun delete(budget: Budget)
    
    @Query("SELECT * FROM budgets WHERE isActive = 1")
    fun getAllActiveBudgets(): Flow<List<Budget>>
    
    @Query("SELECT * FROM budgets WHERE category = :category")
    suspend fun getBudgetByCategory(category: String): Budget?
    
    // Budget vs spending analysis
    @Query("""
        SELECT b.*, 
               COALESCE(SUM(t.amountMinor), 0) as spentAmount,
               (b.monthlyLimit - COALESCE(SUM(t.amountMinor), 0)) as remainingAmount
        FROM budgets b
        LEFT JOIN transactions t ON t.category = b.category 
            AND t.type = 'DEBIT' 
            AND t.ts >= :monthStart 
            AND t.ts <= :monthEnd
        WHERE b.isActive = 1
        GROUP BY b.id
    """)
    fun getBudgetAnalysis(monthStart: Long, monthEnd: Long): Flow<List<BudgetAnalysis>>
}
```

### 2. Budget UI & Visualization

#### **Budget Screen**
Comprehensive budget management interface.

**Features:**
- **Budget Overview**: Visual progress bars for all categories
- **Category Management**: Add, edit, and delete budget categories
- **Spending vs Budget**: Real-time comparison with alerts
- **Budget Alerts**: Notifications for budget breaches and warnings
- **Savings Goals**: Track progress towards financial objectives

**UI Components:**
```kotlin
@Composable
fun BudgetScreen(viewModel: BudgetViewModel) {
    LazyColumn {
        item { BudgetOverviewCard() }
        item { BudgetListCard() }
        item { SavingsGoalCard() }
        item { BudgetAlertsCard() }
    }
}
```

#### **Budget Progress Visualization**
Real-time progress bars with color-coded alerts.

**Color Coding:**
- **Green**: 0-70% of budget used
- **Yellow**: 70-90% of budget used
- **Red**: 90-100% of budget used
- **Dark Red**: Over budget

**Implementation:**
```kotlin
@Composable
fun BudgetProgressBar(
    spent: Double,
    budget: Double,
    modifier: Modifier = Modifier
) {
    val progress = (spent / budget).coerceAtMost(1.0)
    val color = when {
        progress >= 1.0 -> MaterialTheme.colorScheme.error
        progress >= 0.9 -> MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
        progress >= 0.7 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    
    LinearProgressIndicator(
        progress = progress.toFloat(),
        color = color,
        modifier = modifier
    )
}
```

### 3. Budget Alerts & Notifications

#### **Smart Notifications**
Intelligent budget alerts and spending notifications.

**Notification Types:**
- **Budget Breach**: Immediate alert when budget is exceeded
- **Budget Warning**: Alert at 80% of budget usage
- **Large Transaction**: Notification for transactions above threshold
- **Unusual Spending**: Alert for unusual spending patterns
- **Bill Reminders**: Proactive reminders for recurring payments

**Notification Manager:**
```kotlin
class NotificationManager(private val context: Context) {
    fun createBudgetBreachNotification(category: String, amount: Double) {
        val notification = NotificationCompat.Builder(context, BUDGET_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_budget_alert)
            .setContentTitle("Budget Exceeded!")
            .setContentText("$category budget exceeded by ₹${amount}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(category.hashCode(), notification)
    }
}
```

## 📊 Analytics & Insights

### 1. Comprehensive Analytics

#### **Monthly Overview**
Detailed financial summary for selected time periods.

**Metrics:**
- **Total Income**: Sum of all CREDIT transactions
- **Total Expenses**: Sum of all DEBIT transactions
- **Net Balance**: Income minus expenses
- **Transaction Counts**: Number of income and expense transactions
- **Average Amounts**: Average transaction sizes

**Implementation:**
```kotlin
@Composable
fun MonthlyOverviewCard(
    totalCredit: Double,
    totalDebit: Double,
    balance: Double,
    currencyFormat: NumberFormat
) {
    Card {
        Column {
            Text("Monthly Overview")
            Row {
                OverviewItem("Income", totalCredit, SuccessGreen, currencyFormat)
                OverviewItem("Expenses", totalDebit, ErrorRed, currencyFormat)
                OverviewItem("Balance", balance, 
                    if (balance >= 0) SuccessGreen else ErrorRed, currencyFormat)
            }
        }
    }
}
```

#### **Spending Patterns Analysis**
Advanced analysis of spending behavior and patterns.

**Analysis Types:**
- **Daily Patterns**: Day-of-week spending analysis
- **Hourly Patterns**: Time-of-day spending analysis
- **Monthly Trends**: Month-over-month comparisons
- **Seasonal Analysis**: Seasonal spending patterns
- **Merchant Analysis**: Top merchants and spending patterns

**Pattern Detection:**
```kotlin
fun analyzeSpendingPatterns(transactions: List<Transaction>): SpendingPatterns {
    val hourlySpending = mutableMapOf<Int, Double>()
    val dailySpending = mutableMapOf<Int, Double>()
    
    transactions.forEach { transaction ->
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = transaction.ts
        
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        
        hourlySpending[hour] = hourlySpending.getOrDefault(hour, 0.0) + transaction.amount
        dailySpending[dayOfWeek] = dailySpending.getOrDefault(dayOfWeek, 0.0) + transaction.amount
    }
    
    return SpendingPatterns(
        peakHour = hourlySpending.maxByOrNull { it.value }?.key ?: 0,
        peakDay = dailySpending.maxByOrNull { it.value }?.key ?: 0,
        hourlyDistribution = hourlySpending,
        dailyDistribution = dailySpending
    )
}
```

### 2. Visual Analytics

#### **Interactive Charts**
Vico-powered charts for data visualization.

**Chart Types:**
- **Bar Charts**: Category-wise spending breakdown
- **Line Charts**: Spending trends over time
- **Pie Charts**: Payment method distribution
- **Area Charts**: Cumulative spending analysis

**Chart Implementation:**
```kotlin
@Composable
fun CategoryBreakdownChart(
    categories: List<CategoryBreakdown>,
    currencyFormat: NumberFormat
) {
    val chartData = categories.map { 
        ChartEntry(it.amount.toFloat(), it.name)
    }
    
    Chart(
        chart = columnChart(
            data = chartData,
            columns = { entry ->
                column(
                    color = getCategoryColor(entry.extra),
                    width = 16.dp
                )
            }
        )
    )
}
```

#### **Weekly Spending Analysis**
Day-of-week spending patterns with visual representation.

**Features:**
- **Bar Chart**: Spending by day of week
- **Trend Analysis**: Weekly spending trends
- **Comparison**: Week-over-week comparisons
- **Insights**: Peak spending days and patterns

### 3. Advanced Analytics

#### **Savings Analysis**
Comprehensive savings tracking and analysis.

**Metrics:**
- **Savings Rate**: Percentage of income saved
- **Savings Goal Progress**: Progress towards savings targets
- **Emergency Fund**: Emergency fund tracking
- **Investment Tracking**: Investment progress monitoring

**Savings Calculation:**
```kotlin
fun calculateSavingsMetrics(
    income: Double,
    expenses: Double,
    savingsGoal: Double
): SavingsMetrics {
    val savings = income - expenses
    val savingsRate = if (income > 0) (savings / income) * 100 else 0.0
    val goalProgress = if (savingsGoal > 0) (savings / savingsGoal) * 100 else 0.0
    
    return SavingsMetrics(
        totalSavings = savings,
        savingsRate = savingsRate,
        goalProgress = goalProgress,
        isOnTrack = savingsRate >= 20.0 // 20% savings rate target
    )
}
```

#### **Financial Health Score**
Overall financial health assessment.

**Health Indicators:**
- **Spending Efficiency**: Income vs expense ratio
- **Savings Rate**: Percentage of income saved
- **Debt Management**: Debt-to-income ratio
- **Emergency Fund**: Emergency fund adequacy
- **Investment Progress**: Investment portfolio growth

## 🔐 Security & Privacy

### 1. Biometric Authentication

#### **Biometric Manager**
Comprehensive biometric authentication system.

**Features:**
- **Fingerprint Support**: Fingerprint authentication
- **Face Unlock**: Face recognition support
- **Fallback Options**: PIN/Password fallback
- **Secure Storage**: Encrypted preference storage

**Implementation:**
```kotlin
class BiometricManager(private val context: Context) {
    private val biometricManager = BiometricManager.from(context)
    
    fun isBiometricAvailable(): Boolean {
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }
    
    fun authenticate(callback: (Boolean) -> Unit) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("SmartExpenses Authentication")
            .setSubtitle("Use your biometric to access the app")
            .setNegativeButtonText("Cancel")
            .build()
        
        val biometricPrompt = BiometricPrompt(activity, callback)
        biometricPrompt.authenticate(promptInfo)
    }
}
```

#### **App Lock Configuration**
Configurable app lock settings.

**Options:**
- **Biometric Lock**: Require biometric authentication
- **App Lock**: Require authentication to open app
- **Timeout Settings**: Auto-lock after inactivity
- **Emergency Access**: Emergency access options

### 2. Data Encryption

#### **Secure Preferences**
Encrypted storage for sensitive data.

**Features:**
- **AES-256-GCM Encryption**: Military-grade encryption
- **Master Key Management**: Secure key generation and storage
- **Fallback Support**: Graceful degradation to regular preferences
- **Key Rotation**: Automatic key rotation support

**Implementation:**
```kotlin
class SecurePreferences(private val context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    fun saveApiKey(apiKey: String) {
        encryptedPrefs.edit()
            .putString("api_key", apiKey)
            .apply()
    }
    
    fun getApiKey(): String? {
        return encryptedPrefs.getString("api_key", null)
    }
}
```

#### **Database Security**
Secure database storage with encryption.

**Security Features:**
- **SQLite Encryption**: Database-level encryption
- **Access Control**: Restricted database access
- **Data Validation**: Input validation and sanitization
- **Audit Logging**: Security event logging

### 3. Privacy Protection

#### **Data Minimization**
Minimal data collection and processing.

**Principles:**
- **Local Processing**: All processing happens on device
- **No External APIs**: Core functionality works offline
- **User Control**: Complete control over data sharing
- **Transparent Processing**: Clear indication of data usage

#### **Privacy Compliance**
Compliance with privacy regulations and best practices.

**Compliance Features:**
- **Data Portability**: Easy export and deletion of all data
- **Consent Management**: Explicit consent for all data processing
- **Transparency**: Clear privacy policy and data usage
- **User Rights**: Full control over personal data

## 📤 Export & Backup

### 1. Data Export

#### **CSV Export**
Complete transaction data in spreadsheet format.

**Export Features:**
- **Complete Data**: All transaction fields included
- **Formatted Headers**: Clear column headers
- **Date Formatting**: Consistent date formatting
- **Currency Formatting**: Proper currency display
- **Category Mapping**: Human-readable categories

**CSV Format:**
```csv
Date,Time,Amount,Type,Merchant,Channel,Category,Bank,Account
2024-01-15,14:30:00,500.00,DEBIT,Swiggy,UPI,Food,HDFC Bank,****1234
2024-01-15,16:45:00,10000.00,CREDIT,Salary,IMPS,Income,HDFC Bank,****1234
```

#### **PDF Reports**
Formatted financial reports with summaries and charts.

**Report Features:**
- **Executive Summary**: Key financial metrics
- **Category Breakdown**: Visual spending analysis
- **Monthly Trends**: Historical spending patterns
- **Budget Analysis**: Budget vs actual spending
- **Recommendations**: AI-generated financial advice

**PDF Generation:**
```kotlin
class ExportManager(private val context: Context) {
    fun exportToPDF(transactions: List<Transaction>): Uri? {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        
        val canvas = page.canvas
        val paint = Paint()
        
        // Generate PDF content
        generatePDFContent(canvas, paint, transactions)
        
        document.finishPage(page)
        
        val file = File(context.getExternalFilesDir(null), "SmartExpenses_Report_${System.currentTimeMillis()}.pdf")
        val fos = FileOutputStream(file)
        document.writeTo(fos)
        document.close()
        fos.close()
        
        return FileProvider.getUriForFile(context, "com.dheeraj.smartexpenses.fileprovider", file)
    }
}
```

### 2. File Sharing

#### **Share Integration**
Direct sharing to other apps and cloud storage.

**Sharing Options:**
- **Email**: Direct email sharing
- **Cloud Storage**: Google Drive, Dropbox, OneDrive
- **Messaging**: WhatsApp, Telegram, SMS
- **File Managers**: Local file system access

**Share Implementation:**
```kotlin
fun shareFile(uri: Uri, mimeType: String, title: String) {
    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, "SmartExpenses Financial Report")
    }
    
    val chooser = Intent.createChooser(shareIntent, "Share Report")
    context.startActivity(chooser)
}
```

### 3. Backup & Restore

#### **Data Backup**
Comprehensive data backup functionality.

**Backup Features:**
- **Complete Backup**: All app data included
- **Incremental Backup**: Only changed data
- **Compressed Backup**: Space-efficient storage
- **Encrypted Backup**: Secure backup files

#### **Data Restore**
Easy data restoration from backup files.

**Restore Features:**
- **Selective Restore**: Choose what to restore
- **Conflict Resolution**: Handle data conflicts
- **Validation**: Verify backup integrity
- **Rollback**: Undo restore if needed

## 🧪 Testing & Quality Assurance

### 1. Unit Testing

#### **ViewModel Testing**
Comprehensive testing of ViewModel logic.

**Test Coverage:**
- **State Management**: UI state transitions
- **Business Logic**: Calculation and validation
- **Error Handling**: Error scenarios and recovery
- **Data Transformation**: Data processing logic

**Test Example:**
```kotlin
class BudgetViewModelTest {
    @Test
    fun `calculateBudgetProgress should return correct progress`() {
        val viewModel = BudgetViewModel(mockRepository)
        val budget = Budget(category = "Food", monthlyLimit = 10000)
        val spent = 7500.0
        
        val progress = viewModel.calculateBudgetProgress(budget, spent)
        
        assertEquals(0.75f, progress)
    }
}
```

#### **Repository Testing**
Testing of data layer operations.

**Test Coverage:**
- **Database Operations**: CRUD operations
- **Data Validation**: Input validation
- **Error Handling**: Database errors
- **Caching**: Cache behavior

### 2. Integration Testing

#### **SMS Parsing Tests**
End-to-end testing of SMS parsing functionality.

**Test Scenarios:**
- **Real SMS Messages**: Test with actual bank SMS
- **Edge Cases**: Unusual SMS formats
- **Error Handling**: Invalid SMS handling
- **Performance**: Large volume processing

**Test Example:**
```kotlin
@Test
fun `parseSms should correctly parse HDFC debit SMS`() {
    val smsBody = "Rs.500.00 debited from A/c **1234 on 15-Jan-24. Avl Bal Rs.25,000.00"
    val sender = "HDFCBK"
    
    val transaction = SmsParser.parse(sender, smsBody, System.currentTimeMillis())
    
    assertNotNull(transaction)
    assertEquals(50000L, transaction.amountMinor)
    assertEquals("DEBIT", transaction.type)
    assertEquals("HDFC Bank", transaction.bank)
}
```

#### **AI Integration Tests**
Testing of AI service integration.

**Test Coverage:**
- **API Calls**: Successful API requests
- **Error Handling**: API errors and timeouts
- **Data Processing**: Response parsing
- **Caching**: Cache behavior

### 3. Performance Testing

#### **Load Testing**
Testing with large datasets.

**Test Scenarios:**
- **Large SMS Volumes**: 10,000+ SMS messages
- **Database Performance**: Query performance
- **Memory Usage**: Memory consumption
- **Battery Impact**: Battery usage analysis

#### **Stress Testing**
Testing under extreme conditions.

**Test Scenarios:**
- **Rapid SMS Processing**: High-frequency SMS
- **Concurrent Operations**: Multiple simultaneous operations
- **Memory Pressure**: Low memory conditions
- **Network Issues**: Poor network connectivity

## 🔧 Configuration & Customization

### 1. App Configuration

#### **Settings Management**
Comprehensive app settings and configuration.

**Configuration Areas:**
- **SMS Processing**: Import range, bank selection
- **AI Features**: API key, endpoint configuration
- **Security**: Biometric settings, app lock
- **Notifications**: Alert preferences, notification channels
- **Export**: Export format preferences

#### **User Preferences**
User-customizable preferences and settings.

**Preference Categories:**
- **Display**: Theme, language, date format
- **Notifications**: Alert types, timing, frequency
- **Privacy**: Data sharing preferences
- **Performance**: Cache settings, background processing

### 2. Advanced Configuration

#### **Bank Configuration**
Customizable bank and SMS format configuration.

**Configuration Options:**
- **Bank Selection**: Choose which banks to process
- **SMS Format**: Custom SMS parsing patterns
- **Sender Mapping**: Bank sender number mapping
- **Validation Rules**: Custom validation rules

#### **AI Configuration**
Advanced AI service configuration.

**Configuration Options:**
- **API Endpoints**: Custom AI service endpoints
- **Request Timeout**: API request timeout settings
- **Retry Logic**: Retry configuration
- **Cache Settings**: Cache duration and size

## 🚀 Performance Optimization

### 1. Memory Management

#### **Efficient Data Structures**
Optimized data structures for performance.

**Optimizations:**
- **Lazy Loading**: Load data on demand
- **Data Pagination**: Paginate large datasets
- **Memory Pooling**: Reuse objects to reduce GC
- **Weak References**: Prevent memory leaks

#### **Background Processing**
Efficient background processing.

**Features:**
- **Coroutine Usage**: Non-blocking operations
- **Thread Pool**: Optimized thread management
- **Priority Queues**: Priority-based processing
- **Resource Management**: Proper resource cleanup

### 2. Database Optimization

#### **Query Optimization**
Optimized database queries.

**Optimizations:**
- **Indexed Queries**: Proper database indexing
- **Query Caching**: Cache frequent queries
- **Batch Operations**: Batch database operations
- **Connection Pooling**: Efficient connection management

#### **Data Compression**
Efficient data storage.

**Compression Features:**
- **Data Compression**: Compress large data
- **Image Optimization**: Optimize images
- **Cache Compression**: Compress cache data
- **Backup Compression**: Compress backup files

### 3. UI Performance

#### **Compose Optimization**
Optimized Jetpack Compose performance.

**Optimizations:**
- **Lazy Loading**: LazyColumn for large lists
- **State Management**: Efficient state updates
- **Recomposition**: Minimize recomposition
- **Memory Usage**: Optimize memory consumption

#### **Animation Performance**
Smooth animations and transitions.

**Features:**
- **Hardware Acceleration**: Use hardware acceleration
- **Animation Caching**: Cache animation data
- **Frame Rate**: Maintain 60fps
- **Memory Usage**: Optimize animation memory

---

This comprehensive feature documentation covers all aspects of the SmartExpenses application, from core SMS processing to advanced AI features, security, and performance optimization. Each feature is documented with implementation details, code examples, and configuration options to provide a complete understanding of the application's capabilities.
