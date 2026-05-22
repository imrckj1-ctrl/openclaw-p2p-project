package com.imr.chat.ui.chat

import android.Manifest
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import coil.compose.AsyncImage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.imr.chat.data.db.MessageEntity
import com.imr.chat.network.ConnectionState
import com.imr.chat.network.protocol.CommandInfo
import com.imr.chat.ui.components.MarkdownRenderer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Image picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.sendImage(context, it) }
    }

    // File picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.sendFile(context, it) }
    }

    // Voice input
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        matches?.firstOrNull()?.let { text ->
            viewModel.updateInput(text)
        }
    }

    // Permission for microphone
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "说话...")
            }
            voiceLauncher.launch(intent)
        } else {
            Toast.makeText(context, "需要麦克风权限", Toast.LENGTH_SHORT).show()
        }
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size, uiState.streamingContent) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1 + if (uiState.streamingMsgId != null) 1 else 0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("IMRChat")
                        Text(
                            text = when (uiState.connectionState) {
                                ConnectionState.CONNECTED -> "已连接"
                                ConnectionState.CONNECTING -> "连接中..."
                                ConnectionState.AUTHENTICATING -> "认证中..."
                                ConnectionState.DISCONNECTED -> "未连接"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when (uiState.connectionState) {
                                ConnectionState.CONNECTED -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                else -> MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "设置",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        },
        bottomBar = {
            ChatInputBar(
                value = uiState.currentInput,
                onValueChange = { viewModel.updateInput(it) },
                onSend = { viewModel.sendMessage() },
                onImagePick = { imagePickerLauncher.launch("image/*") },
                onFilePick = { filePickerLauncher.launch("*/*") },
                onVoiceInput = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                commands = uiState.commands
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.messages) { message ->
                MessageBubble(message = message)
            }

            // Streaming card
            if (uiState.streamingMsgId != null) {
                item {
                    StreamingCard(
                        content = uiState.streamingContent,
                        startedAt = uiState.streamingStartedAt ?: System.currentTimeMillis(),
                        thinkingContent = uiState.streamingThinkingContent,
                        thinkingExpanded = uiState.thinkingExpanded,
                        onToggleThinking = { viewModel.toggleThinking() }
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: MessageEntity) {
    val isUser = message.isFromUser
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val time = remember(message.createdAt) { timeFormat.format(Date(message.createdAt)) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Time
        Text(
            text = time,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp)
        )

        // Bubble content varies by type
        when (message.type) {
            "system" -> {
                // System message centered
                Text(
                    text = message.content ?: "",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            "image" -> {
                // Image message
                Card(
                    modifier = Modifier.widthIn(max = 280.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        if (message.mediaUrl != null) {
                            AsyncImage(
                                model = message.mediaUrl,
                                contentDescription = message.fileName ?: "图片",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (message.fileName != null) {
                            Text(
                                text = message.fileName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
            "file" -> {
                // File message
                Card(
                    modifier = Modifier.widthIn(max = 240.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = message.fileName ?: "文件",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (message.fileSize != null) {
                                Text(
                                    text = formatFileSize(message.fileSize),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
            "reply" -> {
                // AI reply card with Markdown rendering
                Column {
                    Card(
                        modifier = Modifier.widthIn(max = 320.dp),
                        shape = RoundedCornerShape(
                            topStart = 4.dp,
                            topEnd = 12.dp,
                            bottomStart = 4.dp,
                            bottomEnd = 12.dp
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            MarkdownRenderer(
                                content = message.content ?: "",
                                isDarkTheme = false
                            )
                            // Footer: timing + char count
                            if (message.elapsedMs != null || message.charCount != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val parts = mutableListOf<String>()
                                    if (message.elapsedMs != null) parts.add(formatElapsed(message.elapsedMs))
                                    if (message.charCount != null && message.charCount > 0) parts.add("${message.charCount} 字")
                                    Text(
                                        text = parts.joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                // Default text bubble
                Box(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 12.dp,
                                topEnd = 12.dp,
                                bottomStart = if (isUser) 12.dp else 4.dp,
                                bottomEnd = if (isUser) 4.dp else 12.dp
                            )
                        )
                        .background(
                            if (isUser) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(10.dp)
                ) {
                    Text(
                        text = message.content ?: "",
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes / 1024.0 / 1024.0)} MB"
    }
}

@Composable
fun StreamingCard(
    content: String,
    startedAt: Long,
    thinkingContent: String = "",
    thinkingExpanded: Boolean = false,
    onToggleThinking: () -> Unit = {}
) {
    var cursorVisible by remember { mutableStateOf(true) }
    var elapsed by remember { mutableLongStateOf(0L) }

    // Blinking cursor
    LaunchedEffect(Unit) {
        while (true) {
            delay(530)
            cursorVisible = !cursorVisible
        }
    }
    // Live elapsed counter (updates every 100ms)
    LaunchedEffect(Unit) {
        while (true) {
            elapsed = System.currentTimeMillis() - startedAt
            delay(100)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        // Elapsed time
        Text(
            text = formatElapsed(elapsed),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .padding(bottom = 2.dp),
            maxLines = 1
        )

        // Body card
        Card(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 12.dp,
                bottomStart = 4.dp,
                bottomEnd = 12.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                // Thinking section (collapsible)
                if (thinkingContent.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleThinking() }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (thinkingExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (thinkingExpanded) "收起" else "展开",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "思考过程",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }

                    AnimatedVisibility(visible = thinkingExpanded) {
                        Text(
                            text = thinkingContent,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(start = 20.dp, bottom = 6.dp)
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                    )
                }

                // Reply content
                if (content.isEmpty()) {
                    // Empty state: pulsing dots
                    Text(
                        text = "..." + if (cursorVisible) "▎" else "",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = content + if (cursorVisible) "▎" else "",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                // Live footer
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "${formatElapsed(elapsed)} · ${content.length} 字",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

private fun formatElapsed(ms: Long): String = when {
    ms < 1000 -> "${ms}ms"
    ms < 60000 -> "${"%.1f".format(ms / 1000.0)}s"
    else -> "${ms / 60000}m ${(ms % 60000) / 1000}s"
}

@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onImagePick: () -> Unit,
    onFilePick: () -> Unit,
    onVoiceInput: () -> Unit,
    commands: List<CommandInfo>
) {
    var showCommands by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    val filteredCommands = remember(value, commands) {
        if (value.startsWith("/")) {
            commands.filter { it.name.lowercase().startsWith(value.lowercase()) }
        } else {
            emptyList()
        }
    }

    Column {
        // Command suggestions
        AnimatedVisibility(visible = showCommands && filteredCommands.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column {
                    filteredCommands.forEach { cmd ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onValueChange(cmd.name + " ")
                                    showCommands = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = cmd.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(80.dp)
                            )
                            Text(
                                text = cmd.hint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Input bar
        Surface(
            tonalElevation = 2.dp,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Attachment button
                Box {
                    IconButton(onClick = { showAttachMenu = true }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "附件",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    DropdownMenu(
                        expanded = showAttachMenu,
                        onDismissRequest = { showAttachMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("图片") },
                            onClick = {
                                showAttachMenu = false
                                onImagePick()
                            },
                            leadingIcon = { Icon(Icons.Default.Image, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("文件") },
                            onClick = {
                                showAttachMenu = false
                                onFilePick()
                            },
                            leadingIcon = { Icon(Icons.Default.AttachFile, null) }
                        )
                    }
                }

                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        onValueChange(it)
                        showCommands = it.startsWith("/")
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp, max = 120.dp),
                    placeholder = { Text("输入消息或 / 命令") },
                    singleLine = false,
                    maxLines = 4,
                    shape = RoundedCornerShape(20.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Voice button
                IconButton(
                    onClick = onVoiceInput,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "语音")
                }

                // Send button
                IconButton(
                    onClick = {
                        if (value.isNotBlank()) {
                            onSend()
                            showCommands = false
                        }
                    },
                    enabled = value.isNotBlank(),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                }
            }
        }
    }
}
