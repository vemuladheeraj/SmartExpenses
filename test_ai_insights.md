# AI Insights Feature Test Guide

## Overview
This document outlines the testing steps for the production-ready AI Insights feature in SmartExpenses.

## Test Scenarios

### 1. Initial Setup
- [ ] Open AI Insights screen
- [ ] Verify "Setup AI Insights" card is displayed
- [ ] Verify "Create Google AI Studio API Key" button opens browser
- [ ] Verify "Paste API Key" and "Use Custom Endpoint" buttons are available
- [ ] Verify tutorial card is shown

### 2. API Key Configuration
- [ ] Tap "Paste API Key" button
- [ ] Enter valid API key (starts with AIza...)
- [ ] Verify key is saved securely
- [ ] Verify "API key saved successfully" message appears
- [ ] Verify auto-refresh starts
- [ ] Verify insights are generated and displayed

### 3. Custom Endpoint Configuration
- [ ] Tap "Use Custom Endpoint" button
- [ ] Enter valid HTTPS endpoint URL
- [ ] Verify endpoint is saved securely
- [ ] Verify "Custom endpoint saved successfully" message appears
- [ ] Verify auto-refresh starts
- [ ] Verify insights are generated and displayed

### 4. Insights Display
- [ ] Verify KPIs section shows total spend, transaction counts, largest transaction
- [ ] Verify category breakdown is displayed
- [ ] Verify payment method breakdown is displayed
- [ ] Verify large transactions list is shown
- [ ] Verify recurring payments are identified
- [ ] Verify AI notes are displayed
- [ ] Verify "Last updated" timestamp is shown

### 5. Refresh Functionality
- [ ] Tap "Refresh" button
- [ ] Verify "Refreshing..." status is shown
- [ ] Verify new insights are loaded
- [ ] Verify "AI insights updated" message appears
- [ ] Test debounce: tap refresh multiple times quickly
- [ ] Verify only one request is made within 4 seconds

### 6. Error Handling
- [ ] Test with invalid API key
- [ ] Verify appropriate error message is shown
- [ ] Test with network disconnected
- [ ] Verify cached insights are displayed with error status
- [ ] Test with no transactions
- [ ] Verify "No transactions found" message

### 7. Analytics Unlock
- [ ] Open Analytics screen
- [ ] Verify "Unlock AI Insights" card is shown when no key configured
- [ ] Configure API key
- [ ] Return to Analytics screen
- [ ] Verify "AI Insights Available" card is shown
- [ ] Verify card links to AI Insights screen

### 8. Security
- [ ] Verify API key is never logged in plain text
- [ ] Verify API key is redacted in UI (AIza********************)
- [ ] Verify encrypted storage is used when available
- [ ] Verify fallback to regular SharedPreferences works

### 9. Caching
- [ ] Generate insights
- [ ] Close and reopen app
- [ ] Verify cached insights load instantly
- [ ] Verify "Last updated" time is preserved
- [ ] Clear app data and verify cache is cleared

### 10. Performance
- [ ] Test with 500+ transactions
- [ ] Verify response time is reasonable
- [ ] Test exponential backoff on errors
- [ ] Verify Retry-After header is respected

## Expected JSON Response Format

The AI should return JSON in this exact format:

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
    "by_category": [
      {"name": "Food", "amount": 8000.0},
      {"name": "Transport", "amount": 5000.0}
    ],
    "by_rail": [
      {"name": "UPI", "amount": 15000.0},
      {"name": "CARD", "amount": 10000.0}
    ]
  },
  "large_txns": [
    {"date": "2024-01-15", "merchant": "Amazon", "amount": 5000.0}
  ],
  "recurring": [
    {"name": "Netflix", "day_of_month": 15, "amount": 499.0}
  ],
  "notes": "Your spending shows a healthy balance with most transactions under ₹1000. Consider setting up automatic savings."
}
```

## Test Data Requirements

- At least 10-20 transactions in the last 30 days
- Mix of debit and credit transactions
- Various payment methods (UPI, CARD, IMPS, etc.)
- Different categories and merchants
- Some large transactions (>₹1000)
- Some recurring payments (same merchant, similar amount, same day)

## Success Criteria

- [ ] All test scenarios pass
- [ ] UI is responsive and user-friendly
- [ ] Error states are handled gracefully
- [ ] Security requirements are met
- [ ] Performance is acceptable
- [ ] Caching works correctly
- [ ] Analytics unlock feature works
- [ ] API key management is secure
