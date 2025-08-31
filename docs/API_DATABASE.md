# SmartExpenses - API & Database Documentation

## 🗄️ Database Schema

### Overview
SmartExpenses uses Room (SQLite) as its local database with the following entities:
- **Transactions**: Core transaction data from SMS and manual entries
- **Budgets**: Budget management and tracking
- **Categories**: Spending categories and configurations
- **AI Insights Cache**: Cached AI responses and analytics

### Database Configuration
```kotlin
@Database(
    entities = [Transaction::class, Budget::class, Category::class], 
    version = 1,
    exportSchema = true
)
abstract class AppDb : RoomDatabase() {
    abstract fun txnDao(): TxnDao
    abstract fun budgetDao(): BudgetDao
    abstract fun categoryDao(): CategoryDao
}
```

## 📊 Transaction Entity

### Schema Definition
```kotlin
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["rawSender", "rawBody", "ts"], unique = true),
        Index(value = ["ts"]),
        Index(value = ["type", "ts"]),
        Index(value = ["category"]),
        Index(value = ["merchant"]),
        Index(value = ["channel"])
    ]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,                    // Timestamp in milliseconds
    val amountMinor: Long,           // Amount in minor units (paise)
    val currency: String = "INR",    // Currency code
    val type: String,                // "DEBIT" | "CREDIT" | "TRANSFER"
    val channel: String?,            // Payment channel (UPI, CARD, etc.)
    val merchant: String?,           // Merchant name
    val category: String?,           // Spending category
    val accountTail: String?,        // Last 4 digits of account
    val bank: String?,               // Bank name
    val source: String = "SMS",      // "SMS" | "MANUAL"
    val rawSender: String,           // Original SMS sender
    val rawBody: String              // Original SMS body
)
```

### Field Descriptions

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `id` | Long | Auto-generated primary key | 1, 2, 3... |
| `ts` | Long | Transaction timestamp (epoch ms) | 1705312800000 |
| `amountMinor` | Long | Amount in paise (₹1 = 100 paise) | 50000 (₹500.00) |
| `currency` | String | Currency code | "INR" |
| `type` | String | Transaction type | "DEBIT", "CREDIT", "TRANSFER" |
| `channel` | String | Payment method | "UPI", "CARD", "IMPS" |
| `merchant` | String | Merchant/person name | "Swiggy", "Amazon" |
| `category` | String | Spending category | "Food", "Shopping" |
| `accountTail` | String | Account last 4 digits | "1234" |
| `bank` | String | Bank name | "HDFC Bank" |
| `source` | String | Data source | "SMS", "MANUAL" |
| `rawSender` | String | SMS sender number | "HDFCBK" |
| `rawBody` | String | Original SMS text | "Rs.500.00 debited..." |

### Transaction DAO (Data Access Object)

#### Basic CRUD Operations
```kotlin
@Dao
interface TxnDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(t: Transaction)
    
    @Update
    suspend fun update(t: Transaction)
    
    @Delete
    suspend fun delete(t: Transaction)
    
    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): Transaction?
}
```

#### Query Operations
```kotlin
// Date range queries
@Query("SELECT * FROM transactions WHERE ts BETWEEN :start AND :end ORDER BY ts DESC")
fun inRange(start: Long, end: Long): Flow<List<Transaction>>

@Query("SELECT * FROM transactions WHERE ts BETWEEN :start AND :end AND type = :type ORDER BY ts DESC")
fun inRangeByType(start: Long, end: Long, type: String): Flow<List<Transaction>>

@Query("SELECT * FROM transactions WHERE ts BETWEEN :start AND :end AND channel = :channel ORDER BY ts DESC")
fun inRangeByChannel(start: Long, end: Long, channel: String): Flow<List<Transaction>>

@Query("SELECT * FROM transactions WHERE ts BETWEEN :start AND :end AND merchant LIKE '%' || :merchant || '%' ORDER BY ts DESC")
fun inRangeByMerchant(start: Long, end: Long, merchant: String): Flow<List<Transaction>>
```

