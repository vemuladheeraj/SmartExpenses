package com.dheeraj.smartexpenses.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

sealed class Screen(val route: String, val title: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Outlined.Home, Icons.Filled.Home)
    object Analytics : Screen("analytics", "Analytics", Icons.Outlined.Analytics, Icons.Filled.Analytics)
    object Budget : Screen("budget", "Budget", Icons.Outlined.AccountBalanceWallet, Icons.Filled.AccountBalanceWallet)
    object AiInsights : Screen("ai_insights", "AI Insights", Icons.Outlined.SmartToy, Icons.Filled.SmartToy)
    object Settings : Screen("settings", "Settings", Icons.Outlined.Settings, Icons.Filled.Settings)
    object TransactionList : Screen("transaction_list", "All Transactions", Icons.Outlined.List, Icons.Filled.List)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(
    homeVm: HomeVm,
    onAddTransaction: () -> Unit
) {
    val navController = rememberNavController()
    val screens = listOf(Screen.Home, Screen.Analytics, Screen.Budget, Screen.AiInsights, Screen.Settings)
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                
                screens.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = if (selected) screen.selectedIcon else screen.icon,
                                contentDescription = screen.title
                            )
                        },
                        label = { Text(screen.title) },
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    homeVm = homeVm,
                    onAddTransaction = onAddTransaction,
                    onViewAllTransactions = {
                        navController.navigate(Screen.TransactionList.route)
                    }
                )
            }
            composable(Screen.Analytics.route) {
                AnalyticsScreen(homeVm = homeVm)
            }
            composable(Screen.Budget.route) {
                BudgetScreen()
            }
            composable(Screen.AiInsights.route) {
                AiInsightsScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen(homeVm = homeVm)
            }
            composable(Screen.TransactionList.route) {
                TransactionListScreen(
                    homeVm = homeVm,
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
