package com.dheeraj.smartexpenses

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dheeraj.smartexpenses.data.Budget
import com.dheeraj.smartexpenses.data.BudgetAnalysis
import com.dheeraj.smartexpenses.ui.BudgetViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class BudgetViewModelTest {
    
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()
    
    @Mock
    private lateinit var mockContext: android.content.Context
    
    private lateinit var viewModel: BudgetViewModel
    private val testDispatcher = StandardTestDispatcher()
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        // Note: In a real test, you'd use a test database
        // viewModel = BudgetViewModel()
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    @Test
    fun `test initial state`() = runTest {
        // Given: A fresh BudgetViewModel
        
        // When: The view model is initialized
        
        // Then: Initial state should be correct
        val initialState = viewModel.uiState.value
        assertNotNull(initialState)
        assertTrue(initialState.budgetAnalysis.isEmpty())
        assertEquals(0.0, initialState.totalBudget, 0.01)
        assertEquals(0.0, initialState.totalSpent, 0.01)
        assertEquals(0.0, initialState.totalRemaining, 0.01)
        assertFalse(initialState.showAddBudgetDialog)
        assertNull(initialState.editingBudget)
        assertFalse(initialState.isLoading)
        assertNull(initialState.error)
    }
    
    @Test
    fun `test show add budget dialog`() = runTest {
        // Given: Initial state
        
        // When: Show add budget dialog is called
        viewModel.showAddBudgetDialog()
        
        // Then: Dialog should be shown
        val state = viewModel.uiState.value
        assertTrue(state.showAddBudgetDialog)
        assertNull(state.editingBudget)
    }
    
    @Test
    fun `test hide add budget dialog`() = runTest {
        // Given: Dialog is shown
        viewModel.showAddBudgetDialog()
        
        // When: Hide dialog is called
        viewModel.hideAddBudgetDialog()
        
        // Then: Dialog should be hidden
        val state = viewModel.uiState.value
        assertFalse(state.showAddBudgetDialog)
        assertNull(state.editingBudget)
    }
    
    @Test
    fun `test save budget`() = runTest {
        // Given: A budget to save
        val category = "Food"
        val amount = 5000.0
        
        // When: Save budget is called
        viewModel.saveBudget(category, amount)
        
        // Then: Budget should be saved and dialog hidden
        val state = viewModel.uiState.value
        assertFalse(state.showAddBudgetDialog)
        // Note: In a real test with test database, you'd verify the budget was actually saved
    }
    
    @Test
    fun `test delete budget`() = runTest {
        // Given: A budget exists
        val category = "Food"
        
        // When: Delete budget is called
        viewModel.deleteBudget(category)
        
        // Then: Budget should be deleted
        // Note: In a real test with test database, you'd verify the budget was actually deleted
    }
    
    @Test
    fun `test clear error`() = runTest {
        // Given: An error state (would need to be set up with mock database error)
        
        // When: Clear error is called
        viewModel.clearError()
        
        // Then: Error should be cleared
        val state = viewModel.uiState.value
        assertNull(state.error)
    }
}