#### Aggregation Queries
```kotlin
// Total calculations
@Query("SELECT IFNULL(SUM(CASE WHEN type='DEBIT' THEN amountMinor END),0)/100.0 FROM transactions WHERE ts BETWEEN :start AND :end")
fun totalDebits(start: Long, end: Long): Flow<Double>

@Query("SELECT IFNULL(SUM(CASE WHEN type='CREDIT' THEN amountMinor END),0)/100.0 FROM transactions WHERE ts BETWEEN :start AND :end")
fun totalCredits(start: Long, end: Long): Flow<Double>

// Distinct values
@Query("SELECT DISTINCT merchant FROM transactions WHERE merchant IS NOT NULL AND merchant != '' ORDER BY merchant")
fun getAllMerchants(): Flow<List<String>>

@Query("SELECT DISTINCT channel FROM transactions WHERE channel IS NOT NULL AND channel != '' ORDER BY channel")
fun getAllChannels(): Flow<List<String>>
```

#### Duplicate Detection
```kotlin
@Query("SELECT COUNT(*) FROM transactions WHERE rawSender = :sender AND rawBody = :body AND ts = :timestamp")
suspend fun exists(sender: String, body: String, timestamp: Long): Int
```

#### Bulk Operations
```kotlin
@Query("DELETE FROM transactions WHERE source LIKE 'SMS%'")
suspend fun clearSmsTransactions()

@Query("DELETE FROM transactions")
suspend fun clearAllTransactions()
```

## 💰 Budget Entity

### Schema Definition
```kotlin
@Entity(
    tableName = "budgets",
    indices = [
        Index(value = ["category"], unique = true, name = "unique_budget_category")
    ]
)
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,           // Category name
    val monthlyLimit: Long,         // Monthly limit in paise
    val currency: String = "INR",   // Currency code
    val isActive: Boolean = true,   // Active status
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
```

### Budget DAO Operations
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

## 🏷️ Category Entity

### Schema Definition
```kotlin
@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["name"], unique = true)
    ]
)
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,               // Category name
    val icon: String,               // Icon identifier
    val color: String,              // Hex color code
    val isDefault: Boolean = false  // Default category flag
)
```

### Category DAO Operations
```kotlin
@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: Category)
    
    @Query("SELECT * FROM categories ORDER BY name")
    fun getAllCategories(): Flow<List<Category>>
    
    @Query("SELECT * FROM categories WHERE isDefault = 1")
    fun getDefaultCategories(): Flow<List<Category>>
    
    @Query("SELECT * FROM categories WHERE name = :name")
    suspend fun getCategoryByName(name: String): Category?
}
```

## 🤖 AI Service API

### Google AI Studio (Gemini) Integration

#### API Configuration
```kotlin
class AiService {
    private val geminiApiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent"
    private val customEndpointUrl: String? = null // User-configured endpoint
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
}
```

#### Request Format
```kotlin
data class GeminiRequest(
    val contents: List<GeminiContent>
)

data class GeminiContent(
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String
)
```

#### Response Format
```kotlin
data class GeminiResponse(
    val candidates: List<GeminiCandidate>
)

data class GeminiCandidate(
    val content: GeminiContent,
    val finishReason: String
)
```

#### AI Insights Data Models
```kotlin
@Serializable
data class AiInsights(
    val kpis: Kpis,
    val breakdowns: Breakdowns,
    @SerialName("large_txns")
    val largeTxns: List<LargeTransaction>,
    val recurring: List<RecurringPayment>,
    val notes: String
)

@Serializable
data class Kpis(
    @SerialName("total_spend_inr")
    val totalSpendInr: Double,
    @SerialName("debit_count")
    val debitCount: Int,
    @SerialName("credit_count")
    val creditCount: Int,
    @SerialName("largest_txn_amount")
    val largestTxnAmount: Double,
    @SerialName("largest_txn_merchant")
    val largestTxnMerchant: String?,
    @SerialName("unusual_spend_flag")
    val unusualSpendFlag: Boolean
)

@Serializable
data class Breakdowns(
    @SerialName("by_category")
    val byCategory: List<CategoryBreakdown>,
    @SerialName("by_rail")
    val byRail: List<RailBreakdown>
)

@Serializable
data class CategoryBreakdown(
    val name: String?,
    val amount: Double
)

@Serializable
data class RailBreakdown(
    val name: String,
    val amount: Double
)

@Serializable
data class LargeTransaction(
    val date: String, // YYYY-MM-DD
    val merchant: String?,
    val amount: Double
)

@Serializable
data class RecurringPayment(
    val name: String,
    @SerialName("day_of_month")
    val dayOfMonth: Int,
    val amount: Double
)
```

