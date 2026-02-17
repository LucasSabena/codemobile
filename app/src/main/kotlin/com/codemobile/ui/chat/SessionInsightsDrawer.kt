package com.codemobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionInsightsDrawer(
    uiState: ChatUiState,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onOpenFile: (String) -> Unit
) {
    val tabs = listOf("Contexto", "Cambios", "Archivos")

    ModalDrawerSheet(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(max = 460.dp)
            .fillMaxWidth(0.92f)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Panel de sesion",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar")
                }
            }

            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { onSelectTab(index) },
                        text = { Text(title) }
                    )
                }
            }

            if (uiState.isLoadingInsights) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }

            uiState.insightsError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            when (selectedTab) {
                0 -> ContextTabContent(uiState = uiState)
                1 -> ChangesTabContent(uiState = uiState, onOpenFile = onOpenFile)
                else -> FilesTabContent(uiState = uiState, onOpenFile = onOpenFile)
            }
        }
    }
}

@Composable
private fun ContextTabContent(uiState: ChatUiState) {
    val session = uiState.session
    val project = uiState.project
    val inputTokens = session?.totalInputTokens ?: 0
    val outputTokens = session?.totalOutputTokens ?: 0
    val totalTokens = inputTokens + outputTokens

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoLine("Proyecto", project?.name ?: "-")
                InfoLine("Path", project?.path ?: "-")
                InfoLine("Sesion", session?.title ?: session?.id ?: "-")
                InfoLine("Modo", uiState.sessionMode.name)
                InfoLine("Mensajes", uiState.messages.size.toString())
                InfoLine("Proveedor", uiState.selectedProvider?.displayName ?: "-")
                InfoLine("Modelo", uiState.selectedModelId ?: "-")
                InfoLine("Creada", session?.createdAt?.let(::formatDateTime) ?: "-")
                InfoLine("Input tokens", formatNumber(inputTokens))
                InfoLine("Output tokens", formatNumber(outputTokens))
                InfoLine("Total consumido", formatNumber(totalTokens))
                uiState.tokenUsage?.let {
                    InfoLine(
                        "Ultima respuesta",
                        "in ${formatNumber(it.input)} / out ${formatNumber(it.output)}"
                    )
                }
            }
        }
    }
}

@Composable
private fun ChangesTabContent(
    uiState: ChatUiState,
    onOpenFile: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (uiState.modifiedFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No hay archivos modificados en esta sesion aun.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(uiState.modifiedFiles, key = { it.absolutePath }) { fileChange ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenFile(fileChange.absolutePath) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = fileChange.relativePath,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${formatDateTime(fileChange.lastModified)} - ${formatBytes(fileChange.sizeBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider()
        FilePreviewPane(uiState = uiState)
    }
}

@Composable
private fun FilesTabContent(
    uiState: ChatUiState,
    onOpenFile: (String) -> Unit
) {
    var expandedDirectories by remember(uiState.projectFileTree) {
        mutableStateOf(emptySet<String>())
    }

    val treeItems = remember(uiState.projectFileTree, expandedDirectories) {
        val flattened = mutableListOf<TreeListItem>()
        flattenTree(
            nodes = uiState.projectFileTree,
            expandedDirectories = expandedDirectories,
            depth = 0,
            out = flattened
        )
        flattened
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (treeItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No se encontraron archivos del proyecto.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(treeItems, key = { it.node.absolutePath }) { item ->
                    val node = item.node
                    val isExpanded = expandedDirectories.contains(node.absolutePath)
                    val isSelected = uiState.selectedFilePath == node.absolutePath

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (node.isDirectory) {
                                    expandedDirectories = if (isExpanded) {
                                        expandedDirectories - node.absolutePath
                                    } else {
                                        expandedDirectories + node.absolutePath
                                    }
                                } else {
                                    onOpenFile(node.absolutePath)
                                }
                            }
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                else MaterialTheme.colorScheme.surface
                            )
                            .padding(vertical = 6.dp)
                            .padding(start = (12 + item.depth * 14).dp, end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (node.isDirectory) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.width(16.dp))
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Text(
                            text = node.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        HorizontalDivider()
        FilePreviewPane(uiState = uiState)
    }
}

@Composable
private fun FilePreviewPane(uiState: ChatUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = "Vista solo lectura",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(10.dp)
                )
                .padding(10.dp)
        ) {
            when {
                uiState.isLoadingFilePreview -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }

                uiState.selectedFilePreview == null -> {
                    Text(
                        text = "Selecciona un archivo para previsualizar su contenido.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                uiState.selectedFilePreview.isBinary -> {
                    Text(
                        text = "Archivo binario. No se puede mostrar preview de texto.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    val preview = uiState.selectedFilePreview
                    val scroll = rememberScrollState()
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = preview?.relativePath.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (preview?.isTruncated == true) {
                            Text(
                                text = "Contenido truncado (max 200 KB)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        SelectionContainer {
                            Text(
                                text = preview?.content.orEmpty(),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scroll),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

private data class TreeListItem(
    val node: ProjectFileNode,
    val depth: Int
)

private fun flattenTree(
    nodes: List<ProjectFileNode>,
    expandedDirectories: Set<String>,
    depth: Int,
    out: MutableList<TreeListItem>
) {
    nodes.forEach { node ->
        out += TreeListItem(node = node, depth = depth)
        if (node.isDirectory && expandedDirectories.contains(node.absolutePath)) {
            flattenTree(
                nodes = node.children,
                expandedDirectories = expandedDirectories,
                depth = depth + 1,
                out = out
            )
        }
    }
}

private fun formatDateTime(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun formatBytes(sizeBytes: Long): String {
    if (sizeBytes < 1024) return "$sizeBytes B"
    val kb = sizeBytes / 1024.0
    if (kb < 1024) return "${"%.1f".format(Locale.US, kb)} KB"
    val mb = kb / 1024.0
    return "${"%.1f".format(Locale.US, mb)} MB"
}

private fun formatNumber(value: Int): String {
    return String.format(Locale.US, "%,d", value)
}
