package com.codemobile.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.codemobile.core.model.ThemeMode
import com.codemobile.ui.chat.ChatScreen
import com.codemobile.ui.provider.ConnectProviderScreen
import com.codemobile.ui.provider.ProviderAuthScreen
import com.codemobile.ui.provider.ProviderViewModel
import com.codemobile.ui.settings.SettingsScreen

@Composable
fun CodeMobileNavGraph(
    navController: NavHostController,
    startSessionId: String?,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = if (startSessionId != null) {
            Route.Chat.createRoute(startSessionId)
        } else {
            Route.Chat.createRoute("none")
        },
        modifier = modifier
    ) {
        composable(
            route = Route.Chat.route,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType }
            )
        ) {
            ChatScreen(
                onOpenDrawer = onOpenDrawer,
                onNavigateToSettings = {
                    navController.navigate(Route.Settings.route)
                },
                onNavigateToConnectProvider = {
                    navController.navigate(Route.ConnectProvider.route)
                }
            )
        }

        composable(route = Route.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToConnectProvider = {
                    navController.navigate(Route.ConnectProvider.route)
                },
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange
            )
        }

        // ── Provider Connection Flow ─────────────────────────────
        // We share a single ProviderViewModel across ConnectProvider → Auth
        // by scoping it to the nav back stack entry

        composable(route = Route.ConnectProvider.route) { backStackEntry ->
            val providerViewModel: ProviderViewModel = hiltViewModel()
            ConnectProviderScreen(
                viewModel = providerViewModel,
                onNavigateBack = { navController.popBackStack() },
                onProviderSelected = {
                    navController.navigate(Route.ProviderAuth.route)
                }
            )
        }

        composable(route = Route.ProviderAuth.route) { backStackEntry ->
            // Get the same ViewModel from parent ConnectProvider entry
            val parentEntry = navController.getBackStackEntry(Route.ConnectProvider.route)
            val providerViewModel: ProviderViewModel = hiltViewModel(parentEntry)
            ProviderAuthScreen(
                viewModel = providerViewModel,
                onNavigateBack = { navController.popBackStack() },
                onConnected = {
                    // Pop all the way back to where we came from (Settings or Chat)
                    navController.popBackStack(Route.ConnectProvider.route, inclusive = true)
                }
            )
        }
    }
}
