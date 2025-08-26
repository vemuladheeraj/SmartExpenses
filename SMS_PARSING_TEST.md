# SMS Parsing Test - Before vs After Fixes

## Test Cases for Promotional SMS (Should be REJECTED)

### 1. Cashback Promotional SMS
**SMS**: "Get cashback up to ₹2000 on UPI transactions this weekend! Limited time offer."
- **Before**: ❌ Would be parsed as CREDIT ₹2000 (inflating totals)
- **After**: ✅ REJECTED (promotional content without strong transaction signals)

### 2. Balance Update SMS
**SMS**: "Available balance in your account: ₹1,25,000. Last updated: 15:30"
- **Before**: ❌ Would be parsed as CREDIT ₹1,25,000 (major inflation)
- **After**: ✅ REJECTED (balance-only content without transaction verbs)

### 3. Loan Offer SMS
**SMS**: "Pre-approved personal loan up to ₹5,00,000. Interest rate 12.99%. Apply now!"
- **Before**: ❌ Would be parsed as CREDIT ₹5,00,000 (huge inflation)
- **After**: ✅ REJECTED (loan promotional content)

### 4. Statement SMS
**SMS**: "Your credit card statement is ready. Total due: ₹15,000. Due by 25th."
- **Before**: ❌ Would be parsed as DEBIT ₹15,000 (wrong transaction)
- **After**: ✅ REJECTED (statement content without transaction verbs)

### 5. Festival Offer SMS
**SMS**: "Diwali special offer! Get 20% discount on all purchases. Valid till 15th Nov."
- **Before**: ❌ Might be parsed if amount found
- **After**: ✅ REJECTED (festival promotional content)

## Test Cases for Genuine Transactions (Should be PARSED)

### 1. UPI Payment
**SMS**: "UPI payment of ₹500 debited from your account. To: merchant@upi. Ref: 123456"
- **Before**: ✅ Parsed correctly
- **After**: ✅ Parsed correctly

### 2. ATM Withdrawal
**SMS**: "ATM withdrawal of ₹2000 from your account ending 1234. Available balance: ₹50,000"
- **Before**: ❌ Might pick balance amount ₹50,000
- **After**: ✅ Parsed correctly (picks transaction amount ₹2000, ignores balance)

### 3. Card Purchase
**SMS**: "Card purchase of ₹1500 at AMAZON. Available balance: ₹75,000"
- **Before**: ❌ Might pick balance amount ₹75,000
- **After**: ✅ Parsed correctly (picks transaction amount ₹1500, ignores balance)

### 4. Bank Transfer
**SMS**: "IMPS transfer of ₹10,000 credited to your account. From: SENDER BANK"
- **Before**: ✅ Parsed correctly
- **After**: ✅ Parsed correctly

## How to Test the New Filtering

### 1. Use the Debug Function
```kotlin
// Test specific SMS examples
val testSms = listOf(
    "Get cashback up to ₹2000 on UPI transactions this weekend!",
    "Available balance in your account: ₹1,25,000",
    "UPI payment of ₹500 debited from your account. To: merchant@upi"
)

val result = homeVm.testSmsParsing(testSms)
println(result)
```

### 2. Check Recent Parsing Results
```kotlin
val recentResults = homeVm.debugRecentSmsParsing()
println(recentResults)
```

### 3. Monitor Import Logs
Look for these log messages:
- "Rejected transaction with amount: X" - Amount validation failed
- "SMS parsing failed for: SENDER" - Parsing completely failed
- "Detected transfer pair" - Transfer detection working

## Expected Results After Fixes

### ✅ What Should Be Filtered Out:
1. **All promotional SMS** without genuine transaction data
2. **Balance update SMS** (Available balance, closing balance, etc.)
3. **Statement SMS** (bills, due amounts, etc.)
4. **Loan offer SMS** (pre-approved, interest rates, etc.)
5. **Festival/seasonal offer SMS**

### ✅ What Should Be Parsed:
1. **Genuine transactions** with clear amounts and transaction verbs
2. **UPI/Card/ATM transactions** with proper context
3. **Bank transfers** with clear sender/receiver information
4. **Refunds and reversals** with transaction references

### 📊 Expected Impact on Totals:
- **Before**: Totals inflated by 2-10x due to balance amounts and promotional SMS
- **After**: Totals should reflect actual spending/income patterns
- **Reduction**: Expect 60-80% reduction in inflated amounts

## Testing Steps

1. **Clear existing SMS data** using `clearSmsTransactions()`
2. **Re-import SMS** using `reimportSms(6)` (last 6 months)
3. **Monitor logs** for rejection counts and reasons
4. **Check totals** - they should now be much more reasonable
5. **Use debug functions** to verify filtering is working correctly

## Common Issues to Watch For

1. **False positives**: Genuine transactions being rejected
2. **False negatives**: Promotional SMS still being parsed
3. **Amount confusion**: Wrong amounts being picked from complex SMS
4. **Transfer detection**: Inter-account transfers not being properly identified

If you still see inflated numbers after these fixes, use the debug functions to identify exactly which SMS are slipping through and why.
