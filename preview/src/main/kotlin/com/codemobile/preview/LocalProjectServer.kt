package com.codemobile.preview

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream

/**
 * Lightweight HTTP server that serves project files for preview.
 * Supports both local filesystem paths and SAF (content://) URIs.
 *
 * Usage:
 *   val server = LocalProjectServer(context, projectPath, port = 0)
 *   server.start()
 *   val url = "http://localhost:${server.listeningPort}"
 *   // ... use url in WebView
 *   server.stop()
 */
class LocalProjectServer(
    private val context: Context,
    private val projectPath: String,
    port: Int = 0 // 0 = OS picks a random available port
) : NanoHTTPD(port) {

    private val isSaf: Boolean = projectPath.startsWith("content://")

    // For local filesystem
    private val localRoot: File? = if (!isSaf) File(projectPath) else null

    // For SAF
    private val safRootUri: Uri? = if (isSaf) Uri.parse(projectPath) else null
    private val safRoot: DocumentFile? by lazy {
        safRootUri?.let { DocumentFile.fromTreeUri(context, it) }
    }

    override fun serve(session: IHTTPSession): Response {
        var uri = session.uri?.trimStart('/') ?: ""

        // Default to index.html for root
        if (uri.isBlank()) uri = "index.html"

        // Prevent path traversal
        if (uri.contains("..")) {
            return newFixedLengthResponse(
                Response.Status.FORBIDDEN,
                MIME_PLAINTEXT,
                "Forbidden"
            )
        }

        return try {
            if (isSaf) serveSaf(uri) else serveLocal(uri)
        } catch (e: Exception) {
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Error: ${e.message}"
            )
        }
    }

    private fun serveLocal(relativePath: String): Response {
        val root = localRoot ?: return notFound(relativePath)
        val file = File(root, relativePath).canonicalFile

        // Security: ensure file is inside project root
        if (!file.path.startsWith(root.canonicalPath)) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Forbidden")
        }

        // If directory, try index.html inside it
        if (file.isDirectory) {
            val index = File(file, "index.html")
            if (index.exists() && index.isFile) {
                return serveLocalFile(index)
            }
            return notFound(relativePath)
        }

        if (!file.exists() || !file.isFile) return notFound(relativePath)
        return serveLocalFile(file)
    }

    private fun serveLocalFile(file: File): Response {
        val mimeType = guessMimeType(file.name)
        val inputStream = FileInputStream(file)
        return newFixedLengthResponse(Response.Status.OK, mimeType, inputStream, file.length())
    }

    private fun serveSaf(relativePath: String): Response {
        val root = safRoot ?: return notFound(relativePath)

        var target = findSafDocument(root, relativePath)

        // If directory, try index.html
        if (target != null && target.isDirectory) {
            val index = target.findFile("index.html")
            if (index != null && !index.isDirectory) {
                target = index
            } else {
                return notFound(relativePath)
            }
        }

        if (target == null || target.isDirectory) return notFound(relativePath)

        val mimeType = guessMimeType(target.name ?: "file")
        val inputStream = context.contentResolver.openInputStream(target.uri)
            ?: return notFound(relativePath)
        val size = target.length()

        return newFixedLengthResponse(Response.Status.OK, mimeType, inputStream, size)
    }

    private fun findSafDocument(root: DocumentFile, relativePath: String): DocumentFile? {
        if (relativePath.isBlank()) return root
        var current: DocumentFile = root
        for (segment in relativePath.split('/')) {
            if (segment.isBlank()) continue
            val child = current.findFile(segment) ?: return null
            current = child
        }
        return current
    }

    private fun notFound(path: String): Response {
        val html = """
            <!DOCTYPE html>
            <html><head><meta charset="utf-8"><title>404</title>
            <style>
                body { font-family: -apple-system, sans-serif; display: flex; justify-content: center;
                       align-items: center; height: 100vh; margin: 0; background: #1a1a1a; color: #e0e0e0; }
                .box { text-align: center; }
                h1 { font-size: 72px; margin: 0; opacity: 0.3; }
                p { color: #888; margin-top: 12px; }
                code { background: #2a2a2a; padding: 2px 8px; border-radius: 4px; font-size: 14px; }
            </style></head>
            <body><div class="box">
                <h1>404</h1>
                <p>File not found: <code>$path</code></p>
            </div></body></html>
        """.trimIndent()
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/html", html)
    }

    private fun guessMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension.isBlank()) return MIME_PLAINTEXT

        // Common web types that Android's MimeTypeMap might miss
        return when (extension) {
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js", "mjs" -> "application/javascript"
            "json" -> "application/json"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "ico" -> "image/x-icon"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "wasm" -> "application/wasm"
            "xml" -> "application/xml"
            "txt" -> "text/plain"
            "md" -> "text/markdown"
            "ts" -> "application/typescript"
            "tsx", "jsx" -> "application/javascript"
            "map" -> "application/json"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: MIME_PLAINTEXT
        }
    }

    companion object {
        private const val MIME_PLAINTEXT = "text/plain"
    }
}
