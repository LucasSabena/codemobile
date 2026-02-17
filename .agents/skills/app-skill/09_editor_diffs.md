# Editor de Código y Visor de Diffs

## Concepto

El editor no es el foco principal (esto no es un IDE), pero necesitamos:

1. **Ver código** de archivos del proyecto con syntax highlighting
2. **Ver diffs** de lo que la IA cambió (antes/después)
3. **Aceptar o rechazar** cambios archivo por archivo
4. **Editar manualmente** si hace falta un ajuste fino

Usamos **CodeMirror 6** corriendo dentro de un **WebView** Android, con un bridge Kotlin ↔ JavaScript para comunicación.

---

## Arquitectura del editor

```
┌──────────────────────────┐
│      Compose UI          │
│  (botones, header, tabs) │
│                          │
│  ┌────────────────────┐  │
│  │     WebView        │  │
│  │  ┌──────────────┐  │  │
│  │  │ CodeMirror 6 │  │  │
│  │  │  + merge ext │  │  │
│  │  │  + lang exts │  │  │
│  │  └──────────────┘  │  │
│  └────────────────────┘  │
│                          │
│  [Aplicar] [Rechazar]    │
└──────────────────────────┘

Comunicación:
  Kotlin → JS:  webView.evaluateJavascript("editor.setContent(...)")
  JS → Kotlin:  @JavascriptInterface methods
```

---

## Bundle de CodeMirror

Se crea un bundle HTML/JS/CSS que se incluye en `assets/editor/`:

```
app/src/main/assets/editor/
├── index.html          ← HTML principal que carga CodeMirror
├── editor.bundle.js    ← CodeMirror 6 + extensiones bundleadas
└── editor.css          ← Estilos custom (dark theme)
```

### Dependencias npm del bundle (se compila en build time, no en el móvil):

```json
{
  "dependencies": {
    "@codemirror/state": "^6.x",
    "@codemirror/view": "^6.x",
    "@codemirror/merge": "^6.x",
    "@codemirror/lang-javascript": "^6.x",
    "@codemirror/lang-html": "^6.x",
    "@codemirror/lang-css": "^6.x",
    "@codemirror/lang-json": "^6.x",
    "@codemirror/lang-markdown": "^6.x",
    "@codemirror/lang-python": "^6.x",
    "@codemirror/theme-one-dark": "^6.x",
    "@codemirror/autocomplete": "^6.x",
    "@codemirror/search": "^6.x"
  }
}
```

Se bundlea con `esbuild` o `vite` a un solo JS file. Este bundle se incluye en `assets/` del APK — **no se compila en el dispositivo**.

---

## Bridge Kotlin ↔ JavaScript

### Kotlin → JavaScript (llamar funciones del editor)

```kotlin
class EditorBridge(private val webView: WebView) {
    
    fun openFile(path: String, content: String, language: String) {
        val escaped = content.escapeForJs()
        webView.evaluateJavascript(
            "window.editor.openFile('$path', '$escaped', '$language')",
            null
        )
    }
    
    fun showDiff(originalContent: String, modifiedContent: String, filePath: String) {
        val origEscaped = originalContent.escapeForJs()
        val modEscaped = modifiedContent.escapeForJs()
        webView.evaluateJavascript(
            "window.editor.showDiff('$origEscaped', '$modEscaped', '$filePath')",
            null
        )
    }
    
    fun getContent(callback: (String) -> Unit) {
        webView.evaluateJavascript("window.editor.getContent()") { result ->
            callback(result.unescapeFromJs())
        }
    }
    
    fun setReadOnly(readOnly: Boolean) {
        webView.evaluateJavascript(
            "window.editor.setReadOnly($readOnly)",
            null
        )
    }
}
```

### JavaScript → Kotlin (eventos del editor)

```kotlin
class EditorJsInterface(
    private val onContentChanged: (String) -> Unit,
    private val onSaveRequested: () -> Unit
) {
    @JavascriptInterface
    fun onContentChange(content: String) {
        onContentChanged(content)
    }
    
    @JavascriptInterface
    fun onSave() {
        onSaveRequested()
    }
}

// Setup
webView.addJavascriptInterface(
    EditorJsInterface(
        onContentChanged = { content -> /* actualizar estado */ },
        onSaveRequested = { /* guardar archivo */ }
    ),
    "AndroidBridge"
)
```

---

## Composable del editor

