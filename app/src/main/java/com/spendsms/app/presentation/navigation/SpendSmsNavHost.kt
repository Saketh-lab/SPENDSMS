package com.spendsms.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.spendsms.app.presentation.foundation.FoundationPlaceholderScreen

object SpendSmsRoutes {
    const val FOUNDATION = "foundation"
}

/**
 * Root navigation graph.
 *
 * Destination modules from the approved architecture (onboarding, scan,
 * dashboard, transactions, subscriptions, settings) will be registered here
 * in later steps. This foundation step only hosts a non-product placeholder.
 */
@Composable
fun SpendSmsNavHost() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = SpendSmsRoutes.FOUNDATION,
    ) {
        composable(SpendSmsRoutes.FOUNDATION) {
            FoundationPlaceholderScreen()
        }
    }
}
