package com.codemobile.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codemobile.ai.registry.ProviderRegistry
import com.codemobile.core.model.Message
import com.codemobile.core.model.MessageRole
import com.codemobile.core.model.ProviderConfig
import com.codemobile.core.model.SessionMode
import com.codemobile.preview.PreviewScreen
import com.codemobile.ui.theme.CodeMobileThemeTokens
import kotlinx.coroutines.launch

import androidx.compose.material.icons.filled.PlayArrow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenDrawer: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToConnectProvider: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val insightsDrawerState = rememberDrawerState(DrawerValue.Closed)

    var showModelSelectionDialog by remember { mutableStateOf(false) }
    var insightsTabIndex by rememberSaveable { mutableStateOf(0) }
    var effortLevel by rememberSaveable { mutableStateOf("Normal") }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.insightsError) {
        uiState.insightsError?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearInsightsError()
        }
    }

    if (showModelSelectionDialog) {
        ProviderModelSelectionDialog(
            connectedProviders = uiState.providers,
            currentProvider = uiState.selectedProvider,
            selectedModelId = uiState.selectedModelId,
            onSelectProviderModel = { provider, modelId ->
                viewModel.onSelectProviderModel(provider, modelId)
            },
            onAddProvider = {
                showModelSelectionDialog = false
                onNavigateToConnectProvider()
            },
            onDismiss = { showModelSelectionDialog = false }
        )
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = insightsDrawerState,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    SessionInsightsDrawer(
                        uiState = uiState,
                        selectedTab = insightsTabIndex,
                        onSelectTab = { insightsTabIndex = it },
                        onClose = { scope.launch { insightsDrawerState.close() } },
                        onRefresh = viewModel::refreshSessionInsights,
                        onOpenFile = viewModel::openReadOnlyFile
                    )
                }
            }
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Scaffold(
                    topBar = {
                        ChatTopBar(
                            projectName = uiState.project?.name ?: "Code Mobile",
                            onOpenDrawer = onOpenDrawer,
                            onOpenInsights = {
                                scope.launch {
                                    viewModel.refreshSessionInsights()
                                    insightsDrawerState.open()
                                }
                            },
                            onTogglePreview = { viewModel.togglePreview() },
                            isPreviewRunning = uiState.previewState.isRunning,
                            onSettingsClick = onNavigateToSettings
                        )
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    containerColor = MaterialTheme.colorScheme.background
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .imePadding()
                    ) {
                        if (uiState.noSession) {
                            EmptyStateContent(modifier = Modifier.weight(1f))
                        } else {
                            MessagesList(
                                messages = uiState.messages,
                                isStreaming = uiState.isStreaming,
                                streamingText = uiState.streamingText,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        ChatInputBar(
                            text = uiState.inputText,
                            isStreaming = uiState.isStreaming,
                            sessionMode = uiState.sessionMode,
                            selectedProvider = uiState.selectedProvider,
                            selectedModelId = uiState.selectedModelId,
                            effortLevel = effortLevel,
                            onTextChanged = viewModel::onInputChanged,
                            onSend = viewModel::onSendMessage,
                            onStop = viewModel::onStopStreaming,
                            onSelectMode = { mode ->
                                if (mode != uiState.sessionMode) {
                                    viewModel.onToggleMode()
                                }
                            },
                            onOpenModelPicker = { showModelSelectionDialog = true },
                            onAddProvider = onNavigateToConnectProvider,
                            onSelectEffort = { effortLevel = it },
                            enabled = !uiState.noSession
                        )
                    }
                }
            }
        }
    }

    // Preview overlay
    if (uiState.showPreview && uiState.previewState.url != null) {
        PreviewScreen(
            url = uiState.previewState.url!!,
            mode = uiState.previewState.mode,
            onClose = { viewModel.togglePreview() },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    projectName: String,
    onOpenDrawer: () -> Unit,
    onOpenInsights: () -> Unit,
    onTogglePreview: () -> Unit,
    isPreviewRunning: Boolean,
    onSettingsClick: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = projectName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Default.Menu, contentDescription = "Menu")
            }
        },
        actions = {
            // Preview toggle button
            IconButton(onClick = onTogglePreview) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Preview",
                    tint = if (isPreviewRunning)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onOpenInsights) {
                Icon(Icons.Default.Info, contentDescription = "Panel de sesion")
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun MessagesList(
    messages: List<Message>,
    isStreaming: Boolean,
    streamingText: String,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            MessageBubble(message = message)
        }

        if (isStreaming) {
            item {
                StreamingIndicatorBubble(text = streamingText)
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    modifier: Modifier = Modifier
) {
    val extendedColors = CodeMobileThemeTokens.extendedColors
    val isUser = message.role == MessageRole.USER
    val isTool = message.role == MessageRole.TOOL

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Text(
            text = when (message.role) {
                MessageRole.USER -> "You"
                MessageRole.ASSISTANT -> "AI"
                MessageRole.SYSTEM -> "System"
                MessageRole.TOOL -> "Tool"
            },
            style = MaterialTheme.typography.labelSmall,
            color = when {
                isTool -> extendedColors.toolCallAccent
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        Surface(
            shape = RoundedCornerShape(
                topStart = if (isUser) 16.dp else 4.dp,
                topEnd = if (isUser) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            color = when {
                isUser -> extendedColors.userBubble
                isTool -> extendedColors.toolCallContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Text(
                text = message.content,
                style = if (isTool || message.content.contains("```")) {
                    MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = if (isUser) extendedColors.onUserBubble else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
private fun StreamingIndicatorBubble(
    text: String,
    modifier: Modifier = Modifier
) {
    val extendedColors = CodeMobileThemeTokens.extendedColors
    val infiniteTransition = rememberInfiniteTransition(label = "streaming")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "AI",
            style = MaterialTheme.typography.labelSmall,
            color = extendedColors.streamingIndicator.copy(alpha = alpha),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        Surface(
            shape = RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            if (text.isNotEmpty()) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp)
                )
            } else {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(3) { index ->
                        val dotAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(600, delayMillis = index * 200),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "dot$index"
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(extendedColors.streamingIndicator.copy(alpha = dotAlpha))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    text: String,
    isStreaming: Boolean,
    sessionMode: SessionMode,
    selectedProvider: ProviderConfig?,
    selectedModelId: String?,
    effortLevel: String,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onSelectMode: (SessionMode) -> Unit,
    onOpenModelPicker: () -> Unit,
    onAddProvider: () -> Unit,
    onSelectEffort: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChanged,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            if (enabled) "Escribi tu prompt..."
                            else "Selecciona una sesion para empezar",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    enabled = enabled && !isStreaming,
                    maxLines = 5,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    )
                )

                if (isStreaming) {
                    FilledIconButton(
                        onClick = onStop,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = MaterialTheme.colorScheme.onError
                        )
                    }
                } else {
                    FilledIconButton(
                        onClick = onSend,
                        enabled = text.isNotBlank() && enabled,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.ArrowUpward,
                            contentDescription = "Send",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onAddProvider,
                    enabled = enabled,
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Conectar proveedor")
                }

                ComposerModeSelector(
                    sessionMode = sessionMode,
                    enabled = enabled,
                    onSelectMode = onSelectMode
                )

                ComposerControlChip(
                    label = selectedModelLabel(
                        selectedProvider = selectedProvider,
                        selectedModelId = selectedModelId
                    ),
                    enabled = enabled,
                    onClick = onOpenModelPicker
                )

                ComposerEffortSelector(
                    effortLevel = effortLevel,
                    enabled = enabled,
                    onSelectEffort = onSelectEffort
                )
            }
        }
    }
}

private fun selectedModelLabel(
    selectedProvider: ProviderConfig?,
    selectedModelId: String?
): String {
    if (selectedProvider == null || selectedModelId.isNullOrBlank()) {
        return "Elegir modelo"
    }
    val displayName = ProviderRegistry
        .getById(selectedProvider.registryId)
        ?.models
        ?.firstOrNull { it.id == selectedModelId }
        ?.name
    return displayName ?: selectedModelId
}

@Composable
private fun ComposerModeSelector(
    sessionMode: SessionMode,
    enabled: Boolean,
    onSelectMode: (SessionMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        ComposerControlChip(
            label = if (sessionMode == SessionMode.BUILD) "Build" else "Plan",
            enabled = enabled,
            onClick = { if (enabled) expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Build") },
                onClick = {
                    expanded = false
                    onSelectMode(SessionMode.BUILD)
                }
            )
            DropdownMenuItem(
                text = { Text("Plan") },
                onClick = {
                    expanded = false
                    onSelectMode(SessionMode.PLAN)
                }
            )
        }
    }
}

@Composable
private fun ComposerEffortSelector(
    effortLevel: String,
    enabled: Boolean,
    onSelectEffort: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        ComposerControlChip(
            label = effortLevel,
            enabled = enabled,
            onClick = { if (enabled) expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            listOf("Normal", "High").forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onSelectEffort(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun ComposerControlChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (enabled) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        modifier = Modifier.height(32.dp)
    ) {
        Row(
            modifier = Modifier
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun EmptyStateContent(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Code Mobile",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Abri un proyecto y crea una sesion para empezar",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

@Composable
private fun ProviderModelSelectionDialog(
    connectedProviders: List<ProviderConfig>,
    currentProvider: ProviderConfig?,
    selectedModelId: String?,
    onSelectProviderModel: (ProviderConfig, String) -> Unit,
    onAddProvider: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Elegir modelo",
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onAddProvider) {
                    Icon(Icons.Default.Add, contentDescription = "Conectar proveedor")
                }
            }
        },
        text = {
            if (connectedProviders.isEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "No hay proveedores conectados.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = onAddProvider) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Conectar proveedor")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    connectedProviders.forEachIndexed { providerIndex, provider ->
                        item(key = "provider-${provider.id}") {
                            Text(
                                text = provider.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = if (providerIndex == 0) 0.dp else 10.dp)
                            )
                        }

                        val models = ProviderRegistry.getById(provider.registryId)?.models.orEmpty()
                        if (models.isEmpty()) {
                            item(key = "provider-empty-${provider.id}") {
                                Text(
                                    text = "Sin modelos disponibles",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        } else {
                            items(models, key = { model -> "${provider.id}-${model.id}" }) { model ->
                                val isSelected = currentProvider?.id == provider.id && selectedModelId == model.id
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(model.name, style = MaterialTheme.typography.bodyLarge)
                                            Text(
                                                model.id,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        onSelectProviderModel(provider, model.id)
                                        onDismiss()
                                    },
                                    trailingIcon = {
                                        if (isSelected) {
                                            Icon(Icons.Default.Check, contentDescription = "Selected")
                                        }
                                    }
                                )
                            }
                        }

                        if (providerIndex < connectedProviders.lastIndex) {
                            item(key = "divider-${provider.id}") {
                                HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}
