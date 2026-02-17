package com.codemobile.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.codemobile.ai.auth.BrowserOAuth
import com.codemobile.core.model.ThemeMode
import com.codemobile.ui.drawer.AppDrawerContent
import com.codemobile.ui.navigation.CodeMobileNavGraph
import com.codemobile.ui.navigation.Route
import com.codemobile.ui.theme.AppThemeViewModel
import com.codemobile.ui.theme.CodeMobileTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Handle OAuth callback if launched via deep link
        handleOAuthCallback(intent)

        setContent {
            val appThemeViewModel: AppThemeViewModel = hiltViewModel()
            val themeMode by appThemeViewModel.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            CodeMobileTheme(darkTheme = darkTheme) {
                CodeMobileApp(
                    themeMode = themeMode,
                    onThemeModeChange = appThemeViewModel::setThemeMode
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthCallback(intent)
    }

    /**
     * Handle the OAuth callback deep link: codemobile://oauth/callback?code=XXX&state=YYY
     */
    private fun handleOAuthCallback(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "codemobile" && uri.host == "oauth") {
            BrowserOAuth.onCallbackReceived(uri.toString())
        }
    }
}

@Composable
private fun CodeMobileApp(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentSessionId by rememberSaveable { mutableStateOf<String?>(null) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                onSessionClick = { sessionId ->
                    currentSessionId = sessionId
                    navController.navigate(Route.Chat.createRoute(sessionId)) {
                        popUpTo(navController.graph.startDestinationId) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                },
                onSettingsClick = {
                    navController.navigate(Route.Settings.route) {
                        launchSingleTop = true
                    }
                },
                onCloseDrawer = {
                    scope.launch { drawerState.close() }
                }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) {
        CodeMobileNavGraph(
            navController = navController,
            startSessionId = currentSessionId,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            onOpenDrawer = {
                scope.launch { drawerState.open() }
            }
        )
    }
}
