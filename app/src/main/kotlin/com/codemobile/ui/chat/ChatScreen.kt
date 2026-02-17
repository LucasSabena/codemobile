package com.codemobile.ui.chat

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codemobile.terminal.TerminalService
import com.codemobile.terminal.TerminalSession
import com.codemobile.core.model.MessageRole
import com.codemobile.core.model.SessionMode
import com.codemobile.ui.theme.CodeMobileThemeTokens
import kotlinx.coroutines.launch

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
    var showTerminal by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            ChatTopBar(
                projectName = uiState.project?.name ?: "Code Mobile",
                sessionMode = uiState.sessionMode,
                providers = uiState.providers,
                selectedProvider = uiState.selectedProvider,
                onOpenDrawer = onOpenDrawer,
                onSettingsClick = onNavigateToSettings,
                onProviderSelected = viewModel::onSelectProvider,
                onToggleMode = viewModel::onToggleMode
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
                EmptyStateContent(
                    modifier = Modifier.weight(1f)
                )
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
                onTextChanged = viewModel::onInputChanged,
                onSend = viewModel::onSendMessage,
                onStop = viewModel::onStopStreaming,
                enabled = !uiState.noSession,
                terminalEnabled = !uiState.noSession && uiState.project?.path?.isNotBlank() == true,
                onTerminalClick = { showTerminal = true },
                onPreviewClick = {
                    scope.launch {
                        snackbarHostState.showSnackbar("Preview: próximamente")
                    }
                }
            )
        }
    }

    if (showTerminal) {
        ProjectTerminalDialog(
            projectName = uiState.project?.name ?: "Proyecto",
            projectPath = uiState.project?.path.orEmpty(),
            onDismiss = { showTerminal = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    projectName: String,
    sessionMode: SessionMode,
    providers: List<com.codemobile.core.model.ProviderConfig>,
    selectedProvider: com.codemobile.core.model.ProviderConfig?,
    onOpenDrawer: () -> Unit,
    onSettingsClick: () -> Unit,
    onProviderSelected: (com.codemobile.core.model.ProviderConfig) -> Unit,
    onToggleMode: () -> Unit
) {
    var providerMenuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Column {
                Text(
                    text = projectName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Provider selector
                    Box {
                        TextButton(
                            onClick = { providerMenuExpanded = true },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = selectedProvider?.displayName ?: "Select provider",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        DropdownMenu(
                            expanded = providerMenuExpanded,
                            onDismissRequest = { providerMenuExpanded = false }
                        ) {
                            providers.forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider.displayName) },
                                    onClick = {
                                        onProviderSelected(provider)
                                        providerMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Mode toggle
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.height(28.dp)
                    ) {
                        SegmentedButton(
                            selected = sessionMode == SessionMode.BUILD,
                            onClick = { if (sessionMode != SessionMode.BUILD) onToggleMode() },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            label = {
                                Text(
                                    "Build",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                        SegmentedButton(
                            selected = sessionMode == SessionMode.PLAN,
                            onClick = { if (sessionMode != SessionMode.PLAN) onToggleMode() },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            label = {
                                Text(
                                    "Plan",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }

                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Default.Menu, contentDescription = "Menu")
            }
        },
        actions = {
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectTerminalDialog(
    projectName: String,
    projectPath: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var service by remember { mutableStateOf<TerminalService?>(null) }
    var session by remember { mutableStateOf<TerminalSession?>(null) }
    var isConnecting by remember { mutableStateOf(true) }
    var terminalOutput by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    val outputScroll = rememberScrollState()
    val effectiveCwd = remember(projectPath) {
        if (projectPath.startsWith("content://")) null else projectPath
    }

    val connection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val terminalBinder = binder as? TerminalService.TerminalBinder ?: return
                val boundService = terminalBinder.getService()
                service = boundService
                isConnecting = false
                session = boundService.createSession(cwd = effectiveCwd)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
                session = null
            }
        }
    }

    DisposableEffect(projectPath) {
        TerminalService.start(context)
        val intent = Intent(context, TerminalService::class.java)
        val isBound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)

        onDispose {
            session?.let { activeSession ->
                service?.destroySession(activeSession.id)
            }
            if (isBound) {
                runCatching { context.unbindService(connection) }
            }
        }
    }

    LaunchedEffect(session?.id) {
        val active = session ?: return@LaunchedEffect
        if (effectiveCwd == null && projectPath.startsWith("content://")) {
            terminalOutput += "[Aviso] Este proyecto usa una ruta del selector de archivos (content://). " +
                "La terminal se abrió en el workspace interno.\n"
        }
        active.sendCommand("pwd")
        active.output.collect { chunk ->
            terminalOutput = (terminalOutput + chunk).takeLast(80_000)
        }
    }

    LaunchedEffect(terminalOutput.length) {
        outputScroll.animateScrollTo(outputScroll.maxValue)
    }

    val isRunning = session?.isRunning?.collectAsState(initial = false)?.value ?: false

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Terminal · $projectName",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar terminal")
                        }
                    }
                )
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    color = Color(0xFF111418),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isConnecting) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Conectando terminal...",
                                color = Color(0xFFE6EDF3),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        val renderedOutput = terminalOutput.ifBlank {
                            "Terminal lista en:\n${effectiveCwd ?: "workspace interno"}\n"
                        }
                        Text(
                            text = renderedOutput,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(outputScroll)
                                .padding(12.dp),
                            color = Color(0xFFE6EDF3),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        placeholder = { Text("Ej: ls -la") },
                        enabled = !isConnecting && isRunning
                    )
                    FilledIconButton(
                        onClick = {
                            val cmd = command.trim()
                            if (cmd.isNotEmpty()) {
                                session?.sendCommand(cmd)
                                command = ""
                            }
                        },
                        enabled = !isConnecting && isRunning && command.isNotBlank()
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Ejecutar comando")
                    }
                }
            }
        }
    }
}

@Composable
private fun MessagesList(
    messages: List<com.codemobile.core.model.Message>,
    isStreaming: Boolean,
    streamingText: String,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new messages
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
    message: com.codemobile.core.model.Message,
    modifier: Modifier = Modifier
) {
    val extendedColors = CodeMobileThemeTokens.extendedColors
    val isUser = message.role == MessageRole.USER
    val isTool = message.role == MessageRole.TOOL

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Role label
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
                // Typing dots animation
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
                                .background(
                                    extendedColors.streamingIndicator.copy(alpha = dotAlpha)
                                )
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
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    enabled: Boolean,
    terminalEnabled: Boolean,
    onTerminalClick: () -> Unit,
    onPreviewClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onTerminalClick,
                    enabled = terminalEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        Icons.Default.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Terminal")
                }
                OutlinedButton(
                    onClick = onPreviewClick,
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Preview")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChanged,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            if (enabled) "Escribí tu prompt..."
                            else "Seleccioná una sesión para empezar",
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
                text = "⚡",
                style = MaterialTheme.typography.displayLarge
            )
            Text(
                text = "Code Mobile",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Abrí un proyecto y creá una sesión\npara empezar a vibecodeear",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}
