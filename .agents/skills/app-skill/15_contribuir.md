# Guía de Contribución

## Cómo contribuir a Code Mobile

Code Mobile es un proyecto open source (GPLv3). Toda contribución es bienvenida.

---

## Setup del entorno de desarrollo

### Requisitos

- **Android Studio** Hedgehog (2023.1) o superior
- **JDK 17** (viene con Android Studio)
- **Android SDK** con API 34 y Build Tools 34
- **NDK** (para cross-compilar binarios nativos)
- **Node.js 20 LTS** en tu máquina (para compilar el bundle de CodeMirror)
- **Dispositivo Android** físico ARM64 o emulador ARM64 (x86 emulador NO funciona con los binarios nativos)
- **Docker** (opcional, para cross-compilar binarios con Termux build scripts)

### Clonar y buildear

```bash
# Clonar el repo
git clone https://github.com/user/code-mobile.git
cd code-mobile

# Compilar bundle de CodeMirror
cd editor-bundle
npm install
npm run build
# Esto genera el bundle en app/src/main/assets/editor/

# Abrir en Android Studio
# File → Open → seleccionar carpeta code-mobile

# Build & Run en dispositivo/emulador ARM64
# Run → Run 'app'
```

### Estructura del proyecto

```
code-mobile/
├── app/                        # Módulo :app (UI principal)
│   └── src/main/
│       ├── kotlin/             # Screens, ViewModels, Navigation
│       ├── res/                # Recursos Android
│       └── assets/editor/      # Bundle de CodeMirror (generado)
├── core/                       # Módulo :core (datos y repos)
├── ai/                         # Módulo :ai (providers IA)
├── terminal/                   # Módulo :terminal (PTY + binarios)
├── editor/                     # Módulo :editor (WebView bridge)
├── preview/                    # Módulo :preview (WebView preview)
├── editor-bundle/              # Proyecto npm para compilar CodeMirror
├── binaries/                   # Scripts de cross-compilación
├── docs/                       # Documentación
├── .github/workflows/          # GitHub Actions CI/CD
├── build.gradle.kts            # Build root
├── settings.gradle.kts
├── README.md
├── LICENSE                     # GPLv3
└── CONTRIBUTING.md
```

---

## Workflow de contribución

### 1. Encontrar en qué trabajar
- Revisar **Issues** en GitHub (bugs, features, good first issue)
- Revisar el [roadmap](12_plan_mvp.md) para tareas pendientes
- Proponer una idea creando un Issue primero

### 2. Hacer un fork y branch
```bash
# Fork desde GitHub UI
git clone https://github.com/tu-usuario/code-mobile.git
cd code-mobile
git checkout -b feat/mi-feature
```

### 3. Desarrollar
- Seguir la estructura de módulos existente
- Escribir tests para lógica nueva (`:ai`, `:core` especialmente)
- Mantener el código en Kotlin idiomático
- Compose previews para componentes UI nuevos

### 4. Testing
```bash
# Tests unitarios
./gradlew test

# Tests de UI (requiere dispositivo/emulador conectado)
./gradlew connectedAndroidTest

# Lint
./gradlew lint
```

### 5. Pull Request
- Título descriptivo: `feat: add Claude provider` o `fix: terminal crash on API 26`
- Descripción de qué cambia y por qué
- Screenshots o video si es un cambio de UI
- Linkear el Issue relacionado

---

## Convenciones de código

### Kotlin
- Seguir [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- `ktlint` para formateo automático
- Compose: un archivo por screen/componente principal
- ViewModels con `StateFlow` para estado de UI
- `sealed class` para estados y eventos

### Commits
- Formato: `tipo: descripción`
- Tipos: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `ci`
- Ejemplos:
  - `feat: add Gemini provider with streaming support`
  - `fix: terminal foreground service not starting on API 31+`
  - `docs: update MVP roadmap with phase 3 tasks`

### Branching
- `main` — versión estable, siempre buildeable
- `develop` — integración de features en progreso
- `feat/nombre` — feature branches
- `fix/nombre` — bug fixes

---

## Áreas donde se necesitan contribuciones

| Área | Tipo de skill | Prioridad |
|------|--------------|-----------|
| **Cross-compilación de binarios** | NDK, C/C++, Termux build system | Alta |
| **Providers de IA** | HTTP, SSE, APIs de OpenAI/Claude/Gemini | Alta |
| **UI/UX** | Jetpack Compose, Material 3 | Alta |
| **Terminal integration** | Android Services, PTY, shell | Alta |
| **CodeMirror bundle** | JavaScript, CodeMirror 6 API | Media |
| **Testing** | JUnit, Compose Testing, Espresso | Media |
| **CI/CD** | GitHub Actions, Gradle | Media |
| **Documentación** | Markdown, guías de usuario | Baja |
| **Traducciones** | i18n | Baja |

---

## Licencia

Al contribuir, aceptás que tu código se distribuya bajo **GPLv3**. Esto asegura que:
- El proyecto se mantiene open source
- Forks deben ser open source también
- Compatible con las dependencias de Termux (GPLv3)
