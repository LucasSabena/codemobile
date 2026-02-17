# Preview Web

## Concepto

Cuando el usuario ejecuta un dev server en la terminal (ej: `npm run dev`), la app detecta la URL local y permite previsualizarla en un WebView integrado, sin salir de la app.

---

## Cómo funciona

### 1. Detección automática de URL

La terminal monitorea el output buscando patrones de URLs locales:

```kotlin
object DevServerDetector {
    
    // Patrones comunes de dev servers
    private val URL_PATTERNS = listOf(
        Regex("""(?:Local|Server|App):\s*(https?://(?:localhost|127\.0\.0\.1|0\.0\.0\.0):\d+)""", RegexOption.IGNORE_CASE),
        Regex("""listening (?:on|at)\s*(https?://(?:localhost|127\.0\.0\.1):\d+)""", RegexOption.IGNORE_CASE),
        Regex("""ready (?:on|in|at)\s*(?:.*?)(https?://(?:localhost|127\.0\.0\.1):\d+)""", RegexOption.IGNORE_CASE),
        Regex("""(https?://localhost:\d+)"""),
    )
    
    fun detectUrl(terminalOutput: String): String? {
        for (pattern in URL_PATTERNS) {
            pattern.find(terminalOutput)?.let {
                return it.groupValues[1]
                    .replace("0.0.0.0", "localhost")
            }
        }
        return null
    }
}
```

Esto detecta output de:
- **Vite**: `Local: http://localhost:5173/`
- **Next.js**: `ready - started server on http://localhost:3000`
- **Create React App**: `Local: http://localhost:3000`
- **Express/Koa**: `Server listening on http://localhost:4000`
- Cualquier otro server que imprima una URL localhost

### 2. Notificación al usuario

Cuando se detecta un server:
- Aparece un **toast/snackbar**: "Dev server detectado en localhost:5173 — [Ver preview]"
- El tab "Preview" en el bottom sheet se activa con un indicador (dot verde)
- El usuario puede tocar para abrir el preview

### 3. WebView para preview

```kotlin
@Composable
fun PreviewScreen(
    url: String,
    onOpenExternal: (String) -> Unit,
    onClose: () -> Unit
) {
    Column {
        // Barra superior
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Text(url, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { /* refresh WebView */ }) { Icon(Icons.Default.Refresh, "Refresh") }
            IconButton(onClick = { onOpenExternal(url) }) { Icon(Icons.Default.OpenInBrowser, "Open in Chrome") }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
        }
        
        // WebView
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    loadUrl(url)
                }
            },
            update = { webView ->
                if (webView.url != url) webView.loadUrl(url)
            }
        )
    }
}
```

### 4. Hot Reload / HMR

- Hot Module Replacement funciona automáticamente porque usa WebSocket en localhost
- El WebView de Android usa Chromium → soporta WebSockets nativamente
- Vite, Next.js, CRA: todos envían updates via WS al mismo puerto
- El usuario ve los cambios en tiempo real en el preview

---

## Abrir en navegador externo

Botón para abrir la URL en Chrome/browser del sistema:

```kotlin
fun openInBrowser(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}
```

---

## Mantener el server vivo

El dev server corre como un proceso hijo del terminal, que a su vez está en un **Foreground Service**:

- La notificación persistente dice: "Code Mobile — Terminal activa"
- Android no mata el proceso mientras el foreground service esté activo
- Si el usuario force-kills la app, el server muere (y hay que re-ejecutar el comando)

---

## Limitaciones

| Limitación | Detalle | Mitigación |
|-----------|---------|------------|
| WebView no es Chrome completo | Algunas APIs pueden faltar | Android WebView se actualiza via Play Store, trackea Chromium stable |
| CORS | Algunos frameworks configuran CORS estricto | Rara vez un problema con localhost, pero se puede configurar el WebView |
| Multiple tabs | WebView no tiene tabs como un browser | Botón "abrir en Chrome" para uso avanzado |
| HTTPS | Dev servers suelen usar HTTP | El WebView permite mixed content y HTTP en localhost |
| Performance | WebView consume RAM adicional | Cerrar preview cuando no se usa |
