package com.imr.chat.ui.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.imr.chat.data.SettingsStore
import com.imr.chat.data.db.AppDatabase
import com.imr.chat.data.db.MessageEntity
import com.imr.chat.network.ConnectionState
import com.imr.chat.network.MediaSender
import com.imr.chat.network.WebSocketClient
import com.imr.chat.network.WebSocketListener
import com.imr.chat.network.protocol.CommandInfo
import com.imr.chat.service.ChatService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class ChatUiState(
    val messages: List<MessageEntity> = emptyList(),
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val commands: List<CommandInfo> = emptyList(),
    val currentInput: String = "",
    val streamingMsgId: String? = null,
    val streamingContent: String = "",
    val streamingModel: String? = null,
    val streamingStartedAt: Long? = null,
    val streamingThinkingContent: String = "",
    val thinkingExpanded: Boolean = false,
    val sendingProgress: Float? = null  // null = not sending, 0..1 = progress
)

class ChatViewModel(
    private val context: Context,
    private val database: AppDatabase,
    private val settingsStore: SettingsStore
) : ViewModel() {

    val webSocketClient = WebSocketClient()
    private val messageDao = database.messageDao()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        // Load messages from DB
        viewModelScope.launch {
            messageDao.getAllMessages().collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }

        // Setup WebSocket listener
        webSocketClient.setListener(object : WebSocketListener {
            override fun onStateChanged(state: ConnectionState) {
                _uiState.update { it.copy(connectionState = state) }
            }

            override fun onAuthResult(ok: Boolean, reason: String?) {
                if (ok) {
                    ChatService.start(context)
                } else {
                    viewModelScope.launch {
                        saveSystemMessage("认证失败: ${reason ?: "未知原因"}")
                    }
                }
            }

            override fun onReplyStart(msgId: String, replyTo: String, model: String?, startedAt: Long?) {
                _uiState.update {
                    it.copy(
                        streamingMsgId = msgId,
                        streamingContent = "",
                        streamingModel = model,
                        streamingStartedAt = startedAt ?: System.currentTimeMillis()
                    )
                }
            }

            override fun onReplyChunk(msgId: String, content: String) {
                _uiState.update {
                    it.copy(streamingContent = it.streamingContent + content)
                }
            }

            override fun onReplyEnd(msgId: String, fullContent: String, elapsedMs: Long?, model: String?, charCount: Int?) {
                viewModelScope.launch {
                    messageDao.insert(
                        MessageEntity(
                            msgId = msgId,
                            type = "reply",
                            content = fullContent,
                            isFromUser = false,
                            createdAt = System.currentTimeMillis(),
                            elapsedMs = elapsedMs,
                            modelName = model,
                            charCount = charCount
                        )
                    )
                    _uiState.update {
                        it.copy(
                            streamingMsgId = null,
                            streamingContent = "",
                            streamingModel = null,
                            streamingStartedAt = null,
                            streamingThinkingContent = "",
                            thinkingExpanded = false
                        )
                    }
                }
            }

            override fun onMediaMessage(msgId: String, url: String, text: String?) {
                viewModelScope.launch {
                    val content = text ?: "[图片]"
                    messageDao.insert(
                        MessageEntity(
                            msgId = msgId,
                            type = "image",
                            content = content,
                            mediaUrl = url,
                            isFromUser = false,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }
            }

            override fun onCommands(commands: List<CommandInfo>) {
                _uiState.update { it.copy(commands = commands) }
            }

            override fun onSystemMessage(content: String, level: String) {
                viewModelScope.launch { saveSystemMessage(content) }
            }

            override fun onError(code: String, message: String) {
                viewModelScope.launch { saveSystemMessage("错误: $message") }
            }

            override fun onOfflineMessagesStart(count: Int) {
                viewModelScope.launch { saveSystemMessage("收到 $count 条离线消息") }
            }

            override fun onOfflineDone() {
                // no-op
            }

            override fun onThinkingStart(msgId: String, replyTo: String) {
                _uiState.update {
                    it.copy(
                        streamingThinkingContent = "",
                        thinkingExpanded = false
                    )
                }
            }

            override fun onThinkingChunk(msgId: String, content: String) {
                _uiState.update {
                    it.copy(streamingThinkingContent = it.streamingThinkingContent + content)
                }
            }

            override fun onThinkingEnd(msgId: String, fullContent: String) {
                // Thinking complete — content stays visible for the reply card
                _uiState.update {
                    it.copy(streamingThinkingContent = fullContent)
                }
            }
        })

        // Initialize clientId once
        viewModelScope.launch {
            webSocketClient.clientId = settingsStore.getOrCreateClientId()
        }

        // Observe server config changes — connect/reconnect as needed
        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                val server = settings.servers.getOrNull(settings.activeServerIndex)
                if (server != null && server.host.isNotBlank()) {
                    val currentState = webSocketClient.connectionState.value
                    if (currentState == ConnectionState.DISCONNECTED) {
                        connect(server.host, server.port, server.token, server.useWss)
                    }
                }
            }
        }
    }

    fun connect(host: String, port: Int, token: String, useWss: Boolean = false) {
        webSocketClient.connect(host, port, token, useWss)
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(currentInput = text) }
    }

    fun sendMessage() {
        val content = _uiState.value.currentInput.trim()
        if (content.isEmpty()) return

        val msgId = UUID.randomUUID().toString()

        viewModelScope.launch {
            messageDao.insert(
                MessageEntity(
                    msgId = msgId,
                    type = "text",
                    content = content,
                    isFromUser = true,
                    createdAt = System.currentTimeMillis()
                )
            )
        }

        webSocketClient.sendText(content)
        _uiState.update { it.copy(currentInput = "") }
    }

    fun sendImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(sendingProgress = 0f) }
            try {
                withContext(Dispatchers.IO) {
                    MediaSender.sendImage(context, webSocketClient, uri) { progress ->
                        _uiState.update { it.copy(sendingProgress = progress) }
                    }
                }
                saveSystemMessage("图片已发送")
            } catch (e: Exception) {
                saveSystemMessage("图片发送失败: ${e.message}")
            } finally {
                _uiState.update { it.copy(sendingProgress = null) }
            }
        }
    }

    fun sendFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(sendingProgress = 0f) }
            try {
                withContext(Dispatchers.IO) {
                    MediaSender.sendFile(context, webSocketClient, uri) { progress ->
                        _uiState.update { it.copy(sendingProgress = progress) }
                    }
                }
                saveSystemMessage("文件已发送")
            } catch (e: Exception) {
                saveSystemMessage("文件发送失败: ${e.message}")
            } finally {
                _uiState.update { it.copy(sendingProgress = null) }
            }
        }
    }

    fun getFilteredCommands(query: String): List<CommandInfo> {
        if (!query.startsWith("/")) return emptyList()
        val prefix = query.lowercase()
        return _uiState.value.commands.filter {
            it.name.lowercase().startsWith(prefix)
        }
    }

    fun selectCommand(command: CommandInfo) {
        _uiState.update { it.copy(currentInput = command.name + " ") }
    }

    fun toggleThinking() {
        _uiState.update { it.copy(thinkingExpanded = !it.thinkingExpanded) }
    }

    private suspend fun saveSystemMessage(content: String) {
        messageDao.insert(
            MessageEntity(
                msgId = UUID.randomUUID().toString(),
                type = "system",
                content = content,
                isFromUser = false,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        webSocketClient.disconnect()
        ChatService.stop(context)
    }

    class Factory(
        private val context: Context,
        private val database: AppDatabase,
        private val settingsStore: SettingsStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(context, database, settingsStore) as T
        }
    }
}
