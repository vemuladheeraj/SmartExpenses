package com.dheeraj.smartexpenses.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dheeraj.smartexpenses.data.AppDb
import com.dheeraj.smartexpenses.data.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CategoryViewModel(application: Application) : AndroidViewModel(application) {
    private val categoryDao = AppDb.get(application).categoryDao()
    
    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()
    val defaultCategories: Flow<List<Category>> = categoryDao.getDefaultCategories()
    val customCategories: Flow<List<Category>> = categoryDao.getCustomCategories()
    
    init {
        // Ensure default categories exist when ViewModel is created
        viewModelScope.launch {
            try {
                val defaultCats = categoryDao.getDefaultCategories().first()
                if (defaultCats.isEmpty()) {
                    android.util.Log.d("CategoryViewModel", "No default categories found, populating...")
                    populateDefaultCategories()
                } else {
                    android.util.Log.d("CategoryViewModel", "Default categories found: ${defaultCats.size}")
                }
            } catch (e: Exception) {
                android.util.Log.e("CategoryViewModel", "Error checking default categories", e)
            }
        }
    }
    
    fun addCategory(name: String, icon: String, color: String) {
        viewModelScope.launch {
            val category = Category(
                name = name.trim(),
                icon = icon,
                color = color,
                isDefault = false
            )
            categoryDao.insertCategory(category)
        }
    }
    
    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            categoryDao.deleteCategory(category)
        }
    }
    
    suspend fun isCategoryNameExists(name: String): Boolean {
        return categoryDao.getCategoryCountByName(name.trim()) > 0
    }
    
    suspend fun getCategoryByName(name: String): Category? {
        return categoryDao.getCategoryByName(name)
    }
    
    private suspend fun populateDefaultCategories() {
        val defaultCategoryData = listOf(
            "Food" to ("Restaurant" to "#FF6B6B"),
            "Transport" to ("DirectionsCar" to "#4ECDC4"),
            "Shopping" to ("ShoppingCart" to "#45B7D1"),
            "Entertainment" to ("Movie" to "#96CEB4"),
            "Bills" to ("Receipt" to "#FFEAA7"),
            "Health" to ("LocalHospital" to "#DDA0DD"),
            "Education" to ("School" to "#98D8C8"),
            "Other" to ("AccountBalance" to "#F7DC6F")
        )
        
        defaultCategoryData.forEach { (name, iconColor) ->
            val (icon, color) = iconColor
            val category = Category(
                name = name,
                icon = icon,
                color = color,
                isDefault = true
            )
            categoryDao.insertCategory(category)
        }
        android.util.Log.d("CategoryViewModel", "Default categories populated successfully")
    }
    
    fun debugCategories() {
        viewModelScope.launch {
            try {
                val allCats = categoryDao.getAllCategories().first()
                val defaultCats = categoryDao.getDefaultCategories().first()
                val customCats = categoryDao.getCustomCategories().first()
                
                android.util.Log.d("CategoryViewModel", "Debug - All categories: ${allCats.size}")
                android.util.Log.d("CategoryViewModel", "Debug - Default categories: ${defaultCats.size}")
                android.util.Log.d("CategoryViewModel", "Debug - Custom categories: ${customCats.size}")
                
                allCats.forEach { cat ->
                    android.util.Log.d("CategoryViewModel", "Category: ${cat.name}, Icon: ${cat.icon}, Color: ${cat.color}, Default: ${cat.isDefault}")
                }
            } catch (e: Exception) {
                android.util.Log.e("CategoryViewModel", "Error debugging categories", e)
            }
        }
    }
}
