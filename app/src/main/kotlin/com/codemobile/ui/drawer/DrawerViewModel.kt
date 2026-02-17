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

    fun createProject(name: String, path: String) {
        viewModelScope.launch {
            projectRepository.create(name, path)
        }
    }

    fun createSession(projectId: String) {
        viewModelScope.launch {
            sessionRepository.create(projectId = projectId)
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
    fun cloneRepo(repoUrl: String, customName: String? = null) {
        val normalizedUrl = gitCloneManager.normalizeGitUrl(repoUrl)
        val repoName = customName
            ?: gitCloneManager.extractRepoName(normalizedUrl)
            ?: "project"

        viewModelScope.launch {
            gitCloneManager.clone(
                repoUrl = normalizedUrl,
                destinationDir = gitCloneManager.projectsDir,
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
}
