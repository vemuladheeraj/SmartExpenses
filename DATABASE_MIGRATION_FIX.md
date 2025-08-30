# Database Migration Fix - Budget Table Schema Mismatch

## Problem
The app was crashing with the following error:
```
java.lang.IllegalStateException: Migration didn't properly handle: budgets(com.dheeraj.smartexpenses.data.Budget).
```

The crash was caused by a schema mismatch between the expected and actual database structure for the `budgets` table.

## Root Cause
The issue was in the `MIGRATION_3_4` which created the `budgets` table with:
1. **Duplicate indices**: Both `index_budgets_category` and `unique_budget_category` were created
2. **Default values**: The migration included default values for `currency` and `isActive` columns

However, the `Budget` entity definition only expected:
1. **Single index**: Only `unique_budget_category` 
2. **No default values**: The entity didn't specify default values in the schema

## Solution Implemented

### 1. Updated Budget Entity
**File**: `app/src/main/java/com/dheeraj/smartexpenses/data/Budget.kt`
- Fixed the index name to match what was actually created: `unique_budget_category`
- Kept the default values in the entity definition to match the migration

### 2. Fixed Migration 3_4
**File**: `app/src/main/java/com/dheeraj/smartexpenses/data/AppDb.kt`
- Removed the duplicate index creation (`index_budgets_category`)
- Kept only the unique index (`unique_budget_category`)

### 3. Added Migration 4_5
**File**: `app/src/main/java/com/dheeraj/smartexpenses/data/AppDb.kt`
- Created new migration to remove the duplicate index from existing databases
- This handles the case where users already have the database with both indices

### 4. Updated Database Version
- Increased database version from 4 to 5
- Added the new migration to the database builder

## Changes Made

### Budget.kt
```kotlin
@Entity(
    tableName = "budgets",
    indices = [
        Index(value = ["category"], unique = true, name = "unique_budget_category")
    ]
)
```

### AppDb.kt
```kotlin
@Database(entities = [Transaction::class, Budget::class], version = 5)

// Fixed MIGRATION_3_4
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // ... table creation ...
        // Only create unique index, not duplicate
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `unique_budget_category` ON `budgets` (`category`)")
    }
}

// New MIGRATION_4_5
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Remove duplicate index from existing databases
        database.execSQL("DROP INDEX IF EXISTS `index_budgets_category`")
    }
}
```

## Schema Files
- Updated schema files generated: `app/schemas/com.dheeraj.smartexpenses.data.AppDb/5.json`
- The new schema correctly shows only one index for the budgets table

## Testing
- ✅ Clean build successful
- ✅ Debug build successful  
- ✅ Schema generation successful
- ✅ Migration path: 1 → 2 → 3 → 4 → 5

## Impact
This fix resolves the database migration crash and ensures:
1. New installations work correctly
2. Existing installations with the problematic schema are automatically fixed
3. Future migrations will be consistent with the entity definitions

The app should now start without the database migration crash.

