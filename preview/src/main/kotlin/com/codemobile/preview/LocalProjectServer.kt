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

        // Framework project: serve transformed index with inline Babel + module loader
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
            var response = if (isSaf) serveSaf(resolvedUri) else serveLocal(resolvedUri)

            // Extension resolution: if not found, try .jsx/.tsx/.js/.ts
            if (response.status == Response.Status.NOT_FOUND) {
                val resolved = tryResolveWithExtensions(resolvedUri)
                if (resolved != null) response = resolved
            }

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

    /**
     * Try resolving a URI by appending common JS extensions.
     * Handles imports like './App' → './App.jsx'
     */
    private fun tryResolveWithExtensions(uri: String): Response? {
        val extensions = listOf(".jsx", ".tsx", ".js", ".ts", ".mjs")
        val indexSuffixes = listOf("/index.jsx", "/index.tsx", "/index.js", "/index.ts")
        val hasKnownExt = extensions.any { uri.endsWith(it) } || uri.endsWith(".html") || uri.endsWith(".css")
        if (hasKnownExt) return null
        for (ext in extensions + indexSuffixes) {
            val tryUri = "$uri$ext"
            val response = if (isSaf) serveSaf(tryUri) else serveLocal(tryUri)
            if (response.status == Response.Status.OK) return response
        }
        return null
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

    // ── Framework preview (inline Babel transpilation) ─────────

    /**
     * Serve a transformed index.html that loads Babel standalone from CDN
     * and uses a custom module loader to transpile JSX/TSX in the browser.
     * No Service Worker needed — everything runs in the main thread.
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

        // Inject importmap + Babel CDN before </head> or before first <script>
        val headInject = """<script type="importmap">$importMapJson</script>
<script src="https://esm.sh/@babel/standalone"></script>"""
        html = when {
            html.contains("</head>") ->
                html.replace("</head>", "$headInject\n</head>")
            html.contains("<script") ->
                html.replaceFirst("<script", "$headInject\n<script")
            else -> "$headInject\n$html"
        }

        // Find <script type="module" src="..."> and replace with module loader
        val scriptRegex = Regex("""<script\s+type=["']module["']\s+src=["']([^"']+)["'][^>]*>\s*</script>""")
        val match = scriptRegex.find(html)

        if (match != null) {
            val entrySrc = match.groupValues[1]
            html = html.replace(match.value, generateModuleLoaderScript(entrySrc))
        } else if (detectedEntry != null) {
            val loaderScript = generateModuleLoaderScript("/$detectedEntry")
            html = if (html.contains("</body>")) {
                html.replace("</body>", "$loaderScript\n</body>")
            } else {
                "$html\n$loaderScript"
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
  <script src="https://esm.sh/@babel/standalone"></script>
</head>
<body>
  <div id="root"></div>
  <div id="app"></div>
  ${generateModuleLoaderScript("/$entry")}
</body>
</html>""".trimIndent()
    }

    /**
     * Generate the inline module loader script.
     */
    private fun generateModuleLoaderScript(entrySrc: String): String {
        return MODULE_LOADER_TEMPLATE.replace("__ENTRY__", entrySrc)
    }

    /** Returns true if the URI looks like a request for a static asset (has a file extension) */
    private fun looksLikeAsset(uri: String): Boolean {
        val lastSegment = uri.substringAfterLast('/')
        return lastSegment.contains('.')
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
         * Inline module loader that runs in the main thread.
         * Loads Babel standalone from CDN (already in a <script> tag),
         * then recursively fetches, transforms, and bundles source files
         * using Blob URLs. No Service Worker needed.
         */
        private val MODULE_LOADER_TEMPLATE = """<script>
(async function() {
  var cache = {};
  var loading = {};

  function dirname(p) {
    var idx = p.lastIndexOf('/');
    return idx > 0 ? p.substring(0, idx) : '';
  }

  function normPath(p) {
    var parts = p.split('/');
    var st = [];
    for (var i = 0; i < parts.length; i++) {
      if (parts[i] === '..') { if (st.length) st.pop(); }
      else if (parts[i] && parts[i] !== '.') st.push(parts[i]);
    }
    return '/' + st.join('/');
  }

  async function loadMod(path) {
    if (cache[path]) return cache[path];
    if (loading[path]) return loading[path];
    var rsv;
    loading[path] = new Promise(function(r) { rsv = r; });

    var resp;
    try { resp = await fetch(path); } catch(e) {
      var b = mkBlob('console.error("[Preview] Network error:", ' + JSON.stringify(path) + ');');
      done(path, b, rsv); return b;
    }
    if (!resp.ok) {
      var b = mkBlob('console.error("[Preview] 404:", ' + JSON.stringify(path) + ');');
      done(path, b, rsv); return b;
    }
    var code = await resp.text();
    var segs = path.split('/');
    var last = segs[segs.length - 1] || '';
    var dotIdx = last.lastIndexOf('.');
    var ext = dotIdx > 0 ? last.substring(dotIdx + 1).toLowerCase() : '';
    var ctype = (resp.headers.get('content-type') || '').toLowerCase();

    // CSS -> inject as style tag
    if (ext === 'css' || ctype.indexOf('text/css') !== -1) {
      var js = 'var _s=document.createElement("style");_s.textContent=' + JSON.stringify(code) + ';document.head.appendChild(_s);export default undefined;';
      var b = mkBlob(js);
      done(path, b, rsv); return b;
    }

    // Image/asset -> export URL
    var assetExts = ['svg','png','jpg','jpeg','gif','webp','ico','avif','bmp','mp4','webm','woff','woff2','ttf','otf'];
    if (assetExts.indexOf(ext) !== -1) {
      var b = mkBlob('export default ' + JSON.stringify(path) + ';');
      done(path, b, rsv); return b;
    }

    // JSON -> export default
    if (ext === 'json' || ctype.indexOf('json') !== -1) {
      try { JSON.parse(code); var b = mkBlob('export default ' + code + ';'); done(path, b, rsv); return b; }
      catch(e) { /* not valid JSON, treat as JS */ }
    }

    // Replace Vite/CRA env vars
    code = code.replace(/import\.meta\.env\.(\w+)/g, function(m, k) {
      var d = {DEV:'true',PROD:'false',MODE:'"development"',BASE_URL:'"/"',SSR:'false'};
      return d[k] || 'undefined';
    });
    code = code.replace(/import\.meta\.env/g, '({DEV:true,PROD:false,MODE:"development",BASE_URL:"/",SSR:false})');
    code = code.replace(/import\.meta\.hot/g, 'undefined');
    code = code.replace(/process\.env\.NODE_ENV/g, '"development"');

    // Babel transform (handles JSX, TSX, TS, and plain JS)
    if (window.Babel) {
      try {
        var presets = [['react', {runtime:'automatic'}], 'typescript'];
        var result = Babel.transform(code, {presets:presets, filename:path, sourceType:'module'});
        code = result.code;
      } catch(e) {
        console.error('[Preview] Babel error in ' + path + ':', e);
      }
    }

    // Collect local import specifiers (starting with ./ or ../)
    var specs = {};
    var re1 = /(?:from|import)\s*['"](\\.{1,2}\\/[^'"]*)['"]/g;
    var re2 = /import\(\s*['"](\\.{1,2}\\/[^'"]*)['"]\s*\)/g;
    var m;
    while (m = re1.exec(code)) specs[m[1]] = 1;
    while (m = re2.exec(code)) specs[m[1]] = 1;

    // Resolve and load each local dependency
    var baseDir = dirname(path);
    var blobMap = {};
    var specKeys = Object.keys(specs);
    for (var i = 0; i < specKeys.length; i++) {
      var sp = specKeys[i];
      var abs = normPath(baseDir + '/' + sp);
      blobMap[sp] = await loadMod(abs);
    }

    // Replace import specifiers with blob URLs
    var keys = Object.keys(blobMap);
    for (var i = 0; i < keys.length; i++) {
      var sp = keys[i];
      var blob = blobMap[sp];
      code = code.split("'" + sp + "'").join("'" + blob + "'");
      code = code.split('"' + sp + '"').join('"' + blob + '"');
    }

    var b = mkBlob(code);
    done(path, b, rsv); return b;
  }

  function mkBlob(code) {
    return URL.createObjectURL(new Blob([code], {type:'application/javascript'}));
  }

  function done(path, blob, resolver) {
    cache[path] = blob;
    if (resolver) resolver(blob);
  }

  function waitBabel() {
    return new Promise(function(r) {
      if (window.Babel) return r();
      var iv = setInterval(function() { if (window.Babel) { clearInterval(iv); r(); } }, 50);
      setTimeout(function() { clearInterval(iv); r(); }, 30000);
    });
  }

  var ld = document.createElement('div');
  ld.id = '__pld';
  ld.style.cssText = 'position:fixed;inset:0;display:flex;align-items:center;justify-content:center;background:#0f0f0f;z-index:99999;color:#888;font-family:system-ui';
  ld.innerHTML = '<div style="text-align:center"><div style="font-size:28px;margin-bottom:12px">\u26A1</div><div id="__pmsg">Cargando Babel...</div><div style="margin-top:8px;font-size:12px;color:#555">Descargando transpiler desde CDN</div></div>';
  document.body.appendChild(ld);

  try {
    await waitBabel();
    var msg = document.getElementById('__pmsg');
    if (msg) msg.textContent = 'Transpilando modulos...';
    var entryBlob = await loadMod('__ENTRY__');
    ld.remove();
    await import(entryBlob);
  } catch(e) {
    var h = (e.message || 'Error desconocido').replace(/&/g,'&amp;').replace(/</g,'&lt;');
    ld.innerHTML = '<div style="text-align:center;color:#ef4444;padding:20px;max-width:350px"><div style="font-size:18px;margin-bottom:8px">Error</div><div style="font-size:13px;word-break:break-all">' + h + '</div></div>';
    console.error('[Preview]', e);
  }
})();
</script>"""
    }
}
