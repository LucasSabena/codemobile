# Idea General: Code Mobile

## ¿Qué es?

Una **app Android open-source** para *vibecoding* desde el celular. No es un IDE tradicional para escribir código — es una herramienta **chat-first** donde la IA escribe el código por vos y vos dirigís, iterás, y previsualizás.

Inspirada en [OpenCode CLI](https://github.com/nicholascelestin/opencode), pero diseñada nativa para mobile con UI optimizada para pantalla táctil.

## Funcionalidades principales

- **Chat AI por sesión/proyecto** — cada proyecto tiene sus conversaciones independientes
- **Múltiples providers de IA** — OpenAI, Claude, Gemini, GitHub Copilot, y cualquier API OpenAI-compatible (Ollama, Groq, etc.)
- **Terminal integrada** — Node.js, npm, npx, git, sh corriendo nativamente en el dispositivo
- **Preview web** — ejecutar `npm run dev` y ver la preview en WebView sin salir de la app
- **Editor de diffs** — ver qué cambió la IA, aceptar o rechazar cambios archivo por archivo
- **Git integrado** — clone, commit, push, pull con UI helpers y autenticación por token/OAuth
- **Tool use / function calling** — la IA puede leer/escribir archivos y ejecutar comandos automáticamente (modo Build)
- **Modos Build y Plan** — Build ejecuta cambios reales, Plan solo planifica y explica

## ¿Qué problema resuelve?

Hoy no existe **ninguna app nativa móvil** que combine:

1. Chat con IA para programar
2. Ejecución local de código (Node.js runtime real)
3. Terminal con npm/git funcional
4. Preview de aplicaciones web
5. Git operations nativas

Las alternativas actuales son:

| Solución | Limitación |
|----------|-----------|
| Termux + API manual | Sin UI, sin integración, puro CLI |
| Replit Mobile | Todo en la nube, no local, tier free limitado |
| SSH a tu PC (Termius, Tailscale) | Dependés de tener la PC prendida, no es nativo |
| Apps web (bolt.new, v0.dev) | Browser-only, no optimizado, sin ejecución local |
| GitHub Mobile | Solo gestión de repos, no coding |

**Code Mobile llena ese hueco**: IA + ejecución local + terminal + preview, todo en una app nativa Android.

## Distribución

- **GitHub Releases** — APK firmado por cada release, descarga directa
- **F-Droid** — cuando el build pipeline esté estable (requiere builds reproducibles)
- **No Play Store** — evitar restricciones, costos y review process
- **Open source** — GPLv3, código en GitHub

## Público objetivo

- Desarrolladores que quieren iterar rápido desde el celular
- Makers/indie hackers que vibecodean proyectos personales
- Gente que quiere usar sus suscripciones de IA (Copilot, ChatGPT Plus) para coding en mobile
- Usuarios de Termux que quieren una mejor UX

