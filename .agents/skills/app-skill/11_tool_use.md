# Tool Use / Function Calling de la IA

## ¿Qué es tool use?

Es la capacidad de los modelos de IA de "llamar funciones" que la app ejecuta. En vez de solo responder texto, la IA puede:

1. **Leer archivos** del proyecto
2. **Escribir/crear archivos**
3. **Ejecutar comandos** en la terminal
4. **Listar directorios**
5. **Buscar en archivos**

Esto es **crítico** para el modo BUILD: la IA no solo dice qué hacer, sino que lo hace.

---

## Herramientas disponibles

### Definición para los providers de IA

```kotlin
val CODE_MOBILE_TOOLS = listOf(
    AITool(
        name = "readFile",
        description = "Lee el contenido de un archivo del proyecto",
        parameters = mapOf(
            "path" to AIToolParam(type = "string", description = "Ruta relativa al archivo", required = true)
        )
    ),
    AITool(
        name = "writeFile",
        description = "Escribe contenido en un archivo. Crea el archivo si no existe. Crea directorios intermedios si es necesario.",
        parameters = mapOf(
            "path" to AIToolParam(type = "string", description = "Ruta relativa al archivo", required = true),
            "content" to AIToolParam(type = "string", description = "Contenido completo del archivo", required = true)
        )
    ),
    AITool(
        name = "runCommand",
        description = "Ejecuta un comando en la terminal del proyecto. Devuelve stdout y stderr.",
        parameters = mapOf(
            "command" to AIToolParam(type = "string", description = "Comando a ejecutar", required = true),
            "timeout" to AIToolParam(type = "integer", description = "Timeout en segundos (default: 30)", required = false)
        )
    ),
    AITool(
        name = "listDirectory",
        description = "Lista archivos y carpetas en un directorio. Excluye node_modules y .git por defecto.",
        parameters = mapOf(
            "path" to AIToolParam(type = "string", description = "Ruta relativa al directorio (default: raíz del proyecto)", required = false),
            "recursive" to AIToolParam(type = "boolean", description = "Si listar recursivamente (default: false)", required = false)
        )
    ),
    AITool(
        name = "searchFiles",
        description = "Busca un patrón de texto en los archivos del proyecto (como grep).",
        parameters = mapOf(
            "query" to AIToolParam(type = "string", description = "Texto o regex a buscar", required = true),
            "path" to AIToolParam(type = "string", description = "Directorio donde buscar (default: raíz)", required = false),
            "filePattern" to AIToolParam(type = "string", description = "Patrón de archivos (e.g. '*.ts')", required = false)
        )
    )
)
```

---

## Loop de tool use

```
┌──────────────────────────────────────────────────┐
│                                                   │
│  1. Usuario envía prompt                          │
│     ↓                                             │
│  2. Se envía al provider IA con tools definidos   │
│     ↓                                             │
│  3. IA responde con tool_calls (o texto final)    │
│     ↓                                             │
│  ┌──────────────────────────────────────┐         │
│  │ ¿Hay tool calls?                     │         │
│  │   SÍ → ejecutar cada tool            │         │
│  │      → guardar resultado             │         │
│  │      → enviar resultado al provider  │         │
│  │      → volver a paso 3               │←────┐   │
│  │   NO → mostrar respuesta final       │     │   │
│  └──────────────────────────────────────┘     │   │
│                                          loop ─┘   │
└──────────────────────────────────────────────────┘
```

### Implementación

