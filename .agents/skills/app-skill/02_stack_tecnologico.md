# Stack Tecnológico (Definido)

## Decisión: Enfoque Híbrido

**Kotlin + Jetpack Compose** para la capa UI nativa + **WebView + CodeMirror 6** para el editor de código/diffs + **Binarios nativos compilados** (Node.js, git, sh) para la terminal.

### ¿Por qué híbrido y no React Native?

| Factor | Kotlin/Compose + WebView | React Native |
|--------|--------------------------|-------------|
| Runtimes JS | Solo Node.js embebido | Hermes + Node.js (dos runtimes) |
| Tamaño APK | ~50-70MB | ~80-120MB |
| Performance UI | Nativa, sin bridge | Bridge overhead |
| F-Droid builds | Gradle estándar, simple | Builds complejos |
| Editor de código | WebView + CodeMirror (mismo resultado) | WebView + CodeMirror (mismo resultado) |
| Android APIs | Acceso directo | Via modules/bridge |
| Comunidad Android | Más especializada | Más grande pero generalista |

---

## Plataforma

- **Android API 26+** (Android 8.0 Oreo) — cubre 95%+ de dispositivos activos
- **Target SDK 34** (Android 14)
- **ABIs**: `arm64-v8a` (principal), opcionalmente `armeabi-v7a`
- **No iOS** — complejidad y restricciones de Apple hacen inviable esta app

## Lenguajes

| Lenguaje | Uso |
|----------|-----|
| **Kotlin** | UI (Jetpack Compose), lógica de app, servicios Android |
| **JavaScript** | CodeMirror 6 bundle (WebView), scripts del editor |
| **C/C++** | Binarios nativos compilados (Node.js, git, etc.) |
| **Shell/Bash** | Scripts de bootstrap, environment setup |

## Módulos Gradle

```
:app          — UI principal (Compose), navegación, DI
:core         — Modelos de datos, repositorios, utils compartidos
:ai           — Abstracción de providers IA, streaming, tool use
:terminal     — Integración con binarios nativos, PTY, foreground service
:editor       — WebView + CodeMirror bridge, diffs
:preview      — WebView para preview de dev servers
```

## Librerías principales

### UI y Android
| Librería | Propósito |
|----------|-----------|
| Jetpack Compose | UI declarativa |
| Material 3 | Design system + dynamic colors |
| Compose Navigation | Navegación entre pantallas |
| Hilt / Koin | Dependency injection |
| Coil | Carga de imágenes |

### Datos y persistencia
| Librería | Propósito |
|----------|-----------|
| Room | Base de datos SQLite para sesiones, mensajes, providers |
| DataStore | Preferences y settings |
| EncryptedSharedPreferences | Almacenamiento seguro de API keys (Android Keystore) |

### Networking
| Librería | Propósito |
|----------|-----------|
| OkHttp | HTTP client |
| Retrofit | REST API calls a providers IA |
| OkHttp SSE | Server-Sent Events para streaming de respuestas IA |

### Terminal
| Librería | Propósito |
|----------|-----------|
| `com.termux:terminal-emulator` | PTY allocation, ANSI parsing, terminal emulation |
| `com.termux:terminal-view` | Renderizado de terminal en Android View |

### Editor (WebView)
| Librería | Propósito |
|----------|-----------|
| CodeMirror 6 | Editor de código en WebView |
| `@codemirror/merge` | Visor de diffs side-by-side / inline |
| `@codemirror/lang-*` | Syntax highlighting por lenguaje |

### Binarios nativos (compilados para ARM64)
| Binario | Versión target | Propósito |
|---------|---------------|-----------|
| `node` | Node.js 20 LTS | Runtime JavaScript, npm, npx |
| `git` | Latest stable | Operaciones git completas |
| `sh` (dash/bash) | Minimal | Shell para ejecutar scripts |
| `openssl` | Matching git | HTTPS para git + npm registry |

## Build & CI

- **Gradle KTS** (.gradle.kts) para build scripts
- **GitHub Actions** — build APK por push a `main`, release por tag
- **Signing** — keystore para firmar APKs en CI
- Proguard/R8 para minificación en release

## Seguridad

- API keys cifradas via **Android Keystore + EncryptedSharedPreferences**
- OAuth tokens almacenados con el mismo mecanismo
- No se envían credenciales a ningún servidor propio
- Permisos Android: `INTERNET`, `FOREGROUND_SERVICE`, filesystem del app sandbox
- `MANAGE_EXTERNAL_STORAGE` opcional para acceder a proyectos fuera del sandbox

## Licencia

**GPLv3** — compatible con:
- Librerías de Termux (GPLv3)
- F-Droid (requiere open source)
- Protege contribuciones de la comunidad
