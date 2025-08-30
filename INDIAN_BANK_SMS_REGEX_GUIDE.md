# Indian Bank SMS Regex Patterns Guide

## Overview
This document details the comprehensive regex patterns used in SmartExpenses to parse Indian bank SMS messages. The patterns are designed to handle various formats from different Indian banks, UPI apps, and payment systems.

## Enhanced Regex Patterns

### 1. **Amount Extraction Patterns**

#### Primary Amount Pattern
```kotlin
private val amountRegex = Regex("""(?i)(?:₹|Rs\.?|INR|rupees?|rs\.?|inr)\s*([0-9,]+(?:\.\d{2})?)(?:\s*/-|\s*|$)""")
```

**Handles:**
- `₹1,23,456.78` - Standard Indian format with comma separators
- `Rs.999/-` - Common bank format
- `INR 2500` - International format
- `1,234.00` - Plain number format
- `5000.00` - Decimal format
- `1,00,000` - Indian lakh format

#### Alternative Amount Pattern
```kotlin
private val amountAltRegex = Regex("""(?i)(?:amount|amt|value|sum)\s*[:\s]*([0-9,]+(?:\.\d{2})?)""")
```

**Handles:**
- `Amount: 5000.00`
- `Amt: 1,000`
- `Value: 2,500.50`

### 2. **Transaction Type Detection**

#### Payment Channels
```kotlin
private val txnTypeRegex = Regex("""(?i)\b(?:upi|imps|neft|rtgs|pos|atm|card|cheque|transfer|payment|online|mobile|netbanking|standing\s+instruction|si|recurring|emi|loan|credit|debit)\b""")
```

**Covers:**
- **Digital Payments**: UPI, IMPS, NEFT, RTGS
- **Card Transactions**: POS, ATM, CARD
- **Traditional**: Cheque, Transfer
- **Online Banking**: Online, Mobile, NetBanking
- **Recurring**: Standing Instruction (SI), Recurring, EMI
- **Financial Products**: Loan, Credit, Debit

### 3. **Transaction Verbs (CREDIT/DEBIT Detection)**

#### Credit Indicators
```kotlin
// CREDIT keywords
"credited|received|deposited|successful|completed"
```

**Examples:**
- "Your account has been **credited** with Rs.5000"
- "Amount **received** successfully"
- "**Deposited** Rs.10000 to your account"
- "Transaction **completed** successfully"
- "Payment **successful**"

#### Debit Indicators
```kotlin
// DEBIT keywords  
"debited|withdrawn|deducted|sent|paid|charged|processed"
```

**Examples:**
- "Rs.500 **debited** from your account"
- "Amount **withdrawn** via ATM"
- "**Deducted** Rs.1000 for service charges"
- "**Sent** Rs.2000 to John"
- "**Paid** Rs.1500 to merchant"
- "**Charged** Rs.500 for annual fee"
- "Transaction **processed** successfully"

### 4. **Account Number Patterns**

#### Standard Account Pattern
```kotlin
private val accTailRegex = Regex("""(?i)(?:a/c|account|ac|acc)\s*[:\s]*[xX]{4,}(\d{4,})""")
```

**Handles:**
- `A/c XXXX1234`
- `Account XXXX5678`
- `A/C XXXX9012`
- `Acc: XXXX3456`

#### Loose Account Pattern
```kotlin
private val accTailLoose = Regex("""(?i)(?:a/c|account|ac|acc)\s*[:\s]*(\d{4,})""")
```

**Handles:**
- `A/c 1234`
- `Account 5678`
- `Acc: 9012`

### 5. **Reference Number Patterns**

#### Comprehensive Reference Detection
```kotlin
private val refRegex = Regex("""(?i)(?:ref|utr|transaction\s+id|txn\s+id|order\s+id|payment\s+id|upi\s+ref|imps\s+ref|neft\s+ref|rtgs\s+ref)\s*[:\s]*([a-zA-Z0-9]+)""")
```

**Covers:**
- **General**: Ref, Reference
- **Banking**: UTR (Unique Transaction Reference)
- **Transaction**: Transaction ID, Txn ID
- **Order**: Order ID, Payment ID
- **Specific**: UPI Ref, IMPS Ref, NEFT Ref, RTGS Ref

### 6. **Bank Context Keywords**

