package com.imr.chat.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.gson.Gson
import com.imr.chat.network.protocol.*
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID

object MediaSender {
    private const val MAX_IMAGE_SIZE = 10 * 1024 * 1024 // 10MB
    private const val MAX_FILE_SIZE = 50 * 1024 * 1024   // 50MB
    private const val CHUNK_SIZE = 65536                  // 64KB
    private val gson = Gson()

    fun sendImage(context: Context, ws: WebSocketClient, uri: Uri, onProgress: ((Float) -> Unit)? = null): String {
        val msgId = UUID.randomUUID().toString()
        val bytes = readAndCompressImage(context, uri, MAX_IMAGE_SIZE)
        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

        if (base64.length <= CHUNK_SIZE) {
            // Small image: send directly
            val fileName = getFileName(context, uri) ?: "image.jpg"
            val msg = ImageMessage(
                msgId = msgId,
                fileName = fileName,
                mimeType = "image/jpeg",
                data = base64,
                timestamp = System.currentTimeMillis()
            )
            ws.send(gson.toJson(msg))
            onProgress?.invoke(1f)
        } else {
            // Large image: chunked transfer
            val chunks = splitIntoChunks(base64, CHUNK_SIZE)
            val fileName = getFileName(context, uri) ?: "image.jpg"

            val startMsg = ImageStartMessage(
                msgId = msgId,
                fileName = fileName,
                mimeType = "image/jpeg",
                totalSize = bytes.size.toLong(),
                totalChunks = chunks.size
            )
            ws.send(gson.toJson(startMsg))

            chunks.forEachIndexed { index, chunk ->
                val chunkMsg = ImageChunkMessage(
                    msgId = msgId,
                    chunkIndex = index,
                    data = chunk
                )
                ws.send(gson.toJson(chunkMsg))
                onProgress?.invoke((index + 1).toFloat() / chunks.size)
            }

            val endMsg = ImageEndMessage(msgId = msgId)
            ws.send(gson.toJson(endMsg))
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
                msgId = msgId,
                fileName = fileName,
                mimeType = mimeType,
                fileSize = bytes.size.toLong(),
                data = base64,
                timestamp = System.currentTimeMillis()
            )
            ws.send(gson.toJson(msg))
            onProgress?.invoke(1f)
        } else {
            val chunks = splitIntoChunks(base64, CHUNK_SIZE)

            val startMsg = FileStartMessage(
                msgId = msgId,
                fileName = fileName,
                mimeType = mimeType,
                totalSize = bytes.size.toLong(),
                totalChunks = chunks.size
            )
            ws.send(gson.toJson(startMsg))

            chunks.forEachIndexed { index, chunk ->
                val chunkMsg = FileChunkMessage(
                    msgId = msgId,
                    chunkIndex = index,
                    data = chunk
                )
                ws.send(gson.toJson(chunkMsg))
                onProgress?.invoke((index + 1).toFloat() / chunks.size)
            }

            val endMsg = FileEndMessage(msgId = msgId)
            ws.send(gson.toJson(endMsg))
        }
        return msgId
    }

    private fun readAndCompressImage(context: Context, uri: Uri, maxSize: Int): ByteArray {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("无法读取图片文件")
        val original = BitmapFactory.decodeStream(inputStream)
            ?: throw Exception("无法解码图片")
        inputStream.close()

        var quality = 90
        var output = ByteArrayOutputStream()

        do {
            output.reset()
            original.compress(Bitmap.CompressFormat.JPEG, quality, output)
            quality -= 10
        } while (output.size() > maxSize && quality > 10)

        if (output.size() > maxSize) {
            // Scale down dimensions
            val scale = Math.sqrt(maxSize.toDouble() / output.size())
            val scaled = Bitmap.createScaledBitmap(
                original,
                (original.width * scale).toInt(),
                (original.height * scale).toInt(),
                true
            )
            output.reset()
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, output)
        }

        if (!original.isRecycled) original.recycle()
        return output.toByteArray()
    }

    private fun readFile(context: Context, uri: Uri, maxSize: Int): ByteArray {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("无法读取文件")
        val bytes = inputStream.readBytes()
        inputStream.close()
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
