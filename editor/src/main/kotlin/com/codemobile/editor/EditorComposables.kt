package com.codemobile.editor

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Composable that wraps a WebView running CodeMirror 6.
 * Supports file viewing, editing, and diff display.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CodeEditorView(
    filePath: String?,
    content: String,
    language: String = "javascript",
    readOnly: Boolean = false,
    onContentChanged: (String) -> Unit = {},
    onSaveRequested: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var bridge by remember { mutableStateOf<EditorBridge?>(null) }
    var isEditorReady by remember { mutableStateOf(false) }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true

                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()

                addJavascriptInterface(
                    EditorJsInterface(
                        onContentChanged = onContentChanged,
                        onSaveRequested = onSaveRequested,
                        onReady = { isEditorReady = true }
                    ),
                    "AndroidBridge"
                )

                bridge = EditorBridge(this)
                loadUrl("file:///android_asset/editor/index.html")
            }
        },
        update = { webView ->
            if (isEditorReady && content.isNotEmpty()) {
                bridge?.openFile(filePath ?: "untitled", content, language)
                bridge?.setReadOnly(readOnly)
            }
        },
        modifier = modifier.fillMaxSize()
    )

    DisposableEffect(Unit) {
        onDispose {
            bridge = null
        }
    }
}

/**
 * Composable that displays a diff view with accept/reject controls.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DiffView(
    filePath: String,
    originalContent: String,
    modifiedContent: String,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    var bridge by remember { mutableStateOf<EditorBridge?>(null) }
    var isEditorReady by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        // Header with file path
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = filePath,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // WebView with diff
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true

                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()

                    addJavascriptInterface(
                        EditorJsInterface(onReady = { isEditorReady = true }),
                        "AndroidBridge"
                    )

                    bridge = EditorBridge(this)
                    loadUrl("file:///android_asset/editor/index.html")
                }
            },
            update = {
                if (isEditorReady) {
                    bridge?.showDiff(originalContent, modifiedContent, filePath)
                }
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        // Action buttons
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Rechazar")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Aplicar cambio")
                }
            }
        }
    }
}
