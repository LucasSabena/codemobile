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

        // Virtual endpoint: Service Worker for JSX/TSX transpilation
        if (uri == "__preview_sw.js") {
            return newFixedLengthResponse(
                Response.Status.OK,
                "application/javascript",
                SERVICE_WORKER_CODE
            )
        }

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

        // Framework project: serve transformed index with SW bootstrap + importmap
        if (uri == "index.html" && needsBuild) {
            return try {
                serveTransformedIndex()
            } catch (_: Exception) {
                serveProjectInfoPage()
            }
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

    // ── Framework preview (Service Worker transpilation) ─────────

    /**
     * Serve a transformed index.html that bootstraps a Babel Service Worker
     * and import maps so React/Vue/etc. projects work without npm build.
     */
    private fun serveTransformedIndex(): Response {
        val originalHtml = readProjectFile("index.html")
            ?: readProjectFile("public/index.html")
        val entryPoint = detectEntryPoint()
        val importMapJson = generateImportMapJson()

        val html = if (originalHtml != null) {
            transformExistingIndex(originalHtml, importMapJson, entryPoint)
        } else {
            generateBootstrapIndex(importMapJson, entryPoint)
        }
        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
    }

    private fun transformExistingIndex(
        originalHtml: String,
        importMapJson: String,
        detectedEntry: String?
    ): String {
        var html = originalHtml

        // Inject importmap before </head> or before first <script>
        val importMapTag = "<script type=\"importmap\">$importMapJson</script>"
        html = when {
            html.contains("</head>") ->
                html.replace("</head>", "$importMapTag\n</head>")
            html.contains("<script") ->
                html.replaceFirst("<script", "$importMapTag\n<script")
            else -> "$importMapTag\n$html"
        }

        // Find <script type="module" src="..."> and replace with SW bootstrap
        val scriptRegex = Regex("""<script\s+type=["']module["']\s+src=["']([^"']+)["'][^>]*>\s*</script>""")
        val match = scriptRegex.find(html)

        if (match != null) {
            val entrySrc = match.groupValues[1]
            html = html.replace(match.value, generateBootstrapScript(entrySrc))
        } else if (detectedEntry != null) {
            val bootstrapScript = generateBootstrapScript("/$detectedEntry")
            html = if (html.contains("</body>")) {
                html.replace("</body>", "$bootstrapScript\n</body>")
            } else {
                "$html\n$bootstrapScript"
            }
        }

        return html
    }

    private fun generateBootstrapIndex(importMapJson: String, entryPoint: String?): String {
        val entry = entryPoint ?: "src/main.jsx"
        return """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Preview</title>
  <script type="importmap">$importMapJson</script>
</head>
<body>
  <div id="root"></div>
  <div id="app"></div>
  ${generateBootstrapScript("/$entry")}
</body>
</html>""".trimIndent()
    }

    private fun generateBootstrapScript(entrySrc: String): String {
        return """<script>
(function() {
  if (!('serviceWorker' in navigator)) {
    document.body.innerHTML = '<p style="color:#ef4444;padding:20px">Service Workers no soportados</p>';
    return;
  }
  var ld = document.createElement('div');
  ld.id = '__pload';
  ld.style.cssText = 'position:fixed;inset:0;display:flex;align-items:center;justify-content:center;background:#0f0f0f;color:#888;font-family:system-ui;z-index:99999';
  ld.innerHTML = '<div style="text-align:center"><div style="margin-bottom:12px;font-size:28px">⚡</div><div>Transpilando proyecto...</div><div style="margin-top:8px;font-size:12px;color:#555">Cargando Babel + dependencias desde CDN</div></div>';
  document.body.appendChild(ld);
  navigator.serviceWorker.register('/__preview_sw.js', {scope:'/'}).then(function(reg) {
    if (navigator.serviceWorker.controller) { go(); return; }
    var sw = reg.installing || reg.waiting || reg.active;
    if (!sw) return;
    if (sw.state === 'activated') { go(); return; }
    sw.addEventListener('statechange', function() { if (sw.state === 'activated') go(); });
  }).catch(function(err) {
    ld.innerHTML = '<div style="text-align:center;color:#ef4444;padding:20px"><div style="margin-bottom:8px;font-size:18px">Error</div><div style="font-size:13px">' + err.message + '</div></div>';
  });
  function go() {
    var el = document.getElementById('__pload');
    if (el) el.remove();
    var s = document.createElement('script');
    s.type = 'module';
    s.src = '$entrySrc';
    s.onerror = function(e) { console.error('Entry load failed:', e); };
    document.body.appendChild(s);
  }
})();
</script>"""
    }

    /**
     * Detect the project's JS entry point file.
     */
    private fun detectEntryPoint(): String? {
        val candidates = listOf(
            "src/main.jsx", "src/main.tsx", "src/main.js", "src/main.ts",
            "src/index.jsx", "src/index.tsx", "src/index.js", "src/index.ts",
            "src/App.jsx", "src/App.tsx", "src/App.js", "src/App.ts"
        )
        return if (isSaf) {
            candidates.firstOrNull { path ->
                val doc = findSafDocument(safRoot ?: return@firstOrNull false, path)
                doc != null && !doc.isDirectory
            }
        } else {
            candidates.firstOrNull { path ->
                File(localRoot ?: return@firstOrNull false, path).isFile
            }
        }
    }

    /**
     * Read a file from the project root as text.
     */
    private fun readProjectFile(relativePath: String): String? {
        return try {
            if (isSaf) {
                val doc = findSafDocument(safRoot ?: return null, relativePath) ?: return null
                if (doc.isDirectory) return null
                context.contentResolver.openInputStream(doc.uri)?.bufferedReader()?.readText()
            } else {
                val file = File(localRoot ?: return null, relativePath)
                if (file.isFile) file.readText() else null
            }
        } catch (_: Exception) { null }
    }

    /**
     * Generate an import map JSON string from package.json dependencies.
     * Maps each dependency to its esm.sh CDN URL.
     */
    private fun generateImportMapJson(): String {
        val deps = readPackageJsonDependencies()
        if (deps.isEmpty()) return "{\"imports\":{}}"

        val entries = mutableListOf<String>()
        for ((name, version) in deps) {
            val cleanVersion = version.trimStart('^', '~', '>', '<', '=', ' ')
            val esmUrl = "https://esm.sh/$name@$cleanVersion"
            entries.add("\"$name\":\"$esmUrl\"")
            entries.add("\"$name/\":\"$esmUrl/\"")
        }
        return "{\"imports\":{${entries.joinToString(",")}}}"
    }

    /**
     * Read dependencies from package.json using Android's built-in org.json.
     */
    private fun readPackageJsonDependencies(): Map<String, String> {
        val content = readProjectFile("package.json") ?: return emptyMap()
        val deps = mutableMapOf<String, String>()
        try {
            val json = org.json.JSONObject(content)
            val depsObj = json.optJSONObject("dependencies") ?: return emptyMap()
            for (key in depsObj.keys()) {
                val value = depsObj.optString(key, "")
                if (value.isNotBlank()) {
                    deps[key] = value
                }
            }
        } catch (_: Exception) { }
        return deps
    }

    companion object {
        private const val MIME_PLAINTEXT = "text/plain"

        /**
         * Service Worker JavaScript that intercepts fetch requests and:
         * - Transforms .jsx/.tsx/.ts files using Babel standalone (loaded from CDN)
         * - Converts CSS imports into JS that injects <style> tags
         * - Handles asset imports (SVG, images) as URL exports
         * - Resolves extensionless imports by trying .jsx/.tsx/.js/.ts
         * - Replaces import.meta.env with development defaults
         */
        private val SERVICE_WORKER_CODE = """
importScripts('https://unpkg.com/@babel/standalone@7/babel.min.js');

self.addEventListener('install', function(e) { self.skipWaiting(); });
self.addEventListener('activate', function(e) { e.waitUntil(clients.claim()); });

self.addEventListener('fetch', function(event) {
  var url = new URL(event.request.url);
  if (url.origin !== self.location.origin) return;
  if (url.pathname.startsWith('/__')) return;

  var lastSeg = url.pathname.split('/').pop() || '';
  var ext = lastSeg.indexOf('.') !== -1 ? lastSeg.split('.').pop().toLowerCase() : '';

  // Transform JSX/TSX/TS files
  if (ext === 'jsx' || ext === 'tsx' || ext === 'ts') {
    event.respondWith(handleTransform(event.request, url.pathname, ext));
    return;
  }

  // CSS imported as JS module (not <link> stylesheets)
  if (ext === 'css' && event.request.destination !== 'style') {
    event.respondWith(handleCSS(event.request));
    return;
  }

  // Asset imports (SVG, images) imported from JS
  if (['svg','png','jpg','jpeg','gif','webp','ico','avif','bmp'].indexOf(ext) !== -1
      && event.request.destination === '') {
    event.respondWith(
      Promise.resolve(new Response('export default "' + url.pathname + '";', jsH()))
    );
    return;
  }

  // Extensionless imports — try .jsx, .tsx, .js, .ts, /index.*
  if (!ext && url.pathname !== '/' && event.request.destination === '') {
    event.respondWith(handleNoExt(event.request, url));
    return;
  }
});

function handleTransform(request, pathname, ext) {
  return fetch(request).then(function(resp) {
    if (!resp.ok) return resp;
    return resp.text().then(function(code) {
      return doTransform(code, pathname, ext);
    });
  }).catch(function(e) {
    return errResp(pathname, e.message);
  });
}

function handleCSS(request) {
  return fetch(request).then(function(resp) {
    if (!resp.ok) return new Response('export default "";', jsH());
    return resp.text().then(function(css) {
      var js = 'var __s=document.createElement("style");__s.textContent=' +
               JSON.stringify(css) + ';document.head.appendChild(__s);export default undefined;';
      return new Response(js, jsH());
    });
  }).catch(function() {
    return new Response('export default "";', jsH());
  });
}

function handleNoExt(request, url) {
  var exts = ['.jsx','.tsx','.js','.ts'];
  var idxs = ['/index.jsx','/index.tsx','/index.js','/index.ts'];
  function tryList(list, i) {
    if (i >= list.length) return null;
    return fetch(url.pathname + list[i]).then(function(r) {
      if (!r.ok) return tryList(list, i + 1);
      var e2 = list[i].split('.').pop();
      return r.text().then(function(code) { return doTransform(code, url.pathname + list[i], e2); });
    }).catch(function() { return tryList(list, i + 1); });
  }
  return (tryList(exts, 0) || Promise.resolve(null)).then(function(r) {
    if (r) return r;
    return tryList(idxs, 0) || fetch(request);
  }).then(function(r) {
    return r || fetch(request);
  });
}

function doTransform(code, filename, ext) {
  try {
    // Replace Vite-specific env variables
    code = code.replace(/import\.meta\.env\.(\w+)/g, function(m, k) {
      var d = {DEV:'true',PROD:'false',MODE:'"development"',BASE_URL:'"/"',SSR:'false'};
      return d[k] || 'undefined';
    });
    code = code.replace(/import\.meta\.env/g,
      '({DEV:true,PROD:false,MODE:"development",BASE_URL:"/",SSR:false})');
    code = code.replace(/import\.meta\.hot/g, 'undefined');

    var presets = [];
    if (ext === 'jsx' || ext === 'tsx') presets.push(['react', {runtime:'automatic'}]);
    if (ext === 'tsx' || ext === 'ts') presets.push('typescript');
    if (presets.length === 0) presets.push(['react', {runtime:'automatic'}]);

    var result = Babel.transform(code, {
      presets: presets,
      filename: filename,
      sourceType: 'module'
    });
    return new Response(result.code, jsH());
  } catch(e) {
    return errResp(filename, e.message);
  }
}

function jsH() { return {headers:{'Content-Type':'application/javascript;charset=utf-8'}}; }

function errResp(file, msg) {
  var js = 'console.error("[Preview] Error in ' + file.replace(/'/g,"\\'")
         + ':", ' + JSON.stringify(msg || 'Unknown error') + ');\n'
         + 'document.body.innerHTML += \'<pre style="color:#ef4444;background:#1a1a1a;padding:16px;margin:0;font-size:13px;white-space:pre-wrap">Error in ' + file + ':\\n' + (msg || '').replace(/'/g, "\\'"   ).replace(/\\/g, '\\\\').substring(0, 500) + '</pre>\';
';
  return new Response(js, jsH());
}
        """.trimIndent()
    }
}
