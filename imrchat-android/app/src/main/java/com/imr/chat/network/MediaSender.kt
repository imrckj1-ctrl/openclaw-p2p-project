package com.imr.chat.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.gson.Gson
import com.imr.chat.network.protocol.*
import java.io.ByteArrayOutputStream
import java.util.UUID

object MediaSender {
    private const val MAX_IMAGE_SIZE = 10 * 1024 * 1024 // 10MB
    private const val MAX_FILE_SIZE = 50 * 1024 * 1024   // 50MB
    private const val CHUNK_SIZE = 65536                  // 64KB
    private val gson = Gson()

    fun sendImage(context: Context, ws: WebSocketClient, uri: Uri, onProgress: ((Float) -> Unit)? = null): String {
        val msgId = UUID.randomUUID().toString()
        val (bytes, mimeType) = readImage(context, uri, MAX_IMAGE_SIZE)
        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        val fileName = getFileName(context, uri) ?: "image"

        if (base64.length <= CHUNK_SIZE) {
            val msg = ImageMessage(
                msgId = msgId,
                fileName = fileName,
                mimeType = mimeType,
                data = base64,
                timestamp = System.currentTimeMillis()
            )
            ws.send(gson.toJson(msg))
            onProgress?.invoke(1f)
        } else {
            val chunks = splitIntoChunks(base64, CHUNK_SIZE)
            val startMsg = ImageStartMessage(
                msgId = msgId,
                fileName = fileName,
                mimeType = mimeType,
                totalSize = bytes.size.toLong(),
                totalChunks = chunks.size
            )
            ws.send(gson.toJson(startMsg))
            chunks.forEachIndexed { index, chunk ->
                val chunkMsg = ImageChunkMessage(msgId = msgId, chunkIndex = index, data = chunk)
                ws.send(gson.toJson(chunkMsg))
                onProgress?.invoke((index + 1).toFloat() / chunks.size)
            }
            ws.send(gson.toJson(ImageEndMessage(msgId = msgId)))
        }
        return msgId
    }

    fun sendFile(context: Context, ws: WebSocketClient, uri: Uri, onProgress: ((Float) -> Unit)? = null): String {
        val msgId = UUID.randomUUID().toString()
        val bytes = readFile(context, uri, MAX_FILE_SIZE)
        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        val fileName = getFileName(context, uri) ?: "file"
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

        if (base64.length <= CHUNK_SIZE) {
            val msg = FileMessage(
                msgId = msgId, fileName = fileName, mimeType = mimeType,
                fileSize = bytes.size.toLong(), data = base64,
                timestamp = System.currentTimeMillis()
            )
            ws.send(gson.toJson(msg))
            onProgress?.invoke(1f)
        } else {
            val chunks = splitIntoChunks(base64, CHUNK_SIZE)
            val startMsg = FileStartMessage(
                msgId = msgId, fileName = fileName, mimeType = mimeType,
                totalSize = bytes.size.toLong(), totalChunks = chunks.size
            )
            ws.send(gson.toJson(startMsg))
            chunks.forEachIndexed { index, chunk ->
                val chunkMsg = FileChunkMessage(msgId = msgId, chunkIndex = index, data = chunk)
                ws.send(gson.toJson(chunkMsg))
                onProgress?.invoke((index + 1).toFloat() / chunks.size)
            }
            ws.send(gson.toJson(FileEndMessage(msgId = msgId)))
        }
        return msgId
    }

    private data class ImageResult(val bytes: ByteArray, val mimeType: String)

    private fun readImage(context: Context, uri: Uri, maxSize: Int): ImageResult {
        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"

        // For PNG/GIF/WebP: preserve original format, only compress if too large
        if (mimeType == "image/png" || mimeType == "image/gif" || mimeType == "image/webp") {
            val bytes = readFile(context, uri, maxSize)
            if (bytes.size <= maxSize) return ImageResult(bytes, mimeType)
            // Too large — fall through to JPEG compression
        }

        // JPEG path: decode, compress, scale as needed
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("无法读取图片文件")
        val original = try {
            BitmapFactory.decodeStream(inputStream) ?: throw Exception("无法解码图片")
        } finally {
            inputStream.close()
        }

        return try {
            var quality = 90
            val output = ByteArrayOutputStream()
            while (true) {
                output.reset()
                original.compress(Bitmap.CompressFormat.JPEG, quality, output)
                if (output.size() <= maxSize || quality <= 10) break
                quality -= 10
            }

            if (output.size() > maxSize) {
                val scale = Math.sqrt(maxSize.toDouble() / output.size())
                val scaled = Bitmap.createScaledBitmap(
                    original,
                    (original.width * scale).toInt(),
                    (original.height * scale).toInt(),
                    true
                )
                try {
                    output.reset()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 70, output)
                } finally {
                    if (!scaled.isRecycled && scaled != original) scaled.recycle()
                }
            }
            ImageResult(output.toByteArray(), "image/jpeg")
        } finally {
            if (!original.isRecycled) original.recycle()
        }
    }

    private fun readFile(context: Context, uri: Uri, maxSize: Int): ByteArray {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("无法读取文件")
        val bytes = inputStream.use { it.readBytes() }
        if (bytes.size > maxSize) throw Exception("文件超过 ${maxSize / 1024 / 1024}MB 限制")
        return bytes
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            it.moveToFirst()
            if (nameIndex >= 0) it.getString(nameIndex) else null
        }
    }

    private fun splitIntoChunks(data: String, chunkSize: Int): List<String> {
        val chunks = mutableListOf<String>()
        var offset = 0
        while (offset < data.length) {
            val end = minOf(offset + chunkSize, data.length)
            chunks.add(data.substring(offset, end))
            offset = end
        }
        return chunks
    }
}
