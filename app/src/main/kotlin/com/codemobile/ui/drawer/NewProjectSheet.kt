package com.codemobile.ui.drawer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun NewProjectDialog(
    uiState: DrawerUiState,
    onDismiss: () -> Unit,
    onCreateLocal: (String, String) -> Unit,
    onConnectGitHub: () -> Unit,
    onConnectGitHubWithToken: (String) -> Unit,
    onDisconnectGitHub: () -> Unit,
    onLoadRepos: () -> Unit,
    onCloneRepo: (String, String?) -> Unit,
    onResetCloneState: () -> Unit
) {
    var projectName by remember { mutableStateOf("") }
    var projectPath by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var cloneUrl by remember { mutableStateOf("") }
    var cloneName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo proyecto") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Crear local",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = projectPath,
                    onValueChange = { projectPath = it },
                    label = { Text("Ruta") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { onCreateLocal(projectName.trim(), projectPath.trim()) },
                        enabled = projectName.isNotBlank() && projectPath.isNotBlank()
                    ) {
                        Text("Crear")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "GitHub",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )

                when (val auth = uiState.gitHubAuthState) {
                    is GitHubAuthState.Disconnected -> {
                        Text(
                            text = "Conect\u00e1 tu cuenta con PAT o Device Flow.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = token,
                            onValueChange = { token = it },
                            label = { Text("Personal Access Token") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = { onConnectGitHubWithToken(token) },
                                enabled = token.isNotBlank()
                            ) { Text("Conectar token") }
                            TextButton(onClick = onConnectGitHub) { Text("Device Flow") }
                        }
                    }

                    is GitHubAuthState.WaitingForCode -> {
                        Text("C\u00f3digo: ${auth.userCode}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = auth.verificationUri,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    is GitHubAuthState.Polling -> {
                        Text("Esperando autorizaci\u00f3n...", style = MaterialTheme.typography.bodyMedium)
                    }

                    is GitHubAuthState.Connected -> {
                        Text(
                            text = "Conectado como ${auth.user?.login ?: "usuario"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onLoadRepos) { Text("Cargar repos") }
                            TextButton(onClick = onDisconnectGitHub) { Text("Desconectar") }
                        }
                    }

                    is GitHubAuthState.AuthError -> {
                        Text(
                            text = auth.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = onConnectGitHub) { Text("Reintentar") }
                    }
                }

                if (uiState.gitHubRepos.isNotEmpty()) {
                    Text(
                        text = "Repos recientes",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                    uiState.gitHubRepos.take(6).forEach { repo ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = repo.fullName,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { onCloneRepo(repo.cloneUrl, repo.name) }) {
                                Text("Clonar")
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = cloneUrl,
                    onValueChange = { cloneUrl = it },
                    label = { Text("URL de repo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cloneName,
                    onValueChange = { cloneName = it },
                    label = { Text("Nombre (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = {
                        onCloneRepo(cloneUrl.trim(), cloneName.trim().ifBlank { null })
                    },
                    enabled = cloneUrl.isNotBlank()
                ) {
                    Text("Clonar desde URL")
                }

                when (val cloneState = uiState.cloneState) {
                    is CloneState.Idle -> Unit
                    is CloneState.Cloning -> {
                        Text(cloneState.message, style = MaterialTheme.typography.bodySmall)
                    }
                    is CloneState.Success -> {
                        Text(
                            text = "Clonado en ${cloneState.path}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(onClick = onResetCloneState) { Text("Limpiar estado") }
                    }
                    is CloneState.Error -> {
                        Text(
                            text = cloneState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = onResetCloneState) { Text("Cerrar error") }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}
