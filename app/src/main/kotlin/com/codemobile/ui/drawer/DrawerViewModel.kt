package com.codemobile.ui.drawer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemobile.core.data.repository.ProjectRepository
import com.codemobile.core.data.repository.SessionRepository
import com.codemobile.core.github.GitCloneManager
import com.codemobile.core.github.GitHubApiClient
import com.codemobile.core.github.GitHubAuth
import com.codemobile.core.github.GitHubRepo
import com.codemobile.core.github.GitHubUser
import com.codemobile.core.model.Project
import com.codemobile.core.model.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class DrawerUiState(
    val projects: List<Project> = emptyList(),
    val sessionsMap: Map<String, List<Session>> = emptyMap(),
    val expandedProjectIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    // GitHub
    val gitHubAuthState: GitHubAuthState = GitHubAuthState.Disconnected,
    val gitHubRepos: List<GitHubRepo> = emptyList(),
    val isLoadingRepos: Boolean = false,
    val cloneState: CloneState = CloneState.Idle
)

/** Represents the GitHub authentication state */
sealed class GitHubAuthState {
    data object Disconnected : GitHubAuthState()
    data class WaitingForCode(val userCode: String, val verificationUri: String) : GitHubAuthState()
    data object Polling : GitHubAuthState()
    data class Connected(val user: GitHubUser?) : GitHubAuthState()
    data class AuthError(val message: String) : GitHubAuthState()
}

/** Represents the state of a git clone operation */
sealed class CloneState {
    data object Idle : CloneState()
    data class Cloning(val message: String) : CloneState()
    data class Success(val path: String, val name: String) : CloneState()
    data class Error(val message: String) : CloneState()
}

