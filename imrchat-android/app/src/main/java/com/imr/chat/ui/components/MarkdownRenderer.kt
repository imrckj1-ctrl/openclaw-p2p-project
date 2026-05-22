package com.imr.chat.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun MarkdownRenderer(
    content: String,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = false
) {
    // Split content into code blocks and text segments
    val segments = remember(content) { parseMarkdownSegments(content) }

    Column(modifier = modifier) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.CodeBlock -> {
                    CodeBlockWidget(
                        code = segment.code,
                        language = segment.language
                    )
                }
                is MarkdownSegment.Text -> {
                    RichTextContent(segment.text, isDarkTheme)
                }
            }
        }
    }
}

sealed class MarkdownSegment {
    data class CodeBlock(val code: String, val language: String?) : MarkdownSegment()
    data class Text(val text: String) : MarkdownSegment()
}

fun parseMarkdownSegments(content: String): List<MarkdownSegment> {
    val segments = mutableListOf<MarkdownSegment>()
    val codeBlockRegex = Regex("```(\\w*)\\n([\\s\\S]*?)```")
    var lastIndex = 0

    for (match in codeBlockRegex.findAll(content)) {
        // Text before code block
        if (match.range.first > lastIndex) {
            val text = content.substring(lastIndex, match.range.first)
            if (text.isNotBlank()) {
                segments.add(MarkdownSegment.Text(text))
            }
        }
        // Code block
        val language = match.groupValues[1].ifBlank { null }
        val code = match.groupValues[2].trimEnd()
        segments.add(MarkdownSegment.CodeBlock(code, language))
        lastIndex = match.range.last + 1
    }

    // Remaining text
    if (lastIndex < content.length) {
        val text = content.substring(lastIndex)
        if (text.isNotBlank()) {
            segments.add(MarkdownSegment.Text(text))
        }
    }

    return segments
}

@Composable
fun CodeBlockWidget(code: String, language: String?) {
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
    ) {
        // Header with language and copy button
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language ?: "code",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = { clipboardManager.setText(AnnotatedString(code)) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "复制",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Code content
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
            shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
        ) {
            Text(
                text = code,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun RichTextContent(text: String, isDarkTheme: Boolean) {
    val annotatedText = remember(text) { parseRichText(text) }
    Text(
        text = annotatedText,
        style = MaterialTheme.typography.bodyMedium
    )
}

fun parseRichText(text: String): AnnotatedString {
    return buildAnnotatedString {
        val lines = text.split("\n")
        lines.forEachIndexed { index, line ->
            when {
                // Headers
                line.startsWith("### ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp)) {
                        append(line.removePrefix("### "))
                    }
                }
                line.startsWith("## ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)) {
                        append(line.removePrefix("## "))
                    }
                }
                line.startsWith("# ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp)) {
                        append(line.removePrefix("# "))
                    }
                }
                // List items
                line.startsWith("- ") || line.startsWith("* ") -> {
                    append("  • ")
                    appendFormattedText(line.removePrefix("- ").removePrefix("* "))
                }
                line.matches(Regex("^\\d+\\. .*")) -> {
                    append("  ")
                    appendFormattedText(line)
                }
                // Horizontal rule
                line.matches(Regex("^---+$")) -> {
                    withStyle(SpanStyle(color = androidx.compose.ui.graphics.Color.Gray)) {
                        append("────────────────────────────")
                    }
                }
                else -> {
                    appendFormattedText(line)
                }
            }
            if (index < lines.size - 1) {
                append("\n")
            }
        }
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendFormattedText(text: String) {
    // Handle bold (**text**) and italic (*text*) and inline code (`text`)
    val regex = Regex("\\*\\*(.+?)\\*\\*|\\*(.+?)\\*|`(.+?)`")
    var lastEnd = 0

    for (match in regex.findAll(text)) {
        // Text before match
        if (match.range.first > lastEnd) {
            append(text.substring(lastEnd, match.range.first))
        }

        when {
            match.groupValues[1].isNotEmpty() -> {
                // Bold
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(match.groupValues[1])
                }
            }
            match.groupValues[2].isNotEmpty() -> {
                // Italic
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(match.groupValues[2])
                }
            }
            match.groupValues[3].isNotEmpty() -> {
                // Inline code
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        background = androidx.compose.ui.graphics.Color.LightGray.copy(alpha = 0.3f)
                    )
                ) {
                    append(match.groupValues[3])
                }
            }
        }
        lastEnd = match.range.last + 1
    }

    // Remaining text
    if (lastEnd < text.length) {
        append(text.substring(lastEnd))
    }
}
