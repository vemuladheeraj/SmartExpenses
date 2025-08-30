# SMS Parsing Test - Debug

## Test SMS
```
Update! INR 1,99,023.00 deposited in HDFC Bank A/c XX0545 on 29-AUG-25 for NEFT Cr-CITI0000002-OGS (INDIA) PVT LTD (G3-ITP-2)-VEMULA  DHEERAJ-CITIN52025082917691328.Avl bal INR 2,42,882.10. Cheque deposits in A/C are subject to clearing
```

## Regex Pattern Analysis

### 1. Amount Extraction
- **Pattern**: `(?i)(?:₹|Rs\.?|INR|rupees?|rs\.?|inr)\s*([0-9,]+(?:\.\d{2})?)(?:\s*/-|\s*|$)`
- **Match**: `INR 1,99,023.00` ✅
- **Extracted**: `1,99,023.00`

### 2. Transaction Verb Check
- **Pattern**: `(?i)\b(?:credited?|debited?|withdrawn?|deposited?|sent|received|paid|charged|deducted?|transferred?|processed|completed|successful|failed|rejected|cancelled|reversed|refunded?|bounced|returned)\b`
- **Match**: `deposited` ✅
- **Result**: Should be CREDIT

### 3. Account Tail Check
- **Pattern**: `(?i)(?:a/c|account|ac|acc)\s*[:\s]*[xX]{4,}(\d{4,})`
- **Match**: `A/c XX0545` ✅
- **Extracted**: `0545`

### 4. Bank Context Check
- **Pattern**: `(?i)\b(?:balance|transaction|bank|branch|upi|imps|neft|rtgs|pos|atm|card|cheque|transfer|payment|online|mobile|netbanking|standing\s+instruction|si|recurring|emi|loan|credit|debit|account|a/c|acc|ref|utr|transaction\s+id|txn\s+id)\b`
- **Match**: `Bank` ✅

### 5. Date Check
- **Pattern**: `\b\d{1,2}[/\-\.]\d{1,2}[/\-\.]\d{2,4}\b`
- **Match**: `29-AUG-25` ✅

### 6. Reference Check
- **Pattern**: `(?i)(?:ref|utr|transaction\s+id|txn\s+id|order\s+id|payment\s+id|upi\s+ref|imps\s+ref|neft\s+ref|rtgs\s+ref)\s*[:\s]*([a-zA-Z0-9]+)`
- **Match**: `NEFT Cr-CITI0000002` ❌ (This should match!)

## Issues Found

### 1. **Reference Number Not Detected**
The SMS contains `NEFT Cr-CITI0000002` which should be detected as a reference number, but our current regex doesn't handle this format.

### 2. **NEFT Reference Format**
- **Current**: `NEFT Cr-CITI0000002`
- **Should Match**: `NEFT` + reference number
- **Missing Pattern**: `neft\s+[a-zA-Z0-9-]+`

### 3. **Heuristic Check Should Pass**
- **Transaction Verb**: ✅ `deposited`
- **Account Tail**: ✅ `A/c XX0545`
- **Bank Context**: ✅ `Bank`
- **Date**: ✅ `29-AUG-25`
- **Reference**: ❌ `NEFT Cr-CITI0000002` (not detected)
- **Bank Known**: ✅ `HDFCBK`

**Strong Count**: 4 out of 6
**Result**: Should pass because `hasTxnVerb && strongCount >= 2` (true && 4 >= 2)

## Expected Behavior

This SMS should be:
1. **Detected as Transaction**: ✅ (passes heuristic check)
2. **Classified as CREDIT**: ✅ (`deposited` is strong CREDIT indicator)
3. **Amount Extracted**: ✅ `1,99,023.00`
4. **Account Extracted**: ✅ `0545`
5. **Bank Identified**: ✅ `HDFC Bank`
6. **Channel**: ✅ `NEFT`

## Fix Required

The reference number regex needs to be enhanced to handle:
- `NEFT Cr-CITI0000002`
- `NEFT Ref: 123456`
- `NEFT Reference: ABC123`
- `NEFT Txn: XYZ789`
