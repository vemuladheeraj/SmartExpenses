package com.dheeraj.smartexpenses.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TxnDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(t: Transaction)

    @Update
    suspend fun update(t: Transaction)

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

    @Query("SELECT IFNULL(SUM(CASE WHEN type='DEBIT' THEN amountMinor END),0)/100.0 FROM transactions WHERE ts BETWEEN :start AND :end")
    fun totalDebits(start: Long, end: Long): Flow<Double>

    @Query("SELECT IFNULL(SUM(CASE WHEN type='CREDIT' THEN amountMinor END),0)/100.0 FROM transactions WHERE ts BETWEEN :start AND :end")
    fun totalCredits(start: Long, end: Long): Flow<Double>

    @Query("SELECT COUNT(*) FROM transactions WHERE rawSender = :sender AND rawBody = :body AND ts = :timestamp")
    suspend fun exists(sender: String, body: String, timestamp: Long): Int

    @Query("DELETE FROM transactions WHERE source LIKE 'SMS%'")
    suspend fun clearSmsTransactions()

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getTransactionCount(): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE source LIKE 'SMS%'")
    suspend fun getSmsTransactionCount(): Int

    @Query("SELECT IFNULL(SUM(CASE WHEN type='DEBIT' THEN amountMinor END),0)/100.0 FROM transactions WHERE source LIKE 'SMS%'")
    suspend fun getTotalDebits(): Double

    @Query("SELECT IFNULL(SUM(CASE WHEN type='CREDIT' THEN amountMinor END),0)/100.0 FROM transactions WHERE source LIKE 'SMS%'")
    suspend fun getTotalCredits(): Double

    // Enrichment helpers: find recent transactions missing details
    @Query("SELECT * FROM transactions WHERE source LIKE 'SMS%' AND (merchant IS NULL OR channel IS NULL) ORDER BY ts DESC LIMIT :limit")
    suspend fun findNeedingEnrichment(limit: Int = 200): List<Transaction>
}
