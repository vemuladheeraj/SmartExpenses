package com.dheeraj.smartexpenses.data


import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TxnDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(t: Transaction)

    @Query("SELECT * FROM transactions WHERE ts BETWEEN :start AND :end ORDER BY ts DESC")
    fun inRange(start: Long, end: Long): Flow<List<Transaction>>

    @Query("SELECT IFNULL(SUM(CASE WHEN type='DEBIT' THEN amount END),0) FROM transactions WHERE ts BETWEEN :start AND :end")
    fun totalDebits(start: Long, end: Long): Flow<Double>

    @Query("SELECT IFNULL(SUM(CASE WHEN type='CREDIT' THEN amount END),0) FROM transactions WHERE ts BETWEEN :start AND :end")
    fun totalCredits(start: Long, end: Long): Flow<Double>
}
