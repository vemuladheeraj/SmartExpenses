package com.dheeraj.smartexpenses.data


import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Transaction::class], version = 1)
abstract class AppDb : RoomDatabase() {
    abstract fun txnDao(): TxnDao

    companion object {
        @Volatile private var I: AppDb? = null
        fun get(ctx: Context): AppDb = I ?: synchronized(this) {
            I ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "smart_expenses.db")
                .build()
                .also { I = it }
        }
    }
}
