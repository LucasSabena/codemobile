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

    /**
     * Subdirectories to check (in order) for a built/serveable index.html.
     * Covers Vite (dist), CRA (build), Next.js static export (.next/out, out), Angular (dist).
     */
    private val buildOutputDirs = listOf("dist", "build", "out", ".next/out")

    private val isSaf: Boolean = projectPath.startsWith("content://")

    // For local filesystem
    private val localRoot: File? = if (!isSaf) File(projectPath) else null

    // For SAF
    private val safRootUri: Uri? = if (isSaf) Uri.parse(projectPath) else null
    private val safRoot: DocumentFile? by lazy {
        safRootUri?.let { DocumentFile.fromTreeUri(context, it) }
    }

    /**
     * Detect the best folder to serve from (the one containing a useable index.html).
     * Returns a sub-path relative to the project root, or "" for root.
     */
    private val serveSubDir: String by lazy {
        if (isSaf) detectServeSubDirSaf() else detectServeSubDirLocal()
    }

    /** Project type detected from package.json or file structure */
    val detectedProjectType: String by lazy {
        if (isSaf) detectProjectTypeSaf() else detectProjectTypeLocal()
    }

    /** Whether the project needs a build step before preview works */
    val needsBuild: Boolean by lazy {
        val type = detectedProjectType
        if (type == "static" || type == "unknown") return@lazy false
        // Framework project: check if a real build output directory exists
        // (root index.html in a Vite/CRA project is not a build output — it references src/)
        val hasBuildOutputDir = if (isSaf) {
            buildOutputDirs.any { dir ->
                val parts = dir.split("/")
                var current: DocumentFile? = safRoot
                for (part in parts) { current = current?.findFile(part) }
                current != null && current.isDirectory &&
                    current.findFile("index.html")?.let { !it.isDirectory } == true
            }
        } else {
            val root = localRoot ?: return@lazy false
            buildOutputDirs.any { dir ->
                val candidate = File(root, dir)
                candidate.isDirectory && File(candidate, "index.html").isFile
            }
        }
        !hasBuildOutputDir
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

        // Special endpoint: project info / fallback page
        if (uri == "index.html" && needsBuild) {
            return serveProjectInfoPage()
        }

        // Prepend the detected serve sub-directory
        val resolvedUri = if (serveSubDir.isNotBlank()) "$serveSubDir/$uri" else uri

        return try {
            val response = if (isSaf) serveSaf(resolvedUri) else serveLocal(resolvedUri)
            // SPA fallback: if file not found and it doesn't look like a static asset,
            // serve index.html for client-side routing (React Router, Vue Router, etc.)
            if (response.status == Response.Status.NOT_FOUND && !looksLikeAsset(uri)) {
                val indexUri = if (serveSubDir.isNotBlank()) "$serveSubDir/index.html" else "index.html"
                if (isSaf) serveSaf(indexUri) else serveLocal(indexUri)
            } else {
                response
            }
        } catch (e: Exception) {
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Error: ${e.message}"
            )
        }
    }

    /** Returns true if the URI looks like a request for a static asset (has a file extension) */
    private fun looksLikeAsset(uri: String): Boolean {
        val lastSegment = uri.substringAfterLast('/')
        return lastSegment.contains('.')
    }

    // ── Serve-root detection (local) ──────────────────────────────

    private fun detectServeSubDirLocal(): String {
        val root = localRoot ?: return ""
        // 1. Check common build output directories
        for (dir in buildOutputDirs) {
            val candidate = File(root, dir)
            if (candidate.isDirectory && File(candidate, "index.html").isFile) {
                return dir
            }
        }
        // 2. Root index.html
        if (File(root, "index.html").isFile) return ""
        // 3. public/ folder (CRA shell, some frameworks)
        val publicDir = File(root, "public")
        if (publicDir.isDirectory && File(publicDir, "index.html").isFile) {
            return "public"
        }
        return "" // nothing found — will show info page
    }

    private fun detectProjectTypeLocal(): String {
        val root = localRoot ?: return "unknown"
        val pkgJson = File(root, "package.json")
        if (!pkgJson.isFile) {
            // No package.json → plain HTML/CSS/JS project
            return if (File(root, "index.html").isFile) "static" else "unknown"
        }
        val content = pkgJson.readText()
        return detectFrameworkFromPackageJson(content)
    }

    // ── Serve-root detection (SAF) ────────────────────────────────

    private fun detectServeSubDirSaf(): String {
        val root = safRoot ?: return ""
        for (dir in buildOutputDirs) {
            val parts = dir.split("/")
            var current: DocumentFile? = root
            for (part in parts) {
                current = current?.findFile(part)
            }
            if (current != null && current.isDirectory) {
                val index = current.findFile("index.html")
                if (index != null && !index.isDirectory) return dir
            }
        }
        // Root index.html
        val rootIndex = root.findFile("index.html")
        if (rootIndex != null && !rootIndex.isDirectory) return ""
        // public/
        val publicDir = root.findFile("public")
        if (publicDir != null && publicDir.isDirectory) {
            val pubIndex = publicDir.findFile("index.html")
            if (pubIndex != null && !pubIndex.isDirectory) return "public"
        }
        return ""
    }

    private fun detectProjectTypeSaf(): String {
        val root = safRoot ?: return "unknown"
        val pkgFile = root.findFile("package.json")
        if (pkgFile == null || pkgFile.isDirectory) {
            val indexFile = root.findFile("index.html")
            return if (indexFile != null && !indexFile.isDirectory) "static" else "unknown"
        }
        val content = context.contentResolver.openInputStream(pkgFile.uri)
            ?.bufferedReader()?.readText() ?: return "unknown"
        return detectFrameworkFromPackageJson(content)
    }

    // ── Shared helpers ────────────────────────────────────────────

    private fun detectFrameworkFromPackageJson(content: String): String {
        return when {
            "\"react\"" in content || "\"react-dom\"" in content -> "react"
            "\"vue\"" in content -> "vue"
            "\"@angular/core\"" in content -> "angular"
            "\"svelte\"" in content -> "svelte"
            "\"next\"" in content -> "nextjs"
            "\"nuxt\"" in content -> "nuxt"
            "\"vite\"" in content -> "vite"
            else -> "node"
        }
    }

    private fun serveProjectInfoPage(): Response {
        val framework = detectedProjectType
        val frameworkLabel = when (framework) {
            "react" -> "React"
            "vue" -> "Vue"
            "angular" -> "Angular"
            "svelte" -> "Svelte"
            "nextjs" -> "Next.js"
            "nuxt" -> "Nuxt"
            "vite" -> "Vite"
            "node" -> "Node.js"
            else -> "Web"
        }
        val buildCmd = when (framework) {
            "react", "vue", "svelte", "vite", "node" -> "npm install &amp;&amp; npm run build"
            "angular" -> "npm install &amp;&amp; ng build"
            "nextjs" -> "npm install &amp;&amp; next build &amp;&amp; next export"
            "nuxt" -> "npm install &amp;&amp; nuxt generate"
            else -> "npm install &amp;&amp; npm run build"
        }

        val html = """
            <!DOCTYPE html>
            <html lang="es">
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Proyecto $frameworkLabel</title>
                <style>
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                        background: #0f0f0f;
                        color: #e0e0e0;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        min-height: 100vh;
                        padding: 24px;
                    }
                    .container {
                        text-align: center;
                        max-width: 420px;
                    }
                    .icon {
                        font-size: 48px;
                        margin-bottom: 16px;
                    }
                    h1 {
                        font-size: 22px;
                        font-weight: 600;
                        margin-bottom: 8px;
                    }
                    .subtitle {
                        color: #888;
                        font-size: 14px;
                        line-height: 1.5;
                        margin-bottom: 24px;
                    }
                    .steps {
                        text-align: left;
                        background: #1a1a1a;
                        border-radius: 12px;
                        padding: 20px;
                        margin-bottom: 20px;
                    }
                    .step {
                        display: flex;
                        align-items: flex-start;
                        gap: 12px;
                        margin-bottom: 16px;
                    }
                    .step:last-child { margin-bottom: 0; }
                    .step-num {
                        background: #2563eb;
                        color: #fff;
                        width: 24px;
                        height: 24px;
                        border-radius: 50%;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        font-size: 12px;
                        font-weight: 700;
                        flex-shrink: 0;
                        margin-top: 2px;
                    }
                    .step-text {
                        font-size: 14px;
                        line-height: 1.5;
                    }
                    code {
                        background: #262626;
                        padding: 2px 8px;
                        border-radius: 4px;
                        font-family: 'SF Mono', 'Fira Code', monospace;
                        font-size: 13px;
                        color: #93c5fd;
                    }
                    .badge {
                        display: inline-block;
                        background: #1e3a5f;
                        color: #60a5fa;
                        padding: 4px 12px;
                        border-radius: 999px;
                        font-size: 12px;
                        font-weight: 600;
                        margin-bottom: 16px;
                    }
                    .hint {
                        color: #666;
                        font-size: 12px;
                        line-height: 1.5;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="icon">⚙️</div>
                    <div class="badge">$frameworkLabel</div>
                    <h1>Este proyecto necesita compilarse</h1>
                    <p class="subtitle">
                        Los proyectos $frameworkLabel necesitan un paso de build antes de poder previsualizarse.
                    </p>
                    <div class="steps">
                        <div class="step">
                            <div class="step-num">1</div>
                            <div class="step-text">
                                Pedile a la IA que ejecute:<br>
                                <code>$buildCmd</code>
                            </div>
                        </div>
                        <div class="step">
                            <div class="step-num">2</div>
                            <div class="step-text">Tocá el botón <b>Refresh</b> ↻ en la barra de preview</div>
                        </div>
                        <div class="step">
                            <div class="step-num">3</div>
                            <div class="step-text">¡Tu app se mostrará aquí!</div>
                        </div>
                    </div>
                    <p class="hint">
                        Tip: También podés pedirle a la IA que inicie un servidor de desarrollo
                        con <code>npm run dev</code> — el preview lo detectará automáticamente.
                    </p>
                </div>
            </body>
            </html>
        """.trimIndent()
        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
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
