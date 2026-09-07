package com.lmarena.agent.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A small, dependency-free Markdown renderer for chat bubbles. It supports the
 * markdown that LLMs most commonly emit: headings, paragraphs, bullet & ordered
 * lists, fenced code blocks, block quotes, horizontal rules, bold/italic,
 * inline code, strikethrough and hyperlinks.
 */
@Composable
fun MarkdownText(
    text: String,
    userMessage: Boolean = false,
    modifier: Modifier = Modifier
) {
    val baseColor = if (userMessage) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface
    val codeBg = if (userMessage) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    else MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier) {
        val blocks = parseBlocks(text)
        if (blocks.isEmpty()) {
            Text(
                text = markdownInline(text, baseColor, codeBg),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            blocks.forEach { block ->
                when (block) {
                    is Block.Heading -> Text(
                        text = markdownInline(block.text, baseColor, codeBg),
                        style = when (block.level) {
                            1 -> MaterialTheme.typography.headlineSmall
                            2 -> MaterialTheme.typography.titleLarge
                            else -> MaterialTheme.typography.titleMedium
                        },
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                    is Block.Paragraph -> Text(
                        text = markdownInline(block.text, baseColor, codeBg),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                    is Block.Code -> CodeBlock(block.code)
                    is Block.ListItem -> Text(
                        text = markdownInline("${block.marker} ${block.text}", baseColor, codeBg),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, top = 1.dp, bottom = 1.dp)
                    )
                    is Block.Quote -> Text(
                        text = markdownInline("▍ ${block.text}", baseColor, codeBg),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
                    )
                    is Block.Rule -> HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeBlock(code: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = code,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            modifier = Modifier.padding(10.dp)
        )
    }
}

private sealed class Block {
    data class Paragraph(val text: String) : Block()
    data class Heading(val level: Int, val text: String) : Block()
    data class ListItem(val marker: String, val text: String) : Block()
    data class Code(val code: String) : Block()
    data class Quote(val text: String) : Block()
    object Rule : Block()
}

private fun parseBlocks(text: String): List<Block> {
    val blocks = mutableListOf<Block>()
    val lines = text.trimEnd().lines()
    val para = StringBuilder()
    var inCode = false
    val codeLines = mutableListOf<String>()

    fun flushPara() {
        if (para.isNotBlank()) {
            blocks.add(Block.Paragraph(para.toString().trim()))
            para.setLength(0)
        }
    }

    for (line in lines) {
        val trimmed = line.trim()
        val fence = trimmed.startsWith("```") || trimmed.startsWith("~~~")
        if (fence) {
            flushPara()
            if (inCode) {
                blocks.add(Block.Code(codeLines.joinToString("\n").trimEnd()))
                codeLines.clear()
                inCode = false
            } else {
                inCode = true
            }
            continue
        }
        if (inCode) {
            codeLines.add(line)
            continue
        }
        if (trimmed.isEmpty()) {
            flushPara()
            continue
        }
        when {
            trimmed.startsWith("###") -> { flushPara(); blocks.add(Block.Heading(3, trimmed.drop(3).trim())) }
            trimmed.startsWith("##") -> { flushPara(); blocks.add(Block.Heading(2, trimmed.drop(2).trim())) }
            trimmed.startsWith("#") -> { flushPara(); blocks.add(Block.Heading(1, trimmed.drop(1).trim())) }
            trimmed.startsWith(">") -> { flushPara(); blocks.add(Block.Quote(trimmed.drop(1).trim())) }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
                flushPara()
                val marker = trimmed.take(2).trim()
                blocks.add(Block.ListItem("•", trimmed.drop(marker.length + 1).trim()))
            }
            else -> {
                val ordered = ORDERED_PREFIX.find(trimmed)
                if (ordered != null) {
                    flushPara()
                    blocks.add(Block.ListItem(ordered.value.trim(), trimmed.drop(ordered.value.length).trim()))
                } else if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
                    flushPara()
                    blocks.add(Block.Rule)
                } else {
                    if (para.isNotEmpty()) para.append('\n')
                    para.append(trimmed)
                }
            }
        }
    }
    if (inCode) blocks.add(Block.Code(codeLines.joinToString("\n").trimEnd()))
    flushPara()
    return blocks
}

private val ORDERED_PREFIX = Regex("""^\d{1,3}[.)]\s+""")

private fun markdownInline(text: String, baseColor: Color, codeBg: Color): AnnotatedString =
    buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            // hyperlink [label](url)
            if (c == '[') {
                val close = text.indexOf(']', i)
                if (close != -1 && close + 1 < text.length && text[close + 1] == '(') {
                    val parenClose = text.indexOf(')', close + 1)
                    if (parenClose != -1) {
                        val label = text.substring(i + 1, close)
                        withStyle(
                            SpanStyle(
                                color = baseColor,
                                fontWeight = FontWeight.Medium,
                                textDecoration = TextDecoration.Underline
                            )
                        ) { append(label) }
                        i = parenClose + 1
                        continue
                    }
                }
            }
            // bold, italic, code, strikethrough
            val opener = OPENERS.firstOrNull { text.startsWith(it, i) }
            if (opener != null) {
                val end = text.indexOf(opener, i + opener.length)
                if (end != -1) {
                    val inner = text.substring(i + opener.length, end)
                    when (opener) {
                        "**" -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(inner) }
                        "*" -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(inner) }
                        "`" -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) { append(inner) }
                        "~~" -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(inner) }
                    }
                    i = end + opener.length
                    continue
                }
            }
            withStyle(SpanStyle(color = baseColor)) { append(c.toString()) }
            i++
        }
    }

private val OPENERS = listOf("~~", "**", "`", "*")
