# UI / UX Design

## Filosofía de diseño

Code Mobile **no es un IDE** — es una app de chat con superpoderes de desarrollo. El foco está en:

- Chat como pantalla principal (ocupar 80%+ del tiempo del usuario acá)
- Terminal accesible pero secundaria
- Preview web integrado
- Editor solo para ver diffs y hacer ajustes menores
- Navegación simple, optimizada para una mano

## Estructura de pantallas

```
┌─────────────────────────────────────┐
│          Code Mobile                │
│                                     │
│  ┌─ Drawer izquierdo ────────────┐  │
│  │  • Lista de proyectos         │  │
│  │  • Sesiones por proyecto      │  │
│  │  • + Nueva sesión             │  │
│  │  • ⚙ Settings                 │  │
│  └───────────────────────────────┘  │
│                                     │
│  ┌─ Pantalla principal ──────────┐  │
│  │  [Provider ▼] [Modelo ▼]     │  │
│  │  [Build | Plan]               │  │
│  │                               │  │
│  │  ┌─ Chat ──────────────────┐  │  │
│  │  │  🤖 Respuesta IA       │  │  │
│  │  │  👤 Mensaje usuario     │  │  │
│  │  │  🤖 Respuesta con code  │  │  │
│  │  │  🔧 Tool: readFile()   │  │  │
│  │  │  🤖 Cambios aplicados   │  │  │
│  │  └─────────────────────────┘  │  │
│  │                               │  │
│  │  ┌─ Input ────────────────┐   │  │
│  │  │ Escribí tu prompt...   │   │  │
│  │  │ [📎] [📁]        [➤]  │   │  │
│  │  └────────────────────────┘   │  │
│  │                               │  │
│  │  [Terminal] [Preview] [Files] │  │
│  └───────────────────────────────┘  │
│                                     │
│  ┌─ Bottom Sheet (expandible) ───┐  │
│  │  Terminal / Preview / Diffs   │  │
│  │  (según tab seleccionado)     │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘
```

## Pantallas detalladas

### 1. Lista de proyectos / sesiones (Drawer)
- **Drawer lateral** que se abre con swipe o hamburger menu
- Lista de proyectos (carpetas) con icono y nombre
- Dentro de cada proyecto: lista de sesiones (chats) con título y fecha
- Swipe left en sesión → borrar (con confirmación)
- Botón "+" para crear nueva sesión
- Botón "Clonar repo" para importar proyecto desde GitHub
- Link a Settings al final

### 2. Chat principal (Pantalla central)
- **Header fijo**: selector de provider (dropdown), selector de modelo (dropdown), toggle Build/Plan
- **Lista de mensajes**: scroll vertical, burbujas de chat
  - Mensajes del usuario: alineados a la derecha, color accent
  - Mensajes de la IA: alineados a la izquierda, con syntax highlighting para código
  - Tool calls: collapsibles, muestran qué herramienta se usó, input/output resumido
  - Diffs inline: botón "Ver cambios" que abre el editor de diffs
- **Input bar** fijo abajo:
  - TextArea multi-línea expandible
  - Botón adjuntar contexto (seleccionar archivos del proyecto)
  - Botón adjuntar imagen (para modelos que soportan visión)
  - Botón enviar
- **Indicador de streaming**: animación mientras la IA responde
- **Stop button**: para cancelar una respuesta en progreso

### 3. Terminal (Bottom Sheet / Tab)
- **Bottom sheet** que se expande desde abajo (medio → full screen)
- Renderizado de terminal ANSI completo (via Termux terminal-view)
- Input de comandos con teclado del sistema
- Scroll de historia de output
- Botón "limpiar" terminal
- Indicador de proceso corriendo (spinner)

### 4. Preview web (Bottom Sheet / Tab)
- **WebView** cargando `localhost:PORT`
- Barra de URL read-only mostrando la dirección
- Botones: refresh, abrir en Chrome externo, cerrar
- Auto-detecta cuando un dev server arranca en la terminal

### 5. File Explorer (Bottom Sheet / Tab)
- Árbol de archivos del proyecto actual
- Iconos por tipo de archivo (JS, TS, JSON, MD, etc.)
- Tap en archivo → abre en editor (CodeMirror WebView)
- Long press → opciones (renombrar, borrar, copiar path)

### 6. Editor / Diffs (Pantalla completa o bottom sheet expandido)
- **CodeMirror 6** en WebView
- Modo edición: syntax highlighting, line numbers, dark theme
- Modo diff: vista de cambios con líneas agregadas/removidas coloreadas
- Botones nativos Compose: "Aplicar cambio", "Rechazar", "Editar manualmente"
- Indicador del archivo actual (path relativo)

### 7. Settings
- **Providers de IA**: lista de providers configurados, agregar nuevo, editar API key, test de conexión
- **OAuth**: botón "Conectar con GitHub" para Copilot
- **Provider genérico**: URL del endpoint + API key (para Ollama, Groq, etc.)
- **Terminal**: configurar shell, variables de entorno
- **Apariencia**: tema (dark/light/system), tamaño de fuente
- **Storage**: ver espacio usado por proyectos, limpiar cache
- **Sobre**: versión, licencia, link al repo, check for updates

## Navegación

```
App Start
  └→ Última sesión abierta (o lista de proyectos si primera vez)

Drawer (swipe right o hamburger)
  ├→ Proyecto 1
  │   ├→ Sesión A (tap → abre chat)
  │   └→ Sesión B
  ├→ Proyecto 2
  └→ Settings

Chat principal
  ├→ Bottom tabs: Terminal | Preview | Files
  └→ Cada tab abre un bottom sheet expandible

Desde el chat:
  └→ "Ver cambios" en mensaje → abre Editor/Diffs
```

## Componentes UI reutilizables

| Componente | Descripción |
|-----------|-------------|
| `ChatBubble` | Burbuja de mensaje (user/assistant), con markdown rendering y code blocks |
| `ToolCallCard` | Card collapsible para tool calls de la IA |
| `ProviderSelector` | Dropdown con icono del provider + nombre del modelo |
| `ModeToggle` | Toggle Build / Plan con indicador visual |
| `TerminalView` | Wrapper Compose del terminal-view de Termux |
| `CodeEditorView` | WebView con CodeMirror, bridge para comunicación |
| `DiffView` | CodeMirror merge view para mostrar cambios |
| `FileTreeItem` | Ítem del file explorer con icono, nombre, indent |
| `SessionListItem` | Ítem de sesión con título, fecha, swipe actions |
| `ProjectCard` | Card de proyecto con nombre, path, cantidad de sesiones |

## Diseño visual

- **Theme**: Material 3 con dynamic colors (Material You)
- **Default**: Dark mode
- **Palette**: tonos oscuros (grays/blues) con accent vibrante para acciones
- **Tipografía**: monospace para código y terminal, sans-serif para chat y UI
- **Spacing**: generoso para touch targets (min 48dp)
- **Animaciones**: transiciones suaves en bottom sheet, fade-in para mensajes, skeleton loading

## Landscape mode

En landscape (o tablets):
- Panel izquierdo fijo: sesiones
- Panel central: chat
- Panel derecho: terminal/preview/diffs
- Similar al layout de OpenCode en desktop pero adaptado

## Accesibilidad

- Touch targets mínimo 48dp
- Contraste suficiente en dark y light mode
- Soporte de teclado externo (bluetooth) con shortcuts
- Content descriptions para screen readers
- Tamaño de fuente configurable
