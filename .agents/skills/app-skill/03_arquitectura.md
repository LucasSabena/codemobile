# Arquitectura de Code Mobile

## Diagrama de alto nivel

```
┌─────────────────────────────────────────────────────────┐
│                    Code Mobile (Android)                 │
│                                                         │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │   :app      │  │   :editor    │  │  :preview    │  │
│  │  Compose UI │  │  WebView +   │  │  WebView +   │  │
│  │  Navigation │  │  CodeMirror  │  │  localhost    │  │
│  │  Chat/Sess. │  │  Diffs       │  │  Dev Server  │  │
│  └──────┬──────┘  └──────┬───────┘  └──────┬───────┘  │
│         │                │                  │           │
│  ┌──────┴──────────────────────────────────┴────────┐  │
│  │                    :core                          │  │
│  │  Modelos · Room DB · Repositorios · Utils         │  │
│  └──────┬───────────────────────────┬───────────────┘  │
│         │                           │                   │
│  ┌──────┴──────┐           ┌───────┴────────┐         │
│  │    :ai      │           │   :terminal    │         │
│  │  Providers  │           │  PTY + Binarios│         │
│  │  Streaming  │           │  Node/git/sh   │         │
│  │  Tool Use   │           │  Foreground Svc│         │
│  └──────┬──────┘           └───────┬────────┘         │
│         │                          │                    │
│         ▼                          ▼                    │
│  ┌─────────────┐    ┌──────────────────────────┐      │
│  │ APIs remotas│    │ Binarios nativos ARM64   │      │
│  │ OpenAI      │    │ node, git, sh, openssl   │      │
│  │ Claude      │    │ (compilados con Termux    │      │
│  │ Gemini      │    │  build scripts)           │      │
│  │ Copilot     │    └──────────────────────────┘      │
│  │ OpenAI-compat│                                      │
│  └─────────────┘                                       │
│                                                         │
│  ┌─────────────────────────────────────────────────┐   │
│  │              Android Platform                    │   │
│  │  Keystore · Foreground Service · WebView         │   │
│  │  Room SQLite · FileSystem · PackageInstaller     │   │
│  └─────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

## Módulos y responsabilidades

### `:app` — UI principal
- Jetpack Compose screens (sesiones, chat, settings, file explorer)
- Navegación entre pantallas
- Dependency injection (Hilt/Koin)
- Entry point de la aplicación

### `:core` — Capa de datos
- Modelos de datos: `Project`, `Session`, `Message`, `ProviderConfig`
- Room Database con DAOs
- Repositorios que exponen datos como `Flow<T>`
- Utilidades compartidas (formateo, constantes, extensiones)

### `:ai` — Providers de IA
- Interface `AIProvider` para abstracción
- Implementaciones: OpenAI, Claude, Gemini, GitHub Copilot, OpenAI-compatible
- Streaming via SSE/HTTP chunked
- Tool use / function calling loop
- Manejo de context window y token counting

### `:terminal` — Terminal embebida
- Foreground Service con notificación persistente
- PTY allocation via Termux terminal-emulator lib
- Bridge a binarios nativos (node, git, sh)
- Environment setup ($HOME, $PATH, $PREFIX)
- Output parsing y eventos

### `:editor` — Editor y diffs
- WebView con CodeMirror 6 bundle
- Bridge Kotlin ↔ JavaScript via `@JavascriptInterface`
- Funciones: abrir archivo, mostrar diff, obtener contenido
- Botones nativos de aplicar/rechazar cambios

### `:preview` — Preview web
- WebView apuntando a `localhost:PORT`
- Detección automática de URL/puerto en output de terminal
- Toggle entre terminal y preview
- Botón para abrir en Chrome externo

## Flujo de uso completo

```
1. Usuario abre la app
   └→ Ve lista de proyectos/sesiones (:app)

2. Selecciona o crea un proyecto
   └→ Se crea/abre sesión en Room DB (:core)

3. Elige provider y modelo de IA
   └→ Se carga el AIProvider correspondiente (:ai)

4. Escribe un prompt en el chat
   └→ Se envía al provider con contexto de la sesión
   └→ Respuesta streaming se muestra en tiempo real

5. Si modo BUILD: la IA usa tools
   └→ readFile(), writeFile(), runCommand() (:ai → :terminal → :core)
   └→ Cambios se muestran como diffs (:editor)
   └→ Usuario acepta o rechaza cada cambio

6. Usuario abre terminal
   └→ PTY session activa (:terminal)
   └→ Ejecuta: npm install, npm run dev, etc.

7. Dev server detectado
   └→ URL parseada del output de terminal
   └→ Preview se abre en WebView (:preview)

8. Usuario hace git commit + push
   └→ UI helper o comando directo en terminal
   └→ Autenticación via token almacenado
```

## Comunicación entre módulos

| De → A | Mecanismo |
|--------|-----------|
| `:app` → `:ai` | Llamadas a repositorio, `Flow<AIResponse>` |
| `:ai` → `:terminal` | Tool use invoca comandos via `TerminalService` |
| `:terminal` → `:app` | Callbacks/Flow con output de terminal |
| `:app` → `:editor` | WebView `evaluateJavascript()` + `@JavascriptInterface` |
| `:terminal` → `:preview` | Evento de "servidor detectado" con URL |
| `:core` ↔ todos | Room DB como single source of truth |

## Almacenamiento

```
App Internal Storage ($HOME):
├── projects/
│   ├── mi-proyecto-1/        ← archivos del proyecto (git repo)
│   └── mi-proyecto-2/
├── .npm-global/               ← npm global installs
├── .config/                   ← configuraciones de herramientas
└── tmp/                       ← archivos temporales

Room Database:
├── projects                   ← metadata de proyectos
├── sessions                   ← sesiones de chat
├── messages                   ← mensajes individuales
└── provider_configs           ← configuración de providers IA

EncryptedSharedPreferences:
├── api_keys                   ← API keys cifradas
└── oauth_tokens               ← tokens OAuth cifrados
```
