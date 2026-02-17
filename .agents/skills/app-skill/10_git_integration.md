# Git Integration

## Decisión: Git nativo compilado

Usamos el **binario real de git** compilado para ARM64 (via Termux build scripts), no una librería JS. Esto da acceso a todas las funcionalidades de git sin limitaciones.

---

## Operaciones Git

### Desde la terminal (directo)

El usuario puede ejecutar cualquier comando git estándar:

```bash
git clone https://github.com/user/repo.git
git status
git add .
git commit -m "feat: new feature"
git push origin main
git pull
git branch feature-x
git checkout feature-x
git log --oneline -10
git diff
git stash
git stash pop
```

### UI Helpers (complementan la terminal)

Además de la terminal, la app ofrece UI simplificada para operaciones comunes:

#### 1. Clonar repositorio
```
[Drawer] → "Clonar repo"
  ├── Input: URL del repo (e.g., https://github.com/user/repo.git)
  ├── Input: Nombre de carpeta (auto-detectado de la URL)
  ├── Toggle: Rama específica (opcional)
  └── Botón: [Clonar]
       └→ Ejecuta: git clone {url} ~/projects/{nombre}
       └→ Progress bar basado en output de git
       └→ Al terminar: crea Project en Room DB y abre sesión
```

#### 2. Status rápido
```
[Chat header o File Explorer] → Indicador de git status
  ├── 🟢 3 archivos modificados
  ├── 🟡 1 archivo nuevo (untracked)
  └── 🔴 1 conflicto
```

Se obtiene parseando `git status --porcelain`:

```kotlin
data class GitStatus(
    val modified: List<String>,
    val added: List<String>,
    val deleted: List<String>,
    val untracked: List<String>,
    val conflicted: List<String>
)

fun parseGitStatus(output: String): GitStatus {
    val lines = output.lines().filter { it.isNotBlank() }
    return GitStatus(
        modified = lines.filter { it.startsWith(" M") || it.startsWith("M ") }.map { it.substring(3) },
        added = lines.filter { it.startsWith("A ") }.map { it.substring(3) },
        deleted = lines.filter { it.startsWith(" D") || it.startsWith("D ") }.map { it.substring(3) },
        untracked = lines.filter { it.startsWith("??") }.map { it.substring(3) },
        conflicted = lines.filter { it.startsWith("UU") || it.startsWith("AA") }.map { it.substring(3) }
    )
}
```

#### 3. Commit + Push rápido
```
[Bottom sheet o FAB] → "Commit & Push"
  ├── Checkbox: archivos a incluir (pre-seleccionados: todos los modificados)
  ├── Input: Mensaje de commit
  ├── Toggle: Push automático después del commit
  └── Botón: [Commit]
       └→ git add {archivos seleccionados}
       └→ git commit -m "{mensaje}"
       └→ git push origin {rama actual} (si toggle activado)
```

#### 4. Pull rápido
```
Botón en header → Pull
  └→ git pull origin {rama actual}
  └→ Si hay conflictos: mostrar alerta con archivos en conflicto
```

---

## Autenticación Git

### HTTPS con tokens

```kotlin
object GitCredentialHelper {
    
    fun configureCredentials(context: Context, homeDir: File) {
        // Crear .gitconfig con credential helper
        val gitconfig = File(homeDir, ".gitconfig")
        gitconfig.writeText("""
            [credential]
                helper = store
            [user]
                name = ${getUserName(context)}
                email = ${getUserEmail(context)}
        """.trimIndent())
        
        // Escribir credenciales en .git-credentials
        val credentials = File(homeDir, ".git-credentials")
        val token = getGitHubToken(context)
        if (token != null) {
            credentials.writeText("https://oauth2:$token@github.com\n")
            // Permisos restrictivos
            credentials.setReadable(true, true)
            credentials.setWritable(true, true)
        }
    }
    
    private fun getGitHubToken(context: Context): String? {
        // Reusar el token de OAuth de GitHub Copilot si existe
        // O pedir un Personal Access Token (PAT) al usuario
        return securePrefs.getString("github_token", null)
    }
}
```

### Fuentes del token

1. **OAuth de GitHub Copilot** — si el usuario ya conectó Copilot via OAuth, reusar ese token
2. **Personal Access Token (PAT)** — el usuario ingresa su PAT en Settings
3. **Git credential store** — se guarda encriptado, git lo lee automáticamente

### SSH (futuro)

- Generar par de keys SSH en el dispositivo
- Permitir al usuario copiar la public key para agregarla a GitHub
- Más complejo pero útil para users avanzados

---

## Integración con el chat IA

La IA puede usar git como parte de sus tool calls:

```
Ejemplo de conversación:
  User: "Haceme un commit con todos los cambios"
  AI: [tool: runCommand("git add -A")]
      [tool: runCommand("git status")]
      → "Hay 5 archivos modificados. ¿Qué mensaje de commit querés?"
  User: "feat: agregar dark mode"
  AI: [tool: runCommand("git commit -m 'feat: agregar dark mode'")]
      [tool: runCommand("git push origin main")]
      → "Listo, commit y push realizados"
```

---

## Configuración inicial

Al crear/clonar un proyecto, se configura:

```bash
# Dentro del directorio del proyecto
git config user.name "Nombre del usuario"
git config user.email "email@ejemplo.com"
git config credential.helper store
```

El nombre y email se configuran en Settings de la app (una sola vez).
