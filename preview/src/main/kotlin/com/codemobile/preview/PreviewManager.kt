package com.codemobile.preview

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Describes the type of preview running.
 */
enum class PreviewMode {
    /** Embedded NanoHTTPD serving static project files */
    STATIC,
    /** Dev server detected from terminal output (React, Vite, Next.js, etc.) */
    DEV_SERVER
}

data class PreviewState(
    val isRunning: Boolean = false,
    val url: String? = null,
    val mode: PreviewMode = PreviewMode.STATIC,
    val projectPath: String? = null
)

/**
 * Manages the live preview of project files.
 *
 * Two modes:
 * 1. **Static**: Starts an embedded HTTP server (NanoHTTPD) that serves the project files.
 *    Works for plain HTML/CSS/JS projects.
 * 2. **Dev Server**: Detects a dev server URL from terminal/tool output (e.g. Vite, Next.js,
 *    Angular CLI). Connects the WebView to the detected URL.
 */
@Singleton
class PreviewManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var localServer: LocalProjectServer? = null

    private val _state = MutableStateFlow(PreviewState())
    val state: StateFlow<PreviewState> = _state.asStateFlow()

    /**
     * Start the static file server for a given project path.
     * Stops any previously running server first.
     */
    fun startStaticServer(projectPath: String) {
        stop()
        try {
            val server = LocalProjectServer(context, projectPath, port = 0)
            server.start()
            localServer = server
            _state.value = PreviewState(
                isRunning = true,
                url = "http://localhost:${server.listeningPort}",
                mode = PreviewMode.STATIC,
                projectPath = projectPath
            )
        } catch (e: Exception) {
            _state.value = PreviewState(isRunning = false, url = null)
        }
    }

    /**
     * Set the preview URL from a detected dev server.
     * Stops the static server if it was running.
     */
    fun setDevServerUrl(url: String, projectPath: String? = null) {
        stopLocalServer()
        _state.value = PreviewState(
            isRunning = true,
            url = url,
            mode = PreviewMode.DEV_SERVER,
            projectPath = projectPath
        )
    }

    /**
     * Scan a tool output string for dev server URLs.
     * Returns true if a URL was detected and set.
     */
    fun scanForDevServer(output: String, projectPath: String? = null): Boolean {
        val detectedUrl = DevServerDetector.detectUrl(output)
        if (detectedUrl != null) {
            setDevServerUrl(detectedUrl, projectPath)
            return true
        }
        return false
    }

    /**
     * Stop everything — local server and clear dev server URL.
     */
    fun stop() {
        stopLocalServer()
        _state.value = PreviewState()
    }

    /**
     * Get the current preview URL (either static server or dev server).
     */
    fun getUrl(): String? = _state.value.url

    /**
     * Check whether preview is available.
     */
    fun isRunning(): Boolean = _state.value.isRunning

    private fun stopLocalServer() {
        localServer?.stop()
        localServer = null
    }
}
