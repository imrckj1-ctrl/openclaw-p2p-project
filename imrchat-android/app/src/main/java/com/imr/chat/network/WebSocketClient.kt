package com.imr.chat.network

import com.imr.chat.network.protocol.*
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, AUTHENTICATING
}

interface WebSocketListener {
    fun onStateChanged(state: ConnectionState)
    fun onAuthResult(ok: Boolean, reason: String?)
    fun onReplyStart(msgId: String, replyTo: String, model: String? = null, startedAt: Long? = null)
    fun onReplyChunk(msgId: String, content: String)
    fun onReplyEnd(msgId: String, fullContent: String, elapsedMs: Long? = null, model: String? = null, charCount: Int? = null)
    fun onMediaMessage(msgId: String, url: String, text: String?)
    fun onCommands(commands: List<CommandInfo>)
    fun onSystemMessage(content: String, level: String)
    fun onError(code: String, message: String)
    fun onOfflineMessagesStart(count: Int)
    fun onOfflineDone()
    fun onThinkingStart(msgId: String, replyTo: String)
    fun onThinkingChunk(msgId: String, content: String)
    fun onThinkingEnd(msgId: String, fullContent: String)
}

class WebSocketClient {
    private var webSocket: WebSocket? = null
    private val gson = Gson()
    private var listener: WebSocketListener? = null
    var clientId: String = ""  // persistent device ID, set before connect

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var reconnectAttempt = 0
    private val reconnectDelays = longArrayOf(3000, 5000, 10000, 30000)
    private var shouldReconnect = true
    private var lastHost: String = ""
    private var lastPort: Int = 0
    private var lastToken: String = ""
    private var lastUseWss: Boolean = false

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    fun setListener(listener: WebSocketListener) {
        this.listener = listener
    }

    fun connect(host: String, port: Int, token: String, useWss: Boolean = false) {
        if (_connectionState.value == ConnectionState.CONNECTING) return

        lastHost = host
        lastPort = port
        lastToken = token
        lastUseWss = useWss
        shouldReconnect = true
        reconnectAttempt = 0
        doConnect(host, port, token, useWss)
    }

    private fun doConnect(host: String, port: Int, token: String, useWss: Boolean) {
        val scheme = if (useWss) "wss" else "ws"
        val url = "$scheme://$host:$port"

        setState(ConnectionState.CONNECTING)

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                setState(ConnectionState.AUTHENTICATING)
                // Send auth with persistent client ID
                val auth = AuthMessage(token = token, clientId = clientId)
                webSocket.send(gson.toJson(auth))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                handleDisconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                handleDisconnect()
            }
        })
    }

    private fun handleMessage(json: String) {
        val msg = MessageParser.parse(json) ?: return

        when (msg) {
            is AuthResultMessage -> {
                if (msg.ok) {
                    setState(ConnectionState.CONNECTED)
                    reconnectAttempt = 0
                    // Request command list
                    send(gson.toJson(GetCommandsMessage()))
                }
                listener?.onAuthResult(msg.ok, msg.reason)
            }
            is ReplyStartMessage -> listener?.onReplyStart(msg.msgId, msg.replyTo, msg.model, msg.startedAt)
            is ReplyChunkMessage -> listener?.onReplyChunk(msg.msgId, msg.content)
            is ReplyEndMessage -> listener?.onReplyEnd(msg.msgId, msg.fullContent, msg.elapsedMs, msg.model, msg.charCount)
            is CommandsMessage -> listener?.onCommands(msg.commands)
            is SystemMessage -> listener?.onSystemMessage(msg.content, msg.level)
            is ErrorMessage -> listener?.onError(msg.code, msg.message)
            is OfflineMessagesStart -> listener?.onOfflineMessagesStart(msg.count)
            is OfflineDone -> listener?.onOfflineDone()
            is MediaMessage -> listener?.onMediaMessage(msg.msgId, msg.url, msg.text)
            is ThinkingStartMessage -> listener?.onThinkingStart(msg.msgId, msg.replyTo)
            is ThinkingChunkMessage -> listener?.onThinkingChunk(msg.msgId, msg.content)
            is ThinkingEndMessage -> listener?.onThinkingEnd(msg.msgId, msg.fullContent)
        }
    }

    fun send(json: String): Boolean {
        return webSocket?.send(json) ?: false
    }

    fun sendText(content: String, source: String? = null): String {
        val msgId = UUID.randomUUID().toString()
        val msg = TextMessage(
            msgId = msgId,
            content = content,
            timestamp = System.currentTimeMillis(),
            source = source
        )
        send(gson.toJson(msg))
        return msgId
    }

    fun disconnect() {
        shouldReconnect = false
        webSocket?.close(1000, "User disconnect")
        webSocket = null
        setState(ConnectionState.DISCONNECTED)
    }

    private fun handleDisconnect() {
        setState(ConnectionState.DISCONNECTED)
        if (shouldReconnect) {
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        val delay = reconnectDelays.getOrElse(reconnectAttempt) { 30000L }
        reconnectAttempt++

        Thread {
            Thread.sleep(delay)
            if (shouldReconnect && _connectionState.value == ConnectionState.DISCONNECTED) {
                doConnect(lastHost, lastPort, lastToken, lastUseWss)
            }
        }.start()
    }

    private fun setState(state: ConnectionState) {
        _connectionState.value = state
        listener?.onStateChanged(state)
    }

    fun isConnected(): Boolean = _connectionState.value == ConnectionState.CONNECTED
}
