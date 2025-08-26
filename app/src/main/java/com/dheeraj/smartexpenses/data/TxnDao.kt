package com.dheeraj.smartexpenses.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TxnDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(t: Transaction)

    @Query("SELECT * FROM transactions WHERE ts BETWEEN :start AND :end ORDER BY ts DESC")
    fun inRange(start: Long, end: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE ts BETWEEN :start AND :end AND type = :type ORDER BY ts DESC")
    fun inRangeByType(start: Long, end: Long, type: String): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE ts BETWEEN :start AND :end AND channel = :channel ORDER BY ts DESC")
    fun inRangeByChannel(start: Long, end: Long, channel: String): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE ts BETWEEN :start AND :end AND merchant LIKE '%' || :merchant || '%' ORDER BY ts DESC")
    fun inRangeByMerchant(start: Long, end: Long, merchant: String): Flow<List<Transaction>>

    @Query("SELECT DISTINCT merchant FROM transactions WHERE merchant IS NOT NULL AND merchant != '' ORDER BY merchant")
    fun getAllMerchants(): Flow<List<String>>

    @Query("SELECT DISTINCT channel FROM transactions WHERE channel IS NOT NULL AND channel != '' ORDER BY channel")
    fun getAllChannels(): Flow<List<String>>

    // Updated queries to exclude transfers and provide proper filtered totals
    @Query("""
        SELECT IFNULL(SUM(CASE WHEN type='DEBIT' AND type != 'TRANSFER' THEN amount END),0) 
        FROM transactions 
        WHERE ts BETWEEN :start AND :end 
        AND type != 'TRANSFER'
    """)
    fun totalDebits(start: Long, end: Long): Flow<Double>

    @Query("""
        SELECT IFNULL(SUM(CASE WHEN type='CREDIT' AND type != 'TRANSFER' THEN amount END),0) 
        FROM transactions 
        WHERE ts BETWEEN :start AND :end 
        AND type != 'TRANSFER'
    """)
    fun totalCredits(start: Long, end: Long): Flow<Double>

    // Additional queries for better filtering
    @Query("""
        SELECT IFNULL(SUM(amount),0) 
        FROM transactions 
        WHERE ts BETWEEN :start AND :end 
        AND type = 'DEBIT' 
        AND type != 'TRANSFER'
        AND amount > 0 
        AND amount < 1000000
    """)
    fun totalDebitsFiltered(start: Long, end: Long): Flow<Double>

    @Query("""
        SELECT IFNULL(SUM(amount),0) 
        FROM transactions 
        WHERE ts BETWEEN :start AND :end 
        AND type = 'CREDIT' 
        AND type != 'TRANSFER'
        AND amount > 0 
        AND amount < 1000000
    """)
    fun totalCreditsFiltered(start: Long, end: Long): Flow<Double>

    @Query("SELECT COUNT(*) FROM transactions WHERE rawSender = :sender AND rawBody = :body AND ts = :timestamp")
    suspend fun exists(sender: String, body: String, timestamp: Long): Int

    @Query("DELETE FROM transactions WHERE source = 'SMS'")
    suspend fun clearSmsTransactions()

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getTransactionCount(): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE source = 'SMS'")
    suspend fun getSmsTransactionCount(): Int

    // Query to get suspicious transactions (very high amounts that might be balances)
    @Query("""
        SELECT * FROM transactions 
        WHERE amount > 100000 
        AND source = 'SMS' 
        ORDER BY amount DESC 
        LIMIT 10
    """)
    suspend fun getSuspiciousTransactions(): List<Transaction>

    // Additional queries for comprehensive logging
    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY ts DESC")
    fun getTransactionsByType(type: String): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE channel = :channel ORDER BY ts DESC")
    fun getTransactionsByChannel(channel: String): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE bank = :bank ORDER BY ts DESC")
    fun getTransactionsByBank(bank: String): Flow<List<Transaction>>

    @Query("SELECT COUNT(*) FROM transactions WHERE type = 'CREDIT' AND type != 'TRANSFER'")
    fun getCreditTransactionCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions WHERE type = 'DEBIT' AND type != 'TRANSFER'")
    fun getDebitTransactionCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions WHERE type = 'TRANSFER'")
    fun getTransferTransactionCount(): Flow<Int>
}
