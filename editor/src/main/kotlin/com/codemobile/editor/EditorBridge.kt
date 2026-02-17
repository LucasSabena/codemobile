package com.codemobile.editor

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView

/**
 * Bridge for Kotlin → JavaScript communication with the CodeMirror editor.
 * Calls JavaScript functions on the WebView to control the editor.
 */
class EditorBridge(private val webView: WebView) {

    /**
     * Open a file in the editor with syntax highlighting.
     */
    fun openFile(path: String, content: String, language: String) {
        val escaped = content.escapeForJs()
        webView.evaluateJavascript(
            "window.editor.openFile('${path.escapeForJs()}', '$escaped', '$language')",
            null
        )
    }

    /**
     * Show a diff view comparing original and modified content.
     */
    fun showDiff(originalContent: String, modifiedContent: String, filePath: String) {
        val origEscaped = originalContent.escapeForJs()
        val modEscaped = modifiedContent.escapeForJs()
        webView.evaluateJavascript(
            "window.editor.showDiff('$origEscaped', '$modEscaped', '${filePath.escapeForJs()}')",
            null
        )
    }

    /**
     * Get the current editor content asynchronously.
     */
    fun getContent(callback: (String) -> Unit) {
        webView.evaluateJavascript("window.editor.getContent()") { result ->
            callback(result.unescapeFromJs())
        }
    }

    /**
     * Set the editor as read-only or editable.
     */
    fun setReadOnly(readOnly: Boolean) {
        webView.evaluateJavascript(
            "window.editor.setReadOnly($readOnly)",
            null
        )
    }

    /**
     * Set the editor theme (dark/light).
     */
    fun setTheme(isDark: Boolean) {
        webView.evaluateJavascript(
            "window.editor.setTheme(${if (isDark) "'dark'" else "'light'"})",
            null
        )
    }

    /**
     * Scroll to a specific line number.
     */
    fun scrollToLine(line: Int) {
        webView.evaluateJavascript(
            "window.editor.scrollToLine($line)",
            null
        )
    }

    /**
     * Clear the editor content.
     */
    fun clear() {
        webView.evaluateJavascript("window.editor.clear()", null)
    }
}

/**
 * JavaScript → Kotlin bridge. Methods annotated with @JavascriptInterface
 * can be called from JavaScript in the WebView.
 */
class EditorJsInterface(
    private val onContentChanged: (String) -> Unit = {},
    private val onSaveRequested: () -> Unit = {},
    private val onReady: () -> Unit = {}
) {
    @JavascriptInterface
    fun onContentChange(content: String) {
        onContentChanged(content)
    }

    @JavascriptInterface
    fun onSave() {
        onSaveRequested()
    }

    @JavascriptInterface
    fun onEditorReady() {
        onReady()
    }
}

/**
 * Escape a string for safe injection into JavaScript.
 */
internal fun String.escapeForJs(): String {
    return this
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}

/**
 * Unescape a JavaScript result string.
 */
internal fun String.unescapeFromJs(): String {
    if (this == "null" || this == "undefined") return ""
    return this
        .removeSurrounding("\"")
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")
        .replace("\\'", "'")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
}
