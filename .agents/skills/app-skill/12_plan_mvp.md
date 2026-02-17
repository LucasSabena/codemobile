# Roadmap MVP

## Filosofía: Todo junto mínimo

En vez de priorizar un componente sobre otro, cada fase entrega una versión funcional con **chat + terminal + preview** incrementalmente mejores.

---

## Fase 0 — Scaffolding (1-2 semanas)

- [ ] Crear proyecto Android: Kotlin, Compose, Gradle KTS
- [ ] Configurar módulos: `:app`, `:core`, `:ai`, `:terminal`, `:editor`, `:preview`
- [ ] Setup `minSdk 26`, `targetSdk 34`, `arm64-v8a`
- [ ] Agregar dependencias base (Compose, Material 3, Room, Hilt, OkHttp)
- [ ] Pantalla vacía con navegación básica (drawer + pantalla principal)
- [ ] GitHub repo público con README, LICENSE (GPLv3), CONTRIBUTING
- [ ] GitHub Actions: build APK por push a `main`
- [ ] Cross-compilar binarios Node.js, git, dash, openssl para ARM64
- [ ] Empaquetar como `.so` en `jniLibs/`
- [ ] Compilar bundle de CodeMirror 6 (HTML/JS/CSS) e incluir en `assets/`

**Entregable**: APK que se instala y muestra una pantalla vacía. Binarios y editor listos para integrar.

---

## Fase 1 — MVP Funcional (3-4 semanas)

### Chat básico
- [ ] Room Database con entidades: Project, Session, Message, ProviderConfig
- [ ] Pantalla de chat con burbujas (user/assistant)
- [ ] Input bar con enviar mensaje
- [ ] Streaming de respuestas (texto aparece en tiempo real)
- [ ] Un provider funcional: OpenAI (API key en settings)
- [ ] Pantalla de settings con input de API key y test de conexión

### Terminal básica
- [ ] TerminalService (Foreground Service)
- [ ] Bootstrap al primer inicio (extraer binarios, configurar environment)
- [ ] TerminalView integrado en bottom sheet
- [ ] Ejecutar `node -v`, `git --version`, `npm -v` como smoke test
- [ ] Ejecutar `npm init -y`, `npm install express` como test real

### Preview básico
- [ ] WebView en tab del bottom sheet
- [ ] Input manual de URL localhost
- [ ] Ejecutar `npx serve .` y abrir preview

**Entregable**: APK donde podés chatear con OpenAI, abrir una terminal, ejecutar npm, y ver un preview web. Funcionalidad mínima pero el loop completo funciona.

---

## Fase 2 — Multi-provider y sesiones (2-3 semanas)

### Más providers
- [ ] Implementar ClaudeProvider (Anthropic)
- [ ] Implementar GeminiProvider (Google)
- [ ] Implementar OpenAICompatibleProvider (genérico)
- [ ] Selector de provider y modelo en el header del chat
- [ ] Settings con lista de providers configurados

### Sesiones y proyectos
- [ ] Drawer con lista de proyectos
- [ ] Sesiones por proyecto (crear, borrar, switchear)
- [ ] Persistencia de historial de chat completo
- [ ] Auto-título de sesión basado en primer mensaje
- [ ] Indicador de tokens usados por sesión

### Git básico
- [ ] `git clone` desde UI (input de URL)
- [ ] Crear proyecto a partir de repo clonado
- [ ] Credenciales git via PAT en settings

**Entregable**: APK con múltiples providers de IA, gestión de sesiones/proyectos, y clonar repos.

---

## Fase 3 — Tool Use y Diffs (2-3 semanas)

### Tool use
- [ ] Definir herramientas: readFile, writeFile, runCommand, listDirectory, searchFiles
- [ ] Loop de tool use (IA llama tool → app ejecuta → resultado vuelve a la IA)
- [ ] Tool calls visibles en el chat como cards collapsibles
- [ ] Toggle Build / Plan en header

### Editor y diffs
- [ ] WebView con CodeMirror 6 funcional
- [ ] Abrir archivo desde file explorer → ver con syntax highlighting
- [ ] Mostrar diff cuando la IA modifica un archivo
- [ ] Botones Aplicar / Rechazar / Editar por archivo
- [ ] File explorer básico en tab del bottom sheet

**Entregable**: La IA puede leer y escribir archivos, ejecutar comandos, y el usuario ve los cambios como diffs. Vibecoding real.

---

## Fase 4 — GitHub Copilot y Polish (2-3 semanas)

### GitHub Copilot
- [ ] Registrar OAuth App en GitHub
- [ ] Implementar OAuth PKCE flow completo
- [ ] CopilotProvider funcional
- [ ] Deep link handling para OAuth callback

### Detección automática
- [ ] Detectar URL de dev server en output de terminal
- [ ] Notificación "Server detected" con botón de preview
- [ ] Hot reload funciona en WebView

### Git mejorado
- [ ] Git status UI (archivos modificados, untracked)
- [ ] Commit + Push rápido desde UI
- [ ] Pull rápido
- [ ] Reusar OAuth token de Copilot para git

### UX
- [ ] Dark theme como default (Material 3 dynamic colors)
- [ ] Landscape mode (split screen: chat + terminal)
- [ ] Atajos de teclado para teclado externo
- [ ] Animaciones de chat (fade-in, skeleton loading)
- [ ] Onboarding para primera vez (tutorial rápido)

**Entregable**: App pulida con Copilot, detección automática de servers, git UI, y buen look.

---

## Fase 5 — Release y Distribución (1-2 semanas)

- [ ] In-app update checker (GitHub API releases)
- [ ] Descarga e instalación de APK actualizado
- [ ] Documentación completa en `docs/`
- [ ] CONTRIBUTING.md detallado
- [ ] Tests unitarios para `:ai` y `:core`
- [ ] Tests de UI para flujos principales
- [ ] Test E2E en dispositivo real
- [ ] GitHub Release v0.1.0 con APK firmado y changelog
- [ ] Preparar metadata para F-Droid (cuando esté listo)

**Entregable**: v0.1.0 publicada en GitHub Releases, open source, lista para contribuciones.

---

## Timeline estimado

| Fase | Duración | Acumulado |
|------|----------|-----------|
| Fase 0 — Scaffolding | 1-2 semanas | 2 semanas |
| Fase 1 — MVP Funcional | 3-4 semanas | 6 semanas |
| Fase 2 — Multi-provider | 2-3 semanas | 9 semanas |
| Fase 3 — Tool Use | 2-3 semanas | 12 semanas |
| Fase 4 — Polish | 2-3 semanas | 15 semanas |
| Fase 5 — Release | 1-2 semanas | 17 semanas |

**~4 meses** para una v0.1.0 funcional y publicada.

Nota: los tiempos asumen un desarrollador trabajando part-time. Con más contribuyentes, se puede acelerar significativamente.
