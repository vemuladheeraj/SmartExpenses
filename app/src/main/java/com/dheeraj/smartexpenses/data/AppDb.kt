package com.dheeraj.smartexpenses.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Transaction::class], version = 2)
abstract class AppDb : RoomDatabase() {
    abstract fun txnDao(): TxnDao

    companion object {
        @Volatile private var I: AppDb? = null
        
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    // Add indices for better performance and duplicate prevention
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_rawSender_rawBody_ts` ON `transactions` (`rawSender`, `rawBody`, `ts`)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_ts` ON `transactions` (`ts`)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_type_ts` ON `transactions` (`type`, `ts`)")
                    
                    // Create a unique constraint on the combination of rawSender, rawBody, and ts
                    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `unique_sms_transaction` ON `transactions` (`rawSender`, `rawBody`, `ts`)")
                } catch (e: Exception) {
                    // If migration fails, log the error but don't crash
                    e.printStackTrace()
                }
            }
        }
        
        fun get(ctx: Context): AppDb = I ?: synchronized(this) {
            I ?: try {
                Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "smart_expenses.db")
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration() // Fallback if migration fails
                    .build()
                    .also { I = it }
            } catch (e: Exception) {
                // If database creation fails, try with destructive migration
                e.printStackTrace()
                Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "smart_expenses.db")
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { I = it }
            }
        }
    }
}
