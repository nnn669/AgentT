package com.agentt.app.ui.markdown

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentt.app.ui.markdown.MarkdownParser.Block

/**
 * Renders markdown text with formatting (ported from OpenMinis android).
 * Supports: headings, bold, italic, strikethrough, inline code, code blocks
 * (with copy), bullet/numbered/task lists, blockquotes, tables, thematic breaks
 * and clickable links.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val blocks = remember(markdown) { MarkdownParser.parse(markdown) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (block in blocks) {
            BlockContent(block, color, style)
        }
    }
}

@Composable
private fun BlockContent(block: Block, color: Color, baseStyle: TextStyle) {
    when (block) {
        is Block.Heading -> {
            val (fontSize, fontWeight) = when (block.level) {
                1 -> 22.sp to FontWeight.Bold
                2 -> 19.sp to FontWeight.Bold
                3 -> 17.sp to FontWeight.SemiBold
                4 -> 15.sp to FontWeight.SemiBold
                else -> 14.sp to FontWeight.Medium
            }
            InlineContent(block.content, color, baseStyle.copy(fontSize = fontSize, fontWeight = fontWeight))
        }

        is Block.Paragraph -> InlineContent(block.content, color, baseStyle)
        is Block.CodeBlock -> CodeBlockView(block)

        is Block.Blockquote -> Row(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .width(3.dp)
                    .padding(vertical = 2.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                block.blocks.forEach { BlockContent(it, color.copy(alpha = 0.82f), baseStyle) }
            }
        }

        is Block.BulletList -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            block.items.forEach { item ->
                Row {
                    Text("•", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    InlineContent(item.content, color, baseStyle)
                }
            }
        }

        is Block.NumberedList -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            block.items.forEachIndexed { idx, item ->
                Row {
                    Text("${block.startNumber + idx}.", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    InlineContent(item.content, color, baseStyle)
                }
            }
        }

        is Block.Table -> TableView(block, color, baseStyle)
        Block.ThematicBreak -> HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(vertical = 2.dp)
        )
    }
}

@Composable
private fun CodeBlockView(block: Block.CodeBlock) {
    val clipboard = LocalClipboardManager.current
    val bg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    Column(
        Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                block.language.ifBlank { "code" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { clipboard.setText(AnnotatedString(block.code)) },
                modifier = Modifier.width(28.dp)
            ) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = "复制代码",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(14.dp)
                )
            }
        }
        Text(
            block.code,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.horizontalScroll(rememberScrollState())
        )
    }
}

@Composable
private fun TableView(block: Block.Table, color: Color, baseStyle: TextStyle) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(4.dp)
    ) {
        Row {
            block.headers.forEach { h ->
                InlineContent(h, color, baseStyle.copy(fontWeight = FontWeight.Bold), Modifier.weight(1f).padding(4.dp))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        block.rows.forEach { row ->
            Row {
                row.forEach { cell ->
                    Text(cell, style = baseStyle.copy(fontSize = 13.sp), color = color, modifier = Modifier.weight(1f).padding(4.dp))
                }
            }
        }
    }
}

@Composable
private fun InlineContent(
    text: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, color, style, linkColor) { buildInline(text, color, style, linkColor, context) }
    Text(annotated, style = style, color = color, modifier = modifier)
}

private fun buildInline(
    text: String,
    color: Color,
    style: TextStyle,
    linkColor: Color,
    context: Context,
): AnnotatedString {
    return buildAnnotatedString {
        append(text)

        Regex("`([^`]+)`").findAll(text).forEach { m ->
            addStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, color = linkColor),
                m.range.first,
                m.range.last + 1
            )
        }

        Regex("\\*\\*([^*]+)\\*\\*").findAll(text).forEach { m ->
            addStyle(SpanStyle(fontWeight = FontWeight.Bold), m.range.first + 2, m.range.last - 1)
        }

        Regex("(?<!\\*)\\*([^*\\s][^*]*)\\*(?!\\*)").findAll(text).forEach { m ->
            addStyle(SpanStyle(fontStyle = FontStyle.Italic), m.range.first + 1, m.range.last - 1)
        }

        Regex("~~([^~]+)~~").findAll(text).forEach { m ->
            addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), m.range.first + 2, m.range.last - 2)
        }

        Regex("\\[([^\\]]+)\\]\\(([^)\\s]+)\\)").findAll(text).forEach { m ->
            val url = m.groupValues[2]
            val link = LinkAnnotation.Clickable(
                tag = url,
                styles = TextLinkStyles(style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                linkInteractionListener = LinkInteractionListener {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    true
                }
            )
            addLink(link, m.range.first, m.range.last + 1)
        }
    }
}
