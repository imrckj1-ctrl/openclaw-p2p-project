package com.imr.chat.network.protocol

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

// ========== Base ==========

interface WsMessage {
    val type: String
}

// ========== APP → Server ==========

data class AuthMessage(
    val token: String,
    val clientId: String = ""
) : WsMessage {
    override val type = "auth"
}

data class TextMessage(
    val msgId: String,
    val content: String,
    val timestamp: Long,
    val source: String? = null  // "voice" for voice input
) : WsMessage {
    override val type = "text"
}

data class ImageMessage(
    val msgId: String,
    val fileName: String,
    val mimeType: String,
    val data: String,  // Base64
    val timestamp: Long
) : WsMessage {
    override val type = "image"
}

data class ImageStartMessage(
    val msgId: String,
    val fileName: String,
    val mimeType: String,
    val totalSize: Long,
    val totalChunks: Int
) : WsMessage {
    override val type = "image_start"
}

data class ImageChunkMessage(
    val msgId: String,
    val chunkIndex: Int,
    val data: String
) : WsMessage {
    override val type = "image_chunk"
}

data class ImageEndMessage(
    val msgId: String
) : WsMessage {
    override val type = "image_end"
}

data class FileMessage(
    val msgId: String,
    val fileName: String,
    val mimeType: String,
    val fileSize: Long,
    val data: String,
    val timestamp: Long
) : WsMessage {
    override val type = "file"
}

data class FileStartMessage(
    val msgId: String,
    val fileName: String,
    val mimeType: String,
    val totalSize: Long,
    val totalChunks: Int
) : WsMessage {
    override val type = "file_start"
}

data class FileChunkMessage(
    val msgId: String,
    val chunkIndex: Int,
    val data: String
) : WsMessage {
    override val type = "file_chunk"
}

data class FileEndMessage(
    val msgId: String
) : WsMessage {
    override val type = "file_end"
}

data class GetCommandsMessage(
    override val type: String = "get_commands"
) : WsMessage

// ========== Server → APP: media message (image/file from AI) ==========

data class MediaMessage(
    val msgId: String,
    val replyTo: String?,
    val url: String,
    val text: String?
) : WsMessage {
    override val type = "media"
}

// ========== Server → APP ==========

data class AuthResultMessage(
    val ok: Boolean,
    val serverVersion: String,
    val reason: String?
) : WsMessage {
    override val type = "auth_result"
}

data class ReplyStartMessage(
    val msgId: String,
    val replyTo: String,
    val model: String? = null,
    val startedAt: Long? = null
) : WsMessage {
    override val type = "reply_start"
}

data class ReplyChunkMessage(
    val msgId: String,
    val content: String
) : WsMessage {
    override val type = "reply_chunk"
}

data class ReplyEndMessage(
    val msgId: String,
    val fullContent: String,
    val elapsedMs: Long? = null,
    val model: String? = null,
    val charCount: Int? = null
) : WsMessage {
    override val type = "reply_end"
}

data class CommandInfo(
    val name: String,
    val description: String,
    val hint: String
)

data class CommandsMessage(
    val commands: List<CommandInfo>
) : WsMessage {
    override val type = "commands"
}

data class SystemMessage(
    val msgId: String,
    val content: String,
    val level: String,  // "info", "warn", "error"
    val timestamp: Long?
) : WsMessage {
    override val type = "system"
}

data class ErrorMessage(
    val msgId: String,
    val code: String,
    val message: String,
    val timestamp: Long?
) : WsMessage {
    override val type = "error"
}

data class OfflineMessagesStart(
    val count: Int
) : WsMessage {
    override val type = "offline_messages"
}

data class ThinkingStartMessage(
    val msgId: String,
    val replyTo: String
) : WsMessage {
    override val type = "thinking_start"
}

data class ThinkingChunkMessage(
    val msgId: String,
    val content: String
) : WsMessage {
    override val type = "thinking_chunk"
}

data class ThinkingEndMessage(
    val msgId: String,
    val fullContent: String
) : WsMessage {
    override val type = "thinking_end"
}

data class TypingMessage(
    val clientId: String,
    val typing: Boolean
) : WsMessage {
    override val type = "typing"
}

data class OfflineDone(
    override val type: String = "offline_done"
) : WsMessage

// ========== Parser ==========

object MessageParser {
    private val gson = Gson()

    fun parse(json: String): WsMessage? {
        return try {
            val obj = JsonParser.parseString(json).asJsonObject
            when (obj.get("type")?.asString) {
                "auth_result" -> gson.fromJson(obj, AuthResultMessage::class.java)
                "reply_start" -> gson.fromJson(obj, ReplyStartMessage::class.java)
                "reply_chunk" -> gson.fromJson(obj, ReplyChunkMessage::class.java)
                "reply_end" -> gson.fromJson(obj, ReplyEndMessage::class.java)
                "commands" -> gson.fromJson(obj, CommandsMessage::class.java)
                "system" -> gson.fromJson(obj, SystemMessage::class.java)
                "error" -> gson.fromJson(obj, ErrorMessage::class.java)
                "offline_messages" -> gson.fromJson(obj, OfflineMessagesStart::class.java)
                "offline_done" -> OfflineDone()
                "media" -> gson.fromJson(obj, MediaMessage::class.java)
                "thinking_start" -> gson.fromJson(obj, ThinkingStartMessage::class.java)
                "thinking_chunk" -> gson.fromJson(obj, ThinkingChunkMessage::class.java)
                "thinking_end" -> gson.fromJson(obj, ThinkingEndMessage::class.java)
                "typing" -> gson.fromJson(obj, TypingMessage::class.java)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun toJson(msg: WsMessage): String = gson.toJson(msg)
}
