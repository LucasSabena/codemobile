package com.codemobile.ui.navigation

/**
 * Navigation routes for Code Mobile.
 */
sealed class Route(val route: String) {
    data object Home : Route("home")

    data object Chat : Route("chat/{sessionId}") {
        fun createRoute(sessionId: String) = "chat/$sessionId"
    }

    data object Settings : Route("settings")

    /** List of providers to connect */
    data object ConnectProvider : Route("provider/connect")

    /** Auth screen for a specific provider */
    data object ProviderAuth : Route("provider/auth")

    data object ProviderSetup : Route("settings/provider/{providerId}") {
        fun createRoute(providerId: String) = "settings/provider/$providerId"
        fun createNew() = "settings/provider/new"
    }

    data object Editor : Route("editor/{filePath}") {
        fun createRoute(filePath: String) = "editor/${filePath.encodeForRoute()}"
    }

    data object Diff : Route("diff/{sessionId}/{messageId}") {
        fun createRoute(sessionId: String, messageId: String) = "diff/$sessionId/$messageId"
    }
}

private fun String.encodeForRoute(): String =
    replace("/", "~").replace(" ", "+")

fun String.decodeFromRoute(): String =
    replace("~", "/").replace("+", " ")