```kotlin
@Composable
fun CodeEditorScreen(
    filePath: String,
    content: String,
    language: String,
    readOnly: Boolean = false,
    onSave: (String) -> Unit,
    onClose: () -> Unit
) {
    var webView: WebView? by remember { mutableStateOf(null) }
    
    Column {
        // Header con path del archivo
        TopAppBar(
            title = { Text(filePath.substringAfterLast("/")) },
            subtitle = { Text(filePath, style = MaterialTheme.typography.bodySmall) },
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") } },
            actions = {
                if (!readOnly) {
                    IconButton(onClick = { 
                        webView?.let { wv ->
                            EditorBridge(wv).getContent { onSave(it) }
                        }
                    }) { 
                        Icon(Icons.Default.Save, "Save") 
                    }
                }
            }
        )
        
        // WebView con CodeMirror
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    addJavascriptInterface(EditorJsInterface(...), "AndroidBridge")
                    loadUrl("file:///android_asset/editor/index.html")
                    webView = this
                }
            },
            update = { wv ->
                EditorBridge(wv).openFile(filePath, content, language)
                EditorBridge(wv).setReadOnly(readOnly)
            }
        )
    }
}
```

---

## Visor de diffs

Cuando la IA modifica un archivo (via tool use), se muestra el diff:

```kotlin
@Composable
fun DiffViewScreen(
    filePath: String,
    originalContent: String,
    modifiedContent: String,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onEdit: () -> Unit
) {
    Column {
        // Header
        TopAppBar(
            title = { Text("Cambios: ${filePath.substringAfterLast("/")}") }
        )
        
        // CodeMirror merge view
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    loadUrl("file:///android_asset/editor/index.html")
                }.also { wv ->
                    EditorBridge(wv).showDiff(originalContent, modifiedContent, filePath)
                }
            },
            modifier = Modifier.weight(1f)
        )
        
        // Botones de acción
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(onClick = onReject) {
                Icon(Icons.Default.Close, null)
                Text("Rechazar")
            }
            Button(onClick = onEdit) {
                Icon(Icons.Default.Edit, null)
                Text("Editar")
            }
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Check, null)
                Text("Aplicar")
            }
        }
    }
}
```

### Flujo de diffs en el chat

1. La IA usa `writeFile(path, content)` en modo BUILD
2. La app guarda el contenido original antes de escribir
3. En el chat, el mensaje de tool call muestra: "Archivo modificado: `path`" + botón "[Ver cambios]"
4. Tocar "[Ver cambios]" abre el `DiffViewScreen`
5. El usuario puede:
   - **Aplicar**: se guarda el archivo modificado (ya está en disco)
   - **Rechazar**: se restaura el contenido original
   - **Editar**: se abre el editor con el contenido modificado, editable

---

## File Explorer

Componente para navegar archivos del proyecto:

```kotlin
@Composable
fun FileExplorer(
    rootPath: String,
    onFileSelected: (String) -> Unit
) {
    val files = remember(rootPath) { 
        listFilesRecursive(rootPath)
            .filter { !it.path.contains("node_modules") && !it.path.contains(".git") }
    }
    
    LazyColumn {
        items(files) { file ->
            FileTreeItem(
                file = file,
                onClick = { onFileSelected(file.path) }
            )
        }
    }
}

@Composable
fun FileTreeItem(file: FileNode, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = (file.depth * 16).dp, top = 8.dp, bottom = 8.dp, end = 16.dp)
    ) {
        Icon(
            imageVector = if (file.isDirectory) Icons.Default.Folder else fileIcon(file.extension),
            contentDescription = null,
            tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(8.dp))
        Text(file.name, style = MaterialTheme.typography.bodyMedium)
    }
}
```

## Detección de lenguaje

```kotlin
fun detectLanguage(filePath: String): String {
    return when (filePath.substringAfterLast(".").lowercase()) {
        "js", "jsx", "mjs" -> "javascript"
        "ts", "tsx", "mts" -> "typescript"
        "html", "htm" -> "html"
        "css", "scss", "sass" -> "css"
        "json" -> "json"
        "md", "mdx" -> "markdown"
        "py" -> "python"
        "kt", "kts" -> "kotlin"
        "rs" -> "rust"
        "go" -> "go"
        "yaml", "yml" -> "yaml"
        "xml" -> "xml"
        "sh", "bash", "zsh" -> "shell"
        else -> "text"
    }
}
```
