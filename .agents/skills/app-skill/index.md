# Code Mobile — Documentación del Proyecto

**App Android open-source para vibecoding desde el celular con IA.**

Kotlin/Compose + WebView + Node.js embebido. Chat-first, multi-provider, terminal integrada, editor de diffs, preview web. Sin backend propio — todo corre en el dispositivo + APIs directas a los providers de IA.

**Nombre**: Code Mobile
**Licencia**: GPLv3
**Distribución**: GitHub Releases (APK) + F-Droid
**Plataforma**: Android 8+ (API 26+)

---

## Archivos de documentación

| # | Archivo | Contenido |
|---|---------|-----------|
| 01 | [01_idea.md](01_idea.md) | Visión, problema que resuelve, valor diferencial |
| 02 | [02_stack_tecnologico.md](02_stack_tecnologico.md) | Stack definido: Kotlin/Compose + WebView + Node nativo |
| 03 | [03_arquitectura.md](03_arquitectura.md) | Arquitectura de módulos, flujo de datos, diagrama |
| 04 | [04_ui_ux.md](04_ui_ux.md) | Pantallas, navegación, componentes, diseño mobile |
| 05 | [05_integraciones_ia.md](05_integraciones_ia.md) | Providers IA, API keys, OAuth, abstracción, streaming |
| 06 | [06_terminal_runtime.md](06_terminal_runtime.md) | Terminal embebida, binarios nativos, Node/npm/git, Termux libs |
| 07 | [07_chats_sesiones.md](07_chats_sesiones.md) | Sistema de sesiones, proyectos, persistencia, Room DB |
| 08 | [08_preview_web.md](08_preview_web.md) | Preview de dev servers via WebView, detección de puertos |
| 09 | [09_editor_diffs.md](09_editor_diffs.md) | CodeMirror 6 via WebView, visor de diffs, bridge Kotlin↔JS |
| 10 | [10_git_integration.md](10_git_integration.md) | Git nativo compilado, UI helpers, autenticación |
| 11 | [11_tool_use.md](11_tool_use.md) | Function calling / tool use de la IA (read, write, exec) |
| 12 | [12_plan_mvp.md](12_plan_mvp.md) | Roadmap por fases con tareas concretas |
| 13 | [13_riesgos.md](13_riesgos.md) | Riesgos técnicos, mitigaciones, decisiones tomadas |
| 14 | [14_competencia.md](14_competencia.md) | Análisis de mercado y apps existentes |
| 15 | [15_contribuir.md](15_contribuir.md) | Guía de contribución al proyecto |

---

## Decisiones clave tomadas

- **Enfoque híbrido**: Kotlin/Compose para UI nativa + WebView con CodeMirror para editor/diffs
- **Binarios embebidos propios**: Node.js, git, sh compilados para ARM64 usando Termux build scripts
- **BYOK + OAuth PKCE**: API keys directas del usuario + OAuth para GitHub Copilot, sin backend propio
- **Git nativo compilado**: Binario real de git (no isomorphic-git), full-featured con SSH y HTTPS
- **Editor con visor de diffs**: CodeMirror 6 en WebView + botones aplicar/rechazar cambios
- **MVP "todo junto mínimo"**: Chat + terminal + preview básicos en la primera release funcional
- **Nombre: Code Mobile**
