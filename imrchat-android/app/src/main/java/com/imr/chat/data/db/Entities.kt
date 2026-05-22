package com.imr.chat.data.db

import androidx.room.*

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "msg_id")
    val msgId: String,

    @ColumnInfo(name = "type")
    val type: String,  // "text", "image", "file", "reply"

    @ColumnInfo(name = "content")
    val content: String?,

    @ColumnInfo(name = "file_name")
    val fileName: String? = null,

    @ColumnInfo(name = "file_path")
    val filePath: String? = null,

    @ColumnInfo(name = "mime_type")
    val mimeType: String? = null,

    @ColumnInfo(name = "file_size")
    val fileSize: Long? = null,

    @ColumnInfo(name = "media_url")
    val mediaUrl: String? = null,

    @ColumnInfo(name = "is_from_user")
    val isFromUser: Boolean,

    @ColumnInfo(name = "reply_to")
    val replyTo: String? = null,

    @ColumnInfo(name = "source")
    val source: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "is_offline")
    val isOffline: Boolean = false,

    @ColumnInfo(name = "elapsed_ms")
    val elapsedMs: Long? = null,

    @ColumnInfo(name = "model_name")
    val modelName: String? = null,

    @ColumnInfo(name = "char_count")
    val charCount: Int? = null
)

@Entity(tableName = "commands")
data class CommandEntity(
    @PrimaryKey
    val name: String,

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "hint")
    val hint: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
