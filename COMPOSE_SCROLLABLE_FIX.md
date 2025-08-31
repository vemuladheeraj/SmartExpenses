# Compose Scrollable Layout Fix

## Issue
The app was crashing with the following error:
```
java.lang.IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints, which is disallowed. One of the common reasons is nesting layouts like LazyColumn and Column(Modifier.verticalScroll()).
```

## Root Cause
The issue was caused by nested scrollable layouts in the AI Insights screen:
- `LargeTransactionsCard` had a `LazyColumn` nested inside a `Column` that was already inside the main screen's `LazyColumn`
- `RecurringPaymentsCard` had the same nested `LazyColumn` issue

This created infinite height constraints because Compose couldn't determine the proper height for the nested scrollable components.

## Solution
Replaced the nested `LazyColumn` components with regular `Column` components in both cards:

### Before (Problematic):
```kotlin
LazyColumn {
    items(transactions.take(5)) { transaction ->
        // Transaction row content
    }
}
```

### After (Fixed):
```kotlin
Column {
    transactions.take(5).forEach { transaction ->
        // Transaction row content
    }
}
```

## Why This Works
1. **No nested scrollable layouts**: The main screen's `LazyColumn` handles all scrolling
2. **Small lists**: Since we're only showing 5 items maximum, lazy loading isn't necessary
3. **Proper height constraints**: Regular `Column` components have finite height constraints

## Best Practices for Compose Scrollable Layouts
1. **Avoid nesting scrollable components**: Don't put `LazyColumn` inside another `LazyColumn` or `Column` with `verticalScroll`
2. **Use single scrollable container**: Have one main scrollable container (usually `LazyColumn`) and use regular `Column` for content sections
3. **Consider content size**: Use `LazyColumn` only when you have large lists that benefit from lazy loading
4. **Use `forEach` for small lists**: For small, fixed-size lists, use `Column` with `forEach` instead of `LazyColumn`

## Files Modified
- `app/src/main/java/com/dheeraj/smartexpenses/ui/AiInsightsScreen.kt`
  - `LargeTransactionsCard` component
  - `RecurringPaymentsCard` component

## Testing
- Build completed successfully
- App should no longer crash when navigating to AI Insights screen
- All functionality remains intact
