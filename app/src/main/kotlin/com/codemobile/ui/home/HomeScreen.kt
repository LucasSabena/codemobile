package com.codemobile.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codemobile.core.model.Project
import com.codemobile.core.model.Session
import com.codemobile.ui.drawer.DialogMode
import com.codemobile.ui.drawer.DrawerViewModel
import com.codemobile.ui.drawer.NewProjectDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onSessionClick: (String) -> Unit,
    viewModel: DrawerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var dialogMode by androidx.compose.runtime.remember { mutableStateOf<DialogMode?>(null) }

    Scaffold(
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { dialogMode = DialogMode.IMPORT_GITHUB },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Importar Proyecto (github)")
                }
                Button(
                    onClick = { dialogMode = DialogMode.NEW_PROJECT },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Nuevo Proyecto")
                }
            }
        }
    ) { innerPadding ->
        if (uiState.projects.isEmpty() && !uiState.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No hay proyectos todavía",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Creá o importá un proyecto para empezar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Últimas sesiones",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(uiState.projects, key = { it.id }) { project ->
                    ProjectSessionsCard(
                        project = project,
                        sessions = (uiState.sessionsMap[project.id] ?: emptyList()).take(6),
                        onSessionClick = onSessionClick,
                        onCreateSession = {
                            viewModel.createSession(project.id) { newSessionId ->
                                onSessionClick(newSessionId)
                            }
                        }
                    )
                }
            }
        }
    }

    val activeMode = dialogMode
    if (activeMode != null) {
        NewProjectDialog(
            mode = activeMode,
            uiState = uiState,
            onDismiss = { dialogMode = null },
            onCreateLocal = { name, path ->
                viewModel.createProjectAndSession(name, path) { sessionId ->
                    dialogMode = null
                    onSessionClick(sessionId)
                }
            },
            onCreateDemo = {
                viewModel.createDemoProjectAndSession { sessionId ->
                    dialogMode = null
                    onSessionClick(sessionId)
                }
            },
            onConnectGitHub = { viewModel.connectGitHub() },
            onConnectGitHubWithToken = { viewModel.connectGitHubWithToken(it) },
            onDisconnectGitHub = { viewModel.disconnectGitHub() },
            onLoadRepos = { viewModel.loadGitHubRepos() },
            onCloneRepo = { url, path, name -> viewModel.cloneRepo(url, path, name) },
            onResetCloneState = { viewModel.resetCloneState() }
        )
    }
}

@Composable
private fun ProjectSessionsCard(
    project: Project,
    sessions: List<Session>,
    onSessionClick: (String) -> Unit,
    onCreateSession: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "  ${project.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            HorizontalDivider()
            if (sessions.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Sin sesiones recientes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onCreateSession) {
                        Text("Crear primera sesión")
                    }
                }
            } else {
                sessions.forEach { session ->
                    SessionRow(session = session, onClick = { onSessionClick(session.id) })
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: Session,
    onClick: () -> Unit
) {
    val dateFormat = androidx.compose.runtime.remember {
        SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Chat,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)
        ) {
            Text(
                text = session.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = dateFormat.format(Date(session.updatedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