```kotlin
class ToolExecutor(
    private val projectPath: String,
    private val terminalService: TerminalService
) {
    
    suspend fun execute(toolCall: ToolCall): ToolResult {
        return when (toolCall.name) {
            "readFile" -> readFile(toolCall.arguments)
            "writeFile" -> writeFile(toolCall.arguments)
            "runCommand" -> runCommand(toolCall.arguments)
            "listDirectory" -> listDirectory(toolCall.arguments)
            "searchFiles" -> searchFiles(toolCall.arguments)
            else -> ToolResult(success = false, output = "Herramienta desconocida: ${toolCall.name}")
        }
    }
    
    private suspend fun readFile(args: Map<String, Any>): ToolResult {
        val path = args["path"] as String
        val file = File(projectPath, path)
        return if (file.exists() && file.isFile) {
            ToolResult(success = true, output = file.readText())
        } else {
            ToolResult(success = false, output = "Archivo no encontrado: $path")
        }
    }
    
    private suspend fun writeFile(args: Map<String, Any>): ToolResult {
        val path = args["path"] as String
        val content = args["content"] as String
        val file = File(projectPath, path)
        
        // Guardar contenido original para diff
        val originalContent = if (file.exists()) file.readText() else null
        
        file.parentFile?.mkdirs()
        file.writeText(content)
        
        return ToolResult(
            success = true,
            output = if (originalContent != null) "Archivo modificado: $path" else "Archivo creado: $path",
            metadata = mapOf(
                "originalContent" to (originalContent ?: ""),
                "newContent" to content,
                "filePath" to path
            )
        )
    }
    
    private suspend fun runCommand(args: Map<String, Any>): ToolResult {
        val command = args["command"] as String
        val timeout = (args["timeout"] as? Number)?.toLong() ?: 30
        
        return withTimeoutOrNull(timeout * 1000) {
            val output = terminalService.executeAndCapture(command, cwd = projectPath)
            ToolResult(success = output.exitCode == 0, output = output.text)
        } ?: ToolResult(success = false, output = "Comando excedió el timeout de ${timeout}s")
    }
    
    private suspend fun listDirectory(args: Map<String, Any>): ToolResult {
        val path = args["path"] as? String ?: ""
        val recursive = args["recursive"] as? Boolean ?: false
        val dir = File(projectPath, path)
        
        if (!dir.exists() || !dir.isDirectory) {
            return ToolResult(success = false, output = "Directorio no encontrado: $path")
        }
        
        val files = if (recursive) {
            dir.walkTopDown()
                .filter { !it.path.contains("node_modules") && !it.path.contains(".git") }
                .map { it.relativeTo(File(projectPath)).path + if (it.isDirectory) "/" else "" }
                .toList()
        } else {
            dir.listFiles()
                ?.filter { !it.name.startsWith(".") || it.name == ".gitignore" }
                ?.map { it.name + if (it.isDirectory) "/" else "" }
                ?: emptyList()
        }
        
        return ToolResult(success = true, output = files.joinToString("\n"))
    }
    
    private suspend fun searchFiles(args: Map<String, Any>): ToolResult {
        val query = args["query"] as String
        val path = args["path"] as? String ?: ""
        val pattern = args["filePattern"] as? String
        
        // Usar grep via terminal para eficiencia
        val grepCmd = buildString {
            append("grep -rn ")
            if (pattern != null) append("--include='$pattern' ")
            append("'$query' ")
            append("'${File(projectPath, path).absolutePath}'")
            append(" | head -50")  // limitar resultados
        }
        
        val output = terminalService.executeAndCapture(grepCmd, cwd = projectPath)
        return ToolResult(success = true, output = output.text.ifBlank { "Sin resultados" })
    }
}

data class ToolResult(
    val success: Boolean,
    val output: String,
    val metadata: Map<String, Any>? = null  // info extra para la UI (ej: diff data)
)
```

---

## Cómo se ve en el chat

Cuando la IA hace tool calls, se muestran como cards collapsibles:

```
┌─────────────────────────────────────────────┐
│ 🤖 Voy a revisar la estructura del proyecto │
│                                              │
│ ┌─ 📂 listDirectory("") ─────────────┐     │
│ │  src/                                │     │
│ │  package.json                        │     │
│ │  tsconfig.json                       │     │
│ │  vite.config.ts                      │     │
│ └──────────────────────────────────────┘     │
│                                              │
│ ┌─ 📄 readFile("package.json") ───────┐     │
│ │  {                                   │     │
│ │    "name": "my-app",                 │     │
│ │    "dependencies": { ... }           │     │
│ │  }                                   │     │
│ └──────────────────────────────────────┘     │
│                                              │
│ ┌─ ✏️ writeFile("src/App.tsx") ────────┐     │
│ │  Archivo modificado                  │     │
│ │  [Ver cambios]                       │     │
│ └──────────────────────────────────────┘     │
│                                              │
│ 🤖 Listo, agregué el componente Button      │
│    en App.tsx. ¿Querés que lo estile?        │
└─────────────────────────────────────────────┘
```

Cada tool call card puede expandirse para ver el input/output completo, y las escrituras de archivos tienen un botón "Ver cambios" que abre el diff viewer.

---

## Modo BUILD vs PLAN

| | BUILD | PLAN |
|---|---|---|
| **Tools enviados al provider** | ✅ Sí, todos | ❌ No |
| **La IA ejecuta acciones** | ✅ Sí, lee/escribe/ejecuta | ❌ No, solo describe |
| **Cambios en archivos** | ✅ Reales, con diff para review | ❌ Ninguno |
| **System prompt** | "Sos un coding assistant. Usá las tools para implementar cambios." | "Sos un coding assistant. Explicá tu plan paso a paso sin hacer cambios." |
| **Uso típico** | "Haceme un dark mode" | "¿Cómo agregaría un dark mode?" |

El toggle Build/Plan está en el header del chat y se puede cambiar en cualquier momento de la conversación.