@HiltViewModel
class DrawerViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val sessionRepository: SessionRepository,
    val gitHubAuth: GitHubAuth,
    private val gitHubApiClient: GitHubApiClient,
    private val gitCloneManager: GitCloneManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DrawerUiState())
    val uiState: StateFlow<DrawerUiState> = _uiState.asStateFlow()

    init {
        observeProjects()
        checkGitHubConnection()
    }

    // ── Project / Session management (unchanged) ─────────────

    private fun observeProjects() {
        viewModelScope.launch {
            projectRepository.getAll().collect { projects ->
                _uiState.update { it.copy(projects = projects, isLoading = false) }
                // Load sessions for each project
                projects.forEach { project ->
                    loadSessionsForProject(project.id)
                }
            }
        }
    }

    private fun loadSessionsForProject(projectId: String) {
        viewModelScope.launch {
            sessionRepository.getByProject(projectId).collect { sessions ->
                _uiState.update { state ->
                    state.copy(
                        sessionsMap = state.sessionsMap + (projectId to sessions)
                    )
                }
            }
        }
    }

    fun toggleProjectExpanded(projectId: String) {
        _uiState.update { state ->
            val expanded = state.expandedProjectIds.toMutableSet()
            if (expanded.contains(projectId)) {
                expanded.remove(projectId)
            } else {
                expanded.add(projectId)
            }
            state.copy(expandedProjectIds = expanded)
        }
    }

    fun createProjectAndSession(
        name: String,
        path: String,
        onSessionCreated: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val project = projectRepository.create(name, path)
            val session = sessionRepository.create(projectId = project.id)
            onSessionCreated(session.id)
        }
    }

    fun createDemoProjectAndSession(
        onSessionCreated: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val projectRoot = createDemoProjectFiles()
            val project = projectRepository.create(name = "Demo React", path = projectRoot.absolutePath)
            val session = sessionRepository.create(projectId = project.id)
            onSessionCreated(session.id)
        }
    }

    fun createSession(
        projectId: String,
        onSessionCreated: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val session = sessionRepository.create(projectId = projectId)
            onSessionCreated(session.id)
        }
    }

    fun deleteSession(session: Session) {
        viewModelScope.launch {
            sessionRepository.delete(session)
        }
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch {
            projectRepository.delete(project)
        }
    }

    // ── GitHub authentication ────────────────────────────────

    /** Check if we already have a stored GitHub token on startup */
    private fun checkGitHubConnection() {
        viewModelScope.launch {
            if (gitHubAuth.isAuthenticated()) {
                val user = withContext(Dispatchers.IO) { gitHubAuth.getUser() }
                _uiState.update {
                    it.copy(gitHubAuthState = GitHubAuthState.Connected(user))
                }
            }
        }
    }

    /** Start the GitHub Device Flow OAuth */
    fun connectGitHub() {
        viewModelScope.launch {
            gitHubAuth.startDeviceFlow().collect { event ->
                when (event) {
                    is GitHubAuth.AuthEvent.ShowCode -> {
                        _uiState.update {
                            it.copy(
                                gitHubAuthState = GitHubAuthState.WaitingForCode(
                                    userCode = event.userCode,
                                    verificationUri = event.verificationUri
                                )
                            )
                        }
                    }
                    is GitHubAuth.AuthEvent.Polling -> {
                        // Keep showing the code, just update polling state
                        val current = _uiState.value.gitHubAuthState
                        if (current !is GitHubAuthState.WaitingForCode) {
                            _uiState.update {
                                it.copy(gitHubAuthState = GitHubAuthState.Polling)
                            }
                        }
                    }
                    is GitHubAuth.AuthEvent.Success -> {
                        val user = withContext(Dispatchers.IO) { gitHubAuth.getUser() }
                        _uiState.update {
                            it.copy(gitHubAuthState = GitHubAuthState.Connected(user))
                        }
                    }
                    is GitHubAuth.AuthEvent.Error -> {
                        _uiState.update {
                            it.copy(gitHubAuthState = GitHubAuthState.AuthError(event.message))
                        }
                    }
                }
            }
        }
    }

    /** Connect using a Personal Access Token */
    fun connectGitHubWithToken(token: String) {
        viewModelScope.launch {
            gitHubAuth.saveToken(token)
            val user = withContext(Dispatchers.IO) { gitHubAuth.getUser() }
            if (user != null) {
                _uiState.update {
                    it.copy(gitHubAuthState = GitHubAuthState.Connected(user))
                }
            } else {
                gitHubAuth.clearToken()
                _uiState.update {
                    it.copy(gitHubAuthState = GitHubAuthState.AuthError("Token inválido o sin permisos"))
                }
            }
        }
    }

    /** Disconnect from GitHub (clear stored token) */
    fun disconnectGitHub() {
        gitHubAuth.clearToken()
        _uiState.update {
            it.copy(
                gitHubAuthState = GitHubAuthState.Disconnected,
                gitHubRepos = emptyList()
            )
        }
    }

    // ── GitHub repos ──────────────────────────────────────────

    /** Load the user's recent repositories from GitHub API */
    fun loadGitHubRepos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingRepos = true) }
            val repos = withContext(Dispatchers.IO) { gitHubApiClient.listUserRepos() }
            _uiState.update { it.copy(gitHubRepos = repos, isLoadingRepos = false) }
        }
    }

    // ── Git clone ─────────────────────────────────────────────

    /** Clone a repository from the given URL */
    fun cloneRepo(repoUrl: String, destinationPath: String? = null, customName: String? = null) {
        val normalizedUrl = gitCloneManager.normalizeGitUrl(repoUrl)
        val repoName = customName
            ?: gitCloneManager.extractRepoName(normalizedUrl)
            ?: "project"
        
        // Use user selected path or default
        val targetDir = if (destinationPath != null) {
            destinationPath
        } else {
            gitCloneManager.projectsDir
        }

        viewModelScope.launch {
            gitCloneManager.clone(
                repoUrl = normalizedUrl,
                destinationDir = targetDir,
                repoName = repoName
            ).collect { event ->
                when (event) {
                    is GitCloneManager.CloneEvent.Progress -> {
                        _uiState.update {
                            it.copy(cloneState = CloneState.Cloning(event.message))
                        }
                    }
                    is GitCloneManager.CloneEvent.Success -> {
                        // Create the project in the database
                        projectRepository.create(repoName, event.path)
                        _uiState.update {
                            it.copy(cloneState = CloneState.Success(event.path, repoName))
                        }
                    }
                    is GitCloneManager.CloneEvent.Error -> {
                        _uiState.update {
                            it.copy(cloneState = CloneState.Error(event.message))
                        }
                    }
                }
            }
        }
    }

    /** Reset clone state back to idle */
    fun resetCloneState() {
        _uiState.update { it.copy(cloneState = CloneState.Idle) }
    }

    private suspend fun createDemoProjectFiles(): File = withContext(Dispatchers.IO) {
        val projectsDir = File(context.filesDir, "projects").apply { mkdirs() }
        val folderName = "demo-react-${System.currentTimeMillis()}"
        val root = File(projectsDir, folderName).apply { mkdirs() }
        val srcDir = File(root, "src").apply { mkdirs() }

        File(root, "package.json").writeText(
            """
            {
              "name": "codemobile-demo-react",
              "version": "1.0.0",
              "private": true,
              "scripts": {
                "dev": "vite --host 0.0.0.0 --port 5173",
                "build": "vite build",
                "preview": "vite preview --host 0.0.0.0 --port 4173",
                "dev:fallback": "node server.js"
              },
              "dependencies": {
                "react": "^18.3.1",
                "react-dom": "^18.3.1"
              },
              "devDependencies": {
                "vite": "^5.4.10"
              }
            }
            """.trimIndent()
        )

        File(root, "vite.config.js").writeText(
            """
            import { defineConfig } from 'vite'

            export default defineConfig({
              server: {
                host: '0.0.0.0',
                port: 5173
              },
              preview: {
                host: '0.0.0.0',
                port: 4173
              }
            })
            """.trimIndent()
        )

        File(root, "server.js").writeText(
            """
            const http = require("http");
            const fs = require("fs");
            const path = require("path");

            const PORT = Number(process.env.PORT || 5173);
            const ROOT = __dirname;

            const MIME_TYPES = {
              ".html": "text/html; charset=utf-8",
              ".css": "text/css; charset=utf-8",
              ".js": "text/javascript; charset=utf-8",
              ".json": "application/json; charset=utf-8"
            };

            const server = http.createServer((req, res) => {
              const rawPath = req.url === "/" ? "/preview-static.html" : req.url;
              const safePath = path.normalize(rawPath).replace(/^(\.\.[/\\])+/, "");
              const filePath = path.join(ROOT, safePath);

              fs.readFile(filePath, (err, data) => {
                if (err) {
                  res.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
                  res.end("Not found");
                  return;
                }

                const ext = path.extname(filePath).toLowerCase();
                const contentType = MIME_TYPES[ext] || "application/octet-stream";
                res.writeHead(200, { "Content-Type": contentType });
                res.end(data);
              });
            });

            server.listen(PORT, "0.0.0.0", () => {
              console.log(`Local: http://localhost:${'$'}{PORT}`);
            });
            """.trimIndent()
        )

        File(root, "index.html").writeText(
            """
            <!doctype html>
            <html lang="es">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>CodeMobile React Demo</title>
            </head>
            <body>
              <div id="root"></div>
              <script type="module" src="/src/main.jsx"></script>
            </body>
            </html>
            """.trimIndent()
        )

        File(srcDir, "main.jsx").writeText(
            """
            import React from 'react'
            import { createRoot } from 'react-dom/client'
            import App from './App'
            import './styles.css'

            createRoot(document.getElementById('root')).render(
              <React.StrictMode>
                <App />
              </React.StrictMode>
            )
            """.trimIndent()
        )

        File(srcDir, "App.jsx").writeText(
            """
            export default function App() {
              return (
                <main className="page">
                  <section className="card">
                    <p className="tag">Demo React</p>
                    <h1>CodeMobile + React</h1>
                    <p>
                      Este proyecto demo usa Vite + React. Si hay runtime Node disponible,
                      podés correr <code>npm install</code> y <code>npm run dev</code>.
                    </p>
                  </section>
                </main>
              )
            }
            """.trimIndent()
        )

        File(srcDir, "styles.css").writeText(
            """
            :root {
              font-family: Inter, system-ui, -apple-system, Segoe UI, Roboto, sans-serif;
              color: #10243d;
              background: #eef4ff;
            }

            * {
              box-sizing: border-box;
            }

            body {
              margin: 0;
            }

            .page {
              min-height: 100vh;
              display: grid;
              place-items: center;
              padding: 24px;
            }

            .card {
              width: min(760px, 100%);
              background: white;
              border-radius: 16px;
              padding: 28px;
              box-shadow: 0 12px 34px rgba(27, 42, 74, 0.14);
            }

            .tag {
              display: inline-block;
              margin: 0 0 8px;
              padding: 4px 10px;
              border-radius: 999px;
              background: #dbe9ff;
              color: #0a3c8e;
              font-weight: 600;
              font-size: 12px;
            }
            """.trimIndent()
        )

        File(root, "preview-static.html").writeText(
            """
            <!doctype html>
            <html lang="es">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>CodeMobile React Demo (Static Preview)</title>
              <style>
                body {
                  margin: 0;
                  min-height: 100vh;
                  display: grid;
                  place-items: center;
                  background: #eef4ff;
                  font-family: Inter, system-ui, -apple-system, Segoe UI, Roboto, sans-serif;
                  color: #10243d;
                }
                .card {
                  width: min(760px, calc(100% - 32px));
                  background: white;
                  border-radius: 16px;
                  padding: 28px;
                  box-shadow: 0 12px 34px rgba(27, 42, 74, 0.14);
                }
                code {
                  background: #eef2ff;
                  border-radius: 8px;
                  padding: 2px 6px;
                }
              </style>
            </head>
            <body>
              <div class="card">
                <h1>Demo React creado</h1>
                <p>Este es un preview estático porque la build actual no trae runtime Node/NPM.</p>
                <p>El proyecto generado sí es React (Vite) en <code>src/</code>.</p>
              </div>
            </body>
            </html>
            """.trimIndent()
        )

        File(root, "README.md").writeText(
            """
            # Demo React

            Proyecto base generado por CodeMobile para probar flujo React con Vite.

            ## Scripts

            - `npm run dev`: servidor de desarrollo en `http://localhost:5173`
            - `npm run build`: build de producción
            - `npm run preview`: preview del build
            - `npm run dev:fallback`: servidor estático (`preview-static.html`)
            """.trimIndent()
        )

        root
    }
}
