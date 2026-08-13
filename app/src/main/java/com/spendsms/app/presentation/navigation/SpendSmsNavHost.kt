package com.spendsms.app.presentation.navigation

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.spendsms.app.R
import com.spendsms.app.presentation.common.LoadingState
import com.spendsms.app.presentation.dashboard.DashboardScreen
import com.spendsms.app.presentation.onboarding.BootstrapDestination
import com.spendsms.app.presentation.onboarding.BootstrapViewModel
import com.spendsms.app.presentation.onboarding.OnboardingScreen
import com.spendsms.app.presentation.onboarding.SampleDashboardScreen
import com.spendsms.app.presentation.onboarding.SmsDisclosureScreen
import com.spendsms.app.presentation.scan.ScanFlowScreen
import com.spendsms.app.presentation.settings.PrivacyDeletionScreen
import com.spendsms.app.presentation.settings.SettingsScreen
import com.spendsms.app.presentation.subscriptions.SubscriptionsScreen
import com.spendsms.app.presentation.transactions.CategoryDetailScreen
import com.spendsms.app.presentation.transactions.MerchantDetailScreen
import com.spendsms.app.presentation.transactions.TransactionDetailScreen
import com.spendsms.app.presentation.transactions.TransactionListScreen

@Composable
fun SpendSmsNavHost() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute in MainTabRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == SpendSmsRoutes.DASHBOARD,
                        onClick = { navController.navigateToTab(SpendSmsRoutes.DASHBOARD) },
                        icon = { Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_dashboard)) },
                    )
                    NavigationBarItem(
                        selected = currentRoute == SpendSmsRoutes.TRANSACTIONS,
                        onClick = { navController.navigateToTab(SpendSmsRoutes.TRANSACTIONS) },
                        icon = { Icon(Icons.AutoMirrored.Outlined.ListAlt, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_transactions)) },
                    )
                    NavigationBarItem(
                        selected = currentRoute == SpendSmsRoutes.SUBSCRIPTIONS,
                        onClick = { navController.navigateToTab(SpendSmsRoutes.SUBSCRIPTIONS) },
                        icon = { Icon(Icons.Outlined.Subscriptions, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_subscriptions)) },
                    )
                    NavigationBarItem(
                        selected = currentRoute == SpendSmsRoutes.SETTINGS,
                        onClick = { navController.navigateToTab(SpendSmsRoutes.SETTINGS) },
                        icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_settings)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = SpendSmsRoutes.BOOTSTRAP,
            modifier = Modifier.padding(padding),
        ) {
            composable(SpendSmsRoutes.BOOTSTRAP) {
                val bootstrap: BootstrapViewModel = hiltViewModel()
                val destination by bootstrap.destination.collectAsStateWithLifecycle()
                when (destination) {
                    BootstrapDestination.Loading -> LoadingState()
                    BootstrapDestination.Onboarding -> LaunchedEffect(destination) {
                        navController.navigate(SpendSmsRoutes.ONBOARDING) {
                            popUpTo(SpendSmsRoutes.BOOTSTRAP) { inclusive = true }
                        }
                    }
                    BootstrapDestination.ScanPeriod -> LaunchedEffect(destination) {
                        navController.navigate(SpendSmsRoutes.SCAN_PERIOD) {
                            popUpTo(SpendSmsRoutes.BOOTSTRAP) { inclusive = true }
                        }
                    }
                    BootstrapDestination.Dashboard -> LaunchedEffect(destination) {
                        navController.navigate(SpendSmsRoutes.DASHBOARD) {
                            popUpTo(SpendSmsRoutes.BOOTSTRAP) { inclusive = true }
                        }
                    }
                }
            }
            composable(SpendSmsRoutes.ONBOARDING) {
                OnboardingScreen(
                    onSeeSample = { navController.navigate(SpendSmsRoutes.SAMPLE_DASHBOARD) },
                    onAnalyse = { navController.navigate(SpendSmsRoutes.SMS_DISCLOSURE) },
                )
            }
            composable(SpendSmsRoutes.SAMPLE_DASHBOARD) {
                SampleDashboardScreen(
                    onContinue = { navController.navigate(SpendSmsRoutes.SMS_DISCLOSURE) },
                )
            }
            composable(SpendSmsRoutes.SMS_DISCLOSURE) {
                SmsDisclosureScreen(
                    onPermissionGranted = {
                        navController.navigate(SpendSmsRoutes.SCAN_PERIOD) {
                            popUpTo(SpendSmsRoutes.ONBOARDING) { inclusive = true }
                        }
                    },
                    onSkip = { navController.popBackStack() },
                )
            }
            composable(SpendSmsRoutes.SCAN_PERIOD) {
                ScanFlowScreen(
                    onFinished = {
                        navController.navigate(SpendSmsRoutes.DASHBOARD) {
                            popUpTo(SpendSmsRoutes.SCAN_PERIOD) { inclusive = true }
                        }
                    },
                    onNeedPermission = { navController.navigate(SpendSmsRoutes.SMS_DISCLOSURE) },
                )
            }
            composable(SpendSmsRoutes.DASHBOARD) {
                DashboardScreen(
                    onOpenTransaction = { navController.navigate(SpendSmsRoutes.transactionDetail(it)) },
                    onOpenCategory = { navController.navigate(SpendSmsRoutes.categoryDetail(it)) },
                    onOpenMerchant = {
                        navController.navigate(SpendSmsRoutes.merchantDetail(Uri.encode(it)))
                    },
                    onOpenSubscriptions = { navController.navigateToTab(SpendSmsRoutes.SUBSCRIPTIONS) },
                    onStartScan = { navController.navigate(SpendSmsRoutes.SCAN_PERIOD) },
                )
            }
            composable(SpendSmsRoutes.TRANSACTIONS) {
                TransactionListScreen(
                    onOpenTransaction = { navController.navigate(SpendSmsRoutes.transactionDetail(it)) },
                )
            }
            composable(
                route = SpendSmsRoutes.TRANSACTION_DETAIL,
                arguments = listOf(navArgument("transactionId") { type = NavType.StringType }),
            ) {
                TransactionDetailScreen()
            }
            composable(
                route = SpendSmsRoutes.CATEGORY_DETAIL,
                arguments = listOf(navArgument("categoryId") { type = NavType.StringType }),
            ) {
                CategoryDetailScreen(
                    onOpenTransaction = { navController.navigate(SpendSmsRoutes.transactionDetail(it)) },
                )
            }
            composable(
                route = SpendSmsRoutes.MERCHANT_DETAIL,
                arguments = listOf(navArgument("merchantKey") { type = NavType.StringType }),
            ) {
                MerchantDetailScreen(
                    onOpenTransaction = { navController.navigate(SpendSmsRoutes.transactionDetail(it)) },
                )
            }
            composable(SpendSmsRoutes.SUBSCRIPTIONS) {
                SubscriptionsScreen()
            }
            composable(SpendSmsRoutes.SETTINGS) {
                SettingsScreen(
                    onOpenPrivacyDeletion = { navController.navigate(SpendSmsRoutes.PRIVACY_DELETION) },
                    onStartScan = { navController.navigate(SpendSmsRoutes.SCAN_PERIOD) },
                )
            }
            composable(SpendSmsRoutes.PRIVACY_DELETION) {
                PrivacyDeletionScreen(
                    onDoneNavigateHome = {
                        navController.navigate(SpendSmsRoutes.DASHBOARD) {
                            popUpTo(SpendSmsRoutes.DASHBOARD) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onCancel = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun androidx.navigation.NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