#### Transaction Data for AI
```kotlin
@Serializable
data class TransactionForAi(
    val ts: Long, // epoch ms
    val date: String, // YYYY-MM-DD
    val amount: Double,
    val direction: String, // "debit" or "credit"
    val merchant: String?,
    val rail: String?,
    val category: String?
)
```

### AI Service Implementation
```kotlin
class AiService {
    suspend fun getInsights(transactions: List<TransactionForAi>): Result<AiInsights> {
        return try {
            val prompt = buildPrompt(transactions)
            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(GeminiPart(text = prompt))
                    )
                )
            )
            
            val response = makeApiCall(request)
            val insights = parseResponse(response)
            Result.success(insights)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun buildPrompt(transactions: List<TransactionForAi>): String {
        val json = Json.encodeToString(transactions)
        return """
            Analyze the following financial transaction data and provide insights in the exact JSON format specified.
            
            Transaction Data:
            $json
            
            Please provide insights in this exact format:
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
        """.trimIndent()
    }
}
```

## 🔐 Security & Encryption

### Secure Preferences
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
    
    fun saveApiKey(apiKey: String): Result<Unit> {
        return try {
            encryptedPrefs.edit()
                .putString("api_key", apiKey)
                .apply()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun getApiKey(): String? {
        return encryptedPrefs.getString("api_key", null)
    }
    
    fun saveCustomEndpoint(endpoint: String): Result<Unit> {
        return try {
            encryptedPrefs.edit()
                .putString("custom_endpoint", endpoint)
                .apply()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun getCustomEndpoint(): String? {
        return encryptedPrefs.getString("custom_endpoint", null)
    }
    
    fun clearAllData(): Result<Unit> {
        return try {
            encryptedPrefs.edit().clear().apply()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

## 📤 Export API

### CSV Export
```kotlin
class ExportManager(private val context: Context) {
    fun exportToCSV(transactions: List<Transaction>): Uri? {
        return try {
            val csvContent = buildCSVContent(transactions)
            val file = createCSVFile(csvContent)
            FileProvider.getUriForFile(
                context, 
                "com.dheeraj.smartexpenses.fileprovider", 
                file
            )
        } catch (e: Exception) {
            Log.e("ExportManager", "CSV export failed", e)
            null
        }
    }
    
    private fun buildCSVContent(transactions: List<Transaction>): String {
        val csv = StringBuilder()
        
        // Headers
        csv.appendLine("Date,Time,Amount,Type,Merchant,Channel,Category,Bank,Account,Source")
        
        // Data rows
        transactions.forEach { transaction ->
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(Date(transaction.ts))
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                .format(Date(transaction.ts))
            val amount = transaction.amountMinor / 100.0
            val account = transaction.accountTail ?: "N/A"
            
            csv.appendLine("$date,$time,$amount,${transaction.type},${transaction.merchant ?: "N/A"},${transaction.channel ?: "N/A"},${transaction.category ?: "N/A"},${transaction.bank ?: "N/A"},$account,${transaction.source}")
        }
        
        return csv.toString()
    }
}
```

### PDF Export
```kotlin
fun exportToPDF(transactions: List<Transaction>): Uri? {
    return try {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        
        val canvas = page.canvas
        val paint = Paint()
        
        generatePDFContent(canvas, paint, transactions)
        
        document.finishPage(page)
        
        val file = File(
            context.getExternalFilesDir(null), 
            "SmartExpenses_Report_${System.currentTimeMillis()}.pdf"
        )
        val fos = FileOutputStream(file)
        document.writeTo(fos)
        document.close()
        fos.close()
        
        FileProvider.getUriForFile(
            context, 
            "com.dheeraj.smartexpenses.fileprovider", 
            file
        )
    } catch (e: Exception) {
        Log.e("ExportManager", "PDF export failed", e)
        null
    }
}
```

## 🔔 Notification API

### Notification Manager
```kotlin
class NotificationManager(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    init {
        createNotificationChannels()
    }
    
    private fun createNotificationChannels() {
        val channels = listOf(
            NotificationChannel(
                BUDGET_CHANNEL_ID,
                "Budget Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for budget breaches and warnings"
                enableVibration(true)
                setShowBadge(true)
            },
            NotificationChannel(
                TRANSACTION_CHANNEL_ID,
                "Transaction Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for large transactions and spending patterns"
                enableVibration(false)
                setShowBadge(true)
            }
        )
        
        channels.forEach { channel ->
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    fun createBudgetBreachNotification(category: String, amount: Double) {
        val notification = NotificationCompat.Builder(context, BUDGET_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_budget_alert)
            .setContentTitle("Budget Exceeded!")
            .setContentText("$category budget exceeded by ₹${String.format("%.2f", amount)}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
        
        notificationManager.notify(category.hashCode(), notification)
    }
    
    fun createLargeTransactionNotification(amount: Double, merchant: String) {
        val notification = NotificationCompat.Builder(context, TRANSACTION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_transaction_alert)
            .setContentTitle("Large Transaction")
            .setContentText("₹${String.format("%.2f", amount)} spent at $merchant")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        
        notificationManager.notify(merchant.hashCode(), notification)
    }
}
```

## 📱 SMS Processing API

### SMS Parser
```kotlin
class SmsParser {
    companion object {
        // Bank sender mapping
        private val bankFromSender = mapOf(
            "HDFCBK" to "HDFC Bank",
            "ICICIB" to "ICICI Bank",
            "AXISBK" to "Axis Bank",
            "KOTAK" to "Kotak Bank",
            "SBICRD" to "SBI Card",
            "PNB" to "Punjab National Bank",
            "CANARA" to "Canara Bank",
            "BOI" to "Bank of India"
            // ... more banks
        )
        
        // Amount extraction regex
        private val amountRegex = Regex("""(?i)(?:rs\.?|inr|₹)\s*([0-9,]+\.?[0-9]*)""")
        private val amountAltRegex = Regex("""([0-9,]+\.?[0-9]*)\s*(?:rs\.?|inr|₹)""")
        
        // Transaction type detection
        private val creditKeywords = listOf("credited", "received", "deposited", "successful")
        private val debitKeywords = listOf("debited", "withdrawn", "deducted", "sent", "paid")
        
        // Payment channel detection
        private val channelKeywords = mapOf(
            "UPI" to listOf("upi", "gpay", "phonepe", "paytm", "bhim"),
            "IMPS" to listOf("imps"),
            "NEFT" to listOf("neft"),
            "RTGS" to listOf("rtgs"),
            "CARD" to listOf("card", "pos"),
            "ATM" to listOf("atm", "withdrawal")
        )
    }
    
    fun parse(sender: String, body: String, ts: Long): Transaction? {
        return try {
            // Spam detection
            if (SpamDetector.isSpamMessage(body)) return null
            
            // Amount extraction
            val amount = extractAmount(body) ?: return null
            if (!AmountValidator.isValidTransactionAmount(amount, body)) return null
            
            // Transaction type detection
            val type = determineTransactionType(body) ?: return null
            
            // Transfer detection
            val isTransfer = TransferDetector.isTransfer(body, amount, type)
            val finalType = if (isTransfer) "TRANSFER" else type
            
            // Channel detection
            val channel = extractChannel(body)
            
            // Merchant extraction
            val merchant = extractMerchant(body)
            
            // Account tail extraction
            val accountTail = extractAccountTail(body)
            
            Transaction(
                ts = ts,
                amountMinor = amount,
                type = finalType,
                channel = channel,
                merchant = merchant,
                category = null, // Will be set later
                accountTail = accountTail,
                bank = bankFromSender[sender] ?: "Unknown",
                source = "SMS_REGEX",
                rawSender = sender,
                rawBody = body
            )
        } catch (e: Exception) {
            Log.e("SmsParser", "Failed to parse SMS", e)
            null
        }
    }
    
    private fun extractAmount(body: String): Long? {
        val match = amountRegex.find(body) ?: amountAltRegex.find(body) ?: return null
        val amountStr = match.groupValues[1].replace(",", "")
        return try {
            val amount = BigDecimal(amountStr)
            (amount * BigDecimal(100)).toLong()
        } catch (e: Exception) {
            null
        }
    }
    
    private fun determineTransactionType(body: String): String? {
        val lowerBody = body.lowercase()
        
        return when {
            creditKeywords.any { lowerBody.contains(it) } -> "CREDIT"
            debitKeywords.any { lowerBody.contains(it) } -> "DEBIT"
            else -> null
        }
    }
    
    private fun extractChannel(body: String): String? {
        val lowerBody = body.lowercase()
        
        return channelKeywords.entries.find { (_, keywords) ->
            keywords.any { lowerBody.contains(it) }
        }?.key
    }
    
    private fun extractMerchant(body: String): String? {
        // Extract merchant from patterns like "to MERCHANT", "at MERCHANT", "from MERCHANT"
        val patterns = listOf(
            Regex("""(?i)(?:to|at|from)\s+([A-Za-z0-9\s&._-]{3,40})"""),
            Regex("""(?i)(?:paid to|received from)\s+([A-Za-z0-9\s&._-]{3,40})""")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(body)
            if (match != null) {
                val merchant = match.groupValues[1].trim()
                if (merchant.length >= 3 && !isBankName(merchant)) {
                    return merchant
                }
            }
        }
        
        return null
    }
    
    private fun extractAccountTail(body: String): String? {
        val pattern = Regex("""(?i)(?:a/c|account)\s*[*]*([0-9]{4})""")
        val match = pattern.find(body)
        return match?.groupValues?.get(1)
    }
}
```

### SMS Receiver
```kotlin
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            
            for (message in messages) {
                val sender = message.originatingAddress ?: continue
                val body = message.messageBody ?: continue
                val timestamp = message.timestampMillis
                
                // Process SMS in background
                CoroutineScope(Dispatchers.IO).launch {
                    processSms(context, sender, body, timestamp)
                }
            }
        }
    }
    
    private suspend fun processSms(context: Context, sender: String, body: String, timestamp: Long) {
        try {
            val db = AppDb.get(context)
            val txnDao = db.txnDao()
            
            // Check if SMS already exists
            val exists = txnDao.exists(sender, body, timestamp)
            if (exists > 0) return
            
            // Parse SMS
            val transaction = SmsParser.parse(sender, body, timestamp)
            if (transaction != null) {
                txnDao.insert(transaction)
                Log.d("SmsReceiver", "Processed transaction: ${transaction.merchant} - ₹${transaction.amountMinor / 100}")
            }
        } catch (e: Exception) {
            Log.e("SmsReceiver", "Failed to process SMS", e)
        }
    }
}
```

## 🔄 Data Flow

### Transaction Processing Flow
```
SMS Received → SmsReceiver → SmsParser → Validation → Database → UI Update
     ↓              ↓           ↓           ↓          ↓         ↓
  Broadcast    Background   Regex      Amount/     Room     Compose
  Receiver     Coroutine   Parsing    Spam        Database  State
                              ↓        Check         ↓
                         Transaction              Flow
                         Object                   Update
```

### AI Insights Flow
```
User Request → AI Service → API Call → Response → Cache → UI Display
     ↓            ↓          ↓         ↓        ↓        ↓
  ViewModel   Repository   Gemini    JSON    File    Compose
     ↓            ↓          ↓      Parse   Cache    State
  State      Data Prep    HTTP     Result   Store    Update
  Update     Transform    Client   Parse    Load
```

### Budget Management Flow
```
Budget Set → Database → Analysis → Alerts → Notifications → UI Update
     ↓          ↓         ↓        ↓         ↓            ↓
  User Input   Room     Query    Check    Manager      Compose
     ↓       Database  Budget   Threshold  Send        State
  Validation  Insert    vs       Breach    Alert       Update
              Update    Spend    Warning   Notification
```

## 📊 Performance Considerations

### Database Optimization
- **Indexes**: Proper indexing on frequently queried columns
- **Pagination**: Lazy loading for large datasets
- **Batch Operations**: Bulk inserts and updates
- **Query Optimization**: Efficient SQL queries with proper joins

### Memory Management
- **Flow Usage**: Reactive data streams with automatic cleanup
- **Coroutine Scopes**: Proper scope management for background operations
- **Cache Management**: Intelligent caching with size limits
- **Object Pooling**: Reuse of expensive objects

### Network Optimization
- **Request Debouncing**: Prevent excessive API calls
- **Retry Logic**: Exponential backoff for failed requests
- **Timeout Configuration**: Appropriate timeouts for different operations
- **Response Caching**: Cache API responses to reduce network usage

---

This comprehensive API and database documentation provides detailed information about all data structures, APIs, and data flow patterns used in the SmartExpenses application. It serves as a reference for developers working with the codebase and understanding the application's architecture.
