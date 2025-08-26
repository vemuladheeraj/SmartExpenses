# Inflated Numbers Fixes - SmartExpenses App

## Problem Summary
The app was showing inflated numbers in income/expenses due to several issues in SMS parsing and transaction filtering:

1. **Balance amounts being picked as transactions** - Bank SMS often show "Available balance ₹1,20,000" before the actual transaction
2. **Promotional SMS being counted as credits** - Ads like "Cashback up to ₹2000" were being parsed as CREDIT ₹2000
3. **Database queries including transfers** - Totals were calculated including inter-account transfers
4. **Weak filtering** - Insufficient validation of transaction amounts and context

## Fixes Implemented

### 1. Enhanced Amount Extraction (`SmsParser.kt`)

**Before**: Simple regex that picked the first amount found
```kotlin
val amountStr = amtRegex.find(body)?.groupValues?.getOrNull(1)?.replace(",", "")
val amount = amountStr?.toDoubleOrNull() ?: return null
```

**After**: Context-aware amount extraction that avoids balances
```kotlin
private fun extractAmount(body: String): Double? {
    val all = amtRegex.findAll(body).map { m ->
        val v = m.groupValues[1].replace(",", "").toDoubleOrNull()
        Triple(m.range.first, m.range.last, v)
    }.filter { it.third != null && it.third!! in MIN_AMOUNT..MAX_AMOUNT }.toList()
    
    // Filter out amounts that look like balances
    val candidates = all.filterNot { (s,e,_) -> looksBalance(s,e) }
    
    // Pick amount closest to transaction keywords
    if (candidates.isNotEmpty()) {
        return candidates.minBy { (s, e, _) -> nearestTxnDist(s, e) }.third
    }
    
    // If everything looks like balance, pick smallest
    return all.minBy { it.third!! }.third
}
```

**Key improvements**:
- Amount validation: 1.0 to 1,000,000.0 (₹1 to ₹10 lakhs)
- Context analysis: 60-character window around amounts
- Balance detection: Filters out amounts near balance keywords
- Transaction proximity: Picks amount closest to transaction keywords

### 2. Stronger Promotional SMS Filtering

**Before**: Basic keyword filtering that could be bypassed
```kotlin
val looksPromo = adSpamKeywords.any { lower.contains(it) }
if (looksPromo) return null
```

**After**: Multi-layered filtering with transaction signal requirement
```kotlin
// Expanded promotional keywords
private val adSpamKeywords = listOf(
    "win","winner","prize","jackpot","promo","coupon","discount","sale",
    "limited time","hurry","cashback up to","offer valid","special offer",
    "exclusive offer","limited offer","flash sale","festival offer",
    "diwali offer","new year offer","christmas offer","holiday offer"
)

// Strong transaction signal requirement
private val txnSignal = Regex(
    "(?i)\\b(txn|transaction|utr|ref\\.?\\s*no|auth\\s*code|a/?c|acct|account|ending\\s*\\d{2,}|xx\\d{2,}|card|upi|imps|neft|pos|atm)\\b"
)

// Drop promotional SMS unless they have strong transaction signals
if (looksPromo && !txnSignal.containsMatchIn(body)) return null
```

### 3. Improved Balance and Statement Filtering

**Before**: Limited balance detection
```kotlin
private val balanceOnlyHints = listOf("available balance", "avl bal", "ledger balance", "closing balance")
```

**After**: Comprehensive balance and statement detection
```kotlin
private val balanceOnlyHints = listOf(
    "available balance", "avl bal", "ledger balance", "closing balance", 
    "current balance", "account balance", "bal", "balance"
)
private val statementHints = listOf(
    "statement is sent", "card statement", "minimum of rs", "total of rs", "due by",
    "monthly statement", "credit card statement", "statement generated"
)

// Drop balance-only SMS without transaction verbs
if (!hasCredWord && !hasDebWord && 
    (balanceOnlyHints.any { lower.contains(it) } || statementHints.any { lower.contains(it) })) {
    return null
}
```

### 4. Database Query Improvements (`TxnDao.kt`)

