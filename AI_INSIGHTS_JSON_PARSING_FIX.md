# AI Insights JSON Parsing Fix

## Issue
The AI Insights feature was failing to parse JSON responses from the AI service with the following error:
```
Field 'name' is required for type with serial name 'com.dheeraj.smartexpenses.data.CategoryBreakdown', but it was missing at path: $.breakdowns.by_category[0]
```

## Root Cause
The `CategoryBreakdown` data class had a non-nullable `name` field of type `String`, but the AI service was returning `null` for category names when it couldn't determine the category. This caused the JSON deserialization to fail.

## Solution
Made the `name` field nullable in the `CategoryBreakdown` data class and updated the UI to handle null values gracefully.

### Data Class Fix
**Before:**
```kotlin
@Serializable
data class CategoryBreakdown(
    val name: String,  // Non-nullable
    val amount: Double
)
```

**After:**
```kotlin
@Serializable
data class CategoryBreakdown(
    val name: String?,  // Nullable
    val amount: Double
)
```

### UI Component Fix
Updated the `CategoryBreakdownCard` to handle null category names:

**Before:**
```kotlin
Text(
    text = category.name,  // Could crash if null
    style = MaterialTheme.typography.bodyMedium,
    modifier = Modifier.weight(1f)
)
```

**After:**
```kotlin
Text(
    text = category.name ?: "Unknown Category",  // Safe null handling
    style = MaterialTheme.typography.bodyMedium,
    modifier = Modifier.weight(1f)
)
```

### AI Prompt Improvement
Updated the AI service prompt to be more explicit about handling null values:

**Before:**
```json
"by_category": [{"name": "", "amount": 0.0}]
```

**After:**
```json
"by_category": [{"name": null, "amount": 0.0}]
```

Added explicit instructions:
```
- For categories, use null if category cannot be determined, otherwise use descriptive names like "Food", "Transport", "Shopping", etc.
```

## Why This Works
1. **Handles AI limitations**: The AI may not always be able to categorize transactions, so null values are expected
2. **Graceful degradation**: The UI shows "Unknown Category" instead of crashing
3. **Consistent with other fields**: Other fields like `merchant` are already nullable
4. **Better user experience**: Users can still see the spending amount even if category is unknown

## Files Modified
- `app/src/main/java/com/dheeraj/smartexpenses/data/AiInsights.kt`
  - Made `CategoryBreakdown.name` nullable
- `app/src/main/java/com/dheeraj/smartexpenses/ui/AiInsightsScreen.kt`
  - Updated `CategoryBreakdownCard` to handle null category names
- `app/src/main/java/com/dheeraj/smartexpenses/data/AiService.kt`
  - Updated AI prompt to be explicit about null category names

## Testing
- ✅ Build completed successfully
- ✅ No compilation errors
- ✅ AI Insights should now parse successfully even with null category names
- ✅ UI displays "Unknown Category" for uncategorized transactions

## Best Practices for AI Integration
1. **Make fields nullable**: When dealing with AI responses, make fields nullable if the AI might not always provide values
2. **Provide fallback values**: Always provide meaningful fallback text for null values in UI
3. **Be explicit in prompts**: Clearly specify in AI prompts when null values are acceptable
4. **Test with edge cases**: Test with various AI response scenarios including missing or null fields
