package com.imr.chat.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.imr.chat.data.ServerConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToChat: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    var showPassword by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableIntStateOf(-1) }

    // Form state
    var serverName by remember { mutableStateOf("") }
    var serverHost by remember { mutableStateOf("") }
    var serverPort by remember { mutableStateOf("18790") }
    var serverToken by remember { mutableStateOf("p2p2025") }
    var serverUseWss by remember { mutableStateOf(false) }

    // Load existing server if editing
    LaunchedEffect(editingIndex) {
        if (editingIndex >= 0 && editingIndex < settings.servers.size) {
            val s = settings.servers[editingIndex]
            serverName = s.name
            serverHost = s.host
            serverPort = s.port.toString()
            serverToken = s.token
            serverUseWss = s.useWss
        } else {
            serverName = ""
            serverHost = ""
            serverPort = "18790"
            serverToken = ""
            serverUseWss = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Server list
            Text(
                "服务器配置",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            settings.servers.forEachIndexed { index, server ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (index == settings.activeServerIndex)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    server.name.ifBlank { "服务器 ${index + 1}" },
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    "${server.host}:${server.port}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row {
                                IconButton(onClick = { editingIndex = index }) {
                                    Icon(Icons.Default.Edit, "编辑")
                                }
                                IconButton(onClick = { viewModel.removeServer(index) }) {
                                    Icon(Icons.Default.Delete, "删除")
                                }
                                if (index != settings.activeServerIndex) {
                                    IconButton(onClick = { viewModel.setActiveServer(index) }) {
                                        Icon(Icons.Default.Check, "使用")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Add/Edit form
            HorizontalDivider()
            Text(
                if (editingIndex >= 0) "编辑服务器" else "添加服务器",
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = serverName,
                onValueChange = { serverName = it },
                label = { Text("名称（如：家里/外出）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = serverHost,
                onValueChange = { serverHost = it },
                label = { Text("地址（IP 或域名）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("192.168.31.145 或 your.domain.com") }
            )

            OutlinedTextField(
                value = serverPort,
                onValueChange = { serverPort = it },
                label = { Text("端口") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            OutlinedTextField(
                value = serverToken,
                onValueChange = { serverToken = it },
                label = { Text("Token") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None
                    else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            contentDescription = "显示/隐藏"
                        )
                    }
                }
            )

            // WSS toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("SSL 加密 (wss://)", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "使用自签证书时需先配置网关",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = serverUseWss,
                    onCheckedChange = { serverUseWss = it }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val server = ServerConfig(
                            name = serverName,
                            host = serverHost,
                            port = serverPort.toIntOrNull() ?: 18790,
                            token = serverToken,
                            useWss = serverUseWss
                        )
                        if (editingIndex >= 0) {
                            viewModel.saveServer(editingIndex, server)
                            editingIndex = -1
                        } else {
                            viewModel.addServer(server)
                        }
                        serverName = ""
                        serverHost = ""
                        serverPort = "18790"
                        serverToken = ""
                        serverUseWss = false
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (editingIndex >= 0) "保存" else "添加")
                }

                if (editingIndex >= 0) {
                    OutlinedButton(
                        onClick = { editingIndex = -1 },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }
                }
            }

            // Dark mode
            HorizontalDivider()
            Text("深色模式", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (mode, label) ->
                    FilterChip(
                        selected = settings.darkMode == mode,
                        onClick = { viewModel.setDarkMode(mode) },
                        label = { Text(label) }
                    )
                }
            }

            // Navigate to chat
            if (settings.servers.any { it.host.isNotBlank() }) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onNavigateToChat,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("进入聊天")
                }
            }

            // About
            HorizontalDivider()
            Text("关于", style = MaterialTheme.typography.titleMedium)
            Text("IMRChat v${com.imr.chat.BuildConfig.VERSION_NAME} (${com.imr.chat.BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodySmall)
        }
    }
}
