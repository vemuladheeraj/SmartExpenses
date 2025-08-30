package com.dheeraj.smartexpenses.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<Category>>
    
    @Query("SELECT * FROM categories WHERE isDefault = 1 ORDER BY name ASC")
    fun getDefaultCategories(): Flow<List<Category>>
    
    @Query("SELECT * FROM categories WHERE isDefault = 0 ORDER BY name ASC")
    fun getCustomCategories(): Flow<List<Category>>
    
    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun getCategoryByName(name: String): Category?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category): Long
    
    @Update
    suspend fun updateCategory(category: Category)
    
    @Delete
    suspend fun deleteCategory(category: Category)
    
    @Query("DELETE FROM categories WHERE name = :name")
    suspend fun deleteCategoryByName(name: String)
    
    @Query("SELECT COUNT(*) FROM categories WHERE name = :name")
    suspend fun getCategoryCountByName(name: String): Int
}
