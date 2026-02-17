# Terminal y Runtime Nativo

## Decisión: Binarios ARM64 propios

En vez de depender de Termux instalado o usar nodejs-mobile (limitado), compilamos **binarios nativos para ARM64** usando los build scripts del proyecto Termux.

### ¿Por qué?

| Opción | Pros | Contras |
|--------|------|---------|
| **Binarios propios** ✅ | UX integrada, un solo APK, sin deps externas | Compilación compleja, mantenimiento de binarios |
| nodejs-mobile | Probado (Manyverse) | No tiene npm real, no child_process, limitado |
| Depender de Termux | Todo ya compilado | UX fragmentada, el usuario debe instalar otra app |
| Servidor remoto (SSH) | Simple | No es nativo, necesita PC prendida |

---

## Binarios a compilar

Usando [termux-packages](https://github.com/termux/termux-packages) build system:

| Binario | Versión | Tamaño aprox | Propósito |
|---------|---------|-------------|-----------|
| `node` | 20 LTS | ~25MB | Runtime JS, npm, npx |
| `git` | latest | ~10MB | Control de versiones |
| `dash` o `bash` | minimal | ~1MB | Shell para scripts |
| `openssl` | matching | ~5MB | HTTPS para git y npm registry |
| `zlib` | latest | ~200KB | Compresión (dep de node y git) |
| `readline` | latest | ~300KB | Input interactivo en terminal |
| **Total** | | **~42MB** | |

### Cómo se empaquetan

Los binarios se colocan como archivos `.so` en `jniLibs/arm64-v8a/`:

```
app/src/main/jniLibs/arm64-v8a/
├── libnode.so          ← binario de node renombrado
├── libgit.so           ← binario de git renombrado
├── libdash.so          ← shell
├── libssl.so           ← openssl
├── libcrypto.so        ← openssl crypto
├── libz.so             ← zlib
└── libreadline.so      ← readline
```

Android trata los `.so` como native libraries y los extrae automáticamente a `nativeLibraryDir` al instalar el APK. Desde ahí los ejecutamos.

### Cross-compilación

```bash
# Clonar termux-packages
git clone https://github.com/termux/termux-packages.git
cd termux-packages

# Configurar para cross-compile Android ARM64
# El sistema usa Docker para un entorno reproducible
./build-package.sh -a aarch64 nodejs-lts
./build-package.sh -a aarch64 git
./build-package.sh -a aarch64 dash
./build-package.sh -a aarch64 openssl
```

Los binarios resultantes se renombran a `lib*.so` y se incluyen en el proyecto Android.

---

## Environment setup

Al primer inicio de la app, se ejecuta un bootstrap:

```kotlin
class TerminalBootstrap(private val context: Context) {
    
    fun setup() {
        val home = context.filesDir  // /data/data/com.codemobile/files
        val binDir = context.applicationInfo.nativeLibraryDir  // donde están los .so
        
        // Crear estructura de directorios
        File(home, "projects").mkdirs()
        File(home, ".npm-global").mkdirs()
        File(home, ".config").mkdirs()
        File(home, "tmp").mkdirs()
        
        // Configurar npm
        // npm viene como scripts JS dentro del paquete de node
        // Se extrae al primer inicio
        extractNpmScripts(home)
        
        // Variables de entorno
        val env = mapOf(
            "HOME" to home.absolutePath,
            "PATH" to "$binDir:${home.absolutePath}/.npm-global/bin",
            "PREFIX" to home.absolutePath,
            "TMPDIR" to File(home, "tmp").absolutePath,
            "NODE_PATH" to "${home.absolutePath}/.npm-global/lib/node_modules",
            "TERM" to "xterm-256color",
            "LANG" to "en_US.UTF-8",
            // npm config
            "npm_config_prefix" to "${home.absolutePath}/.npm-global",
            "npm_config_cache" to "${home.absolutePath}/.npm/_cache"
        )
    }
}
```

---

## Terminal Service

Un **Foreground Service** mantiene los procesos vivos cuando la app está en background:

```kotlin
class TerminalService : Service() {
    
    private val sessions = mutableMapOf<String, TerminalSession>()
    
    override fun onCreate() {
        super.onCreate()
        // Notificación persistente (requerida para foreground service)
        startForeground(NOTIFICATION_ID, createNotification())
    }
    
    fun createSession(
        command: String = "/bin/sh", // o la ruta al dash
        cwd: String? = null,
        env: Map<String, String> = emptyMap()
    ): TerminalSession {
        // Crear PTY usando la lib de Termux
        val session = TerminalSession(
            command = listOf(getNativeBinaryPath("dash")),
            cwd = cwd ?: getHomeDir(),
            env = buildEnvArray(env),
            client = terminalClient
        )
        sessions[session.id] = session
        return session
    }
    
    fun executeCommand(sessionId: String, command: String) {
        sessions[sessionId]?.write(command + "\n")
    }
}
```

---

## PTY y terminal emulation

Usamos la librería de **Termux terminal-emulator** para:

1. **PTY allocation** — crear pseudo-terminals reales
2. **ANSI parsing** — interpretar códigos de escape para colores, cursor, etc.
3. **Terminal buffer** — scroll back, selección de texto

```kotlin
// Dependencia en build.gradle.kts
dependencies {
    implementation("com.termux:terminal-emulator:0.118.0")
    implementation("com.termux:terminal-view:0.118.0")
}
```

La `TerminalView` de Termux es un View nativo Android que se integra en Compose via `AndroidView`:

```kotlin
@Composable
fun TerminalScreen(session: TerminalSession) {
    AndroidView(
        factory = { context ->
            TerminalView(context, null).apply {
                setTextSize(14)
                attachSession(session)
                // Configurar colores, fuente, etc.
            }
        }
    )
}
```

---

## Comandos soportados en la terminal

Con los binarios embebidos, el usuario puede ejecutar:

```bash
# Node.js
node script.js
node -e "console.log('hello')"
npm install
npm run dev
npm run build
npx create-vite@latest my-app
npx shadcn@latest init

# Git
git clone https://github.com/user/repo.git
git add .
git commit -m "mensaje"
git push origin main
git pull

# Shell
cd projects/mi-app
ls -la
cat package.json
echo "hello" > file.txt

# Servidores de desarrollo
npm run dev          # Vite, Next.js, etc.
npx serve ./dist     # Servir archivos estáticos
```

## Limitaciones conocidas

- **Paquetes npm con binarios nativos**: algunos no tienen precompilados para `android-arm64` (ej: `esbuild`, `swc`). Mitigación: usar alternativas WASM (`esbuild-wasm`) o paquetes curados
- **No hay `sudo` ni root**: no se pueden instalar paquetes del sistema, solo npm packages
- **Proceso puede ser matado**: Android mata procesos en background agresivamente. El Foreground Service con notificación persistente mitiga esto, pero no lo garantiza al 100%
- **Symlinks limitados**: el filesystem de Android no soporta bien symlinks en external storage. Usar internal storage resuelve esto
- **RAM**: Node.js + V8 consume ~50-150MB base. En dispositivos low-end puede ser un problema
