package com.codemobile.ui.drawer

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon

enum class DialogMode {
    NEW_PROJECT,
    IMPORT_GITHUB
}

@Composable
fun NewProjectDialog(
    mode: DialogMode = DialogMode.NEW_PROJECT,
    uiState: DrawerUiState,
    onDismiss: () -> Unit,
    onCreateLocal: (String, String) -> Unit,
    onConnectGitHub: () -> Unit,
    onConnectGitHubWithToken: (String) -> Unit,
    onDisconnectGitHub: () -> Unit,
    onLoadRepos: () -> Unit,
    onCloneRepo: (String, String?, String?) -> Unit,
    onResetCloneState: () -> Unit
) {
    val context = LocalContext.current
    var projectName by remember { mutableStateOf("") }
    var projectPath by remember { mutableStateOf("") } // For Create
    var clonePath by remember { mutableStateOf("") }   // For Clone
    var cloneUrl by remember { mutableStateOf("") }
    var cloneName by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(if (mode == DialogMode.IMPORT_GITHUB) 0 else 1) }

    // Unified launcher handling
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Ignore
            }
            if (selectedTab == 1) { // Create Local
                projectPath = uri.toString()
            } else { // Clone (Tab 2 or Tab 0 invocation?)
                clonePath = uri.toString()
            }
        }
    }

    val tabs = listOf("GitHub", "Crear", "Clonar")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo Proyecto") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                when (selectedTab) {
                    0 -> { // GitHub
                        Text("Cuenta GitHub", style = MaterialTheme.typography.titleSmall)
                        when (val auth = uiState.gitHubAuthState) {
                            is GitHubAuthState.Disconnected -> {
                                var tempToken by remember { mutableStateOf("") }
                                OutlinedTextField(
                                    value = tempToken,
                                    onValueChange = { tempToken = it },
                                    label = { Text("Personal Access Token") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(
                                        onClick = { onConnectGitHubWithToken(tempToken) },
                                        enabled = tempToken.isNotBlank()
                                    ) { Text("Conectar Token") }
                                    TextButton(onClick = onConnectGitHub) { Text("Device Flow") }
                                }
                            }
                            is GitHubAuthState.Connected -> {
                                Text("Conectado como ${auth.user?.login}")
                                Row {
                                    TextButton(onClick = onLoadRepos) { Text("Cargar Repos") }
                                    TextButton(onClick = onDisconnectGitHub) { Text("Desconectar") }
                                }
                                if (uiState.isLoadingRepos) {
                                    Text(
                                        "Cargando repositorios...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                uiState.reposError?.let { error ->
                                    Text(
                                        error,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                if (uiState.gitHubRepos.isNotEmpty()) {
                                    Text("Repositorios", style = MaterialTheme.typography.labelLarge)
                                    uiState.gitHubRepos.take(15).forEach { repo ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                repo.fullName, 
                                                style = MaterialTheme.typography.bodySmall, 
                                                modifier = Modifier.weight(1f)
                                            )
                                            TextButton(onClick = { 
                                                cloneUrl = repo.cloneUrl
                                                cloneName = repo.name
                                                selectedTab = 2 // Go to Clone Tab
                                            }) {
                                                Text("Clonar")
                                            }
                                        }
                                    }
                                }
                            }
                            is GitHubAuthState.AuthError -> {
                                Text(
                                    auth.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                var tempToken by remember { mutableStateOf("") }
                                OutlinedTextField(
                                    value = tempToken,
                                    onValueChange = { tempToken = it },
                                    label = { Text("Personal Access Token") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                TextButton(
                                    onClick = { onConnectGitHubWithToken(tempToken) },
                                    enabled = tempToken.isNotBlank()
                                ) { Text("Reintentar") }
                            }
                            else -> { Text("Conectando...") }
                        }
                    }
                    1 -> { // Create
                        OutlinedTextField(
                            value = projectName,
                            onValueChange = { projectName = it },
                            label = { Text("Nombre del Proyecto") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = projectPath,
                            onValueChange = {},
                            label = { Text("Ubicación") },
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { folderPickerLauncher.launch(null) }) {
                                    Icon(Icons.Default.Add, contentDescription = "Folder")
                                }
                            }
                        )
                        TextButton(
                            onClick = { onCreateLocal(projectName, projectPath) },
                            enabled = projectName.isNotBlank() && projectPath.isNotBlank()
                        ) {
                            Text("Crear Proyecto")
                        }
                    }
                    2 -> { // Clone
                        OutlinedTextField(
                            value = cloneUrl,
                            onValueChange = { cloneUrl = it },
                            label = { Text("URL del Repositorio") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = clonePath,
                            onValueChange = {},
                            label = { Text("Guardar en") },
                            readOnly = true, // Readonly, user must pick
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { folderPickerLauncher.launch(null) }) {
                                    Icon(Icons.Default.Add, contentDescription = "Folder")
                                }
                            }
                        )
                         OutlinedTextField(
                            value = cloneName,
                            onValueChange = { cloneName = it },
                            label = { Text("Nombre carpeta (Opcional)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        TextButton(
                            onClick = { 
                                onCloneRepo(cloneUrl, clonePath.ifBlank { null }, cloneName.ifBlank { null }) 
                            },
                            enabled = cloneUrl.isNotBlank() && clonePath.isNotBlank()
                        ) {
                            Text("Clonar")
                        }
                        
                        // Status
                        when (val st = uiState.cloneState) {
                            is CloneState.Cloning -> Text(st.message)
                            is CloneState.Error -> Text("Error: ${st.message}", color = MaterialTheme.colorScheme.error)
                            is CloneState.Success -> Text("Éxito!", color = MaterialTheme.colorScheme.primary)
                            else -> {}
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