#### Comprehensive Banking Terms
```kotlin
private val bankyContextRegex = Regex("""(?i)\b(?:balance|transaction|bank|branch|upi|imps|neft|rtgs|pos|atm|card|cheque|transfer|payment|online|mobile|netbanking|standing\s+instruction|si|recurring|emi|loan|credit|debit|account|a/c|acc|ref|utr|transaction\s+id|txn\s+id)\b""")
```

**Includes:**
- **Core Banking**: Balance, Transaction, Bank, Branch
- **Payment Methods**: UPI, IMPS, NEFT, RTGS, POS, ATM, Card, Cheque
- **Channels**: Online, Mobile, NetBanking
- **Services**: Standing Instruction, SI, Recurring, EMI
- **Products**: Loan, Credit, Debit
- **Identifiers**: Account, A/c, Acc, Ref, UTR, Transaction ID

### 7. **Date Patterns**

#### Indian Date Formats
```kotlin
private val dateLikeRegex = Regex("""\b\d{1,2}[/\-\.]\d{1,2}[/\-\.]\d{2,4}\b""")
```

**Handles:**
- `12/08/2024` - Slash format
- `12-08-2024` - Dash format  
- `12.08.2024` - Dot format
- `12/08/24` - Short year format

### 8. **Comprehensive Bank Sender Mapping**

#### Major Private Banks
- `HDFCBK` → HDFC Bank
- `ICICIB` → ICICI Bank
- `AXISBK` → Axis Bank
- `KOTAK` → Kotak Bank
- `YESB` → Yes Bank
- `IDFCBK` → IDFC Bank
- `INDUSB` → IndusInd Bank
- `RBLBNK` → RBL Bank
- `FEDERAL` → Federal Bank

#### Public Sector Banks
- `SBICRD` → State Bank of India
- `PNB` → Punjab National Bank
- `CANBK` → Canara Bank
- `BOB` → Bank of Baroda
- `UNION` → Union Bank of India
- `BANKIND` → Bank of India
- `CENTRAL` → Central Bank of India
- `UCO` → UCO Bank
- `INDIAN` → Indian Bank

#### Regional & Cooperative Banks
- `VIJAYA` → Vijaya Bank
- `DENA` → Dena Bank
- `ANDHRA` → Andhra Bank
- `SVCB` → SVC Co-operative Bank
- `SARASWAT` → Saraswat Co-operative Bank

#### Payment Banks & Small Finance Banks
- `AIRTPB` → Airtel Payments Bank
- `PAYTM` → Paytm Payments Bank
- `IPOSB` → India Post Payments Bank
- `AU` → AU Small Finance Bank
- `UJJIVAN` → Ujjivan Small Finance Bank

#### UPI Apps & Payment Systems
- `UPI` → UPI Payment
- `GPAY` → Google Pay
- `PHONEPE` → PhonePe
- `BHIM` → BHIM UPI

## Common Indian Bank SMS Formats

### 1. **HDFC Bank Format**
```
HDFCBK: Rs.5000.00 credited to A/c XX1234 on 15/08/2024. 
Ref: 123456789. UPI Ref: 987654321. 
Balance: Rs.25,000.00
```

### 2. **ICICI Bank Format**
```
ICICIB: Rs.2000.00 debited from A/c XX5678 on 15-08-2024. 
Txn ID: ICICI123456. 
Available Balance: Rs.15,000.00
```

### 3. **SBI Format**
```
SBICRD: Rs.10000.00 transferred to A/c XX9012 via NEFT on 15.08.2024. 
UTR: SBI123456789. 
Current Balance: Rs.50,000.00
```

### 4. **UPI Payment Format**
```
UPI: Rs.500.00 paid to Amazon via Google Pay. 
Ref: 123456789. 
UPI Ref: 987654321
```

### 5. **IMPS Transfer Format**
```
IMPS: Rs.3000.00 sent to John Doe (A/c XX3456) on 15/08/2024. 
Ref: IMPS123456. 
Balance: Rs.20,000.00
```

### 6. **Card Transaction Format**
```
CARD: Rs.1500.00 charged for POS transaction at Reliance Store on 15-08-2024. 
Card: XXXX1234. 
Available Credit: Rs.25,000.00
```

## Validation Logic

