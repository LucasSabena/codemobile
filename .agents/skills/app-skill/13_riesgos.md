# Riesgos Técnicos y Decisiones

## Riesgos altos

### 1. Paquetes npm con binarios nativos
**Problema**: Muchos paquetes npm populares para dev (esbuild, swc, node-sass, sharp) incluyen binarios precompilados por plataforma. No todos tienen builds para `linux-arm64` (que es lo que ve Node en Android).

**Impacto**: `npm install` falla para estos paquetes, rompiendo el workflow.

**Mitigación**:
- Usar alternativas WASM: `esbuild-wasm`, `@aspect-build/swc` (WASM)
- Mantener una lista curada de templates/frameworks probados que funcionan:
  - ✅ Vite + React/Vue/Svelte (usa esbuild-wasm)
  - ✅ Astro (usa esbuild-wasm)
  - ✅ Express / Koa / Fastify (pure JS)
  - ⚠️ Next.js (requiere swc — probar con `--experimental-turbo` o fallback)
  - ❌ Proyectos con dependencias nativas de C++ (bcrypt, canvas, etc.)
- Pre-compilar binarios clave (esbuild, swc) para android-arm64 y distribuirlos
- Documentar paquetes conocidos que no funcionan

### 2. Android mata procesos en background
**Problema**: Android agresivamente mata procesos en background para ahorrar batería, especialmente en OEMs como Xiaomi, Samsung, Huawei.

**Impacto**: El dev server se muere cuando el usuario cambia de app.

**Mitigación**:
- **Foreground Service** con notificación persistente (obligatorio)
- Guía al usuario para desactivar optimización de batería para Code Mobile
- Usar `WakeLock` parcial mientras hay un server corriendo
- Auto-restart del server si se detecta que murió

### 3. Storage y filesystem en Android
**Problema**: Android 11+ tiene Scoped Storage que limita acceso a archivos fuera del sandbox de la app.

**Impacto**: No se puede acceder a archivos en carpetas del usuario (ej: Downloads, Documents) fácilmente.

**Mitigación**:
- Usar **internal storage** del app (`context.filesDir`) como ($HOME) — sin restricciones
- Para acceso externo: pedir `MANAGE_EXTERNAL_STORAGE` (solo F-Droid/GitHub, Play Store lo rechazaría)
- Los proyectos se clonan/inician dentro del sandbox de la app

### 4. Tamaño del APK
**Problema**: Node.js (~25MB) + git (~10MB) + OpenSSL (~5MB) + app + CodeMirror = 60-80MB.

**Impacto**: Descarga pesada, almacenamiento en dispositivos low-end.

**Mitigación**:
- Split APKs por ABI (arm64 y armv7 separados)
- Comprimir binarios y extraer al primer inicio
- Alternativa futura: bootstrap post-install (APK liviano, descarga binarios después)

---

## Riesgos medios

### 5. Terminal/PTY en Android
**Problema**: La emulación de terminal completa (PTY, ANSI codes, señales) es compleja de implementar.

**Mitigación**: Usar la librería probada de Termux (`terminal-emulator` + `terminal-view`) que ya resuelve todo esto. Es GPLv3, compatible con nuestra licencia.

### 6. Costos de API de IA para usuarios
**Problema**: Las APIs de IA cuestan dinero. El vibecoding consume muchos tokens.

**Mitigación**:
- **BYOK**: el usuario ya sabe cuánto paga
- Soportar providers gratuitos/baratos: Gemini Flash (free tier generoso), Ollama (local, gratis)
- Mostrar token counter en tiempo real para que el usuario sepa cuánto está gastando
- Modo PLAN no usa tools (menos tokens)

### 7. UX de coding en pantalla pequeña
**Problema**: Escribir código en un celular de 6" con teclado touch es inherentemente incómodo.

**Mitigación**:
- **La app no es para escribir código** — es para chat con IA
- La IA escribe el código, el usuario solo dirige
- El teclado se usa para prompts en lenguaje natural, no para coding
- Landscape mode para quienes quieren más espacio
- Soporte de teclado bluetooth/externo

### 8. F-Droid builds
**Problema**: F-Droid requiere compilar todo desde fuente, incluyendo Node.js (30-60 min de build).

**Mitigación**:
- Empezar con GitHub Releases (sin restricciones)
- Agregar F-Droid después cuando el pipeline esté maduro
- Documentar el build process para que F-Droid pueda reproducirlo

---

## Riesgos bajos

### 9. WebView compatibility
Problema: Algunas features web modernas pueden no estar en el WebView del dispositivo.
Mitigación: Android WebView se actualiza via Play Store, trackea Chromium stable. La mayoría de features funcionan.

### 10. Networking (localhost)
Problema: Algunos carriers o redes bloquean puertos inusuales.
Mitigación: Usar `127.0.0.1` explícitamente, puertos altos (>8000). Todo es local, no pasa por la red.

---

## Decisiones tomadas y razonamiento

| Decisión | Alternativa descartada | Razón |
|----------|----------------------|-------|
| **Kotlin/Compose + WebView** | React Native | Evita dos runtimes JS, APK más chico, builds simples, mejor para F-Droid |
| **Binarios ARM64 propios** | Depender de Termux instalado | UX integrada, un solo APK, sin dependencias externas |
| **Binarios ARM64 propios** | nodejs-mobile | nodejs-mobile no tiene npm real, no child_process, muy limitado |
| **Git nativo compilado** | isomorphic-git (JS) | Full-featured (SSH, submodules, performance), ya compilamos junto con Node |
| **Git nativo compilado** | JGit (Java) | Más familiaridad con el git real, mismo binario que en desktop |
| **BYOK + OAuth PKCE** | Backend proxy | Zero cost de infraestructura, privacidad del usuario, sin servidor propio |
| **API 26+ (Android 8)** | API 21 (Android 5) | Mejor filesystem, foreground services, 95%+ coverage es suficiente |
| **Room DB** | MMKV / AsyncStorage | Standard Android, relaciones SQL, migrations, bien documentado |
| **GPLv3** | MIT/Apache | Compatible con Termux libs (GPLv3), protege open source, F-Droid friendly |
| **GitHub Releases primero** | F-Droid primero | Iteración rápida, sin gatekeepers, F-Droid viene después |
| **CodeMirror 6** | Monaco Editor | Más liviano, mejor mobile support, modular, funciona bien en WebView |
| **Solo Android** | Android + iOS | iOS no permite ejecutar binarios arbitrarios, terminal imposible |
| **Dark mode default** | Light mode default | La mayoría de devs prefieren dark, Material 3 lo hace fácil |
| **Nombre: Code Mobile** | Vibecode | Más descriptivo, más profesional, no trademark issues |
