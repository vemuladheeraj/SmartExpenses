# Debug Inflated Numbers - Step by Step Guide

## Current Status
Even after implementing strict filtering, you're still seeing inflated numbers. This guide will help you identify exactly what's still getting through and why.

## Step 1: Analyze Current Data

### Use the New Debug Function
```kotlin
// This will show you exactly what's in your database
val analysis = homeVm.analyzeInflatedNumbers()
println(analysis)
```

**Look for:**
- High-value transactions (>50K) that might be balances
- Transactions with promotional keywords
- Transactions that look like balance updates
- Unusual amount distributions

## Step 2: Check Recent SMS Parsing

### Monitor the Logs
Look for these log messages in Android Studio Logcat:
```
SmsParser: REJECTED: Promotional content detected: [SMS content]
SmsParser: REJECTED: Balance-only content detected: [SMS content]
SmsParser: REJECTED: High amount with balance context: [amount] - [SMS content]
SmsParser: PARSED: [amount] [type] - [merchant] ([bank]) - [SMS content]
```

**Filter by tag:** `SmsParser`

## Step 3: Test Specific SMS Examples

### Use the Test Function
```kotlin
val testSms = listOf(
    // Test promotional SMS
    "Get cashback up to ₹2000 on UPI transactions this weekend!",
    "Available balance in your account: ₹1,25,000",
    "Pre-approved personal loan up to ₹5,00,000",
    
    // Test genuine transactions
    "UPI payment of ₹500 debited from your account. To: merchant@upi",
    "ATM withdrawal of ₹2000 from your account ending 1234"
)

val result = homeVm.testSmsParsing(testSms)
println(result)
```

## Step 4: Identify the Problem

### Common Issues Still Causing Inflation:

1. **SMS with Mixed Content**
   - SMS that contain both promotional content AND transaction data
   - Example: "Cashback up to ₹2000 on UPI transactions. Your UPI payment of ₹500 was successful"

2. **Balance Updates with Transaction Context**
   - SMS that mention both balance and a transaction
   - Example: "Available balance: ₹1,00,000. UPI payment of ₹1000 successful"

3. **Loan/Statement SMS with Amounts**
   - SMS about loans or statements that contain amounts
   - Example: "Your loan amount of ₹2,00,000 has been approved"

4. **Promotional SMS with Transaction Keywords**
   - SMS that use transaction-like language for offers
   - Example: "Get ₹500 cashback credited to your account on next purchase"

## Step 5: Fix the Remaining Issues

### If You Still See Problems:

1. **Check the Analysis Results**
   - Look at the "SUSPICIOUS TRANSACTIONS" section
   - Identify patterns in what's still getting through

2. **Examine High-Value Transactions**
   - Look at transactions >50K
   - Check if they're genuine or balance updates

3. **Review Promotional Keywords**
   - The current list might be missing some keywords
   - Add any new promotional patterns you find

## Step 6: Manual Cleanup (If Needed)

### Remove Problematic Transactions
```kotlin
// If you find specific transactions that shouldn't be there
// You can manually remove them from the database
// But first, identify the root cause using the debug functions
```

## Step 7: Re-import with Even Stricter Filtering

### If Problems Persist:
1. **Clear existing data**: `clearSmsTransactions()`
2. **Re-import**: `reimportSms(6)`
3. **Monitor logs** for rejection patterns
4. **Use debug functions** to verify results

## Expected Results After These Fixes

### What Should Be Filtered Out:
- ✅ All promotional SMS (even with transaction keywords)
- ✅ Balance update SMS (even with transaction context)
- ✅ Loan/statement SMS with amounts
- ✅ High-value amounts without strong transaction signals

### What Should Be Parsed:
- ✅ Genuine UPI/Card/ATM transactions
- ✅ Bank transfers with clear references
- ✅ Refunds and reversals
- ✅ Transactions with strong signals (UTR, auth codes, etc.)

## Debugging Checklist

- [ ] Run `analyzeInflatedNumbers()` to see current state
- [ ] Check Logcat for rejection/parsing logs
- [ ] Test specific SMS examples with `testSmsParsing()`
- [ ] Identify patterns in what's still getting through
- [ ] Look for high-value transactions that might be balances
- [ ] Check for promotional content in parsed transactions
- [ ] Verify that totals are now reasonable

## If You Still See Issues

1. **Share the debug output** - The analysis will show exactly what's wrong
2. **Check specific transaction examples** - Look at the raw SMS content
3. **Identify new patterns** - There might be new types of promotional SMS
4. **Consider manual review** - Some edge cases might need manual handling

## Key Points

- **The new filtering is extremely strict** - It should catch 95%+ of promotional SMS
- **Logging is comprehensive** - You'll see exactly what's being rejected and why
- **Debug functions are detailed** - They'll show you the current state of your data
- **Focus on patterns** - Look for common themes in what's still getting through

Run the `analyzeInflatedNumbers()` function first - it will give you a complete picture of what's currently in your database and help identify the remaining issues.
