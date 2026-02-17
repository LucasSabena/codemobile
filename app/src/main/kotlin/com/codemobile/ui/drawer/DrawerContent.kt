package com.codemobile.ui.drawer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppDrawerContent(
    onSessionClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onCloseDrawer: () -> Unit,
    viewModel: DrawerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showNewProjectDialog by remember { mutableStateOf(false) }

    ModalDrawerSheet(
        modifier = Modifier.width(300.dp)
    ) {
        // Header
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            Text(
                text = "⚡ Code Mobile",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Vibecoding desde el celu",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        // New project button
        NavigationDrawerItem(
            icon = {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            label = {
                Text(
                    "Nuevo proyecto",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            },
            selected = false,
            onClick = { showNewProjectDialog = true },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        // Projects list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(uiState.projects, key = { it.id }) { project ->
                val isExpanded = uiState.expandedProjectIds.contains(project.id)
                val sessions = uiState.sessionsMap[project.id] ?: emptyList()

                ProjectItem(
                    project = project,
                    sessions = sessions,
                    isExpanded = isExpanded,
                    onToggleExpand = { viewModel.toggleProjectExpanded(project.id) },
                    onSessionClick = { sessionId ->
                        onSessionClick(sessionId)
                        onCloseDrawer()
                    },
                    onNewSession = {
                        viewModel.createSession(project.id)
                    },
                    onDeleteSession = { session ->
                        viewModel.deleteSession(session)
                    }
                )
            }

            if (uiState.projects.isEmpty() && !uiState.isLoading) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Sin proyectos aún",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Creá uno para empezar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        HorizontalDivider()

        // Settings
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text("Settings") },
            selected = false,
            onClick = {
                onSettingsClick()
                onCloseDrawer()
            },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )

        Spacer(modifier = Modifier.height(8.dp))
    }

    // New project dialog
    if (showNewProjectDialog) {
        NewProjectDialog(
            uiState = uiState,
            onDismiss = { showNewProjectDialog = false },
            onCreateLocal = { name, path ->
                viewModel.createProjectAndSession(name, path) { sessionId ->
                    showNewProjectDialog = false
                    onSessionClick(sessionId)
                    onCloseDrawer()
                }
            },
            onCreateDemo = {
                viewModel.createDemoProjectAndSession { sessionId ->
                    showNewProjectDialog = false
                    onSessionClick(sessionId)
                    onCloseDrawer()
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
private fun ProjectItem(
    project: Project,
    sessions: List<Session>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSessionClick: (String) -> Unit,
    onNewSession: () -> Unit,
    onDeleteSession: (Session) -> Unit
) {
    Column {
        // Project header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = project.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (sessions.isNotEmpty()) {
                Text(
                    text = "${sessions.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Sessions list
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                sessions.forEach { session ->
                    SessionItem(
                        session = session,
                        onClick = { onSessionClick(session.id) },
                        onDelete = { onDeleteSession(session) }
                    )
                }

                // Add session button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNewSession() }
                        .padding(start = 56.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Nueva sesión",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionItem(
    session: Session,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(start = 56.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Chat,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = dateFormat.format(Date(session.createdAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// NewProjectDialog is defined in NewProjectSheet.kt
