package com.imr.chat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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

@Composable
fun MarkdownRenderer(
    content: String,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = false
) {
    val segments = remember(content) { parseMarkdownSegments(content) }

    SelectionContainer(modifier = modifier) {
        Column {
            segments.forEach { segment ->
                when (segment) {
                    is MarkdownSegment.CodeBlock -> {
                        CodeBlockWidget(
                            code = segment.code,
                            language = segment.language
                        )
                    }
                    is MarkdownSegment.Table -> {
                        TableWidget(segment)
                    }
                    is MarkdownSegment.Text -> {
                        RichTextContent(segment.text, isDarkTheme)
                    }
                }
            }
        }
    }
}

sealed class MarkdownSegment {
    data class CodeBlock(val code: String, val language: String?) : MarkdownSegment()
    data class Table(val header: List<String>, val rows: List<List<String>>) : MarkdownSegment()
    data class Text(val text: String) : MarkdownSegment()
}

fun parseMarkdownSegments(content: String): List<MarkdownSegment> {
    val segments = mutableListOf<MarkdownSegment>()
    val lines = content.split("\n")
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        when {
            // Code block start
            line.trimStart().startsWith("```") -> {
                val language = line.trimStart().removePrefix("```").trim().ifBlank { null }
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                i++ // skip closing ```
                if (codeLines.isNotEmpty()) {
                    segments.add(MarkdownSegment.CodeBlock(codeLines.joinToString("\n").trimEnd(), language))
                }
            }
            // Table detection: line contains '|' and has at least 2 columns
            isTableRow(line) -> {
                val tableLines = mutableListOf<String>()
                while (i < lines.size && isTableRow(lines[i])) {
                    tableLines.add(lines[i])
                    i++
                }
                val table = parseTable(tableLines)
                if (table != null) {
                    segments.add(table)
                } else {
                    // Fallback: treat as plain text
                    segments.add(MarkdownSegment.Text(tableLines.joinToString("\n")))
                }
            }
            else -> {
                // Collect plain text until next code block or table
                val textLines = mutableListOf<String>()
                while (i < lines.size &&
                    !lines[i].trimStart().startsWith("```") &&
                    !isTableRow(lines[i])
                ) {
                    textLines.add(lines[i])
                    i++
                }
                val text = textLines.joinToString("\n")
                if (text.isNotBlank()) {
                    segments.add(MarkdownSegment.Text(text))
                }
            }
        }
    }

    return segments
}

private fun isTableRow(line: String): Boolean {
    val trimmed = line.trim()
    return trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.count { it == '|' } >= 3
}

private fun parseTable(lines: List<String>): MarkdownSegment.Table? {
    if (lines.isEmpty()) return null

    // Filter out separator lines like |---|---|
    val dataLines = lines.filter { !it.matches(Regex("^\\|[\\s\\-:]+\\|[\\s\\-:|]+$")) }
    if (dataLines.isEmpty()) return null

    val rows = dataLines.map { line ->
        line.trim().removeSurrounding("|").split("|").map { it.trim() }
    }

    val header = rows.first()
    val body = rows.drop(1)
    return MarkdownSegment.Table(header, body)
}

@Composable
fun TableWidget(table: MarkdownSegment.Table) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val headerBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)

    Column(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
    ) {
        // Header row
        Row(
            modifier = Modifier.background(headerBg)
        ) {
            table.header.forEach { cell ->
                Text(
                    text = cell,
                    modifier = Modifier
                        .widthIn(min = 60.dp)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        // Data rows
        table.rows.forEach { row ->
            Row {
                row.forEachIndexed { idx, cell ->
                    Text(
                        text = cell,
                        modifier = Modifier
                            .widthIn(min = 60.dp)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (table.rows.last() != table.rows.last()) {
                HorizontalDivider(color = borderColor.copy(alpha = 0.3f))
            }
        }
    }
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
                line.startsWith("- ") || line.startsWith("* ") -> {
                    append("  • ")
                    appendFormattedText(line.removePrefix("- ").removePrefix("* "))
                }
                line.matches(Regex("^\\d+\\. .*")) -> {
                    append("  ")
                    appendFormattedText(line)
                }
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
    val regex = Regex("\\*\\*(.+?)\\*\\*|\\*(.+?)\\*|`(.+?)`")
    var lastEnd = 0

    for (match in regex.findAll(text)) {
        if (match.range.first > lastEnd) {
            append(text.substring(lastEnd, match.range.first))
        }

        when {
            match.groupValues[1].isNotEmpty() -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(match.groupValues[1])
                }
            }
            match.groupValues[2].isNotEmpty() -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(match.groupValues[2])
                }
            }
            match.groupValues[3].isNotEmpty() -> {
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

    if (lastEnd < text.length) {
        append(text.substring(lastEnd))
    }
}