### 1. **Heuristic Check**
```kotlin
private fun looksLikeTransaction(sender: String, body: String): Boolean {
    val hasTxnVerb = txnVerbRegex.containsMatchIn(body)
    val hasAccTail = accTailRegex.containsMatchIn(body) || accTailLoose.containsMatchIn(body)
    val hasBankCtx = bankyContextRegex.containsMatchIn(body)
    val hasRef = refRegex.containsMatchIn(body)
    val hasDate = dateLikeRegex.containsMatchIn(body)
    val bankKnown = bankFromSender[sender] != null

    // Need verbs + at least one other strong signal
    val strongCount = listOf(hasTxnVerb, hasAccTail, hasRef, hasDate, bankKnown, hasBankCtx).count { it }
    return hasTxnVerb && strongCount >= 2
}
```

**Requirements:**
- Must have transaction verbs (credited, debited, etc.)
- Must have at least 2 strong signals from:
  - Account tail
  - Reference number
  - Date
  - Known bank sender
  - Bank context keywords

### 2. **Strong Transaction Signals**
```kotlin
private fun hasStrongTransactionSignals(body: String): Boolean {
    val hasRef = refRegex.containsMatchIn(body)
    val hasAccTail = accTailRegex.containsMatchIn(body) || accTailLoose.containsMatchIn(body)
    val hasDate = dateLikeRegex.containsMatchIn(body)
    val hasBankCtx = bankyContextRegex.containsMatchIn(body)
    
    // Count strong signals
    val strongSignals = listOf(hasRef, hasAccTail, hasDate, hasBankCtx).count { it }
    return strongSignals >= 2
}
```

**Requires at least 2 of:**
- Reference number
- Account tail
- Date
- Bank context

## Merchant Extraction

### Enhanced Preposition Patterns
```kotlin
val merchantPatterns = listOf(
    Regex("""(?i)to\s+([A-Za-z\s]+?)(?:\s+via|\s+using|\s+for|\s+ref|$)"""),
    Regex("""(?i)for\s+([A-Za-z\s]+?)(?:\s+via|\s+using|\s+ref|$)"""),
    Regex("""(?i)at\s+([A-Za-z\s]+?)(?:\s+via|\s+using|\s+ref|$)"""),
    Regex("""(?i)from\s+([A-Za-z\s]+?)(?:\s+via|\s+using|\s+ref|$)"""),
    Regex("""(?i)via\s+([A-Za-z\s]+?)(?:\s+ref|$)"""),
    Regex("""(?i)using\s+([A-Za-z\s]+?)(?:\s+ref|$)""")
)
```

**Examples:**
- "Payment **to Amazon** via UPI" → merchant: "Amazon"
- "Amount paid **for groceries**" → merchant: "groceries"
- "Transaction **at Reliance Store**" → merchant: "Reliance Store"
- "Payment **from John Doe**" → merchant: "John Doe"
- "Sent **via Google Pay**" → merchant: "Google Pay"

## Transfer Detection

### Internal Transfer Logic
```kotlin
private object RecentAmountWindow {
    private const val WINDOW_MS: Long = 3 * 60 * 1000 // 3 minutes
    
    fun recordAndDetect(amountMinor: Long, type: String, ts: Long): Boolean {
        // Detect opposite type within 3-minute window
        // Same amount, opposite directions suggest internal transfer
    }
}
```

**Identifies:**
- Same amount credited and debited within 3 minutes
- Internal bank transfers between accounts
- UPI transfers between same person's accounts

## Benefits of Enhanced Patterns

### 1. **Comprehensive Coverage**
- Handles all major Indian banks
- Covers various SMS formats
- Supports multiple payment methods

### 2. **Robust Validation**
- Multiple validation layers
- Strong signal requirements
- Fallback patterns for edge cases

### 3. **Indian Banking Specific**
- Indian number format (1,00,000)
- Indian date formats
- Indian bank sender codes
- UPI and digital payment support

### 4. **Easy Maintenance**
- Clear pattern structure
- Well-documented regex
- Easy to add new banks/formats

## Testing Recommendations

### 1. **Test with Real SMS**
- Collect SMS from various banks
- Test different amount formats
- Verify merchant extraction

### 2. **Edge Cases**
- Very large amounts (crores)
- Special characters in merchant names
- Multiple reference numbers
- Mixed date formats

### 3. **Performance Testing**
- Large SMS volumes
- Complex regex execution
- Memory usage patterns

## Conclusion

The enhanced regex patterns provide comprehensive coverage for Indian bank SMS messages while maintaining high accuracy and reliability. The patterns are designed to handle the diverse formats used by different Indian banks and payment systems, ensuring robust transaction parsing for the SmartExpenses app.