**Before**: Queries included all transactions including transfers
```kotlin
@Query("SELECT IFNULL(SUM(CASE WHEN type='DEBIT' THEN amount END),0) FROM transactions WHERE ts BETWEEN :start AND :end")
fun totalDebits(start: Long, end: Long): Flow<Double>
```

**After**: Filtered queries excluding transfers and suspicious amounts
```kotlin
@Query("""
    SELECT IFNULL(SUM(amount),0) 
    FROM transactions 
    WHERE ts BETWEEN :start AND :end 
    AND type = 'DEBIT' 
    AND type != 'TRANSFER'
    AND amount > 0 
    AND amount < 1000000
""")
fun totalDebitsFiltered(start: Long, end: Long): Flow<Double>
```

**Additional queries added**:
- `totalDebitsFiltered()` and `totalCreditsFiltered()` - exclude transfers and cap amounts
- `getSuspiciousTransactions()` - identify transactions > ₹1 lakh for debugging

### 5. Enhanced Transfer Detection (`HomeVm.kt`)

**Before**: Basic transfer detection with 10-minute window
```kotlin
val windowMs = 10 * 60 * 1000L // 10 minutes
```

**After**: Improved transfer detection with additional validation
```kotlin
val windowMs = 15 * 60 * 1000L // 15 minutes
val candidateChannels = setOf("UPI", "IMPS", "NEFT", "NETBANKING", "TRANSFER")

// Additional checks for transfer likelihood
val sameBank = a.bank == b.bank
val reasonableAmount = amount in 100.0..1000000.0 // ₹100 to ₹10 lakhs

if (channelOk && tailsOk && sameBank && reasonableAmount) {
    ids.add(a.id)
    ids.add(b.id)
}
```

**Key improvements**:
- Increased time window to 15 minutes
- Added bank matching requirement
- Amount range validation (₹100 to ₹10 lakhs)
- Better logging for debugging

### 6. Amount Validation Throughout the Pipeline

**Before**: No amount validation during import
```kotlin
SmsParser.parse(sender, body, ts)?.let { transaction ->
    dao.insert(transaction)
}
```

**After**: Amount validation at multiple stages
```kotlin
// In SmsParser.parse()
if (amount < MIN_AMOUNT || amount > MAX_AMOUNT) return null

// In HomeVm.importRecentSms()
if (transaction.amount in 1.0..1000000.0) {
    dao.insert(transaction)
} else {
    rejectedCount++
    Log.d("HomeVm", "Rejected transaction with amount: ${transaction.amount}")
}

// In HomeVm totals calculation
transactions.filter { 
    it.type == "DEBIT" && 
    !isInterAccountTransfer(it) && 
    it.id !in pairIds &&
    it.amount in 1.0..1000000.0
}.sumOf { it.amount }
```

### 7. Debug and Monitoring Tools

Added comprehensive debugging capabilities:
```kotlin
/** Debug function to show potential inflated numbers */
suspend fun debugInflatedNumbers(): String {
    // Shows suspicious transactions, high amounts, transfer counts
    // Helps identify remaining issues
}
```

## Expected Results

After implementing these fixes:

1. **Balance amounts** will no longer be counted as transactions
2. **Promotional SMS** will be filtered out unless they contain genuine transaction data
3. **Transfer pairs** will be properly detected and excluded from totals
4. **Amount validation** will prevent extremely high or low amounts from being processed
5. **Database totals** will reflect actual spending/income, not inflated numbers

## Usage

1. **Re-import SMS**: Use `reimportSms()` to clear existing data and import with new filters
2. **Monitor logs**: Check for "Rejected transaction" and "Detected transfer pair" messages
3. **Debug if needed**: Use `debugInflatedNumbers()` to identify any remaining issues
4. **Set account tails**: Configure `setAccountTails()` for better transfer detection

## Testing

The fixes should resolve:
- ✅ Balance amounts being counted as transactions
- ✅ Promotional SMS inflating credit totals
- ✅ Transfer pairs being double-counted
- ✅ Extremely high amounts from parsing errors
- ✅ Statement SMS being processed as transactions

Monitor the app after re-importing SMS to verify that totals are now reasonable and match your actual spending patterns.
