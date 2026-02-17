# Análisis de Competencia y Mercado

## El nicho

**"Vibecoding nativo en móvil"** — chatear con IA para programar, con ejecución local, terminal, y preview. Todo en el celular. Todo offline-capable (excepto las APIs de IA).

**Estado actual: nicho vacío.** No existe ninguna app que combine todo esto.

---

## Apps existentes

### Terminales y editores

| App | Qué hace | Qué le falta |
|-----|----------|-------------|
| **Termux** | Terminal Linux completa en Android. Node, git, npm, Python, todo funciona. F-Droid. | Sin UI moderna, sin IA, puro CLI. Play Store version deprecated. |
| **Acode** | Editor de código con syntax highlighting, plugins, soporte git básico. | Sin runtime, sin terminal real, sin IA, sin preview. |
| **Spck Editor** | Editor HTML/CSS/JS con live preview y git. | Solo web front-end, sin Node.js backend, sin IA. |
| **Dcoder** | IDE móvil con 50+ lenguajes, ejecución en la nube. | Cloud-only, no local. Sin IA real. |

### IDEs en la nube (con app mobile)

| App | Qué hace | Qué le falta |
|-----|----------|-------------|
| **Replit Mobile** | IDE completo con AI (Ghostwriter), ejecución en la nube, multiplayer. | Todo remoto, no local. Free tier muy limitado. Caro para uso serio. |
| **GitHub Codespaces** | VS Code en la nube, accesible desde browser mobile. | Requiere suscripción GitHub, no optimizado para mobile, 100% cloud. |
| **Gitpod** | Dev environments en la nube. | Igual, 100% cloud, no mobile-native. |

### IA para coding (sin ejecución)

| App | Qué hace | Qué le falta |
|-----|----------|-------------|
| **ChatGPT app** | Chat con GPT, puede generar código. | No ejecuta, no tiene terminal, no tiene preview, no tiene git. |
| **Claude app** | Chat con Claude, artifacts para código. | Igual, solo texto/artifacts, no ejecuta nada. |
| **GitHub Copilot Chat** | Chat en VS Code/GitHub mobile. | Solo dentro de VS Code o GitHub, no standalone, no ejecuta. |

### IDE IA Desktop (sin mobile)

| App | Qué hace | Qué le falta |
|-----|----------|-------------|
| **Cursor** | VS Code fork con IA profunda (autocomplete, chat, agent mode). | Desktop only. No existe versión mobile. |
| **Windsurf** | Similar a Cursor, IA-first editor. | Desktop only. |
| **Bolt.new** | Genera apps completas desde prompt, preview en browser. | Web-only, no mobile-native, ejecución en la nube. |
| **Lovable** | Igual que bolt, genera apps con IA. | Web-only, no local. |
| **v0.dev** | Genera UI components con IA (Vercel). | Web-only, solo UI, no full-stack. |
| **OpenCode CLI** | CLI para vibecoding con múltiples providers. | Terminal-only, no tiene UI gráfica, desktop/SSH only. |
| **Aider** | CLI de pair programming con IA. | Terminal-only, desktop/SSH only. |

### Conexión remota

| App | Qué hace | Qué le falta |
|-----|----------|-------------|
| **Termius** | SSH client premium para conectarse a servers/PC. | Solo conexión remota, necesitás la PC prendida. |
| **Tailscale** | VPN para acceder a tus dispositivos. | Solo networking, no es IDE ni terminal por sí solo. |
| **JuiceSSH** | SSH client gratis para Android. | Solo SSH, nada más. |

---

## Mapa de mercado

```
                    Ejecución local
                         ▲
                         │
           Termux ●      │      ● Code Mobile (nosotros)
           Acode ●       │        ← NADIE ACÁ
                         │
  Solo editor ◄──────────┼──────────► Chat IA first
                         │
          Dcoder ●       │      ● ChatGPT/Claude apps
     Spck Editor ●       │      ● Copilot Chat
                         │
                         │
            Replit ●     │      ● Bolt.new / Lovable
        Codespaces ●     │      ● v0.dev
                         │
                    Ejecución en nube
```

**Code Mobile ocupa el cuadrante superior derecho: Chat IA first + Ejecución local.** Nadie más está ahí.

---

## Ventajas competitivas

1. **Único en su categoría**: no existe otra app que combine chat IA + ejecución local en mobile
2. **Open source**: sin lock-in, comunidad puede contribuir, transparencia total
3. **BYOK**: el usuario usa sus propias suscripciones de IA (no pagamos nosotros)
4. **Sin backend**: zero infrastructure cost, privacy-first
5. **Offline-capable**: terminal y git funcionan sin internet (solo IA necesita conexión)
6. **Multi-provider**: soporta todos los providers principales, no ata al usuario a uno

## Riesgos de mercado

1. **Cursor/Windsurf podrían sacar app mobile**: tienen recursos, pero no es su foco
2. **Replit podría mejorar su mobile**: ya está en el espacio, pero son cloud-only
3. **Apple/Google podrían hacer algo nativo**: improbable a corto plazo
4. **El coding en mobile podría no tener suficiente demanda**: necesita validación

## Tamaño del mercado potencial

- Desarrolladores móviles activos globalmente: ~30 millones
- Usuarios de GitHub mobile: ~10 millones+
- Usuarios de Termux: ~5 millones+ (F-Droid + Play Store historical)
- Usuarios de Replit mobile: ~2 millones+
- Subscripciones a Copilot: ~1.8 millones+
- **Target inicial**: early adopters que ya usan Termux o Replit mobile + desarrolladores que pagan Copilot/ChatGPT y quieren usarlo para coding en el celu
